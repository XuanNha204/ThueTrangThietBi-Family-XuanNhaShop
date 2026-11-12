import { RefreshCw, ShieldCheck } from 'lucide-react';
import type { Row } from '../../shared/types';
import { date } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import { Button, InfoNote, QueryState, Table } from '../../shared/ui';
import { SePayConfiguration } from './SePayConfiguration';
import { SePayResolution, reconciliationLabels } from './SePayResolution';
const states: Record<string, string> = {
  TEST: 'Gửi thử — không ghi tiền',
  RECEIVED: 'Đã nhận — chờ đối soát',
  CONFLICT: 'Dữ liệu gửi lại khác nhau',
};
function amount(value: string) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(BigInt(value));
}
export function SePay() {
  const status = useQuery<Row>('/finance/sepay/status'),
    events = useQuery<Row[]>('/finance/sepay/events'),
    { refresh } = useApp();
  return (
    <div className="lifecycle-body sepay-inbox">
      <div className="panel-heading">
        <div>
          <h2>Webhook SePay</h2>
          <p>Theo dõi thông báo ngân hàng đã được xác thực</p>
        </div>
        <Button
          className="secondary small"
          busy={status.loading || events.loading}
          onClick={refresh}
        >
          <RefreshCw size={16} />
          Cập nhật SePay
        </Button>
      </div>
      <InfoNote>
        Giao dịch chỉ ghi sổ khi khớp tài khoản nhận và nghĩa vụ. Mẫu thử không ghi tiền. Khoản
        thiếu, thừa, đến muộn hoặc mã không rõ cần kế toán đối soát. Ghi cọc không tự xác nhận đơn.
      </InfoNote>
      <div className="sepay-bank-configuration">
        <SePayConfiguration />
      </div>
      <QueryState loading={status.loading} error={status.error}>
        {status.data && (
          <div className="summary-pills">
            <span>
              <ShieldCheck size={16} />
              HMAC: {status.data.ready ? 'Sẵn sàng' : 'Chưa cấu hình'}
            </span>
            <span>{status.data.event_count} thông báo đang lưu</span>
            <span>Nhận gần nhất: {date(status.data.last_received_at, true)}</span>
          </div>
        )}
      </QueryState>
      <QueryState loading={events.loading} error={events.error}>
        <Table
          empty={!events.data?.length}
          headers={[
            'Giao dịch / thời điểm',
            'Ngân hàng / số tiền',
            'Mã / tham chiếu',
            'Đối soát và ghi sổ',
            'Lần nhận',
            'Thao tác',
          ]}
        >
          {events.data?.map((e) => (
            <tr key={e.id}>
              <td>
                <strong>
                  {String(e.transaction_id) === '0' ? 'Gửi thử' : '#' + e.transaction_id}
                </strong>
                <small>{date(e.occurred_at, true)}</small>
              </td>
              <td>
                {e.gateway}
                <small>{e.account_masked}</small>
                <strong className="amount">{amount(e.amount)}</strong>
                <small>{e.transfer_type === 'in' ? 'Tiền vào' : 'Tiền ra'}</small>
              </td>
              <td className="wrap-cell">
                {e.payment_code || 'Chưa có mã'}
                <small>{e.reference_code || 'Không có tham chiếu'}</small>
              </td>
              <td className="wrap-cell">
                <strong>
                  {e.status === 'TEST'
                    ? states.TEST
                    : reconciliationLabels[e.reconciliation_reason] || e.reconciliation_status}
                </strong>
                {e.status === 'CONFLICT' && <small>{states.CONFLICT}</small>}
                {e.conflict_count > 0 && (
                  <small>{e.conflict_count} lần có khác biệt cần đối chiếu</small>
                )}
                {e.order_code && (
                  <small>
                    Đơn {e.order_code} · {e.request_code}
                  </small>
                )}
                {e.money_id && (
                  <small title={e.money_id}>Mã ghi sổ: {e.money_id.slice(0, 8)}…</small>
                )}
                {e.resolution_note && <small>{e.resolution_note}</small>}
              </td>
              <td>
                {e.delivery_count}
                <small>{date(e.last_received_at, true)}</small>
              </td>
              <td>
                {String(e.transaction_id) === '0' ? 'Mẫu kiểm tra' : <SePayResolution event={e} />}
              </td>
            </tr>
          ))}
        </Table>
      </QueryState>
    </div>
  );
}
