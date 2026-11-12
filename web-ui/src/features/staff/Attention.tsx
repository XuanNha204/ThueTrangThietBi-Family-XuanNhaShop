import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, RefreshCw } from 'lucide-react';
import type { Row } from '../../shared/types';
import { date } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  InfoNote,
  PageHeader,
  Panel,
  QueryState,
  SearchInput,
  Table,
} from '../../shared/ui';
const riskLabels: Record<string, string> = {
  OVERDUE: 'Quá giờ trả',
  UNSAFE: 'Thiết bị chưa đủ điều kiện',
  LATE_PREVIOUS: 'Đơn trước trả muộn',
  PREVIOUS_INCIDENT: 'Đơn trước có sự cố',
  LOST_PENDING: 'Đang xác minh mất thiết bị',
};
export function Attention() {
  const q = useQuery<Row[]>('/rentals/attention'),
    { refresh } = useApp();
  const [search, setSearch] = useState(''),
    [filter, setFilter] = useState('ALL');
  const rows = (q.data ?? []).filter(
    (r) =>
      (filter === 'ALL' || r.risk === filter) &&
      `${r.code} ${r.customer_name} ${r.equipment_code}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageHeader
        eyebrow="ƯU TIÊN XỬ LÝ"
        title="Lịch cần chú ý"
        description="Nhận diện thiết bị quá hạn và các đơn đang có nguy cơ không thể bàn giao."
        action={
          <Button className="secondary" busy={q.loading} onClick={refresh}>
            <RefreshCw size={16} />
            Cập nhật
          </Button>
        }
      />
      <InfoNote>
        Thiết bị đang bị trả muộn hoặc có báo mất chờ duyệt sẽ không được cấp lịch mới. Với đơn bị
        ảnh hưởng, đối chiếu thực tế và đề nghị đổi mã hoặc đổi lịch để khách chấp thuận. Không tự
        cộng phí trả trễ.
      </InfoNote>
      <div className="summary-pills">
        <span>{q.data?.filter((r) => r.risk === 'OVERDUE').length ?? 0} thiết bị quá giờ trả</span>
        <span>
          {q.data?.filter((r) => r.risk !== 'OVERDUE').length ?? 0} dòng cần kiểm tra trước giao
        </span>
      </div>
      <Panel>
        <div className="table-toolbar">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Tìm đơn, khách hoặc mã thiết bị…"
          />
          <select
            aria-label="Lọc loại cảnh báo"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="ALL">Tất cả cảnh báo</option>
            {Object.entries(riskLabels).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            empty={!rows.length}
            headers={['Đơn thuê / khách', 'Thiết bị', 'Giờ trả theo thỏa thuận', 'Cần xử lý', '']}
          >
            {rows.map((r) => (
              <tr key={`${r.order_id}-${r.item_id}`}>
                <td>
                  <Link className="order-code" to={'/app/orders/' + r.order_id}>
                    {r.code}
                  </Link>
                  <small>{r.customer_name}</small>
                </td>
                <td>
                  <strong>{r.equipment_code}</strong>
                  <Badge status={r.equipment_state} />
                </td>
                <td>{date(r.ends_at, true)}</td>
                <td className="wrap-cell">
                  <strong>{riskLabels[r.risk] ?? r.risk}</strong>
                  {r.affected_by && <small>Đơn trước: {r.affected_by}</small>}
                </td>
                <td>
                  <Link
                    className="icon-button"
                    aria-label={'Xử lý đơn ' + r.code}
                    to={'/app/orders/' + r.order_id}
                  >
                    <ArrowRight size={17} />
                  </Link>
                </td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
    </>
  );
}
