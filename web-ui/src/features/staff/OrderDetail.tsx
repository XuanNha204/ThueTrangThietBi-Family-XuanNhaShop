import { useCallback, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  Check,
  ClipboardCheck,
  Download,
  FileText,
  MapPin,
  PackageCheck,
  Plus,
  RotateCcw,
  ShieldCheck,
  Truck,
  Upload,
  Wallet,
} from 'lucide-react';
import type { RentalDetail, Row } from '../../shared/types';
import { date, money } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  ConfirmDialog,
  Dialog,
  Empty,
  FormDialog,
  InfoNote,
  PageHeader,
  Panel,
  QueryState,
  Table,
} from '../../shared/ui';
import { CustomerHeader } from '../customer/Catalog';
import { RentalChanges } from '../rental/Changes';
import { RentalIncidents } from '../rental/Incidents';
import { RentalNotices } from '../rental/Notices';
import { RentalGuides } from '../rental/Guides';
import { RentalPayments } from '../rental/Payments';
export function OrderDetail({ customer = false }: { customer?: boolean }) {
  const { id } = useParams();
  const { user } = useApp();
  const q = useQuery<RentalDetail>('/rentals/' + id),
    history = useQuery<Row[]>('/rentals/' + id + '/handovers'),
    evidence = useQuery<Row[]>('/rentals/' + id + '/evidence');
  const command = useCommand();
  const [modal, setModal] = useState<ReactNode>(null),
    [uploading, setUploading] = useState(false);
  const [paymentSnapshot, setPaymentSnapshot] = useState<{
    base: RentalDetail | null;
    data: RentalDetail;
  } | null>(null);
  const onPaymentChecked = useCallback(
    (next: RentalDetail) => {
      setPaymentSnapshot({ base: q.data, data: next });
    },
    [q.data],
  );
  const d = paymentSnapshot?.base === q.data ? paymentSnapshot.data : q.data,
    o = d?.order;
  const close = () => setModal(null);
  const can = (...r: string[]) => !!user && r.includes(user.role);
  function confirmAction(path: string, title: string, description: string, success: string) {
    setModal(
      <ConfirmDialog
        title={title}
        description={description}
        onClose={close}
        onConfirm={async () => (await command.run(path, undefined, success)).ok}
      />,
    );
  }
  function receive(kind: string) {
    if (!d || !o) return;
    setModal(
      <FormDialog
        title={
          kind === 'DEPOSIT_IN'
            ? 'Ghi nhận cọc đã nhận'
            : kind === 'CHARGE_IN'
              ? 'Ghi nhận bồi thường đã nhận'
              : 'Ghi nhận tiền thuê đã nhận'
        }
        description="Chỉ ghi nhận khi đã kiểm tra khoản tiền thực nhận. Thao tác này không gọi ngân hàng hoặc chuyển tiền."
        onClose={close}
        submitLabel="Ghi nhận khoản thu"
        fields={[
          {
            key: 'amount',
            label: 'Số tiền thực nhận (VND)',
            type: 'number',
            min: 1,
            step: 1,
            defaultValue:
              kind === 'DEPOSIT_IN'
                ? o.depositRequired - d.balance.depositHeld
                : kind === 'CHARGE_IN'
                  ? d.balance.chargesDue - d.balance.chargesPaid
                  : o.rentalTotal - d.balance.rentalPaid,
          },
          { key: 'note', label: 'Căn cứ / số chứng từ', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await command.run(
              '/finance/receive',
              { orderId: id, kind, amount: Number(v.amount), note: v.note },
              'Đã ghi nhận khoản thu vào sổ tiền.',
            )
          ).ok
        }
      />,
    );
  }
  function refund() {
    if (!d) return;
    setModal(
      <FormDialog
        title="Lập yêu cầu hoàn cọc"
        description="Yêu cầu cần được một người có quyền khác phê duyệt. Bản dev ghi nhận hoàn tiền thủ công, không chuyển tiền qua ngân hàng."
        onClose={close}
        submitLabel="Gửi yêu cầu phê duyệt"
        fields={[
          {
            key: 'amount',
            label: 'Số tiền hoàn (VND)',
            type: 'number',
            min: 1,
            step: 1,
            defaultValue: d.balance.depositHeld,
          },
          { key: 'reason', label: 'Lý do và căn cứ', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await command.run(
              '/finance/refunds',
              { orderId: id, amount: Number(v.amount), reason: v.reason },
              'Đã gửi yêu cầu hoàn cọc.',
            )
          ).ok
        }
      />,
    );
  }
  function handover(returning = false) {
    if (!d) return;
    setModal(
      <HandoverDialog
        detail={d}
        returning={returning}
        onClose={close}
        onSubmit={async (body) =>
          (
            await command.run(
              `/rentals/${id}/${returning ? 'return' : 'handover'}`,
              body,
              returning
                ? 'Đã ghi nhận thiết bị trả về. Cần kiểm tra trước khi sẵn sàng.'
                : 'Đã ghi nhận biên bản bàn giao.',
            )
          ).ok
        }
      />,
    );
  }
  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    setUploading(true);
    try {
      const body = new FormData(form);
      const r = await command.run(`/rentals/${id}/evidence`, body, 'Đã đính kèm bằng chứng.');
      if (r.ok) form.reset();
    } finally {
      setUploading(false);
    }
  }
  const content = (
    <>
      <Link className="text-link back-link" to={customer ? '/my-rentals' : '/app/orders'}>
        <ArrowLeft size={16} />
        Danh sách đơn thuê
      </Link>
      <QueryState loading={q.loading} error={q.error}>
        {o && d && (
          <>
            <PageHeader
              eyebrow="HỒ SƠ THUÊ THIẾT BỊ"
              title={o.code}
              description={`Khách hàng: ${o.customerName}`}
              action={<Badge status={o.status} />}
            />
            <div className="order-detail-grid">
              <div className="order-main">
                <Panel>
                  <div className="panel-heading">
                    <h2>Thông tin đơn thuê</h2>
                    <span className="subtle-code">Phiên bản {o.agreementVersion}</span>
                  </div>
                  <div className="order-facts">
                    <div>
                      <CalendarDays />
                      <span>
                        Ngày giờ nhận<strong>{date(o.startsAt, true)}</strong>
                      </span>
                    </div>
                    <div>
                      <CalendarDays />
                      <span>
                        Trả dự kiến<strong>{date(o.endsAt, true)}</strong>
                      </span>
                    </div>
                    <div className="full">
                      <MapPin />
                      <span>
                        Địa điểm nhận / sử dụng<strong>{o.address}</strong>
                      </span>
                    </div>
                    {o.status === 'HELD' && (
                      <div className="full hold-deadline">
                        <ShieldCheck />
                        <span>
                          Hạn giữ chỗ<strong>{date(o.expiresAt, true)}</strong>
                        </span>
                      </div>
                    )}
                  </div>
                </Panel>
                <Panel>
                  <div className="panel-heading">
                    <div>
                      <h2>Thiết bị trong đơn</h2>
                      <p>{d.items.length} thiết bị · Theo dõi đến từng mã riêng</p>
                    </div>
                  </div>
                  <Table headers={['Thiết bị', 'Tiền thuê', 'Giao / trả', 'Tình trạng hiện tại']}>
                    {d.items.map((i) => (
                      <tr key={i.id}>
                        <td>
                          <strong>{i.type_name}</strong>
                          <small>{i.equipment_code}</small>
                        </td>
                        <td className="amount">{money(i.rental_amount)}</td>
                        <td>
                          {i.resolved_at ? (
                            <>
                              <span className="state-text">Mất — đã duyệt xử lý</span>
                              <small>{date(i.resolved_at, true)} · Không ghi nhận đã trả</small>
                            </>
                          ) : i.returned_at ? (
                            <>
                              <span className="state-text green">
                                Đã trả {date(i.returned_at, true)}
                              </span>
                              <small>
                                {i.return_condition === 'DAMAGED'
                                  ? 'Có sự cố cần kiểm tra'
                                  : 'Chờ kết quả kiểm tra'}
                              </small>
                            </>
                          ) : i.handed_at ? (
                            <>
                              <span>Đã giao</span>
                              <small>{date(i.handed_at, true)}</small>
                            </>
                          ) : (
                            <span className="muted">Chưa bàn giao</span>
                          )}
                        </td>
                        <td>
                          <Badge status={i.equipment_state} />
                        </td>
                      </tr>
                    ))}
                  </Table>
                </Panel>
                <Panel>
                  <div className="panel-heading">
                    <div>
                      <h2>Giao nhận & bằng chứng</h2>
                      <p>Lưu dấu vết mỗi lần bàn giao và thu hồi</p>
                    </div>
                    <FileText size={18} />
                  </div>
                  <div className="handover-history">
                    {history.data?.length ? (
                      history.data.map((h) => (
                        <div key={h.id}>
                          <span
                            className={`timeline-icon ${h.kind === 'OUT' ? 'terracotta' : 'sage'}`}
                          >
                            {h.kind === 'OUT' ? <Truck size={18} /> : <RotateCcw size={18} />}
                          </span>
                          <div>
                            <strong>
                              {h.kind === 'OUT' ? 'Bàn giao thiết bị' : 'Thu hồi thiết bị'} ·{' '}
                              {h.item_count} thiết bị
                            </strong>
                            <small>
                              {date(h.created_at, true)} · {h.performed_by}
                            </small>
                            <p>{h.note}</p>
                            <small>Người xác nhận: {h.accepted_by}</small>
                          </div>
                        </div>
                      ))
                    ) : (
                      <p className="muted">Chưa có biên bản giao nhận.</p>
                    )}
                    {history.error && <p className="inline-error">{history.error}</p>}
                  </div>
                  <div className="evidence-list">
                    {evidence.data?.map((f) => (
                      <a key={f.id} href={'/api/evidence/' + f.id} className="file-chip">
                        <FileText size={18} />
                        <span>
                          {f.original_name}
                          <small>
                            {Math.ceil(f.size / 1024)} KB · {date(f.created_at)}
                          </small>
                        </span>
                        <Download size={16} />
                      </a>
                    ))}
                  </div>
                  {can('OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN') && (
                    <form className="upload-inline" onSubmit={upload}>
                      <label className="field">
                        <span>Thêm ảnh hoặc biên bản</span>
                        <input
                          type="file"
                          name="file"
                          accept="image/png,image/jpeg,application/pdf"
                          required
                        />
                        <small>PNG, JPEG hoặc PDF · tối đa 5 MB · lưu riêng tư.</small>
                      </label>
                      <Button type="submit" className="secondary" busy={uploading}>
                        <Upload size={16} />
                        Đính kèm
                      </Button>
                    </form>
                  )}
                </Panel>
                <RentalPayments detail={d} onChecked={onPaymentChecked} />
                <RentalGuides orderId={id!} />
                <RentalChanges detail={d} />
                <RentalIncidents detail={d} />
                <RentalNotices orderId={o.id} />
                <Panel className="terms-panel">
                  <details>
                    <summary>
                      <FileText size={18} />
                      Điều khoản thuê ban đầu
                      <ArrowRight size={16} />
                    </summary>
                    <p>{o.terms}</p>
                  </details>
                </Panel>
              </div>
              <aside className="order-side">
                <Panel className="finance-summary">
                  <span className="eyebrow">ĐỐI CHIẾU TÀI CHÍNH</span>
                  <h2>Tiền thuê & tiền cọc</h2>
                  <div className="summary-line">
                    <span>Tiền thuê thỏa thuận</span>
                    <strong>{money(o.rentalTotal)}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Tiền thuê đã nhận</span>
                    <strong>{money(d.balance.rentalPaid)}</strong>
                  </div>
                  <div className="summary-line total">
                    <span>Thuê còn phải nhận</span>
                    <strong>{money(Math.max(0, o.rentalTotal - d.balance.rentalPaid))}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Cọc theo thỏa thuận</span>
                    <strong>{money(o.depositRequired)}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Cọc đang giữ</span>
                    <strong>{money(d.balance.depositHeld)}</strong>
                  </div>
                  {d.balance.chargesDue > 0 && (
                    <>
                      <div className="summary-line">
                        <span>Bồi thường đã duyệt</span>
                        <strong>{money(d.balance.chargesDue)}</strong>
                      </div>
                      <div className="summary-line">
                        <span>Bồi thường đã nhận</span>
                        <strong>{money(d.balance.chargesPaid)}</strong>
                      </div>
                      <div className="summary-line">
                        <span>Bồi thường còn phải nhận</span>
                        <strong>
                          {money(Math.max(0, d.balance.chargesDue - d.balance.chargesPaid))}
                        </strong>
                      </div>
                    </>
                  )}
                  <InfoNote>
                    Tiền cọc được theo dõi riêng, không tính vào tiền thuê đã thu.
                  </InfoNote>
                  {can('OWNER', 'ACCOUNTANT') && (
                    <div className="action-stack">
                      {d.balance.depositHeld < o.depositRequired &&
                        !['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(o.status) && (
                          <Button onClick={() => receive('DEPOSIT_IN')}>
                            <Wallet size={16} />
                            Ghi nhận cọc
                          </Button>
                        )}
                      {d.balance.rentalPaid < o.rentalTotal &&
                        !['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(o.status) && (
                          <Button className="secondary" onClick={() => receive('RENTAL_IN')}>
                            Thu tiền thuê
                          </Button>
                        )}
                      {d.balance.chargesPaid < d.balance.chargesDue && (
                        <Button className="secondary" onClick={() => receive('CHARGE_IN')}>
                          Thu bồi thường đã duyệt
                        </Button>
                      )}
                      {d.balance.depositHeld > 0 &&
                        ['CANCELLED', 'EXPIRED', 'AWAIT_SETTLEMENT'].includes(o.status) && (
                          <Button className="secondary" onClick={refund}>
                            <RotateCcw size={16} />
                            Lập yêu cầu hoàn cọc
                          </Button>
                        )}
                    </div>
                  )}
                </Panel>
                <Panel className="order-actions">
                  <h2>Thao tác đơn thuê</h2>
                  <div className="action-stack">
                    {can('OWNER', 'MANAGER', 'INVENTORY') && o.status === 'HELD' && (
                      <Button
                        onClick={() =>
                          confirmAction(
                            `/rentals/${id}/confirm`,
                            'Xác nhận đơn thuê?',
                            'Hệ thống kiểm tra lại hiệu lực giữ chỗ, lịch và cọc đã nhận.',
                            'Đã xác nhận đơn thuê.',
                          )
                        }
                      >
                        <Check size={16} />
                        Xác nhận đơn
                      </Button>
                    )}
                    {can('OWNER', 'MANAGER', 'INVENTORY') && o.status === 'CONFIRMED' && (
                      <Button
                        className="secondary"
                        onClick={() =>
                          confirmAction(
                            `/rentals/${id}/prepare`,
                            'Chuẩn bị thiết bị?',
                            'Chỉ chuyển thiết bị đang sẵn sàng sang bước chuẩn bị cho đơn này.',
                            'Đã chuyển đơn sang chuẩn bị.',
                          )
                        }
                      >
                        <PackageCheck size={16} />
                        Bắt đầu chuẩn bị
                      </Button>
                    )}
                    {can('OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY') &&
                      ['CONFIRMED', 'PREPARING', 'RENTED', 'PARTIAL_RETURN'].includes(o.status) &&
                      d.items.some((i) => !i.handed_at) && (
                        <Button onClick={() => handover()}>
                          <Truck size={16} />
                          Bàn giao thiết bị
                        </Button>
                      )}
                    {can('OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY') &&
                      ['RENTED', 'PARTIAL_RETURN'].includes(o.status) && (
                        <Button onClick={() => handover(true)}>
                          <RotateCcw size={16} />
                          Ghi nhận trả thiết bị
                        </Button>
                      )}
                    {can('OWNER', 'ACCOUNTANT') && o.status === 'AWAIT_SETTLEMENT' && (
                      <Button
                        onClick={() =>
                          confirmAction(
                            `/rentals/${id}/settle`,
                            'Hoàn tất đơn thuê?',
                            'Đơn chỉ được tất toán khi đã xử lý đủ nghĩa vụ thiết bị, thu đủ tiền thuê và bồi thường đã duyệt, xử lý hết cọc.',
                            'Đã hoàn tất đơn thuê.',
                          )
                        }
                      >
                        <ClipboardCheck size={16} />
                        Tất toán đơn
                      </Button>
                    )}
                    {can('OWNER', 'MANAGER', 'INVENTORY', 'CUSTOMER') &&
                      ['HELD', 'CONFIRMED', 'PREPARING'].includes(o.status) && (
                        <Button
                          className="danger-text"
                          onClick={() =>
                            confirmAction(
                              `/rentals/${id}/cancel`,
                              'Hủy đơn thuê này?',
                              'Lịch thiết bị sẽ được giải phóng. Khoản tiền đã nhận vẫn được giữ trong sổ để kế toán đối soát và xử lý hoàn cọc.',
                              'Đã hủy đơn thuê.',
                            )
                          }
                        >
                          Hủy đơn thuê
                        </Button>
                      )}
                    {['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(o.status) && (
                      <p className="muted">
                        Đơn đã kết thúc bước vận hành. Hồ sơ và sổ tiền được giữ để đối chiếu.
                      </p>
                    )}
                    {customer && (
                      <p className="muted">
                        Cửa hàng đối soát cọc và xác nhận đơn trước khi chuẩn bị bàn giao.
                      </p>
                    )}
                  </div>
                  <Link className="text-link" to={customer ? '/my-support' : '/app/support'}>
                    Cần hỗ trợ về đơn này
                    <ArrowRight size={15} />
                  </Link>
                </Panel>
              </aside>
            </div>
          </>
        )}
      </QueryState>
      {modal}
    </>
  );
  return customer ? (
    <>
      <CustomerHeader />
      <main className="store-container customer-content">{content}</main>
    </>
  ) : (
    content
  );
}
function HandoverDialog({
  detail,
  returning,
  onClose,
  onSubmit,
}: {
  detail: RentalDetail;
  returning: boolean;
  onClose: () => void;
  onSubmit: (body: unknown) => Promise<boolean>;
}) {
  const available = detail.items.filter((i) =>
    returning ? i.handed_at && !i.returned_at && !i.resolved_at : !i.handed_at,
  );
  const [selected, setSelected] = useState(available.map((i) => i.id)),
    [accepted, setAccepted] = useState(detail.order.customerName),
    [condition, setCondition] = useState('GOOD'),
    [note, setNote] = useState(''),
    [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      if (await onSubmit({ items: selected, acceptedBy: accepted, condition, note })) onClose();
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={returning ? 'Ghi nhận thiết bị trả về' : 'Lập biên bản bàn giao'}
      description="Đối chiếu thực tế từng mã thiết bị và ghi nhận người xác nhận."
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form className="form-stack" onSubmit={submit}>
        <fieldset className="item-selector">
          <legend>Chọn thiết bị thực tế {returning ? 'đã trả' : 'bàn giao'}</legend>
          {available.map((i) => (
            <label key={i.id}>
              <input
                type="checkbox"
                checked={selected.includes(i.id)}
                onChange={(e) =>
                  setSelected((v) =>
                    e.target.checked ? [...v, i.id] : v.filter((id) => id !== i.id),
                  )
                }
              />
              <span>
                <strong>{i.equipment_code}</strong>
                {i.type_name}
              </span>
            </label>
          ))}
        </fieldset>
        <label className="field">
          <span>Người xác nhận nhận / trả</span>
          <input
            required
            maxLength={120}
            value={accepted}
            onChange={(e) => setAccepted(e.target.value)}
          />
        </label>
        {returning && (
          <label className="field">
            <span>Tình trạng khi nhận lại</span>
            <select value={condition} onChange={(e) => setCondition(e.target.value)}>
              <option value="GOOD">Chưa ghi nhận hư hỏng</option>
              <option value="DAMAGED">Có sự cố / hư hỏng cần kiểm tra</option>
            </select>
          </label>
        )}
        <label className="field">
          <span>Phụ kiện, tình trạng & ghi chú thực tế</span>
          <textarea
            required
            rows={3}
            maxLength={1000}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
        {returning && (
          <InfoNote>
            Thiết bị trả về luôn cần kiểm tra trước khi chuyển sang sẵn sàng. Việc trả thiết bị
            không tự hoàn tiền cọc.
          </InfoNote>
        )}
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={busy} disabled={!selected.length}>
            Lưu biên bản
            <Check size={17} />
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
