import { useState } from 'react';
import type { FormEvent } from 'react';
import { Settings } from 'lucide-react';
import type { Row } from '../../shared/types';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Button, Dialog, InfoNote } from '../../shared/ui';

export function SePayConfiguration() {
  const { user } = useApp(),
    q = useQuery<Row>('/finance/sepay/configuration'),
    command = useCommand();
  const [open, setOpen] = useState(false);
  const c = q.data;
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!c) return;
    const data = new FormData(e.currentTarget);
    const result = await command.run(
      '/finance/sepay/configuration',
      {
        version: c.version,
        gateway: data.get('gateway'),
        accountNumber: data.get('account'),
        accountHolder: data.get('holder'),
        enabled: data.has('enabled'),
        automaticPosting: data.has('auto'),
      },
      'Đã lưu tài khoản nhận SePay.',
    );
    if (result.ok) setOpen(false);
  }
  return (
    <>
      {c && (
        <p className="subtle">
          Tài khoản nhận: {c.gateway || 'Chưa cấu hình'} ·{' '}
          {c.account_number ? '****' + c.account_number.slice(-4) : '—'} ·{' '}
          {c.enabled
            ? c.automatic_posting
              ? 'Tự ghi sổ khi khớp đủ điều kiện'
              : 'Kế toán đối soát trước khi ghi sổ'
            : 'Chưa bật nhận thanh toán'}
        </p>
      )}
      {q.error && <p role="alert">{q.error}</p>}
      {user?.role === 'OWNER' && (
        <Button className="secondary small" disabled={!c} onClick={() => setOpen(true)}>
          <Settings size={16} />
          Cấu hình tài khoản nhận
        </Button>
      )}
      {open && c && (
        <Dialog
          title="Tài khoản nhận SePay"
          description="Dùng thông tin tài khoản ngân hàng đã kết nối trên SePay. Không nhập khóa HMAC ở đây."
          onClose={() => {
            if (!command.busy) setOpen(false);
          }}
        >
          <form className="form-stack" onSubmit={save}>
            <label className="field">
              <span>Mã ngân hàng trên webhook</span>
              <input
                name="gateway"
                required
                maxLength={40}
                pattern="[A-Za-z][A-Za-z0-9]{1,39}"
                defaultValue={c.gateway || 'MBBank'}
              />
              <small>Ví dụ: MBBank. Phải khớp trường gateway SePay gửi.</small>
            </label>
            <label className="field">
              <span>Số tài khoản nhận</span>
              <input
                name="account"
                required
                inputMode="numeric"
                pattern="[0-9]{4,30}"
                maxLength={30}
                defaultValue={c.account_number || ''}
              />
            </label>
            <label className="field">
              <span>Tên chủ tài khoản</span>
              <input name="holder" required maxLength={120} defaultValue={c.account_holder || ''} />
            </label>
            <label className="checkbox-field">
              <input type="checkbox" name="enabled" defaultChecked={c.enabled} />
              Bật tạo yêu cầu chuyển khoản
            </label>
            <label className="checkbox-field">
              <input type="checkbox" name="auto" defaultChecked={c.automatic_posting} />
              Tự ghi sổ khi khớp tài khoản, mã, số tiền và thời hạn
            </label>
            <InfoNote>
              Tiền sai/thiếu/thừa, đến muộn hoặc đơn đã đóng được giữ để đối soát. Không tự xác nhận
              đơn, chuyển tiền hoặc hoàn tiền. Tắt tự ghi sổ vẫn lưu webhook.
            </InfoNote>
            <div className="dialog-actions">
              <Button
                type="button"
                className="secondary"
                disabled={command.busy}
                onClick={() => setOpen(false)}
              >
                Quay lại
              </Button>
              <Button type="submit" busy={command.busy}>
                Lưu cấu hình
              </Button>
            </div>
          </form>
        </Dialog>
      )}
    </>
  );
}
