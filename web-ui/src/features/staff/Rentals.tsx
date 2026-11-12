import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import type { RentalSummary } from '../../shared/types';
import { date, money } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  Empty,
  PageHeader,
  Panel,
  QueryState,
  SearchInput,
  Table,
} from '../../shared/ui';
import { CustomerHeader } from '../customer/Catalog';
export function RentalTable({
  rows,
  customer = false,
}: {
  rows: RentalSummary[];
  customer?: boolean;
}) {
  return (
    <Table
      headers={[
        'Đơn thuê',
        ...(customer ? [] : ['Khách hàng']),
        'Thời gian thuê',
        'Tiền thuê',
        'Trạng thái',
        '',
      ]}
      empty={!rows.length}
    >
      {rows.map((o) => (
        <tr key={o.id}>
          <td>
            <Link className="order-code" to={(customer ? '/my-rentals/' : '/app/orders/') + o.id}>
              {o.code}
            </Link>
            <small>{o.item_count} thiết bị</small>
          </td>
          {!customer && (
            <td>
              <div className="person-cell">
                <span className="avatar tiny">{o.customer_name.split(' ').at(-1)?.[0]}</span>
                <strong>{o.customer_name}</strong>
              </div>
            </td>
          )}
          <td>
            {date(o.starts_at)}
            <small>đến {date(o.ends_at)}</small>
          </td>
          <td className="amount">
            {money(o.rental_total)}
            <small>Cọc {money(o.deposit_required)}</small>
          </td>
          <td>
            <Badge status={o.status} />
          </td>
          <td>
            <Link
              className="icon-button"
              to={(customer ? '/my-rentals/' : '/app/orders/') + o.id}
              aria-label={`Xem đơn ${o.code}`}
            >
              <ArrowRight size={17} />
            </Link>
          </td>
        </tr>
      ))}
    </Table>
  );
}
export function Rentals({ customer = false }: { customer?: boolean }) {
  const { user } = useApp();
  const q = useQuery<RentalSummary[]>('/rentals');
  const [search, setSearch] = useState(''),
    [status, setStatus] = useState('ALL');
  const rows = (q.data ?? []).filter(
    (o) =>
      (status === 'ALL' || o.status === status) &&
      (o.code + ' ' + o.customer_name).toLowerCase().includes(search.toLowerCase()),
  );
  const canCreate = customer || ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role);
  const content = (
    <>
      <PageHeader
        eyebrow={customer ? 'ĐỒNG HÀNH CÙNG XuanNha' : 'VẬN HÀNH'}
        title={customer ? 'Đơn thuê của tôi' : 'Quản lý đơn thuê'}
        description="Theo dõi thiết bị, tiền cọc và mọi lần giao nhận trong cùng một đơn."
        action={
          canCreate && (
            <Link to="/book" className="button">
              <Plus size={17} />
              Tạo đơn thuê
            </Link>
          )
        }
      />
      <div className="summary-pills">
        <span>
          <i className="dot terracotta" />
          {q.data?.length ?? 0} đơn đang lưu
        </span>
        <span>{q.data?.filter((x) => x.status === 'HELD').length ?? 0} đang giữ chỗ</span>
        <span>
          {q.data?.filter((x) => ['RENTED', 'PARTIAL_RETURN'].includes(x.status)).length ?? 0} đang
          thuê
        </span>
      </div>
      <Panel>
        <div className="table-toolbar">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Tìm mã đơn hoặc tên khách…"
          />
          <select
            aria-label="Lọc trạng thái đơn"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            {[
              ['ALL', 'Tất cả trạng thái'],
              ['HELD', 'Đang giữ chỗ'],
              ['CONFIRMED', 'Đã xác nhận'],
              ['PREPARING', 'Đang chuẩn bị'],
              ['RENTED', 'Đang thuê'],
              ['PARTIAL_RETURN', 'Trả một phần'],
              ['AWAIT_SETTLEMENT', 'Chờ tất toán'],
              ['COMPLETED', 'Hoàn tất'],
              ['CANCELLED', 'Đã hủy'],
              ['EXPIRED', 'Hết giữ chỗ'],
            ].map(([v, t]) => (
              <option key={v} value={v}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <RentalTable rows={rows} customer={customer} />
        </QueryState>
      </Panel>
    </>
  );
  return customer ? (
    <>
      <CustomerHeader />
      <main className="store-container customer-content">{content}</main>
    </>
  ) : (
    content
  );
}
export function RentalCalendar() {
  const q = useQuery<RentalSummary[]>('/rentals');
  const [offset, setOffset] = useState(0);
  const first = new Date();
  first.setHours(0, 0, 0, 0);
  first.setDate(first.getDate() + offset * 7);
  const days = Array.from({ length: 7 }, (_, i) => new Date(first.getTime() + i * 86400000));
  return (
    <>
      <PageHeader
        eyebrow="CHỦ ĐỘNG CHO MỖI LẦN GIAO NHẬN"
        title="Lịch cho thuê"
        description="Hiển thị các đơn đang chiếm lịch theo thời gian thuê. Khả năng phục vụ cụ thể được kiểm tra khi tạo đơn."
      />
      <Panel>
        <div className="panel-heading">
          <h2>
            {date(days[0].toISOString())} — {date(days[6].toISOString())}
          </h2>
          <div className="button-group">
            <Button
              className="secondary icon-only"
              aria-label="Tuần trước"
              onClick={() => setOffset((v) => v - 1)}
            >
              <ChevronLeft size={17} />
            </Button>
            <Button className="secondary" onClick={() => setOffset(0)}>
              Hôm nay
            </Button>
            <Button
              className="secondary icon-only"
              aria-label="Tuần sau"
              onClick={() => setOffset((v) => v + 1)}
            >
              <ChevronRight size={17} />
            </Button>
          </div>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <div className="calendar-scroll">
            <div className="calendar-grid">
              {days.map((day) => {
                const end = new Date(day.getTime() + 86400000),
                  rows = (q.data ?? []).filter(
                    (o) =>
                      !['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(o.status) &&
                      new Date(o.starts_at) < end &&
                      new Date(o.ends_at) > day &&
                      (o.status !== 'HELD' || new Date(o.expires_at) > new Date()),
                  );
                return (
                  <div className="calendar-day" key={day.toISOString()}>
                    <div
                      className={day.toDateString() === new Date().toDateString() ? 'today' : ''}
                    >
                      <small>
                        {new Intl.DateTimeFormat('vi-VN', { weekday: 'short' }).format(day)}
                      </small>
                      <strong>{day.getDate()}</strong>
                      <span>{rows.length} đơn</span>
                    </div>
                    {rows.map((o) => (
                      <Link className="calendar-order" key={o.id} to={'/app/orders/' + o.id}>
                        <Badge status={o.status} />
                        <strong>{o.code}</strong>
                        <small>{o.customer_name}</small>
                        <small>{o.item_count} thiết bị</small>
                      </Link>
                    ))}
                    {!rows.length && <p className="calendar-empty">Chưa có lịch thuê</p>}
                  </div>
                );
              })}
            </div>
          </div>
        </QueryState>
      </Panel>
    </>
  );
}
