import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date, money } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import {
  choices,
  enumChoices,
  month,
  useWorkflowForm,
  workflowLabels as label,
} from './WorkflowForms';
export function PayrollExtras() {
  const { user } = useApp(),
    q = useQuery<Row>('/payroll-extras'),
    payroll = useQuery<Row>('/payroll'),
    accounts = useQuery<Row[]>(user?.role === 'OWNER' ? '/accounts' : null),
    form = useWorkflowForm();
  function adjustment() {
    form.open(
      'Lập khoản lương bổ sung',
      '/payroll-extras/adjustments',
      [
        {
          key: 'staffId',
          label: 'Nhân viên',
          options: choices(payroll.data?.staff || [], (r) => r.name),
        },
        { key: 'period', label: 'Kỳ lương', type: 'month', defaultValue: month() },
        {
          key: 'kind',
          label: 'Loại khoản',
          options: enumChoices(['ALLOWANCE', 'BONUS', 'DEDUCTION', 'ADVANCE']),
        },
        { key: 'amount', label: 'Số tiền (đ)', type: 'number', min: 0.01, step: 0.01 },
        { key: 'basis', label: 'Chính sách, chứng từ và căn cứ', type: 'textarea' },
      ],
      (v) => ({ ...v, amount: Number(v.amount) }),
      'Khoản chờ duyệt sẽ chặn chốt kỳ. Với tạm ứng, người duyệt xác nhận đã chi thực tế và hệ thống ghi tiền; khoản này được trừ một lần khi lập bảng lương.',
    );
  }
  return (
    <>
      <PageHeader
        title="Khoản lương & đối chiếu"
        description="Phụ cấp, thưởng, khấu trừ, tạm ứng và phản hồi yêu cầu của nhân viên."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          <div className="workflow-toolbar">
            <Button onClick={adjustment}>Lập khoản lương bổ sung</Button>
            {user?.role === 'OWNER' && (
              <Button
                className="secondary"
                onClick={() =>
                  form.open(
                    'Liên kết tài khoản xem phiếu lương',
                    '/payroll-extras/links',
                    [
                      {
                        key: 'staffId',
                        label: 'Hồ sơ nhân viên',
                        options: choices(
                          (payroll.data?.staff || []).filter((s: Row) => !s.user_id),
                          (r) => r.name,
                        ),
                      },
                      {
                        key: 'accountId',
                        label: 'Tài khoản của đúng nhân viên',
                        options: choices(
                          (accounts.data || []).filter((a) => a.role !== 'CUSTOMER' && a.enabled),
                          (r) => r.username + ' · ' + r.displayName,
                        ),
                      },
                    ],
                    (v) => v,
                    'Đối chiếu đúng người trước khi liên kết. Liên kết cấp quyền xem phiếu lương cá nhân và không được đổi sang tài khoản khác qua màn hình này.',
                  )
                }
              >
                Liên kết tài khoản nhân viên
              </Button>
            )}
          </div>
          <InfoNote>
            Duyệt tạm ứng chỉ khi đã đối chiếu khoản tiền thực chi. Lương thực trả = lương công +
            phụ cấp + thưởng − khấu trừ − tạm ứng đã duyệt. Không tự tính thuế/bảo hiểm.
          </InfoNote>
          <Table
            headers={['Nhân viên / kỳ', 'Khoản / số tiền', 'Căn cứ / trạng thái', 'Thao tác']}
            empty={!q.data?.adjustments?.length}
          >
            {q.data?.adjustments?.map((r: Row) => (
              <tr key={r.id}>
                <td>
                  {r.staff_name}
                  <small>{r.period}</small>
                </td>
                <td>
                  {label[r.kind]}
                  <small>{money(r.amount)}</small>
                </td>
                <td>
                  {r.basis}
                  <small>
                    {label[r.status]} · {r.requested_by}
                  </small>
                  <small>{r.decision_note}</small>
                </td>
                <td>
                  {r.status === 'PENDING' && r.requested_by !== user?.username && (
                    <div className="workflow-actions">
                      <Button
                        onClick={() =>
                          form.decide(
                            r.kind === 'ADVANCE' ? 'Xác nhận đã chi tạm ứng' : 'Duyệt khoản lương',
                            '/payroll-extras/adjustments/' + r.id + '/decide',
                            true,
                            r.kind === 'ADVANCE'
                              ? 'Nhập chứng từ thực chi; thao tác này ghi một khoản chi tạm ứng vào sổ tiền.'
                              : undefined,
                          )
                        }
                      >
                        {r.kind === 'ADVANCE' ? 'Xác nhận đã chi' : 'Duyệt khoản'}
                      </Button>
                      <Button
                        className="secondary"
                        onClick={() =>
                          form.decide(
                            'Từ chối khoản lương',
                            '/payroll-extras/adjustments/' + r.id + '/decide',
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
          <h2>Đề nghị đối chiếu lương</h2>
          <Table
            headers={['Nhân viên / kỳ', 'Đề nghị', 'Phản hồi', 'Thao tác']}
            empty={!q.data?.questions?.length}
          >
            {q.data?.questions?.map((r: Row) => (
              <tr key={r.id}>
                <td>
                  {r.staff_name}
                  <small>{r.period}</small>
                </td>
                <td>
                  {r.question}
                  <small>{date(r.created_at, true)}</small>
                </td>
                <td>
                  {label[r.status]}
                  <small>{r.answer}</small>
                </td>
                <td>
                  {r.status === 'OPEN' && r.requested_by !== user?.username && (
                    <Button
                      onClick={() =>
                        form.open(
                          'Phản hồi đối chiếu lương',
                          '/payroll-extras/questions/' + r.id + '/answer',
                          [
                            {
                              key: 'answer',
                              label: 'Kết quả kiểm tra và cách xử lý',
                              type: 'textarea',
                            },
                          ],
                          (v) => v,
                          'Phản hồi không tự sửa bảng lương đã duyệt.',
                        )
                      }
                    >
                      Phản hồi
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </Table>
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
export function MyPayslips() {
  const q = useQuery<Row>('/payroll-extras/mine'),
    form = useWorkflowForm();
  return (
    <>
      <PageHeader
        title="Phiếu lương của tôi"
        description="Căn cứ tính lương đã được duyệt và kết quả đối chiếu dành riêng cho bạn."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          {!q.data?.payroll?.length && (
            <p>
              Chưa có phiếu lương đã duyệt hoặc tài khoản chưa được liên kết hồ sơ nhân viên. Liên
              hệ chủ cửa hàng để đối chiếu.
            </p>
          )}
          {q.data?.payroll?.map((p: Row) => (
            <article className="guide-card" key={p.id}>
              <h2>
                Kỳ {p.period} · {p.staff_name}
              </h2>
              <p>
                Thực trả: <strong>{money(p.amount)}</strong> ·{' '}
                {p.status === 'PAID' ? 'Đã chi / tất toán' : 'Đã duyệt, chờ chi'}
              </p>
              <Table headers={['Khoản', 'Số tiền', 'Căn cứ']}>
                {p.lines.map((l: Row, i: number) => (
                  <tr key={i}>
                    <td>{label[l.kind] || l.kind}</td>
                    <td>{money(l.amount)}</td>
                    <td>{l.basis}</td>
                  </tr>
                ))}
              </Table>
              <Button
                className="secondary"
                onClick={() =>
                  form.open(
                    'Đề nghị kiểm tra phiếu lương',
                    '/payroll-extras/questions',
                    [{ key: 'question', label: 'Nội dung cần đối chiếu', type: 'textarea' }],
                    (v) => ({ ...v, payrollId: p.id }),
                  )
                }
              >
                Đề nghị kiểm tra phiếu lương
              </Button>
            </article>
          ))}
          <h2>Đề nghị của tôi</h2>
          {q.data?.questions?.map((r: Row) => (
            <article className="guide-card" key={r.id}>
              <strong>
                Kỳ {r.period} · {label[r.status]}
              </strong>
              <p>{r.question}</p>
              <p>{r.answer || 'Đang chờ phản hồi.'}</p>
            </article>
          ))}
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
