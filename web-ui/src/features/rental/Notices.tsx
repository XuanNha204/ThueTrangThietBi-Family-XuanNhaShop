import type { Row } from '../../shared/types';
import { date } from '../../shared/api';
import { useQuery } from '../../shared/state';
import { Panel, QueryState } from '../../shared/ui';
const labels: Record<string, string> = {
  DUE_REMINDER: 'Nhắc giờ trả theo thỏa thuận',
  OVERDUE_NOTICE: 'Đã quá giờ trả',
  SCHEDULE_AT_RISK: 'Lịch có nguy cơ bị ảnh hưởng',
  AGREEMENT_UPDATED: 'Đã cập nhật thỏa thuận',
  CHANGE_PROPOSED: 'Có đề nghị thay đổi',
  INCIDENT_DECIDED: 'Kết quả xử lý sự cố',
  ORDER_HELD: 'Đã giữ chỗ',
  ORDER_CONFIRMED: 'Đã xác nhận thuê',
  ORDER_CANCELLED: 'Đơn đã hủy',
  PAYMENT_RECORDED: 'Đã ghi nhận khoản thu',
};
export function RentalNotices({ orderId }: { orderId: string }) {
  const q = useQuery<Row[]>(`/rentals/${orderId}/notices`);
  return (
    <Panel className="lifecycle-panel">
      <div className="panel-heading">
        <div>
          <h2>Thông báo của đơn</h2>
          <p>Nhắc trả và diễn biến thỏa thuận được lưu để tra cứu</p>
        </div>
      </div>
      <QueryState loading={q.loading} error={q.error}>
        <div className="lifecycle-body notice-list">
          {!q.data?.length && <p className="muted">Chưa có thông báo.</p>}
          {q.data?.map((n) => (
            <article key={n.id}>
              <div>
                <strong>{labels[n.event_type] ?? 'Cập nhật hồ sơ thuê'}</strong>
                <small>
                  {date(n.created_at, true)}
                  {n.recipient_scope === 'STAFF' ? ' · Nội bộ cửa hàng' : ''}
                </small>
              </div>
              <p>
                {String(n.payload).replace(/\d{4}-\d{2}-\d{2}T[\d:.]+Z/g, (value) =>
                  date(value, true),
                )}
              </p>
            </article>
          ))}
        </div>
      </QueryState>
    </Panel>
  );
}
