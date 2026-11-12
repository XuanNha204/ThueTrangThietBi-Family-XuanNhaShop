import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  ArrowUpRight,
  BarChart3,
  Bell,
  Boxes,
  CalendarDays,
  ClipboardList,
  FileInput,
  Headphones,
  LayoutDashboard,
  LogOut,
  Menu,
  Search,
  Settings2,
  ShieldCheck,
  Users,
  Wallet,
  Wrench,
  X,
  BadgeDollarSign,
  CalendarClock,
} from 'lucide-react';
import { Brand } from '../customer/Catalog';
import { useApp } from '../../shared/state';
import { request, clearCsrf, roles } from '../../shared/api';
import { Breadcrumb } from '../../shared/ui';
export const staffNavigation = [
  {
    path: '/app',
    label: 'Tổng quan',
    icon: LayoutDashboard,
    roles: ['OWNER', 'MANAGER', 'ACCOUNTANT', 'INVENTORY', 'DELIVERY', 'TECHNICIAN', 'HR'],
    group: 'VẬN HÀNH',
  },
  {
    path: '/app/orders',
    label: 'Đơn thuê',
    icon: ClipboardList,
    roles: ['OWNER', 'MANAGER', 'ACCOUNTANT', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'],
  },
  {
    path: '/app/calendar',
    label: 'Lịch cho thuê',
    icon: CalendarDays,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'],
  },
  {
    path: '/app/attention',
    label: 'Lịch cần chú ý',
    icon: CalendarClock,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'],
  },
  {
    path: '/app/equipment',
    label: 'Thiết bị & kho',
    icon: Boxes,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'],
  },
  {
    path: '/app/customers',
    label: 'Khách hàng',
    icon: Users,
    roles: ['OWNER', 'MANAGER', 'ACCOUNTANT', 'INVENTORY', 'DELIVERY'],
  },
  {
    path: '/app/maintenance',
    label: 'Kiểm tra & bảo trì',
    icon: Wrench,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'TECHNICIAN'],
  },
  {
    path: '/app/finance',
    label: 'Thu chi & tiền cọc',
    icon: Wallet,
    roles: ['OWNER', 'ACCOUNTANT'],
    group: 'QUẢN LÝ',
  },
  {
    path: '/app/payroll',
    label: 'Nhân sự & lương',
    icon: BadgeDollarSign,
    roles: ['OWNER', 'ACCOUNTANT', 'HR'],
  },
  {
    path: '/app/support',
    label: 'Chăm sóc khách',
    icon: Headphones,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY'],
  },
  {
    path: '/app/stock-operations',
    label: 'Kho & tiếp nhận',
    icon: Boxes,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'ACCOUNTANT'],
  },
  {
    path: '/app/accounting',
    label: 'Chi phí & đối soát',
    icon: Wallet,
    roles: ['OWNER', 'ACCOUNTANT'],
  },
  {
    path: '/app/warranty',
    label: 'Bảo hành thiết bị',
    icon: Wrench,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'TECHNICIAN'],
  },
  {
    path: '/app/verifications',
    label: 'Xác minh khách thuê',
    icon: ShieldCheck,
    roles: ['OWNER', 'MANAGER', 'INVENTORY'],
  },
  {
    path: '/app/dispatch',
    label: 'Giao nhận & hướng dẫn',
    icon: CalendarDays,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'],
  },
  {
    path: '/app/payroll-extras',
    label: 'Khoản lương & đối chiếu',
    icon: BadgeDollarSign,
    roles: ['OWNER', 'ACCOUNTANT'],
  },
  {
    path: '/app/my-payslips',
    label: 'Phiếu lương của tôi',
    icon: FileInput,
    roles: ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN', 'HR', 'ACCOUNTANT'],
  },
  {
    path: '/app/performance',
    label: 'Hiệu quả kinh doanh',
    icon: BarChart3,
    roles: ['OWNER', 'ACCOUNTANT'],
  },
  { path: '/app/reports', label: 'Báo cáo', icon: BarChart3, roles: ['OWNER', 'ACCOUNTANT'] },
  {
    path: '/app/imports',
    label: 'Nhập dữ liệu',
    icon: FileInput,
    roles: ['OWNER', 'INVENTORY'],
    group: 'HỆ THỐNG',
  },
  { path: '/app/audit', label: 'Lịch sử thao tác', icon: ShieldCheck, roles: ['OWNER'] },
  { path: '/app/settings', label: 'Tài khoản & chính sách', icon: Settings2, roles: ['OWNER'] },
];
const items = staffNavigation;
export function StaffShell() {
  const { user, setUser, toast } = useApp();
  const navigate = useNavigate(),
    location = useLocation();
  const [open, setOpen] = useState(false),
    [search, setSearch] = useState('');
  const title =
    [...items].reverse().find((i) => i.path !== '/app' && location.pathname.startsWith(i.path))
      ?.label ?? 'Tổng quan';
  async function logout() {
    try {
      await request('/auth/logout', { method: 'POST' });
      clearCsrf();
      setUser(null);
      navigate('/login', { replace: true, state: null });
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      {open && (
        <button
          className="sidebar-backdrop"
          aria-label="Đóng menu"
          onClick={() => setOpen(false)}
        />
      )}
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="sidebar-brand">
          <Brand />
          <button
            className="icon-button mobile-only"
            aria-label="Đóng menu"
            onClick={() => setOpen(false)}
          >
            <X size={20} />
          </button>
        </div>
        <div className="shop-switch">
          <span className="shop-avatar">XN</span>
          <div>
            <strong>Cửa hàng XuanNha</strong>
            <small>Không gian quản lý</small>
          </div>
          <span className="online-dot" />
        </div>
        <nav className="side-nav">
          {items
            .filter((i) => i.roles.includes(user!.role))
            .map((i) => (
              <div key={i.path}>
                {i.group && <div className="nav-group">{i.group}</div>}
                <NavLink
                  end={i.path === '/app'}
                  to={i.path}
                  onClick={() => setOpen(false)}
                  className={({ isActive }) => (isActive ? 'active' : '')}
                >
                  <i.icon size={18} />
                  <span>{i.label}</span>
                </NavLink>
              </div>
            ))}
        </nav>
        <div className="sidebar-bottom">
          <Link to="/" className="store-shortcut">
            Xem cửa hàng
            <ArrowUpRight size={16} />
          </Link>
          <div className="sidebar-profile">
            <span className="avatar">{user?.displayName.split(' ').at(-1)?.slice(0, 1)}</span>
            <div>
              <strong>{user?.displayName}</strong>
              <small>{roles[user?.role ?? '']}</small>
            </div>
            <button
              className="icon-button"
              title="Đăng xuất"
              aria-label="Đăng xuất"
              onClick={logout}
            >
              <LogOut size={17} />
            </button>
          </div>
          <small className="dev-label">
            {import.meta.env.DEV
              ? 'Phiên bản dev 0.3 · Dữ liệu mẫu'
              : 'XuanNhaShop · Quản lý cửa hàng'}
          </small>
        </div>
      </aside>
      <div className="app-main">
        <header className="app-topbar">
          <div className="topbar-left">
            <button
              className="icon-button mobile-only"
              aria-label="Mở menu"
              onClick={() => setOpen(true)}
            >
              <Menu size={21} />
            </button>
            <Breadcrumb>{title}</Breadcrumb>
          </div>
          <div className="topbar-right">
            <form
              className="topbar-search"
              onSubmit={(e) => {
                e.preventDefault();
                navigate('/?q=' + encodeURIComponent(search));
              }}
            >
              <Search size={16} />
              <input
                aria-label="Tìm trong cửa hàng"
                placeholder="Tìm thiết bị…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </form>
            <span className="topbar-date">
              {new Intl.DateTimeFormat('vi-VN', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
              }).format(new Date())}
            </span>
            {['OWNER', 'MANAGER'].includes(user!.role) && (
              <Link className="icon-button" to="/app/notifications" aria-label="Xem thông báo">
                <Bell size={19} />
              </Link>
            )}
          </div>
        </header>
        <main id="main-content" className="app-content">
          <Outlet />
        </main>
        <footer className="app-footer">
          © {new Date().getFullYear()} XuanNhaShop<span>Thuê vừa đủ, sống tiện nghi.</span>
        </footer>
      </div>
    </div>
  );
}
