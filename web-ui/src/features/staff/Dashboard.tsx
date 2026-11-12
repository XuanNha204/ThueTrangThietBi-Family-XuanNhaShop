import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ArrowUpRight,
  Boxes,
  CalendarDays,
  ClipboardList,
  Plus,
  ShieldCheck,
  Wallet,
  ArrowDownToLine,
  Wrench,
  PackageCheck,
  Headphones,
} from 'lucide-react';
import { useApp, useQuery } from '../../shared/state';
import type { Dashboard as DashboardData, RentalSummary } from '../../shared/types';
import { money, number } from '../../shared/api';
import { PageHeader, Panel, QueryState } from '../../shared/ui';
import { DeviceArt } from '../../shared/DeviceArt';
import { RentalTable } from './Rentals';
export function Dashboard() {
  const { user } = useApp();
  const q = useQuery<DashboardData>('/dashboard');
  const orders = useQuery<RentalSummary[]>(user?.role !== 'HR' ? '/rentals' : null);
  const d = q.data;
  const canCreate = ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role),
    canEquipment = ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'].includes(user!.role);
  return (
    <>
      <PageHeader
        eyebrow="KHÔNG GIAN QUẢN LÝ"
        title="Tổng quan cửa hàng"
        description="Một góc nhìn rõ ràng cho mọi hoạt động trong ngày."
        action={
          canCreate && (
            <Link to="/book" className="button">
              <Plus size={17} />
              Tạo đơn thuê
            </Link>
          )
        }
      />
      <QueryState loading={q.loading} error={q.error}>
        {d && (
          <>
            <section className="welcome-banner">
              <div>
                <span className="welcome-kicker">
                  <span className="online-dot" /> CHÀO{' '}
                  {user?.displayName.split(' ').slice(-2).join(' ').toLocaleUpperCase('vi')}
                </span>
                <h2>Ngày mới, vận hành gọn gàng.</h2>
                <p>
                  Bạn đang có <strong>{d.active_orders} đơn thuê đang xử lý</strong> và{' '}
                  <strong>{d.attention_assets} thiết bị cần chú ý</strong>.<br />
                  Cùng kiểm tra để mỗi lần giao nhận đều trọn vẹn.
                </p>
                {canEquipment && (
                  <Link to="/app/equipment">
                    Theo dõi thiết bị
                    <ArrowRight size={16} />
                  </Link>
                )}
              </div>
              <div className="welcome-art">
                <div className="welcome-circle" />
                <DeviceArt kind="cleaner" />
                <span className="floating-tag">
                  <PackageCheck size={16} />
                  Sẵn sàng cho tổ ấm
                </span>
              </div>
            </section>
            <div className="stats-grid">
              <Stat
                label="Tổng thiết bị"
                value={number(d.total_assets)}
                note={`${d.ready_assets} thiết bị sẵn sàng`}
                icon={<Boxes />}
                tone="brown"
              />
              <Stat
                label="Thiết bị đang thuê"
                value={number(d.rented_assets)}
                note={`${Math.round((d.rented_assets / Math.max(1, d.total_assets)) * 100)}% tổng số thiết bị`}
                icon={<PackageCheck />}
                tone="green"
              />
              <Stat
                label="Đơn thuê đang xử lý"
                value={number(d.active_orders)}
                note={`${d.held_orders} đơn đang giữ chỗ`}
                icon={<ClipboardList />}
                tone="blue"
              />
              <Stat
                label={d.rental_received !== undefined ? 'Tiền thuê đã thu' : 'Yêu cầu hỗ trợ'}
                value={
                  d.rental_received !== undefined
                    ? money(d.rental_received)
                    : number(d.open_tickets)
                }
                note={
                  d.rental_received !== undefined
                    ? 'Từ sổ tiền · không bao gồm cọc'
                    : `${d.overdue_items} thiết bị trả muộn`
                }
                icon={d.rental_received !== undefined ? <Wallet /> : <Headphones />}
                tone="ochre"
              />
            </div>
            <div className="dashboard-charts">
              <Panel className="activity-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Nhịp hoạt động cửa hàng</h2>
                    <p>Số đơn được tạo trong 7 ngày gần nhất</p>
                  </div>
                  <span className="period-pill">7 ngày</span>
                </div>
                <div
                  className="bar-chart"
                  role="img"
                  aria-label={d.trend.map((t) => `${t.day}: ${t.orders} đơn`).join('; ')}
                >
                  <div className="chart-axis">
                    <span>{Math.max(1, ...d.trend.map((t) => t.orders))}</span>
                    <span>0</span>
                  </div>
                  <div className="chart-bars">
                    {d.trend.map((t, i) => (
                      <div className="chart-column" key={t.day}>
                        <div className="bar-track">
                          <div
                            className={`bar ${i === 6 ? 'today' : ''}`}
                            style={{
                              height: `${Math.max(t.orders ? 8 : 0, (t.orders / Math.max(1, ...d.trend.map((t) => t.orders))) * 100)}%`,
                            }}
                          />
                          <span className="bar-value">{t.orders}</span>
                        </div>
                        <span>
                          {new Intl.DateTimeFormat('vi-VN', { weekday: 'short' }).format(
                            new Date(t.day + 'T12:00:00'),
                          )}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
                <div className="chart-legend">
                  <i />
                  Số đơn thuê mới
                  <span>
                    Dữ liệu cập nhật{' '}
                    {new Intl.DateTimeFormat('vi-VN', {
                      hour: '2-digit',
                      minute: '2-digit',
                    }).format(new Date(d.asOf))}
                  </span>
                </div>
              </Panel>
              <Panel className="readiness-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Thiết bị theo nhóm</h2>
                    <p>Đang sẵn sàng / tổng số</p>
                  </div>
                  <Boxes size={18} />
                </div>
                <div className="category-progress">
                  {d.categories.map((c, i) => (
                    <div key={c.category}>
                      <div>
                        <span>
                          <i className={`category-dot cat-${i}`} />
                          {c.category}
                        </span>
                        <strong>
                          {c.ready}
                          <small> / {c.total}</small>
                        </strong>
                      </div>
                      <div className="progress-track">
                        <i
                          style={{ width: `${(c.ready / Math.max(1, c.total)) * 100}%` }}
                          className={`cat-${i}`}
                        />
                      </div>
                    </div>
                  ))}
                </div>
                {canEquipment && (
                  <Link className="panel-link" to="/app/equipment">
                    Xem tất cả thiết bị
                    <ArrowUpRight size={16} />
                  </Link>
                )}
              </Panel>
            </div>
            {orders.data && (
              <Panel>
                <div className="panel-heading">
                  <div>
                    <h2>Đơn thuê gần đây</h2>
                    <p>Theo dõi từ giữ chỗ đến khi hoàn tất</p>
                  </div>
                  <Link className="text-link" to="/app/orders">
                    Xem tất cả
                    <ArrowRight size={15} />
                  </Link>
                </div>
                <RentalTable rows={orders.data.slice(0, 5)} />
              </Panel>
            )}
            <div className="quick-grid">
              {canCreate && (
                <Link to="/book">
                  <span className="quick-icon terracotta">
                    <Plus />
                  </span>
                  <div>
                    <strong>Tạo đơn thuê</strong>
                    <small>Chọn thiết bị, kiểm tra lịch</small>
                  </div>
                  <ArrowUpRight size={18} />
                </Link>
              )}
              {canEquipment && (
                <Link to="/app/calendar">
                  <span className="quick-icon sage">
                    <CalendarDays />
                  </span>
                  <div>
                    <strong>Xem lịch cho thuê</strong>
                    <small>Chủ động chuẩn bị giao nhận</small>
                  </div>
                  <ArrowUpRight size={18} />
                </Link>
              )}
              {['OWNER', 'MANAGER', 'TECHNICIAN', 'INVENTORY'].includes(user!.role) && (
                <Link to="/app/maintenance">
                  <span className="quick-icon sand">
                    <Wrench />
                  </span>
                  <div>
                    <strong>Kiểm tra & bảo trì</strong>
                    <small>{d.attention_assets} thiết bị cần theo dõi</small>
                  </div>
                  <ArrowUpRight size={18} />
                </Link>
              )}
            </div>
          </>
        )}
      </QueryState>
    </>
  );
}
function Stat({
  label,
  value,
  note,
  icon,
  tone,
}: {
  label: string;
  value: string;
  note: string;
  icon: React.ReactNode;
  tone: string;
}) {
  return (
    <div className="stat-card">
      <div>
        <span>{label}</span>
        <i className={`stat-icon ${tone}`}>{icon}</i>
      </div>
      <strong>{value}</strong>
      <small>{note}</small>
    </div>
  );
}
