import { useEffect, useRef, useState } from 'react';
import type { Row } from '../../shared/types';
import { date, get, statusLabels } from '../../shared/api';
import { useQuery } from '../../shared/state';
import { Badge, Button, Dialog, QueryState } from '../../shared/ui';
type HistoryPage = {
  equipment: Row;
  events: Row[];
  nextCursor: { before: string; beforeKey: string } | null;
};
const labels: Record<string, string> = {
  INSPECTION: 'Kiểm tra thiết bị',
  FAULT_OPEN: 'Ghi nhận lỗi',
  FAULT_CLOSED: 'Đóng phiếu lỗi',
  PLAN_SCHEDULED: 'Lập kế hoạch bảo trì',
  PLAN_STARTED: 'Bắt đầu bảo trì',
  PLAN_COMPLETED: 'Kết thúc bảo trì',
  PLAN_CANCELLED: 'Hủy kế hoạch bảo trì',
  RENTAL_BOOKED: 'Đơn thuê liên quan (ngày lập đơn)',
  HANDED_OVER: 'Bàn giao cho khách',
  RETURNED: 'Thu hồi thiết bị',
  RESOLVED: 'Xử lý nghĩa vụ thiết bị mất',
};
export function EquipmentHistoryDialog({
  equipmentId,
  onClose,
}: {
  equipmentId: string;
  onClose: () => void;
}) {
  const q = useQuery<HistoryPage>(`/equipment/${equipmentId}/history`);
  const [extra, setExtra] = useState<{ base: HistoryPage | null; pages: HistoryPage[] }>({
    base: null,
    pages: [],
  });
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const abort = useRef<AbortController | null>(null);
  useEffect(() => () => abort.current?.abort(), []);
  const pages = extra.base === q.data ? extra.pages : [],
    events = [...(q.data?.events || []), ...pages.flatMap((p) => p.events)],
    cursor = pages.length ? pages.at(-1)!.nextCursor : q.data?.nextCursor;
  async function more() {
    if (!cursor || busy) return;
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    setError('');
    try {
      const params = new URLSearchParams(cursor);
      const data = await get<HistoryPage>(
        `/equipment/${equipmentId}/history?${params}`,
        controller.signal,
      );
      if (!controller.signal.aborted) setExtra({ base: q.data, pages: [...pages, data] });
    } catch (e) {
      if (!controller.signal.aborted)
        setError(e instanceof Error ? e.message : 'Chưa tải được lịch sử.');
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return (
    <Dialog
      title={'Lịch sử thiết bị' + (q.data ? ': ' + q.data.equipment.code : '')}
      description="Thuê, giao trả, kiểm tra, lỗi và bảo trì theo thời gian. Mỗi lần tải tối đa 50 sự kiện."
      onClose={onClose}
      wide
    >
      <QueryState loading={q.loading} error={q.error}>
        {q.data && (
          <>
            <p>
              <strong>{q.data.equipment.name}</strong> · {q.data.equipment.warehouse}
            </p>
            <Badge status={q.data.equipment.state} />
            {!events.length && <p>Chưa có sự kiện được ghi nhận.</p>}
            <div className="equipment-history">
              {events.map((event) => (
                <article key={event.event_key}>
                  <div className="history-event-heading">
                    <strong>{labels[event.kind] || event.kind}</strong>
                    <time dateTime={event.event_at}>{date(event.event_at, true)}</time>
                  </div>
                  {event.order_code && (
                    <p>
                      Đơn {event.order_code} · Trạng thái hiện tại:{' '}
                      {statusLabels[event.order_status] || event.order_status}
                    </p>
                  )}
                  {event.actor && <small>Người ghi nhận: {event.actor}</small>}
                  {event.summary && <p>{event.summary}</p>}
                  {event.kind === 'INSPECTION' && (
                    <>
                      <p>
                        <strong>{event.passed ? 'Kiểm tra đạt' : 'Chưa đạt — cần xử lý'}</strong> ·
                        Checklist bổ sung phiên bản {event.checklist_version}
                      </p>
                      {event.results_recorded ? (
                        <details>
                          <summary>Xem kết quả từng tiêu chí</summary>
                          <ul className="inspection-results">
                            {event.results.map((r: Row) => (
                              <li key={r.code}>
                                <strong>
                                  {r.label}: {r.passed ? 'Đạt' : 'Chưa đạt'}
                                </strong>
                                <small>{r.blocking ? 'Bắt buộc đạt' : 'Theo dõi'}</small>
                                {r.note && <p>{r.note}</p>}
                              </li>
                            ))}
                          </ul>
                        </details>
                      ) : (
                        <p className="muted">
                          Bản ghi cũ chỉ có kết luận tổng; chưa lưu kết quả từng tiêu chí.
                        </p>
                      )}
                    </>
                  )}
                </article>
              ))}
            </div>
            {error && (
              <p role="alert" className="inline-error">
                {error}
              </p>
            )}
            {cursor && (
              <Button className="secondary" busy={busy} onClick={() => void more()}>
                Tải sự kiện cũ hơn
              </Button>
            )}
          </>
        )}
      </QueryState>
      <div className="dialog-actions">
        <Button className="secondary" onClick={onClose}>
          Đóng lịch sử
        </Button>
      </div>
    </Dialog>
  );
}
