import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { Panel, Button, QueryState } from '../../shared/ui';
import { useWorkflowForm } from '../staff/WorkflowForms';
export function RentalGuides({ orderId }: { orderId: string }) {
  const { user } = useApp(),
    q = useQuery<Row>('/rentals/' + orderId + '/guides'),
    form = useWorkflowForm();
  const can = ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'CUSTOMER'].includes(user!.role);
  return (
    <Panel className="workflow-panel">
      <h2>Hướng dẫn sử dụng & an toàn</h2>
      <QueryState loading={q.loading} error={q.error}>
        {q.data?.guides?.map((g: Row) => {
          const ack = q.data?.acknowledgements?.find((a: Row) => a.guide_id === g.id);
          return (
            <article className="guide-card" key={g.type_id}>
              <h3>{g.type_name}</h3>
              {g.id ? (
                <>
                  <details>
                    <summary>
                      {g.title} · phiên bản {g.version}
                    </summary>
                    <p className="preserve-lines">{g.instructions}</p>
                    <h4>Lưu ý an toàn</h4>
                    <p className="preserve-lines">{g.safety}</p>
                    <small>Nguồn: {g.source}</small>
                  </details>
                  {ack ? (
                    <p>
                      Đã xác nhận: {ack.confirmed_by} ·{' '}
                      {ack.method === 'CUSTOMER'
                        ? 'Khách xác nhận trực tiếp'
                        : 'Nhân viên ghi nhận đã hướng dẫn'}
                    </p>
                  ) : (
                    can && (
                      <Button
                        className="secondary"
                        onClick={() =>
                          form.open(
                            'Xác nhận đã được hướng dẫn',
                            '/rentals/' + orderId + '/guides/acknowledge',
                            [
                              ...(user?.role === 'CUSTOMER'
                                ? []
                                : [{ key: 'confirmedBy', label: 'Người đã nhận hướng dẫn' }]),
                              {
                                key: 'evidence',
                                label:
                                  user?.role === 'CUSTOMER'
                                    ? 'Nội dung đã hiểu / lưu ý đã được giải đáp'
                                    : 'Căn cứ đã hướng dẫn và xác nhận của khách',
                                type: 'textarea',
                              },
                            ],
                            (v) => ({ ...v, guideId: g.id }),
                            'Đọc hướng dẫn và lưu ý an toàn trước khi xác nhận. Nếu còn điểm chưa rõ, liên hệ cửa hàng để được hướng dẫn.',
                          )
                        }
                      >
                        Xác nhận đã được hướng dẫn
                      </Button>
                    )
                  )}
                </>
              ) : (
                <p>
                  Chưa có hướng dẫn được công bố. Hãy yêu cầu cửa hàng hướng dẫn trực tiếp trước khi
                  sử dụng.
                </p>
              )}
            </article>
          );
        })}
        <details>
          <summary>Các lần xác nhận trước</summary>
          {q.data?.acknowledgements?.map((a: Row) => (
            <article key={a.id}>
              <h3>
                {a.title} · phiên bản {a.version}
              </h3>
              <p>
                {a.confirmed_by} · {a.evidence}
              </p>
              <details>
                <summary>Nội dung đã xác nhận</summary>
                <p className="preserve-lines">{a.instructions}</p>
                <p className="preserve-lines">{a.safety}</p>
              </details>
            </article>
          ))}
        </details>
      </QueryState>
      {form.modal}
    </Panel>
  );
}
