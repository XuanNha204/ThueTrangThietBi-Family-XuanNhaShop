import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { NewPasswordFields } from '../../shared/AccountPassword';
import { request } from '../../shared/api';
import { Button } from '../../shared/ui';

export function PasswordRecovery({ onComplete }: { onComplete: () => void }) {
  const [email, setEmail] = useState('');
  const [until, setUntil] = useState(0);
  const [wait, setWait] = useState(0);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!until) return;
    const timer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((until - Date.now()) / 1000));
      setWait(left);
      if (left === 0) window.clearInterval(timer);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [until]);

  async function send() {
    setBusy(true);
    setError('');
    try {
      const options = await request<{ resendAfter: number }>('/auth/password/recovery/start', {
        method: 'POST',
        body: { email },
      });
      setUntil(Date.now() + options.resendAfter * 1000);
      setWait(options.resendAfter);
      setCode('');
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function complete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await request('/auth/password/recovery/complete', {
        method: 'POST',
        body: { ...Object.fromEntries(new FormData(event.currentTarget)), code },
      });
      onComplete();
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>{until ? 'Đặt mật khẩu mới' : 'Quên mật khẩu?'}</h1>
      <p>
        {until
          ? 'Nếu Gmail đã được xác minh, mã 6 số sẽ được gửi đến địa chỉ bạn đã nhập. Mã có hiệu lực 5 phút.'
          : 'Nhập địa chỉ Gmail đã xác minh trên tài khoản XuanNhaShop.'}
      </p>
      {until ? (
        <form className="form-stack" onSubmit={complete}>
          <label className="field">
            <span>Mã xác minh</span>
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, ''))}
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="[0-9]{6}"
              maxLength={6}
              required
              autoFocus
            />
          </label>
          <NewPasswordFields />
          {error && (
            <div role="alert" className="inline-error">
              {error}
            </div>
          )}
          <Button type="submit" busy={busy}>
            Lưu mật khẩu mới
          </Button>
        </form>
      ) : (
        <form
          className="form-stack"
          onSubmit={(event) => {
            event.preventDefault();
            void send();
          }}
        >
          <label className="field">
            <span>Địa chỉ Gmail</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              maxLength={160}
              autoComplete="email"
              autoCapitalize="none"
              required
            />
          </label>
          {error && (
            <div role="alert" className="inline-error">
              {error}
            </div>
          )}
          <Button type="submit" busy={busy}>
            Gửi mã đặt lại mật khẩu
          </Button>
        </form>
      )}
      {until > 0 && (
        <div className="login-switch">
          <button type="button" disabled={busy || wait > 0} onClick={() => void send()}>
            {wait > 0 ? `Gửi lại mã sau ${wait} giây` : 'Gửi lại mã'}
          </button>
          {' · '}
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setUntil(0);
              setError('');
            }}
          >
            Đổi địa chỉ Gmail
          </button>
        </div>
      )}
      <div className="login-switch">
        <Link className="text-link" to="/login" replace>
          Về đăng nhập
        </Link>
      </div>
    </>
  );
}
