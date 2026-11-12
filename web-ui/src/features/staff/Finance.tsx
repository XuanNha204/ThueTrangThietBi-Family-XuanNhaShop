import { useState } from 'react';
import type { ReactNode } from 'react';
import { Check, Download, Plus, RotateCcw, Wallet, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { SePay } from './SePay';
import type { Row, RentalSummary } from '../../shared/types';
import { date, money, moneyKinds } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  ConfirmDialog,
  FormDialog,
  InfoNote,
  PageHeader,
  Panel,
  QueryState,
  SearchInput,
  Table,
} from '../../shared/ui';
export function Finance() {
  const ledger = useQuery<Row[]>('/finance/ledger'),
    approvals = useQuery<Row[]>('/finance/approvals'),
    orders = useQuery<RentalSummary[]>('/rentals');
  const { user } = useApp();
  const cmd = useCommand();
  const [tab, setTab] = useState('ledger'),
    [search, setSearch] = useState(''),
    [modal, setModal] = useState<ReactNode>(null);
  const close = () => setModal(null);
  const rows = (ledger.data ?? []).filter((m) =>
    (m.order_code + ' ' + m.note + ' ' + (moneyKinds[m.kind] ?? ''))
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  function receive() {
    setModal(
      <FormDialog
        title="Ghi nhận khoản thu thực tế"
        description="Đối chiếu tiền đã nhận trước khi ghi sổ. Tiền cọc không được tính vào tiền thuê. Bản dev không gọi cổng thanh toán."
        onClose={close}
        fields={[
          {
            key: 'orderId',
            label: 'Đơn thuê',
            options: (orders.data ?? []).map((o) => ({
              value: o.id,
              label: o.code + ' · ' + o.customer_name,
            })),
          },
          {
            key: 'kind',
            label: 'Loại khoản thu',
            options: [
              { value: 'DEPOSIT_IN', label: 'Nhận tiền cọc' },
              { value: 'RENTAL_IN', label: 'Thu tiền thuê' },
              { value: 'CHARGE_IN', label: 'Thu bồi thường đã duyệt' },
            ],
          },
          { key: 'amount', label: 'Số tiền thực nhận (VND)', type: 'number', min: 1, step: 1 },
          { key: 'note', label: 'Căn cứ / số chứng từ', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              '/finance/receive',
              { ...v, amount: Number(v.amount) },
              'Đã ghi nhận khoản thu.',
            )
          ).ok
        }
      />,
    );
  }
  function decide(a: Row, approved: boolean) {
    setModal(
      <ConfirmDialog
        title={approved ? 'Xác nhận hoàn cọc thực tế?' : 'Từ chối yêu cầu hoàn cọc?'}
        description={
          approved
            ? `Đơn ${a.order_code}: ${money(a.amount)}. Chỉ xác nhận sau khi đối chiếu việc hoàn tiền thực tế; thao tác sẽ duyệt và ghi sổ khoản hoàn. Không có lệnh chuyển tiền ngân hàng.`
            : 'Yêu cầu sẽ được lưu ở trạng thái từ chối; tiền cọc đang giữ không thay đổi.'
        }
        onClose={close}
        onConfirm={async () =>
          (
            await cmd.run(
              '/finance/approvals/' + a.id,
              { approved },
              approved ? 'Đã duyệt và ghi sổ hoàn cọc.' : 'Đã từ chối yêu cầu.',
            )
          ).ok
        }
      />,
    );
  }
  const deposit = (ledger.data ?? []).reduce(
      (n, m) =>
        n +
        (m.kind === 'DEPOSIT_IN'
          ? Number(m.amount)
          : m.kind === 'DEPOSIT_REFUND'
            ? -Number(m.amount)
            : 0),
      0,
    ),
    rent = (ledger.data ?? [])
      .filter((m) => m.kind === 'RENTAL_IN')
      .reduce((n, m) => n + Number(m.amount), 0);
  return (
    <>
      <PageHeader
        eyebrow="QUẢN LÝ"
        title="Thu chi & tiền cọc"
        description="Ghi nhận có căn cứ, tách rõ tiền thuê và nghĩa vụ hoàn cọc."
        action={
          <Button onClick={receive}>
            <Plus size={17} />
            Ghi nhận khoản thu
          </Button>
        }
      />
      <div className="finance-cards">
        <div>
          <span>Tiền thuê đã thu · danh sách hiện tại</span>
          <strong>{money(rent)}</strong>
          <small>Không bao gồm tiền cọc</small>
        </div>
        <div>
          <span>Tiền cọc đang giữ · danh sách hiện tại</span>
          <strong>{money(deposit)}</strong>
          <small>Khoản phải đối chiếu với khách</small>
        </div>
        <div>
          <span>Yêu cầu chờ phê duyệt</span>
          <strong>{approvals.data?.filter((a) => a.status === 'PENDING').length ?? 0}</strong>
          <small>Người duyệt khác người lập</small>
        </div>
      </div>
      <Panel>
        <div className="tabs">
          <button className={tab === 'ledger' ? 'active' : ''} onClick={() => setTab('ledger')}>
            Sổ giao dịch
          </button>
          <button
            className={tab === 'approvals' ? 'active' : ''}
            onClick={() => setTab('approvals')}
          >
            Phê duyệt hoàn cọc
          </button>
          <button className={tab === 'sepay' ? 'active' : ''} onClick={() => setTab('sepay')}>
            Webhook SePay
          </button>
        </div>
        {tab === 'sepay' ? (
          <SePay />
        ) : tab === 'ledger' ? (
          <>
            <div className="table-toolbar">
              <SearchInput
                value={search}
                onChange={setSearch}
                placeholder="Tìm mã đơn, căn cứ hoặc loại tiền…"
              />
              <Link className="button secondary small" to="/app/reports">
                <Download size={15} />
                Xuất theo kỳ
              </Link>
            </div>
            <QueryState loading={ledger.loading} error={ledger.error}>
              <Table
                headers={[
                  'Thời điểm',
                  'Đơn thuê',
                  'Loại giao dịch',
                  'Số tiền',
                  'Người ghi',
                  'Căn cứ',
                ]}
                empty={!rows.length}
              >
                {rows.map((m) => (
                  <tr key={m.id}>
                    <td>{date(m.created_at, true)}</td>
                    <td>
                      {m.order_id ? (
                        <Link className="order-code" to={'/app/orders/' + m.order_id}>
                          {m.order_code}
                        </Link>
                      ) : (
                        'Chi lương'
                      )}
                    </td>
                    <td>
                      <span
                        className={`money-kind ${m.kind === 'DEPOSIT_IN' ? 'deposit' : m.kind.endsWith('OUT') || m.kind === 'DEPOSIT_REFUND' ? 'out' : 'income'}`}
                      >
                        {moneyKinds[m.kind] ?? m.kind}
                      </span>
                    </td>
                    <td className="amount">{money(m.amount)}</td>
                    <td>{m.recorded_by}</td>
                    <td className="wrap-cell">{m.note}</td>
                  </tr>
                ))}
              </Table>
            </QueryState>
          </>
        ) : (
          <QueryState loading={approvals.loading} error={approvals.error}>
            <Table
              headers={['Đơn thuê', 'Số tiền hoàn', 'Người lập', 'Lý do', 'Trạng thái', 'Xử lý']}
              empty={!approvals.data?.length}
            >
              {approvals.data?.map((a) => (
                <tr key={a.id}>
                  <td>
                    <Link className="order-code" to={'/app/orders/' + a.target_id}>
                      {a.order_code}
                    </Link>
                    <small>{date(a.created_at, true)}</small>
                  </td>
                  <td className="amount">{money(a.amount)}</td>
                  <td>
                    {a.requester}
                    <small>{a.approver ? 'Duyệt: ' + a.approver : ''}</small>
                  </td>
                  <td className="wrap-cell">{a.reason}</td>
                  <td>
                    <Badge status={a.status} />
                  </td>
                  <td>
                    {a.status === 'PENDING' ? (
                      a.requester === user?.username ? (
                        <small>
                          Bạn là người lập.
                          <br />
                          Cần người khác phê duyệt.
                        </small>
                      ) : (
                        <div className="button-group">
                          <Button className="secondary small" onClick={() => decide(a, false)}>
                            <X size={14} />
                            Từ chối
                          </Button>
                          <Button className="small" onClick={() => decide(a, true)}>
                            <Check size={14} />
                            Duyệt & ghi hoàn
                          </Button>
                        </div>
                      )
                    ) : (
                      <span className="muted">Đã xử lý</span>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          </QueryState>
        )}
      </Panel>
      {modal}
    </>
  );
}
