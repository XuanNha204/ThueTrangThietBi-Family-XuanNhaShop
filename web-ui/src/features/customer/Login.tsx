import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { Brand } from './Catalog';
import { DeviceArt } from '../../shared/DeviceArt';
import { Button } from '../../shared/ui';
import { request, clearCsrf, roles } from '../../shared/api';
import { useApp } from '../../shared/state';
import type { User } from '../../shared/types';
import { staffNavigation } from '../staff/Shell';
import { passwordHint, passwordPattern } from '../../shared/password';
export function Login() {
  const { setUser } = useApp();
  const navigate = useNavigate(),
    location = useLocation();
  const [mode, setMode] = useState<'login' | 'register'>('login'),
    [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [show, setShow] = useState(false),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const form = new FormData(e.currentTarget);
    try {
      if (mode === 'register')
        await request('/auth/register', {
          method: 'POST',
          body: {
            username,
            password,
            name: form.get('name'),
            phone: form.get('phone'),
            address: form.get('address'),
          },
        });
      const u = await request<User>('/auth/login', {
        method: 'POST',
        body: { username, password },
      });
      clearCsrf();
      setUser(u);
      const target = (location.state as { from?: string } | null)?.from;
      const path = target?.split('?')[0] ?? '';
      const destinationRoles = staffNavigation.find(
        (item) => item.path !== '/app' && (path === item.path || path.startsWith(item.path + '/')),
      )?.roles;
      const canReturn =
        target &&
        (u.role === 'CUSTOMER'
          ? /^\/(?:my-(?:rentals|support|profile)(?:\/|$)|book(?:\?|$)|$)/.test(target)
          : path === '/' ||
            path === '/app' ||
            (path === '/book' && ['OWNER', 'MANAGER', 'INVENTORY'].includes(u.role)) ||
            (path === '/app/notifications' && ['OWNER', 'MANAGER'].includes(u.role)) ||
            destinationRoles?.includes(u.role));
      navigate(canReturn ? target : u.role === 'CUSTOMER' ? '/my-rentals' : '/app', {
        replace: true,
      });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-page">
      <aside className="login-story">
        <Brand />
        <div>
          <span className="eyebrow">CHÀO MỪNG BẠN VỀ NHÀ</span>
          <h1>
            Những điều tiện nghi,
            <br />
            bắt đầu từ đây.
          </h1>
          <p>Cùng XuanNha chăm chút tổ ấm và quản lý mỗi lần thuê thật gọn gàng.</p>
          <div className="login-art">
            <DeviceArt kind="cleaner" />
            <DeviceArt kind="purifier" />
          </div>
        </div>
        <small>XuanNhaShop · Thuê vừa đủ, sống tiện nghi.</small>
      </aside>
      <main className="login-form-area">
        <Link to="/" className="text-link">
          <ArrowLeft size={16} />
          Về cửa hàng
        </Link>
        <div className="login-form">
          <div className="eyebrow">XuanNhaShop</div>
          <h1>{mode === 'login' ? 'Chào bạn,' : 'Tạo tài khoản thuê'}</h1>
          <p>
            {mode === 'login'
              ? 'Đăng nhập để tiếp tục cùng XuanNha.'
              : 'Lưu thông tin và theo dõi đơn thuê của bạn.'}
          </p>
          <form className="form-stack" onSubmit={submit}>
            {mode === 'register' && (
              <>
                <label className="field">
                  <span>Họ và tên</span>
                  <input name="name" autoComplete="name" required maxLength={120} />
                </label>
                <label className="field">
                  <span>Số điện thoại</span>
                  <input
                    name="phone"
                    type="tel"
                    autoComplete="tel"
                    required
                    pattern="\+?[0-9]{8,15}"
                  />
                </label>
                <label className="field">
                  <span>Địa chỉ</span>
                  <input name="address" autoComplete="street-address" required maxLength={400} />
                </label>
              </>
            )}
            <label className="field">
              <span>Tên đăng nhập</span>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
                maxLength={60}
              />
            </label>
            <label className="field">
              <span>Mật khẩu</span>
              <div className="password-input">
                <input
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  type={show ? 'text' : 'password'}
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  required
                  minLength={mode === 'register' ? 8 : 1}
                  pattern={mode === 'register' ? passwordPattern : undefined}
                  title={mode === 'register' ? passwordHint : undefined}
                  maxLength={72}
                />
                <button
                  type="button"
                  aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  onClick={() => setShow((v) => !v)}
                >
                  {show ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {mode === 'register' && (
                <small>
                  {passwordHint} Tên đăng nhập dùng chữ thường, số, dấu chấm, gạch dưới hoặc
                  gạch ngang.
                </small>
              )}
            </label>
            {error && (
              <div role="alert" className="inline-error">
                {error}
              </div>
            )}
            <Button type="submit" busy={busy}>
              {mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản & đăng nhập'}
              <ArrowRight size={17} />
            </Button>
          </form>
          <div className="login-switch">
            {mode === 'login' ? 'Bạn chưa có tài khoản?' : 'Bạn đã có tài khoản?'}{' '}
            <button
              onClick={() => {
                setMode(mode === 'login' ? 'register' : 'login');
                setError('');
              }}
            >
              {mode === 'login' ? 'Đăng ký' : 'Đăng nhập'}
            </button>
          </div>
          {import.meta.env.DEV && mode === 'login' && (
            <details className="demo-accounts">
              <summary>Tài khoản dùng thử tại máy này</summary>
              <p>Dữ liệu minh họa, không phải giao dịch thực tế.</p>
              <select
                aria-label="Chọn tài khoản dùng thử"
                defaultValue=""
                onChange={(e) => {
                  setUsername(e.target.value);
                  setPassword('XuanNhaDev!2026');
                }}
              >
                <option value="" disabled>
                  Chọn vai trò
                </option>
                {[
                  ['owner', 'OWNER'],
                  ['accountant', 'ACCOUNTANT'],
                  ['stock', 'INVENTORY'],
                  ['delivery', 'DELIVERY'],
                  ['technician', 'TECHNICIAN'],
                  ['hr', 'HR'],
                  ['customer', 'CUSTOMER'],
                ].map(([u, r]) => (
                  <option key={u} value={u}>
                    {roles[r]} · {u}
                  </option>
                ))}
              </select>
              <p>
                Mật khẩu mẫu: <code>XuanNhaDev!2026</code>
              </p>
            </details>
          )}
          <div className="login-security">
            <ShieldCheck size={15} />
            Phiên đăng nhập được quản lý bởi máy chủ.
          </div>
        </div>
      </main>
    </div>
  );
}
