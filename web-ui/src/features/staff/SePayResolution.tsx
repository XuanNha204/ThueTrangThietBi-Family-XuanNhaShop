import { useState } from 'react';
import type { Row } from '../../shared/types';
import { date, money, moneyKinds } from '../../shared/api';
import { useCommand, useQuery } from '../../shared/state';
import { Button, Dialog, FormDialog, QueryState } from '../../shared/ui';

export const reconciliationLabels: Record<string, string> = {
  PENDING: 'Đang chờ đối chiếu',
  SAMPLE: 'Mẫu thử — không ghi tiền',
  CONFLICT: 'Payload xung đột — cần kiểm tra SePay',
  OUTGOING: 'Tiền ra — không thu vào đơn',
  CONFIG_DISABLED: 'Chưa bật tài khoản nhận',
  WRONG_ACCOUNT: 'Không khớp ngân hàng/tài khoản',
  UNKNOWN_CODE: 'Không có một mã thanh toán duy nhất',
  ALREADY_PAID: 'Yêu cầu đã được giao dịch khác thanh toán',
  INVALID_TIME: 'Thời gian giao dịch không hợp lệ',
  ORDER_CLOSED: 'Đơn đóng hoặc hết giữ chỗ',
  LATE_PAYMENT: 'Mã thanh toán hết hiệu lực',
  AMOUNT_MISMATCH: 'Số tiền khác yêu cầu',
  AUTO_DISABLED: 'Chờ kế toán đối soát',
  EXCEEDS_DUE: 'Vượt nghĩa vụ còn phải thu',
  MANUAL_MATCH: 'Đã ghi sổ qua đối soát',
  EXACT_MATCH: 'Đã ghi sổ tự động',
  MANUAL_IGNORE: 'Đã đánh dấu không ghi sổ',
};
export function SePayResolution({ event }: { event: Row }) {
  const command = useCommand(),
    [dialog, setDialog] = useState<'match' | 'ignore' | null>(null);
  if (event.money_id) return null;
  return (
    <div className="bank-payment-actions">
      {event.transaction_id !== '0' &&
        event.status !== 'CONFLICT' &&
        event.transfer_type === 'in' && (
          <Button className="secondary small" onClick={() => setDialog('match')}>
            Đối soát ghi sổ
          </Button>
        )}
      <Button
        className="ghost small"
        busy={command.busy}
        onClick={() =>
          command.run(
            `/finance/sepay/events/${event.id}/retry`,
            undefined,
            'Đã đưa vào hàng đợi kiểm tra lại.',
          )
        }
      >
        Kiểm tra lại
      </Button>
      {event.reconciliation_status !== 'IGNORED' && (
        <Button className="ghost small" onClick={() => setDialog('ignore')}>
          Không ghi sổ
        </Button>
      )}
      {dialog === 'ignore' && (
        <FormDialog
          title="Đánh dấu không ghi sổ"
          description="Giữ nguyên dữ liệu nhận và lịch sử quyết định. Không xóa giao dịch ngân hàng."
          onClose={() => setDialog(null)}
          submitLabel="Lưu quyết định"
          fields={[{ key: 'reason', label: 'Lý do và căn cứ', type: 'textarea' }]}
          onSubmit={async (v) =>
            (
              await command.run(
                `/finance/sepay/events/${event.id}/ignore`,
                { reason: v.reason },
                'Đã lưu quyết định không ghi sổ.',
              )
            ).ok
          }
        />
      )}
      {dialog === 'match' && <MatchDialog event={event} onClose={() => setDialog(null)} />}
    </div>
  );
}
function MatchDialog({ event, onClose }: { event: Row; onClose: () => void }) {
  const rentals = useQuery<Row[]>('/rentals'),
    [order, setOrder] = useState(event.order_id || ''),
    [selected, setSelected] = useState(''),
    command = useCommand();
  const requests = useQuery<Row[]>(order ? `/rentals/${order}/payments` : null);
  return (
    <Dialog
      title="Đối soát giao dịch SePay"
      description={`Ghi toàn bộ ${money(event.amount)} vào một nghĩa vụ. Không tách tiền hoặc tự hoàn phần thừa. Khoản cọc đến muộn vẫn cần xử lý hoàn theo phê duyệt.`}
      onClose={onClose}
    >
      <form
        className="form-stack"
        onSubmit={async (e) => {
          e.preventDefault();
          const data = new FormData(e.currentTarget);
          if (
            (
              await command.run(
                `/finance/sepay/events/${event.id}/match`,
                { requestId: selected, reason: data.get('reason') },
                'Đã ghi sổ giao dịch SePay.',
              )
            ).ok
          )
            onClose();
        }}
      >
        <QueryState loading={rentals.loading} error={rentals.error}>
          <label className="field">
            <span>Đơn thuê</span>
            <select
              value={order}
              required
              onChange={(e) => {
                setOrder(e.target.value);
                setSelected('');
              }}
            >
              <option value="">Chọn đơn</option>
              {rentals.data?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.code} · {o.customer_name}
                </option>
              ))}
            </select>
          </label>
        </QueryState>
        <QueryState loading={requests.loading} error={requests.error}>
          <label className="field">
            <span>Yêu cầu thanh toán gốc</span>
            <select value={selected} required onChange={(e) => setSelected(e.target.value)}>
              <option value="">Chọn khoản phải thu</option>
              {requests.data
                ?.filter((p) => !p.money_id)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {moneyKinds[p.kind]} · {money(p.amount)} · {p.code} · {date(p.expires_at, true)}
                  </option>
                ))}
            </select>
            <small>Nếu chưa có yêu cầu, tạo tại hồ sơ đơn đang còn hiệu lực.</small>
          </label>
        </QueryState>
        <label className="field">
          <span>Căn cứ đã đối chiếu với ngân hàng và khách</span>
          <textarea name="reason" required maxLength={300} />
        </label>
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={command.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={command.busy} disabled={!selected}>
            Xác nhận ghi sổ
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
