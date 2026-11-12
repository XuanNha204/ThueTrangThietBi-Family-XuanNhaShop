import { useCallback, useEffect, useRef, useState } from 'react';
import { QrCode, RefreshCw, X } from 'lucide-react';
import type { RentalDetail, Row } from '../../shared/types';
import { date, get, money, moneyKinds } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Button, ConfirmDialog, InfoNote, Panel, QueryState } from '../../shared/ui';

export function RentalPayments({
  detail,
  onChecked,
}: {
  detail: RentalDetail;
  onChecked: (next: RentalDetail) => void;
}) {
  const { user } = useApp(),
    command = useCommand();
  const allowed = !!user && ['OWNER', 'ACCOUNTANT', 'CUSTOMER'].includes(user.role);
  const q = useQuery<Row[]>(allowed ? `/rentals/${detail.order.id}/payments` : null);
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [checked, setChecked] = useState<{ base: Row[] | null; data: Row[] } | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState('');
  const pending = useRef<AbortController | null>(null);
  const payments = checked?.base === q.data ? checked.data : q.data;
  const checkPayment = useCallback(async () => {
    if (!allowed || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setChecking(true);
    setCheckError('');
    try {
      const data = await get<Row[]>(`/rentals/${detail.order.id}/payments`, controller.signal);
      const next = await get<RentalDetail>(`/rentals/${detail.order.id}`, controller.signal);
      if (!controller.signal.aborted) {
        setChecked({ base: q.data, data });
        onChecked(next);
      }
    } catch (error) {
      if (!controller.signal.aborted)
        setCheckError(
          error instanceof Error
            ? error.message
            : 'Chưa kiểm tra được thanh toán. Vui lòng thử lại.',
        );
    } finally {
      if (pending.current === controller) {
        pending.current = null;
        if (!controller.signal.aborted) setChecking(false);
      }
    }
  }, [allowed, detail.order.id, q.data, onChecked]);
  useEffect(
    () => () => {
      pending.current?.abort();
      pending.current = null;
    },
    [checkPayment],
  );
  const open = payments?.some(
    (p) => p.status === 'OPEN' && new Date(p.expires_at).getTime() > Date.now(),
  );
  useEffect(() => {
    if (!open) return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void checkPayment();
    }, 10000);
    return () => window.clearInterval(timer);
  }, [open, checkPayment]);
  if (!allowed) return null;
  const o = detail.order,
    b = detail.balance;
  const active =
    !['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(o.status) &&
    (o.status !== 'HELD' || new Date(o.expiresAt).getTime() > Date.now());
  const dues = [
    { kind: 'DEPOSIT_IN', amount: o.depositRequired - b.depositHeld, label: 'Tạo QR tiền cọc' },
    { kind: 'RENTAL_IN', amount: o.rentalTotal - b.rentalPaid, label: 'Tạo QR tiền thuê' },
    { kind: 'CHARGE_IN', amount: b.chargesDue - b.chargesPaid, label: 'Tạo QR bồi thường' },
  ];
  return (
    <Panel className="bank-payments">
      <div className="panel-heading">
        <div>
          <h2>Chuyển khoản SePay</h2>
          <p>Mã riêng cho từng khoản phải thu</p>
        </div>
        <Button
          className="secondary small"
          busy={q.loading || checking}
          onClick={() => void checkPayment()}
        >
          <RefreshCw size={16} />
          Kiểm tra thanh toán
        </Button>
      </div>
      <InfoNote>
        Chuyển đúng tài khoản, số tiền và nội dung trên QR. Tiền cọc tách tiền thuê. Hệ thống kiểm
        tra thanh toán định kỳ khi trang đang mở; nhận đủ cọc vẫn cần cửa hàng xác nhận đơn trước
        khi hết giữ chỗ.
      </InfoNote>
      {checkError && (
        <p className="inline-error" role="alert">
          {checkError}
        </p>
      )}
      {active && (
        <div className="bank-payment-actions">
          {dues
            .filter((d) => d.amount > 0)
            .map((d) => (
              <Button
                key={d.kind}
                className="secondary small"
                busy={command.busy}
                onClick={() =>
                  command.run(
                    `/rentals/${o.id}/payments`,
                    { kind: d.kind },
                    'Đã tạo yêu cầu chuyển khoản.',
                  )
                }
              >
                <QrCode size={16} />
                {d.label}
              </Button>
            ))}
        </div>
      )}
      <QueryState loading={q.loading && !payments} error={payments ? '' : q.error}>
        {!payments?.length && (
          <p className="subtle">
            Chưa có yêu cầu chuyển khoản. Chọn khoản cần thanh toán để lấy thông tin nhận tiền.
          </p>
        )}
        {payments?.map((p) => {
          const usable =
            active &&
            p.payable === true &&
            p.status === 'OPEN' &&
            new Date(p.expires_at).getTime() > Date.now();
          const label =
            p.status === 'PAID'
              ? 'Đã ghi nhận vào sổ tiền'
              : p.status === 'CANCELLED'
                ? 'Đã hủy yêu cầu'
                : usable
                  ? 'Chờ chuyển khoản'
                  : 'Cần tạo yêu cầu mới hoặc đơn đã hết hiệu lực';
          const qr = new URL('https://vietqr.app/img');
          for (const [key, value] of Object.entries({
            acc: p.account_number,
            bank: p.gateway,
            amount: p.amount,
            des: p.code,
            holder: p.account_holder
              .normalize('NFD')
              .replace(/[\u0300-\u036f]/g, '')
              .replace(/đ/g, 'd')
              .replace(/Đ/g, 'D')
              .toUpperCase(),
            template: 'compact',
          }))
            qr.searchParams.set(key, String(value));
          return (
            <article className="bank-payment-card" key={p.id}>
              <div className="bank-payment-details">
                <strong>
                  {moneyKinds[p.kind]} · {money(p.amount)}
                </strong>
                <span role="status">{label}</span>
                <span>
                  {p.gateway} · {p.account_number}
                </span>
                <span>{p.account_holder}</span>
                <span>
                  Nội dung: <strong className="payment-code">{p.code}</strong>
                </span>
                <small>Hạn thanh toán: {date(p.expires_at, true)}</small>
                {p.money_id && <small>Mã ghi sổ: {p.money_id}</small>}
                {p.status === 'OPEN' && (
                  <Button className="ghost small" onClick={() => setCancelling(p.id)}>
                    <X size={14} />
                    Hủy yêu cầu chuyển khoản
                  </Button>
                )}
              </div>
              {usable && (
                <div className="payment-qr">
                  <img
                    src={qr.toString()}
                    width={220}
                    height={220}
                    loading="lazy"
                    referrerPolicy="no-referrer"
                    alt={`QR ${moneyKinds[p.kind]} ${money(p.amount)}, nội dung ${p.code}`}
                  />
                  <small>Nếu ảnh QR không tải được, dùng thông tin chuyển khoản bên cạnh.</small>
                </div>
              )}
            </article>
          );
        })}
      </QueryState>
      {cancelling && (
        <ConfirmDialog
          title="Hủy yêu cầu chuyển khoản?"
          description="Thao tác này chỉ đóng mã thanh toán, không hủy đơn và không hoàn tiền. Nếu đã chuyển, liên hệ cửa hàng để đối soát."
          onClose={() => setCancelling(null)}
          onConfirm={async () =>
            (
              await command.run(
                `/payments/${cancelling}/cancel`,
                undefined,
                'Đã hủy yêu cầu chuyển khoản.',
              )
            ).ok
          }
        />
      )}
    </Panel>
  );
}
