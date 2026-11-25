import { StockOperations } from './features/staff/StockOperations';
import { Accounting } from './features/staff/Accounting';
import { Warranty } from './features/staff/Warranty';
import { CustomerVerification } from './features/staff/CustomerVerification';
import { Dispatch } from './features/staff/Dispatch';
import { PayrollExtras } from './features/staff/PayrollExtras';
import { MyPayslips } from './features/staff/PayrollExtras';
import { BusinessPerformance } from './features/staff/BusinessPerformance';
import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { Catalog, CustomerHeader } from './features/customer/Catalog';
import { Login } from './features/customer/Login';
import { Booking } from './features/customer/Booking';
import { Profile } from './features/customer/Profile';
import { Attention } from './features/staff/Attention';
import { StaffShell, staffNavigation } from './features/staff/Shell';
import { Dashboard } from './features/staff/Dashboard';
import { Rentals, RentalCalendar } from './features/staff/Rentals';
import { OrderDetail } from './features/staff/OrderDetail';
import { Inventory, Maintenance } from './features/staff/Inventory';
import { Customers } from './features/staff/Customers';
import { Finance } from './features/staff/Finance';
import { Payroll } from './features/staff/Payroll';
import { Support } from './features/staff/Support';
import { Imports } from './features/staff/Imports';
import { Audit, Notifications, Reports, Settings } from './features/staff/SystemPages';
import { useApp } from './shared/state';
import { Loading, PageHeader, Panel } from './shared/ui';
import { AccountPassword } from './shared/AccountPassword';
function Guard({ staff = false, roles }: { staff?: boolean; roles?: string[] }) {
  const { user, authLoading } = useApp(),
    location = useLocation();
  if (authLoading) return <Loading />;
  if (!user)
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  if (staff && user.role === 'CUSTOMER') return <Navigate to="/my-rentals" replace />;
  if (roles && !roles.includes(user.role))
    return (
      <div className="store-container customer-content">
        <PageHeader
          title="Trang này cần quyền phù hợp"
          description="Tài khoản hiện tại chưa được cấp quyền thao tác tại đây."
        />
        <Link className="button" to={user.role === 'CUSTOMER' ? '/my-rentals' : '/app'}>
          Về trang của tôi
        </Link>
      </div>
    );
  return <Outlet />;
}
function NotFound() {
  return (
    <div className="customer-content">
      <Panel>
        <div className="empty">
          <span className="eyebrow">404</span>
          <h1>Chưa tìm thấy trang này.</h1>
          <p>Kiểm tra đường dẫn hoặc quay về cửa hàng để tiếp tục.</p>
          <Link className="button" to="/">
            Về cửa hàng
          </Link>
        </div>
      </Panel>
    </div>
  );
}
function StaffAccess({ path, children }: { path: string; children: ReactNode }) {
  const { user } = useApp();
  const allowed =
    staffNavigation.find((item) => item.path === '/app/' + path)?.roles ??
    (path === 'notifications' ? ['OWNER', 'MANAGER'] : []);
  if (!user || !allowed.includes(user.role))
    return (
      <Panel>
        <PageHeader
          title="Trang này cần quyền phù hợp"
          description="Tài khoản hiện tại chưa được cấp quyền thao tác tại đây."
        />
        <Link className="button" to="/app">
          Về tổng quan
        </Link>
      </Panel>
    );
  return children;
}
export function App() {
  const location = useLocation();
  useEffect(() => {
    document.title = 'XuanNhaShop · Thuê vừa đủ, sống tiện nghi';
    window.scrollTo({ top: 0, behavior: 'instant' });
  }, [location.pathname]);
  return (
    <Routes>
      <Route path="/" element={<Catalog />} />
      <Route path="/login" element={<Login />} />
      <Route element={<Guard roles={['OWNER', 'MANAGER', 'INVENTORY', 'CUSTOMER']} />}>
        <Route path="/book" element={<Booking />} />
      </Route>
      <Route element={<Guard roles={['CUSTOMER']} />}>
        <Route path="/my-rentals" element={<Rentals customer />} />
        <Route path="/my-rentals/:id" element={<OrderDetail customer />} />
        <Route path="/my-support" element={<Support customer />} />
        <Route path="/my-profile" element={<Profile />} />
      </Route>
      <Route element={<Guard staff />}>
        <Route path="/app" element={<StaffShell />}>
          <Route index element={<Dashboard />} />
          <Route
            path="password"
            element={
              <>
                <PageHeader title="Đổi mật khẩu" />
                <AccountPassword />
              </>
            }
          />
          <Route
            path="attention"
            element={
              <StaffAccess path="attention">
                <Attention />
              </StaffAccess>
            }
          />
          <Route
            path="orders"
            element={
              <StaffAccess path="orders">
                <Rentals />
              </StaffAccess>
            }
          />
          <Route
            path="orders/:id"
            element={
              <StaffAccess path="orders">
                <OrderDetail />
              </StaffAccess>
            }
          />
          <Route
            path="calendar"
            element={
              <StaffAccess path="calendar">
                <RentalCalendar />
              </StaffAccess>
            }
          />
          <Route
            path="equipment"
            element={
              <StaffAccess path="equipment">
                <Inventory />
              </StaffAccess>
            }
          />
          <Route
            path="customers"
            element={
              <StaffAccess path="customers">
                <Customers />
              </StaffAccess>
            }
          />
          <Route
            path="maintenance"
            element={
              <StaffAccess path="maintenance">
                <Maintenance />
              </StaffAccess>
            }
          />
          <Route
            path="finance"
            element={
              <StaffAccess path="finance">
                <Finance />
              </StaffAccess>
            }
          />
          <Route
            path="payroll"
            element={
              <StaffAccess path="payroll">
                <Payroll />
              </StaffAccess>
            }
          />
          <Route
            path="support"
            element={
              <StaffAccess path="support">
                <Support />
              </StaffAccess>
            }
          />
          <Route
            path="reports"
            element={
              <StaffAccess path="reports">
                <Reports />
              </StaffAccess>
            }
          />
          <Route
            path="imports"
            element={
              <StaffAccess path="imports">
                <Imports />
              </StaffAccess>
            }
          />
          <Route
            path="audit"
            element={
              <StaffAccess path="audit">
                <Audit />
              </StaffAccess>
            }
          />
          <Route
            path="notifications"
            element={
              <StaffAccess path="notifications">
                <Notifications />
              </StaffAccess>
            }
          />
          <Route
            path="settings"
            element={
              <StaffAccess path="settings">
                <Settings />
              </StaffAccess>
            }
          />
          <Route
            path="stock-operations"
            element={
              <StaffAccess path="stock-operations">
                <StockOperations />
              </StaffAccess>
            }
          />
          <Route
            path="accounting"
            element={
              <StaffAccess path="accounting">
                <Accounting />
              </StaffAccess>
            }
          />
          <Route
            path="warranty"
            element={
              <StaffAccess path="warranty">
                <Warranty />
              </StaffAccess>
            }
          />
          <Route
            path="verifications"
            element={
              <StaffAccess path="verifications">
                <CustomerVerification />
              </StaffAccess>
            }
          />
          <Route
            path="dispatch"
            element={
              <StaffAccess path="dispatch">
                <Dispatch />
              </StaffAccess>
            }
          />
          <Route
            path="payroll-extras"
            element={
              <StaffAccess path="payroll-extras">
                <PayrollExtras />
              </StaffAccess>
            }
          />
          <Route
            path="my-payslips"
            element={
              <StaffAccess path="my-payslips">
                <MyPayslips />
              </StaffAccess>
            }
          />
          <Route
            path="performance"
            element={
              <StaffAccess path="performance">
                <BusinessPerformance />
              </StaffAccess>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
      <Route
        path="*"
        element={
          <>
            <CustomerHeader />
            <NotFound />
          </>
        }
      />
    </Routes>
  );
}
