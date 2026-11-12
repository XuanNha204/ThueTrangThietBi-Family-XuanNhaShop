import { useState } from 'react';
import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date, money } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import { choices, today, useWorkflowForm, workflowLabels as label } from './WorkflowForms';
export function StockOperations() {
  const { user } = useApp(),
    q = useQuery<Row>('/stock'),
    catalog = useQuery<Row[]>('/catalog');
  const assets = useQuery<Row[]>(user?.role === 'ACCOUNTANT' ? null : '/equipment');
  const form = useWorkflowForm(),
    [tab, setTab] = useState('receipts');
  const owner = user?.role === 'OWNER',
    financial = owner || user?.role === 'ACCOUNTANT';
  const warehouses = choices(q.data?.warehouses || [], (r) => r.name);
  const equipment = choices(
    (assets.data || []).filter((e) => e.state !== 'DISPOSED'),
    (r) => `${r.code} · ${r.name} · ${r.warehouse}`,
  );
  function receive() {
    form.open(
      'Tiếp nhận lô thiết bị',
      '/stock/receipts',
      [
        { key: 'warehouseId', label: 'Kho tiếp nhận', options: warehouses },
        {
          key: 'typeId',
          label: 'Loại thiết bị',
          options: choices(catalog.data || [], (r) => r.name),
        },
        { key: 'supplierName', label: 'Nhà cung cấp' },
        { key: 'documentRef', label: 'Số chứng từ / nơi lưu hồ sơ' },
        {
          key: 'acquiredOn',
          label: 'Ngày nhận',
          type: 'date',
          defaultValue: today(),
          max: today(),
        },
        { key: 'warrantyUntil', label: 'Hạn bảo hành', type: 'date', required: false },
        {
          key: 'unitCost',
          label: 'Giá mua mỗi thiết bị (đ)',
          type: 'number',
          min: 0.01,
          step: 0.01,
        },
        {
          key: 'usefulLifeMonths',
          label: 'Thời gian phân bổ giá mua (tháng)',
          type: 'number',
          min: 0,
          max: 600,
          defaultValue: 0,
          help: 'Nhập 0 nếu chưa xác định; báo cáo sẽ cảnh báo thiếu căn cứ phân bổ.',
        },
        {
          key: 'lines',
          label: 'Các mã thiết bị và số sê-ri',
          type: 'textarea',
          help: 'Mỗi dòng: MÃ-THIẾT-BỊ | SỐ-SÊ-RI. Tối đa 100 mã. Nếu không có sê-ri, ghi căn cứ nhận dạng.',
        },
        { key: 'reason', label: 'Căn cứ đối chiếu tiếp nhận', type: 'textarea' },
      ],
      (v) => ({
        ...v,
        warrantyUntil: v.warrantyUntil || null,
        usefulLifeMonths: Number(v.usefulLifeMonths),
        unitCost: Number(v.unitCost),
        lines: v.lines
          .split('\n')
          .filter((x) => x.trim())
          .map((x) => {
            const [code, ...serial] = x.split('|');
            return { code: code.trim(), serialNumber: serial.join('|').trim() };
          }),
      }),
      'Người khác có quyền chủ cửa hàng duyệt lô. Mã mới ở trạng thái Chờ nghiệm thu; chưa tự ghi khoản chi hoặc công nợ.',
    );
  }
  function action(kind: string) {
    form.open(
      kind === 'TRANSFER' ? 'Lập phiếu chuyển kho' : 'Đề nghị thanh lý',
      '/stock/actions',
      [
        { key: 'equipmentId', label: 'Thiết bị', options: equipment },
        ...(kind === 'TRANSFER'
          ? [{ key: 'toWarehouse', label: 'Kho đích', options: warehouses }]
          : [
              {
                key: 'recoveryValue',
                label: 'Giá trị thu hồi dự kiến (đ)',
                type: 'number',
                min: 0,
                step: 0.01,
                defaultValue: 0,
              },
            ]),
        { key: 'reason', label: 'Lý do và căn cứ', type: 'textarea' },
      ],
      (v) => ({
        ...v,
        kind,
        toWarehouse: v.toWarehouse || null,
        recoveryValue: Number(v.recoveryValue || 0),
      }),
      'Thiết bị phải hết nghĩa vụ thuê/bảo trì. Trong khi chờ xác nhận, thiết bị tạm ngừng khai thác. Giá trị dự kiến không phải khoản tiền đã thu.',
    );
  }
  function count() {
    form.open(
      'Lập biên bản kiểm kê',
      '/stock/counts',
      [
        { key: 'warehouseId', label: 'Kho kiểm kê', options: warehouses },
        {
          key: 'observedCodes',
          label: 'Mã quan sát thực tế tại kho',
          type: 'textarea',
          required: false,
          help: 'Mỗi dòng một mã; để trống khi không tìm thấy mã nào. Thiết bị đang thuê/chuẩn bị/chuyển kho/gửi bảo hành được loại khỏi danh sách dự kiến.',
        },
        { key: 'reason', label: 'Thời điểm, người kiểm và căn cứ', type: 'textarea' },
      ],
      (v) => ({
        ...v,
        observedCodes: v.observedCodes
          .split('\n')
          .map((x) => x.trim())
          .filter(Boolean),
      }),
      'Chủ cửa hàng khác người lập duyệt chênh lệch. Mã thiếu bị ngừng khai thác; mã thừa không tự tạo tài sản hoặc ghi tiền.',
    );
  }
  return (
    <>
      <PageHeader
        title="Kho & tiếp nhận"
        description="Theo dõi từng mã, vị trí, hồ sơ nhận và quyết định biến động tài sản."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          <div className="workflow-toolbar">
            {['receipts', 'actions', 'counts', 'warehouses'].map((x, i) => (
              <Button key={x} className={tab === x ? '' : 'secondary'} onClick={() => setTab(x)}>
                {['Tiếp nhận theo lô', 'Chuyển kho / thanh lý', 'Kiểm kê', 'Điểm phục vụ / kho'][i]}
              </Button>
            ))}
          </div>
          {tab === 'receipts' && (
            <>
              {financial && <Button onClick={receive}>Tiếp nhận lô thiết bị</Button>}
              <Table
                headers={[
                  'Lô / chứng từ',
                  'Kho / loại',
                  'Số lượng / giá',
                  'Trạng thái',
                  'Thao tác',
                ]}
                empty={!q.data?.receipts?.length}
              >
                {q.data?.receipts?.map((r: Row) => (
                  <tr key={r.id}>
                    <td>
                      {r.document_ref}
                      <small>{r.supplier_name}</small>
                      <details>
                        <summary>Chi tiết mã tiếp nhận</summary>
                        {r.lines.map((l: Row) => (
                          <p key={l.id}>
                            {l.code} · {l.serial_number}
                          </p>
                        ))}
                        <p>{r.reason}</p>
                        <p>{r.decision_note}</p>
                      </details>
                    </td>
                    <td>
                      {r.warehouse}
                      <small>{r.type_name}</small>
                    </td>
                    <td>
                      {r.quantity} mã
                      {financial && (
                        <small>
                          {money(r.unit_cost)} / mã · Tổng{' '}
                          {money(Number(r.unit_cost) * Number(r.quantity))}
                        </small>
                      )}
                    </td>
                    <td>
                      {label[r.status]}
                      <small>
                        {r.requested_by} · {date(r.created_at, true)}
                      </small>
                    </td>
                    <td>
                      {owner && r.status === 'PENDING' && r.requested_by !== user?.username && (
                        <div className="workflow-actions">
                          <Button
                            onClick={() =>
                              form.decide(
                                'Duyệt tiếp nhận lô',
                                '/stock/receipts/' + r.id + '/decide',
                                true,
                                'Xác nhận đã đối chiếu đủ mã, số lượng, giá trị và chứng từ. Thiết bị vẫn cần kiểm tra an toàn.',
                              )
                            }
                          >
                            Duyệt nhận lô
                          </Button>
                          <Button
                            className="secondary"
                            onClick={() =>
                              form.decide(
                                'Từ chối lô',
                                '/stock/receipts/' + r.id + '/decide',
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
          {tab === 'actions' && (
            <>
              <div className="workflow-toolbar">
                {user?.role !== 'ACCOUNTANT' && (
                  <Button onClick={() => action('TRANSFER')}>Lập phiếu chuyển kho</Button>
                )}
                {['OWNER', 'MANAGER'].includes(user!.role) && (
                  <Button className="secondary" onClick={() => action('DISPOSE')}>
                    Đề nghị thanh lý
                  </Button>
                )}
              </div>
              <Table
                headers={['Thiết bị / loại phiếu', 'Vị trí', 'Căn cứ', 'Trạng thái', 'Thao tác']}
                empty={!q.data?.actions?.length}
              >
                {q.data?.actions?.map((r: Row) => (
                  <tr key={r.id}>
                    <td>
                      {r.code}
                      <small>{label[r.kind]}</small>
                    </td>
                    <td>
                      {r.from_name}
                      {r.to_name && <> → {r.to_name}</>}
                    </td>
                    <td>
                      {r.reason}
                      {financial && r.kind === 'DISPOSE' && (
                        <small>Dự kiến thu hồi: {money(r.recovery_value)}</small>
                      )}
                      <small>{r.decision_note}</small>
                    </td>
                    <td>
                      {label[r.status]}
                      <small>{r.requested_by}</small>
                    </td>
                    <td>
                      {r.status === 'PENDING' &&
                        r.requested_by !== user?.username &&
                        (owner || (r.kind === 'TRANSFER' && user?.role === 'INVENTORY')) && (
                          <div className="workflow-actions">
                            <Button
                              onClick={() =>
                                form.decide(
                                  r.kind === 'TRANSFER'
                                    ? 'Xác nhận đã nhận tại kho đích'
                                    : 'Duyệt thanh lý',
                                  '/stock/actions/' + r.id + '/decide',
                                  true,
                                )
                              }
                            >
                              {r.kind === 'TRANSFER' ? 'Xác nhận nhận' : 'Duyệt thanh lý'}
                            </Button>
                            <Button
                              className="secondary"
                              onClick={() =>
                                form.decide(
                                  'Từ chối phiếu',
                                  '/stock/actions/' + r.id + '/decide',
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
          {tab === 'counts' && (
            <>
              {user?.role !== 'ACCOUNTANT' && <Button onClick={count}>Lập biên bản kiểm kê</Button>}
              <InfoNote>
                Danh sách dự kiến là các mã đang được ghi nhận tại kho, không gồm mã đang thuê,
                chuẩn bị, đã thanh lý, đang gửi bảo hành hoặc chờ phiếu chuyển/thanh lý. Biến động
                sau lúc lập làm biên bản phải kiểm kê lại.
              </InfoNote>
              <Table
                headers={['Kho / thời điểm', 'Chênh lệch', 'Kết luận', 'Thao tác']}
                empty={!q.data?.counts?.length}
              >
                {q.data?.counts?.map((r: Row) => {
                  const unexpected = r.observed_codes
                    .split('\n')
                    .filter((c: string) => c && !r.lines.some((l: Row) => l.code === c));
                  return (
                    <tr key={r.id}>
                      <td>
                        {r.warehouse}
                        <small>
                          {date(r.created_at, true)} · {r.requested_by}
                        </small>
                      </td>
                      <td>
                        Dự kiến {r.lines.length}; thiếu{' '}
                        {r.lines.filter((l: Row) => !l.found).length}; ngoài danh sách{' '}
                        {unexpected.length}
                        <details>
                          <summary>Đối chiếu từng mã</summary>
                          {r.lines.map((l: Row) => (
                            <p key={l.equipment_id}>
                              {l.code}: {l.found ? 'Có tại kho' : 'Chưa tìm thấy'}
                            </p>
                          ))}
                          {unexpected.map((c: string) => (
                            <p key={c}>{c}: ngoài danh sách — cần xác minh riêng</p>
                          ))}
                        </details>
                      </td>
                      <td>
                        {label[r.status]}
                        <p>{r.reason}</p>
                        <small>{r.decision_note}</small>
                      </td>
                      <td>
                        {owner && r.status === 'PENDING' && r.requested_by !== user?.username && (
                          <div className="workflow-actions">
                            <Button
                              onClick={() =>
                                form.decide(
                                  'Duyệt kết luận kiểm kê',
                                  '/stock/counts/' + r.id + '/decide',
                                  true,
                                )
                              }
                            >
                              Duyệt kết luận
                            </Button>
                            <Button
                              className="secondary"
                              onClick={() =>
                                form.decide(
                                  'Từ chối kiểm kê',
                                  '/stock/counts/' + r.id + '/decide',
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
                  );
                })}
              </Table>
            </>
          )}
          {tab === 'warehouses' && (
            <>
              {owner && (
                <Button
                  onClick={() =>
                    form.open('Thêm điểm phục vụ / kho', '/stock/warehouses', [
                      { key: 'name', label: 'Tên điểm phục vụ / kho' },
                      { key: 'address', label: 'Địa chỉ', type: 'textarea' },
                    ])
                  }
                >
                  Thêm điểm phục vụ / kho
                </Button>
              )}
              <InfoNote>
                Quản lý tập trung các kho. Tra lịch thuê dùng tổng tài sản đủ điều kiện; chưa phân
                quyền nhân viên theo từng chi nhánh.
              </InfoNote>
              <Table headers={['Điểm phục vụ / kho', 'Địa chỉ', 'Thiết bị chưa thanh lý']}>
                {q.data?.warehouses?.map((w: Row) => (
                  <tr key={w.id}>
                    <td>{w.name}</td>
                    <td>{w.address}</td>
                    <td>{w.asset_count}</td>
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
