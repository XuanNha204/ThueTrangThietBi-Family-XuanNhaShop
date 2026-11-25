import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LockKeyhole } from 'lucide-react';
import { clearCsrf, request } from './api';
import { passwordHint, passwordPattern } from './password';
import { useApp, useQuery } from './state';
import { Button, InfoNote, Panel, QueryState } from './ui';

export const passwordResetPath = '/login?reset=1';
export const passwordSavedNotice = 'Đã lưu mật khẩu mới. Hãy đăng nhập lại.';

export function NewPasswordFields() {
  return (
    <>
      <label className="field">
        <span>Mật khẩu mới</span>
        <input
          name="newPassword"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          pattern={passwordPattern}
          title={passwordHint}
        />
        <small>{passwordHint}</small>
      </label>
      <label className="field">
        <span>Nhập lại mật khẩu mới</span>
        <input
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
        />
      </label>
    </>
  );
}

export function AccountPassword() {
  const q = useQuery<{ hasPassword: boolean; verifiedGmail: boolean }>('/auth/password');
  const { setUser } = useApp();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function change(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await request('/auth/password/change', {
        method: 'POST',
        body: Object.fromEntries(new FormData(event.currentTarget)),
      });
      clearCsrf();
      setUser(null);
      navigate('/login', { replace: true, state: { notice: passwordSavedNotice } });
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Panel className="profile-panel">
      <QueryState loading={q.loading} error={q.error}>
        {q.data?.hasPassword ? (
          <form className="form-stack" onSubmit={change}>
            <label className="field">
              <span>Mật khẩu hiện tại</span>
              <input
                name="currentPassword"
                type="password"
                autoComplete="current-password"
                required
                maxLength={100}
              />
            </label>
            <NewPasswordFields />
            {error && (
              <div role="alert" className="inline-error">
                {error}
              </div>
            )}
            <InfoNote>Bạn sẽ cần đăng nhập lại sau khi đổi mật khẩu.</InfoNote>
            <Button busy={busy} type="submit">
              <LockKeyhole size={17} />
              Đổi mật khẩu
            </Button>
          </form>
        ) : q.data?.verifiedGmail ? (
          <div className="form-stack">
            <InfoNote>Dùng mã xác minh gửi đến Gmail để đặt mật khẩu XuanNhaShop.</InfoNote>
            <Link to={passwordResetPath} className="button">
              Đặt mật khẩu
            </Link>
          </div>
        ) : null}
      </QueryState>
    </Panel>
  );
}
