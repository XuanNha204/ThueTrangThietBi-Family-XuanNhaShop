import { useCallback, useEffect, useState } from 'react';
import type { FormEvent, MouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { clearCsrf, request } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import type { User } from '../../shared/types';
import { Button, Loading } from '../../shared/ui';
import googleMark from './google-mark.png';

const RETURN_PATH = 'xuannhashop.google.return';
type GoogleContact = { name: string; email: string };

function customerDestination(target: string | null) {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.includes('\\')) {
    return '/my-rentals';
  }
  const url = new URL(target, window.location.origin);
  if (url.origin !== window.location.origin) return '/my-rentals';
  return /^\/(?:book|my-rentals(?:\/[0-9a-f-]{36})?|my-support|my-profile)?$/.test(url.pathname)
    ? url.pathname + url.search
    : '/my-rentals';
}

export function GoogleSignIn({ from, busy }: { from?: string; busy: boolean }) {
  const options = useQuery<{ google: boolean }>('/auth/options');
  if (!options.data?.google) return null;

  function rememberDestination(event: MouseEvent<HTMLAnchorElement>) {
    if (busy) {
      event.preventDefault();
      return;
    }
    try {
      sessionStorage.setItem(RETURN_PATH, customerDestination(from ?? null));
    } catch {
      // Storage restrictions only affect the return destination.
    }
  }

  return (
    <div className="google-sign-in">
      <span className="google-sign-in-divider">hoặc</span>
      <a
        className="google-sign-in-button"
        href="/api/auth/authorize/google"
        aria-disabled={busy}
        onClick={rememberDestination}
      >
        <img src={googleMark} width={18} height={18} alt="" />
        Tiếp tục với Google
      </a>
    </div>
  );
}

export function GoogleLoginContinuation({ phase }: { phase: 'setup' | 'success' }) {
  const { setUser } = useApp();
  const navigate = useNavigate();
  const profile = useQuery<GoogleContact>(phase === 'setup' ? '/auth/google/pending' : null);
  const signedIn = useQuery<User>(phase === 'success' ? '/auth/me' : null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const finish = useCallback(
    (user: User) => {
      let target: string | null = null;
      try {
        target = sessionStorage.getItem(RETURN_PATH);
        sessionStorage.removeItem(RETURN_PATH);
      } catch {
        // Use the customer's rental list if session storage is unavailable.
      }
      clearCsrf();
      setUser(user);
      navigate(customerDestination(target), { replace: true });
    },
    [navigate, setUser],
  );

  useEffect(() => {
    if (signedIn.data) finish(signedIn.data);
  }, [finish, signedIn.data]);

  async function complete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    const form = new FormData(event.currentTarget);
    try {
      finish(
        await request<User>('/auth/google/complete', {
          method: 'POST',
          body: { name: form.get('name'), phone: form.get('phone'), address: form.get('address') },
        }),
      );
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const flowError = profile.error || signedIn.error;
  return (
    <>
      <h1>{phase === 'setup' ? 'Hoàn tất hồ sơ thuê' : 'Đang đăng nhập'}</h1>
      {flowError ? (
        <div className="form-stack">
          <div role="alert" className="inline-error">
            {flowError}
          </div>
          <Link className="text-link" to="/login" replace>
            Quay lại đăng nhập
          </Link>
        </div>
      ) : profile.data ? (
        <>
          <p>Bổ sung thông tin liên hệ để cửa hàng xác nhận đơn và giao thiết bị.</p>
          <form className="form-stack" onSubmit={complete}>
            <label className="field">
              <span>Email Google</span>
              <input type="email" value={profile.data.email} readOnly />
            </label>
            <label className="field">
              <span>Họ và tên</span>
              <input
                name="name"
                autoComplete="name"
                maxLength={120}
                defaultValue={profile.data.name}
                required
              />
            </label>
            <label className="field">
              <span>Số điện thoại</span>
              <input
                type="tel"
                name="phone"
                autoComplete="tel"
                maxLength={16}
                pattern={'\\+?[0-9]{8,15}'}
                required
              />
            </label>
            <label className="field">
              <span>Địa chỉ liên hệ</span>
              <textarea
                name="address"
                autoComplete="street-address"
                maxLength={400}
                rows={3}
                required
              />
            </label>
            {error && (
              <div role="alert" className="inline-error">
                {error}
              </div>
            )}
            <Button type="submit" busy={busy}>
              Lưu và đăng nhập
            </Button>
            {!busy && (
              <Link className="text-link" to="/login" replace>
                Quay lại đăng nhập
              </Link>
            )}
          </form>
        </>
      ) : (
        <Loading />
      )}
    </>
  );
}
