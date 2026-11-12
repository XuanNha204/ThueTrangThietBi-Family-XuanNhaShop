import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import { choices, today, useWorkflowForm } from './WorkflowForms';
export function CustomerVerification() {
  const { user } = useApp(),
    q = useQuery<Row>('/verifications'),
    customers = useQuery<Row[]>('/customers'),
    form = useWorkflowForm(),
    p = q.data?.policy;
  const can = ['OWNER', 'MANAGER'].includes(user!.role);
  function policy() {
    form.open(
      'Công bố chính sách xác minh',
      '/verifications/policy',
      [
        {
          key: 'required',
          label: 'Bắt buộc trước xác nhận / bàn giao',
          options: [
            { value: 'true', label: 'Bắt buộc xác minh' },
            { value: 'false', label: 'Chưa bắt buộc' },
          ],
          defaultValue: String(p?.required || false),
        },
        { key: 'title', label: 'Tên chính sách', defaultValue: p?.title },
        {
          key: 'criteria',
          label: 'Tiêu chí và cách đối chiếu được công bố',
          type: 'textarea',
          defaultValue: p?.criteria,
        },
        { key: 'reason', label: 'Căn cứ áp dụng', type: 'textarea' },
      ],
      (v) => ({ ...v, required: v.required === 'true', expectedVersion: p?.version || 0 }),
      'Phiên bản mới làm kết quả xác minh theo chính sách cũ không còn đủ điều kiện. Nội dung sẽ hiển thị cho khách khi đặt thuê.',
    );
  }
  function verify() {
    form.open(
      'Ghi kết quả xác minh khách',
      '/verifications',
      [
        {
          key: 'customerId',
          label: 'Khách hàng',
          options: choices(customers.data || [], (r) => r.name + ' · ' + r.phone),
        },
        {
          key: 'passed',
          label: 'Kết quả đối chiếu mọi tiêu chí',
          options: [
            { value: 'true', label: 'Đạt điều kiện' },
            { value: 'false', label: 'Chưa đạt điều kiện' },
          ],
        },
        { key: 'method', label: 'Cách xác minh thực tế' },
        {
          key: 'referenceHint',
          label: 'Mã tham chiếu đã che',
          help: 'Chỉ lưu tham chiếu cần thiết, ví dụ ****1234; không nhập toàn bộ số giấy tờ hoặc ảnh giấy tờ.',
        },
        { key: 'note', label: 'Căn cứ và kết luận theo chính sách', type: 'textarea' },
        { key: 'validUntil', label: 'Hiệu lực đến', type: 'date', min: today() },
      ],
      (v) => ({ ...v, passed: v.passed === 'true', policyVersion: p?.version }),
      'Chỉ ghi sau khi đã kiểm tra thực tế; đây không phải xác minh danh tính tự động. Hồ sơ khách thay đổi sẽ cần xác minh lại.',
    );
  }
  return (
    <>
      <PageHeader
        title="Xác minh điều kiện thuê"
        description="Công bố tiêu chí và lưu căn cứ đối chiếu trước khi cam kết giao thiết bị."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          <h2>{p?.title || 'Chưa công bố chính sách'}</h2>
          <p>
            Phiên bản {p?.version || 0} · {p?.required ? 'Bắt buộc xác minh' : 'Chưa bật bắt buộc'}
          </p>
          <p className="preserve-lines">{p?.criteria}</p>
          <div className="workflow-toolbar">
            {user?.role === 'OWNER' && (
              <Button className="secondary" onClick={policy}>
                Công bố chính sách
              </Button>
            )}
            {can && Number(p?.version) > 0 && (
              <Button onClick={verify}>Ghi kết quả xác minh khách</Button>
            )}
          </div>
          <InfoNote>
            Khi bật bắt buộc, khách vẫn có thể tạo giữ chỗ; cửa hàng chỉ xác nhận hoặc bàn giao sau
            khi khách có kết quả đạt còn hiệu lực theo phiên bản hiện hành.
          </InfoNote>
          <Table
            headers={['Khách hàng', 'Kết quả / hiệu lực', 'Căn cứ', 'Người xác minh']}
            empty={!q.data?.verifications?.length}
          >
            {q.data?.verifications?.map((r: Row) => (
              <tr key={r.id}>
                <td>{r.customer_name}</td>
                <td>
                  {r.passed ? 'Đạt' : 'Chưa đạt'}
                  <small>
                    Đến {date(r.valid_until)} · chính sách {r.policy_version}
                  </small>
                  <small>Kết quả lịch sử; cần đối chiếu phiên bản hồ sơ hiện tại.</small>
                </td>
                <td>
                  {r.method}
                  <small>{r.reference_hint}</small>
                  <p>{r.note}</p>
                </td>
                <td>
                  {r.verified_by}
                  <small>{date(r.verified_at, true)}</small>
                </td>
              </tr>
            ))}
          </Table>
          <details>
            <summary>Lịch sử chính sách</summary>
            {q.data?.history?.map((r: Row) => (
              <article key={r.version}>
                <h3>
                  Phiên bản {r.version}: {r.title}
                </h3>
                <p className="preserve-lines">{r.criteria}</p>
                <p>
                  {r.reason} · {r.published_by} · {date(r.published_at, true)}
                </p>
              </article>
            ))}
          </details>
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
