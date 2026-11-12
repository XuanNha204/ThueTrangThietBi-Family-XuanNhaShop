import { useState } from 'react';
import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date, money } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import {
  choices,
  enumChoices,
  optional,
  today,
  useWorkflowForm,
  workflowLabels as label,
} from './WorkflowForms';
export function Accounting() {
  const { user } = useApp(),
    q = useQuery<Row>('/accounting'),
    stock = useQuery<Row>('/stock');
  const form = useWorkflowForm(),
    [tab, setTab] = useState('expenses');
  function expense() {
    form.open(
      'Lập khoản chi phí / phải trả',
      '/accounting/expenses',
      [
        {
          key: 'category',
          label: 'Loại chi phí',
          options: enumChoices(['PURCHASE', 'MAINTENANCE', 'CLEANING', 'TRANSPORT', 'OTHER']),
        },
        {
          key: 'supplierId',
          label: 'Nhà cung cấp',
          options: choices(q.data?.suppliers || [], (r) => r.name),
          required: false,
        },
        {
          key: 'purchaseReceiptId',
          label: 'Lô mua đã tiếp nhận',
          options: choices(
            (stock.data?.receipts || []).filter((r: Row) => r.status === 'ACCEPTED'),
            (r) =>
              `${r.document_ref} · ${r.supplier_name} · ${money(Number(r.unit_cost) * Number(r.quantity))}`,
          ),
          required: false,
          help: 'Bắt buộc với khoản mua thiết bị; nhập đúng tổng giá trị lô.',
        },
        {
          key: 'equipmentId',
          label: 'Thiết bị liên quan',
          options: choices(q.data?.equipment || [], (r) => r.code + ' · ' + r.name),
          required: false,
        },
        {
          key: 'incurredOn',
          label: 'Ngày phát sinh',
          type: 'date',
          defaultValue: today(),
          max: today(),
        },
        { key: 'dueOn', label: 'Hạn phải trả', type: 'date', defaultValue: today() },
        { key: 'amount', label: 'Giá trị phải trả (đ)', type: 'number', min: 0.01, step: 0.01 },
        { key: 'documentRef', label: 'Chứng từ / nơi lưu hồ sơ' },
        { key: 'description', label: 'Nội dung và căn cứ phân loại', type: 'textarea' },
      ],
      (v) => ({
        ...v,
        supplierId: optional(v.supplierId),
        purchaseReceiptId: optional(v.purchaseReceiptId),
        equipmentId: optional(v.equipmentId),
        amount: Number(v.amount),
      }),
      'Lập khoản phải trả chưa ghi chi tiền. Người khác duyệt, sau đó ghi từng khoản chi thực tế.',
    );
  }
  function reconcile() {
    form.open(
      'Đối soát ca / ngày',
      '/accounting/reconciliations',
      [
        { key: 'startsAt', label: 'Bắt đầu ca', type: 'datetime-local' },
        { key: 'endsAt', label: 'Kết thúc ca', type: 'datetime-local' },
        { key: 'openingBalance', label: 'Số dư đầu ca tổng hợp (đ)', type: 'number', step: 0.01 },
        {
          key: 'countedBalance',
          label: 'Số dư cuối ca kiểm đếm / đối chiếu (đ)',
          type: 'number',
          step: 0.01,
        },
        {
          key: 'reason',
          label: 'Phạm vi tài khoản, căn cứ kiểm đếm và chênh lệch',
          type: 'textarea',
        },
      ],
      (v) => ({
        ...v,
        startsAt: new Date(v.startsAt).toISOString(),
        endsAt: new Date(v.endsAt).toISOString(),
        openingBalance: Number(v.openingBalance),
        countedBalance: Number(v.countedBalance),
      }),
      'Đối chiếu sổ tổng hợp mọi kênh thanh toán, gồm cả tiền cọc. Chưa tách quỹ tiền mặt/từng ngân hàng. Chọn ca đã kết thúc, tối đa 48 giờ; chênh lệch không tự tạo bút toán điều chỉnh.',
    );
  }
  return (
    <>
      <PageHeader
        title="Chi phí & đối soát"
        description="Chứng từ, phê duyệt, công nợ nhà cung cấp và tiền thực chi."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          <div className="workflow-toolbar">
            {['expenses', 'suppliers', 'reconciliations'].map((x, i) => (
              <Button key={x} className={x === tab ? '' : 'secondary'} onClick={() => setTab(x)}>
                {['Chi phí / phải trả', 'Nhà cung cấp / công nợ', 'Đối soát ca / ngày'][i]}
              </Button>
            ))}
          </div>
          {tab === 'expenses' && (
            <>
              <Button onClick={expense}>Lập khoản chi phí / phải trả</Button>
              <InfoNote>
                Chỉ bấm ghi chi sau khi đối chiếu chứng từ tiền đã trả. Hệ thống ghi sổ, không gửi
                lệnh chuyển tiền ngân hàng.
              </InfoNote>
              <Table
                headers={['Nội dung / chứng từ', 'Giá trị / đã trả', 'Trạng thái', 'Thao tác']}
                empty={!q.data?.expenses?.length}
              >
                {q.data?.expenses?.map((r: Row) => (
                  <tr key={r.id}>
                    <td>
                      <strong>{label[r.category]}</strong> · {r.description}
                      <small>
                        {r.document_ref} · {r.supplier_name || 'Không gắn nhà cung cấp'}
                      </small>
                      <small>
                        Phát sinh {date(r.incurred_on)} · Hạn trả {date(r.due_on)}
                      </small>
                    </td>
                    <td>
                      {money(r.amount)}
                      <small>Đã trả {money(r.paid)}</small>
                      <small>Còn {money(Number(r.amount) - Number(r.paid))}</small>
                    </td>
                    <td>
                      {label[r.status]}
                      <small>{r.requested_by}</small>
                      <small>{r.decision_note}</small>
                    </td>
                    <td>
                      <div className="workflow-actions">
                        {r.status === 'PENDING' && r.requested_by !== user?.username && (
                          <>
                            <Button
                              onClick={() =>
                                form.decide(
                                  'Duyệt chi phí',
                                  '/accounting/expenses/' + r.id + '/decide',
                                  true,
                                )
                              }
                            >
                              Duyệt chi phí
                            </Button>
                            <Button
                              className="secondary"
                              onClick={() =>
                                form.decide(
                                  'Từ chối chi phí',
                                  '/accounting/expenses/' + r.id + '/decide',
                                  false,
                                )
                              }
                            >
                              Từ chối
                            </Button>
                          </>
                        )}
                        {r.status === 'APPROVED' && Number(r.paid) < Number(r.amount) && (
                          <Button
                            onClick={() =>
                              form.open(
                                'Ghi nhận khoản đã chi',
                                '/accounting/expenses/' + r.id + '/pay',
                                [
                                  {
                                    key: 'amount',
                                    label: 'Số tiền thực chi (đ)',
                                    type: 'number',
                                    min: 0.01,
                                    step: 0.01,
                                    max: Number(r.amount) - Number(r.paid),
                                    defaultValue: Number(r.amount) - Number(r.paid),
                                  },
                                  {
                                    key: 'evidence',
                                    label: 'Chứng từ chi thực tế',
                                    type: 'textarea',
                                  },
                                ],
                                (v) => ({ ...v, amount: Number(v.amount) }),
                                'Xác nhận khoản tiền đã trả; không nhập lại giao dịch đã ghi nhận.',
                              )
                            }
                          >
                            Ghi khoản đã chi
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </Table>
            </>
          )}
          {tab === 'suppliers' && (
            <>
              <Button
                onClick={() =>
                  form.open('Thêm nhà cung cấp', '/accounting/suppliers', [
                    { key: 'name', label: 'Tên nhà cung cấp' },
                    { key: 'contact', label: 'Đầu mối liên hệ' },
                    { key: 'documentRef', label: 'Mã hồ sơ / căn cứ nhận dạng' },
                  ])
                }
              >
                Thêm nhà cung cấp
              </Button>
              <Table
                headers={[
                  'Nhà cung cấp',
                  'Liên hệ / hồ sơ',
                  'Phải trả đã duyệt',
                  'Đã trả / còn nợ',
                ]}
                empty={!q.data?.suppliers?.length}
              >
                {q.data?.suppliers?.map((r: Row) => {
                  const debt = q.data?.payables?.find((p: Row) => p.id === r.id);
                  return (
                    <tr key={r.id}>
                      <td>{r.name}</td>
                      <td>
                        {r.contact}
                        <small>{r.document_ref}</small>
                      </td>
                      <td>{money(debt?.total || 0)}</td>
                      <td>
                        {money(debt?.paid || 0)}
                        <small>
                          Còn {money(Number(debt?.total || 0) - Number(debt?.paid || 0))}
                        </small>
                      </td>
                    </tr>
                  );
                })}
              </Table>
            </>
          )}
          {tab === 'reconciliations' && (
            <>
              <Button onClick={reconcile}>Đối soát ca / ngày</Button>
              <Table
                headers={[
                  'Khoảng / người lập',
                  'Số dư kỳ vọng / thực tế',
                  'Chênh lệch / kết luận',
                  'Thao tác',
                ]}
                empty={!q.data?.reconciliations?.length}
              >
                {q.data?.reconciliations?.map((r: Row) => (
                  <tr key={r.id}>
                    <td>
                      {date(r.starts_at, true)} → {date(r.ends_at, true)}
                      <small>{r.requested_by}</small>
                    </td>
                    <td>
                      {money(r.expected_balance)}
                      <small>
                        Thực tế {money(r.counted_balance)} · Đầu ca {money(r.opening_balance)}
                      </small>
                    </td>
                    <td>
                      <strong>{money(r.difference)}</strong>
                      <small>{label[r.status]}</small>
                      <p>{r.reason}</p>
                      <small>{r.decision_note}</small>
                    </td>
                    <td>
                      {r.status === 'PENDING' && r.requested_by !== user?.username && (
                        <div className="workflow-actions">
                          <Button
                            onClick={() =>
                              form.decide(
                                'Duyệt kết luận đối soát',
                                '/accounting/reconciliations/' + r.id + '/decide',
                                true,
                                'Ghi rõ cách xử lý chênh lệch nếu có. Phê duyệt không tự điều chỉnh tiền.',
                              )
                            }
                          >
                            Duyệt kết luận
                          </Button>
                          <Button
                            className="secondary"
                            onClick={() =>
                              form.decide(
                                'Từ chối đối soát',
                                '/accounting/reconciliations/' + r.id + '/decide',
                                false,
                              )
                            }
                          >
                            Từ chối
                          </Button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </Table>
            </>
          )}
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
