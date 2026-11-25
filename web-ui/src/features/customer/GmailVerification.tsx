import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { request } from '../../shared/api';
import type { User } from '../../shared/types';
import { Button } from '../../shared/ui';

export type GmailStage = {
  stage: 'none' | 'gmail' | 'otp';
  emailHint: string;
  expiresIn: number;
  resendAfter: number;
};

export function GmailVerification({
  stage,
  onChange,
  onVerified,
}: {
  stage: GmailStage;
  onChange: (value: GmailStage) => void;
  onVerified: (user: User) => void;
}) {
  const [input, setInput] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [seconds, setSeconds] = useState({ expires: stage.expiresIn, resend: stage.resendAfter });
  const isOtp = stage.stage === 'otp';

  useEffect(() => {
    const start = Date.now();
    const tick = () => {
      const elapsed = Math.floor((Date.now() - start) / 1000);
      setSeconds({
        expires: Math.max(0, stage.expiresIn - elapsed),
        resend: Math.max(0, stage.resendAfter - elapsed),
      });
    };
    tick();
    if (stage.expiresIn === 0 && stage.resendAfter === 0) return;
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [stage]);

  async function run(action: 'verify' | 'enroll' | 'resend' | 'cancel') {
    setError('');
    setBusy(true);
    try {
      if (action === 'verify') {
        onVerified(
          await request<User>('/auth/otp/verify', { method: 'POST', body: { code: input } }),
        );
      } else if (action === 'cancel') {
        await request('/auth/otp/cancel', { method: 'POST' });
        onChange({ stage: 'none', emailHint: '', expiresIn: 0, resendAfter: 0 });
      } else {
        const body = action === 'enroll' ? { email: input } : undefined;
        onChange(await request<GmailStage>('/auth/otp/' + action, { method: 'POST', body }));
        setInput('');
      }
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void run(isOtp ? 'verify' : 'enroll');
  }

  return (
    <>
      <h1>{isOtp ? 'Nhập mã xác minh' : 'Xác minh Gmail của bạn'}</h1>
      <p>
        {isOtp ? (
          <>
            Mã 6 số đã được gửi đến <strong>{stage.emailHint}</strong>.
          </>
        ) : (
          'Thêm Gmail để đăng nhập và tiếp tục sử dụng hồ sơ cùng các đơn thuê hiện có.'
        )}
      </p>
      <form className="form-stack" onSubmit={submit}>
        <label className="field">
          <span>{isOtp ? 'Mã xác minh' : 'Địa chỉ Gmail'}</span>
          <input
            key={stage.stage}
            type={isOtp ? 'text' : 'email'}
            inputMode={isOtp ? 'numeric' : undefined}
            autoComplete={isOtp ? 'one-time-code' : 'email'}
            maxLength={isOtp ? 6 : 160}
            pattern={isOtp ? '[0-9]{6}' : undefined}
            value={input}
            onChange={(e) =>
              setInput(isOtp ? e.target.value.replace(/[^0-9]/g, '') : e.target.value)
            }
            required
            autoFocus
          />
          {isOtp && (
            <small>
              {seconds.expires > 0
                ? `Mã hết hạn sau ${Math.floor(seconds.expires / 60)}:${String(seconds.expires % 60).padStart(2, '0')}.`
                : 'Mã đã hết hạn. Hãy yêu cầu gửi lại.'}
            </small>
          )}
        </label>
        {error && (
          <div role="alert" className="inline-error">
            {error}
          </div>
        )}
        <Button type="submit" busy={busy} disabled={isOtp && seconds.expires === 0}>
          {isOtp ? 'Xác nhận và đăng nhập' : 'Gửi mã xác minh'}
        </Button>
      </form>
      {isOtp && (
        <div className="login-switch">
          <button
            type="button"
            disabled={busy || seconds.resend > 0}
            onClick={() => void run('resend')}
          >
            {seconds.resend > 0 ? `Gửi lại mã sau ${seconds.resend} giây` : 'Gửi lại mã'}
          </button>
        </div>
      )}
      <div className="login-switch">
        <button type="button" disabled={busy} onClick={() => void run('cancel')}>
          Quay lại
        </button>
      </div>
    </>
  );
}
