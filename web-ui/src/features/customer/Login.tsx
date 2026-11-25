import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { Brand } from './Catalog';
import { DeviceArt } from '../../shared/DeviceArt';
import { Button, InfoNote, Loading } from '../../shared/ui';
import { request, clearCsrf, roles } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import type { User } from '../../shared/types';
import { staffNavigation } from '../staff/Shell';
import { passwordHint, passwordPattern } from '../../shared/password';
import { passwordResetPath, passwordSavedNotice } from '../../shared/AccountPassword';
import { GoogleLoginContinuation, GoogleSignIn, takeGoogleDestination } from './GoogleLogin';
import { GmailVerification } from './GmailVerification';
import type { GmailStage } from './GmailVerification';
import { PasswordRecovery } from './PasswordRecovery';

const googleErrors: Record<string, string> = {
  blocked: 'Tài khoản khách thuê đang bị khóa. Hãy liên hệ cửa hàng để được hỗ trợ.',
  failed: 'Đăng nhập Google chưa hoàn tất. Bạn có thể thử lại.',
  'gmail-required': 'Vui lòng chọn tài khoản Google có địa chỉ @gmail.com.',
  limited: 'Mã vừa được gửi hoặc bạn đã đạt giới hạn gửi mã. Hãy đợi trước khi thử lại.',
  'mail-failed': 'Chưa gửi được mã xác minh. Hãy thử đăng nhập lại sau.',
};

export function Login() {
  const { setUser } = useApp();
  const navigate = useNavigate(),
    location = useLocation();
  const options = useQuery<{ gmail: boolean; google: boolean }>('/auth/options');
  const gmailEnabled = options.data?.gmail === true;
  const view = new URLSearchParams(location.search);
  const recovering = gmailEnabled && view.get('reset') === '1';
  const notice = (location.state as { notice?: string } | null)?.notice;
  const pending = useQuery<GmailStage>(gmailEnabled ? '/auth/otp/state' : null);
  const [stageOverride, setStage] = useState<GmailStage | null>(null);
  const [legacy, setLegacy] = useState(false);
  const stage = stageOverride ?? pending.data;
  const google = options.data?.google ? view.get('google') : null;
  const googleError =
    google === 'otp' && stage?.stage === 'none'
      ? 'Phiên xác minh đã hết hạn. Hãy đăng nhập bằng Google lại.'
      : google && Object.hasOwn(googleErrors, google)
        ? googleErrors[google]
        : '';
  const [mode, setMode] = useState<'login' | 'register'>('login'),
    [username, setUsername] = useState(''),
    [password, setPassword] = useState(''),
    [show, setShow] = useState(false),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false);
  const gmailField = gmailEnabled && (mode === 'register' || !legacy);

  function finishLogin(u: User) {
    clearCsrf();
    setUser(u);
    const target =
      google === 'otp' || google === 'setup'
        ? takeGoogleDestination()
        : (location.state as { from?: string } | null)?.from;
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
  }

  function continueVerification(next: GmailStage) {
    clearCsrf();
    setPassword('');
    setShow(false);
    setStage(next);
  }

  function updateVerification(next: GmailStage) {
    setStage(next);
    if (next.stage === 'none' && (google === 'otp' || google === 'setup')) {
      takeGoogleDestination();
      navigate('/login', { replace: true });
    }
  }

  function finishRecovery() {
    clearCsrf();
    setUser(null);
    setStage({ stage: 'none', emailHint: '', expiresIn: 0, resendAfter: 0 });
    setMode('login');
    setLegacy(false);
    setPassword('');
    setShow(false);
    setError('');
    navigate('/login', { replace: true, state: { notice: passwordSavedNotice } });
  }

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError('');
    const form = new FormData(e.currentTarget);
    try {
      if (mode === 'register') {
        const result = await request<GmailStage | { id: string }>('/auth/register', {
          method: 'POST',
          body: {
            ...(gmailEnabled ? { email: username } : { username }),
            password,
            name: form.get('name'),
            phone: form.get('phone'),
            address: form.get('address'),
          },
        });
        if ('stage' in result) {
          continueVerification(result);
          return;
        }
      }
      const result = await request<User | GmailStage>('/auth/login', {
        method: 'POST',
        body: { username, password },
      });
      if ('stage' in result) continueVerification(result);
      else finishLogin(result);
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
          {recovering ? (
            <PasswordRecovery onComplete={finishRecovery} />
          ) : options.loading || (gmailEnabled && !stage && !pending.error) ? (
            <Loading />
          ) : options.error || (gmailEnabled && pending.error && !stageOverride) ? (
            <div role="alert" className="inline-error">
              {options.error || pending.error}
            </div>
          ) : stage && stage.stage !== 'none' ? (
            <GmailVerification
              stage={stage}
              onChange={updateVerification}
              onVerified={finishLogin}
            />
          ) : google === 'setup' || google === 'success' ? (
            <GoogleLoginContinuation
              key={google}
              phase={google}
              onOtp={gmailEnabled ? continueVerification : undefined}
            />
          ) : (
            <>
              <h1>{mode === 'login' ? 'Chào bạn,' : 'Tạo tài khoản thuê'}</h1>
              <p>
                {mode === 'login'
                  ? 'Đăng nhập để tiếp tục cùng XuanNha.'
                  : 'Lưu thông tin và theo dõi đơn thuê của bạn.'}
              </p>
              {notice && (
                <div role="status">
                  <InfoNote>{notice}</InfoNote>
                </div>
              )}
              <form className="form-stack" onSubmit={submit}>
                {googleError && !error && (
                  <div role="alert" className="inline-error">
                    {googleError}
                  </div>
                )}
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
                      <input
                        name="address"
                        autoComplete="street-address"
                        required
                        maxLength={400}
                      />
                    </label>
                  </>
                )}
                <label className="field">
                  <span>{gmailField ? 'Địa chỉ Gmail' : 'Tên đăng nhập'}</span>
                  <input
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    type={gmailField ? 'email' : 'text'}
                    autoComplete="username"
                    autoCapitalize="none"
                    required
                    maxLength={gmailField ? 160 : 60}
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
                      {passwordHint}{' '}
                      {gmailEnabled
                        ? 'Mỗi Gmail chỉ đăng ký được một tài khoản.'
                        : 'Tên đăng nhập dùng chữ thường, số, dấu chấm, gạch dưới hoặc gạch ngang.'}
                    </small>
                  )}
                </label>
                {mode === 'login' && gmailEnabled && !legacy && (
                  <Link to={passwordResetPath} className="text-link">
                    Quên mật khẩu?
                  </Link>
                )}
                {error && (
                  <div role="alert" className="inline-error">
                    {error}
                  </div>
                )}
                <Button type="submit" busy={busy}>
                  {mode === 'login'
                    ? 'Đăng nhập'
                    : gmailEnabled
                      ? 'Gửi mã xác minh'
                      : 'Tạo tài khoản & đăng nhập'}
                  <ArrowRight size={17} />
                </Button>
              </form>
              {options.data?.google && (
                <GoogleSignIn
                  from={(location.state as { from?: string } | null)?.from}
                  busy={busy}
                />
              )}
              {mode === 'login' && gmailEnabled && (
                <div className="login-switch">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setLegacy(!legacy);
                      setUsername('');
                      setPassword('');
                      setError('');
                    }}
                  >
                    {legacy ? 'Đăng nhập bằng Gmail' : 'Đăng nhập nhân viên hoặc tài khoản cũ'}
                  </button>
                </div>
              )}
              <div className="login-switch">
                {mode === 'login' ? 'Bạn chưa có tài khoản?' : 'Bạn đã có tài khoản?'}{' '}
                <button
                  disabled={busy}
                  onClick={() => {
                    setMode(mode === 'login' ? 'register' : 'login');
                    setLegacy(false);
                    setUsername('');
                    setPassword('');
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
                      setLegacy(true);
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
            </>
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
