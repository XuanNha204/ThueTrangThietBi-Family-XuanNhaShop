import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { ArrowRight, Check, FileClock, Plus, RefreshCw } from 'lucide-react';
import type { Equipment, RentalDetail, Row } from '../../shared/types';
import { date, localInput, shopDate, money, request } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  ConfirmDialog,
  Dialog,
  FormDialog,
  InfoNote,
  Panel,
  QueryState,
} from '../../shared/ui';

export const changeKinds: Record<string, string> = {
  EXTEND: 'Gia hạn thuê',
  RESCHEDULE: 'Đổi lịch thuê',
  ADDRESS: 'Đổi địa chỉ nhận',
  SWAP: 'Đổi mã thiết bị',
};
function parse(value: string | null): Row {
  try {
    return value ? JSON.parse(value) : {};
  } catch {
    return {};
  }
}
export function RentalChanges({ detail }: { detail: RentalDetail }) {
  const { user } = useApp();
  const q = useQuery<{ changes: Row[]; revisions: Row[] }>(
    '/rentals/' + detail.order.id + '/changes',
  );
  const command = useCommand();
  const [modal, setModal] = useState<ReactNode>(null);
  const close = () => setModal(null),
    base = '/rentals/' + detail.order.id + '/changes/';
  const staff = ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role);
  const editable =
    (staff || user?.role === 'CUSTOMER') &&
    ['HELD', 'CONFIRMED', 'PREPARING', 'RENTED', 'PARTIAL_RETURN'].includes(detail.order.status);
  function decide(change: Row, approved: boolean) {
    setModal(
      <FormDialog
        title={approved ? 'Duyệt áp dụng thay đổi' : 'Từ chối đề nghị'}
        description={
          approved
            ? 'Kiểm tra lại lịch, tình trạng thiết bị và xác nhận của khách. Khi thành công, thỏa thuận mới được lưu cùng lịch đã điều chỉnh.'
            : 'Giữ nguyên thỏa thuận và lịch thuê hiện tại.'
        }
        onClose={close}
        submitLabel={approved ? 'Duyệt và cập nhật thỏa thuận' : 'Lưu từ chối'}
        fields={[{ key: 'note', label: 'Căn cứ quyết định', type: 'textarea' }]}
        onSubmit={async (v) =>
          (
            await command.run(
              base + change.id + '/decide',
              { approved, note: v.note },
              approved ? 'Đã lưu thỏa thuận mới và cập nhật lịch.' : 'Đã từ chối đề nghị.',
            )
          ).ok
        }
      />,
    );
  }
  function consent(change: Row) {
    setModal(
      <FormDialog
        title="Ghi nhận khách đã chấp thuận"
        description="Chỉ ghi nhận sau khi thực sự đối chiếu với khách. Nội dung này lưu người ghi nhận và căn cứ; không phải chữ ký số."
        onClose={close}
        submitLabel="Lưu căn cứ chấp thuận"
        fields={[
          {
            key: 'customerName',
            label: 'Người chấp thuận',
            defaultValue: detail.order.customerName,
          },
          {
            key: 'basis',
            label: 'Căn cứ: biên bản, tin nhắn hoặc cuộc gọi đã đối chiếu',
            type: 'textarea',
          },
        ]}
        onSubmit={async (v) =>
          (
            await command.run(
              base + change.id + '/consent',
              v,
              'Đã ghi nhận căn cứ khách chấp thuận.',
            )
          ).ok
        }
      />,
    );
  }
  function action(change: Row, accept: boolean) {
    setModal(
      <ConfirmDialog
        title={accept ? 'Chấp thuận báo giá thay đổi?' : 'Rút hoặc từ chối đề nghị?'}
        description={
          accept
            ? 'Bạn đồng ý thời gian, địa chỉ và giá nêu trong đề nghị. Cửa hàng vẫn cần kiểm tra và duyệt áp dụng; lịch hiện tại chưa thay đổi.'
            : 'Đơn tiếp tục theo thỏa thuận hiện hành.'
        }
        onClose={close}
        onConfirm={async () =>
          (
            await command.run(
              base + change.id + (accept ? '/accept' : '/withdraw'),
              undefined,
              accept ? 'Đã chấp thuận, chờ cửa hàng duyệt.' : 'Đã rút đề nghị.',
            )
          ).ok
        }
      />,
    );
  }
  return (
    <Panel className="lifecycle-panel">
      <div className="panel-heading">
        <div>
          <h2>Thay đổi & phiên bản thỏa thuận</h2>
          <p>Giữ lại căn cứ trước và sau mỗi lần điều chỉnh</p>
        </div>
        {editable && (
          <Button
            className="secondary small"
            onClick={() => setModal(<ChangeDialog detail={detail} onClose={close} />)}
          >
            <Plus size={16} />
            Đề nghị thay đổi
          </Button>
        )}
      </div>
      <QueryState loading={q.loading} error={q.error}>
        <div className="lifecycle-body">
          {!q.data?.changes.length && (
            <p className="muted">
              Chưa có đề nghị thay đổi. Lịch và giá hiện tại tiếp tục được áp dụng.
            </p>
          )}
          {q.data?.changes.map((change) => {
            const draft = parse(change.draft_json),
              pending = change.status === 'PENDING';
            return (
              <article className="change-card" key={change.id}>
                <div className="lifecycle-card-heading">
                  <strong>{changeKinds[change.kind]}</strong>
                  <Badge status={change.status} />
                </div>
                <p>{draft.reason}</p>
                <dl className="change-facts">
                  <div>
                    <dt>Thời gian đề nghị</dt>
                    <dd>
                      {date(draft.startsAt, true)} → {date(draft.endsAt, true)}
                    </dd>
                  </div>
                  <div>
                    <dt>Tiền thuê trước → sau</dt>
                    <dd>
                      {money(draft.previousTotal)} → <strong>{money(draft.rentalTotal)}</strong>
                    </dd>
                  </div>
                  <div>
                    <dt>Địa chỉ</dt>
                    <dd>{draft.address}</dd>
                  </div>
                </dl>
                {change.kind === 'SWAP' && (
                  <p>
                    Mã trong thỏa thuận mới:{' '}
                    {draft.items?.map((item: Row) => item.equipmentCode).join(', ')}
                  </p>
                )}
                <div className="change-meta">
                  <span>
                    Đề nghị: {change.requested_by} · {date(change.created_at, true)}
                  </span>
                  <span>
                    {change.customer_accepted_by
                      ? 'Khách chấp thuận: ' + change.customer_accepted_by
                      : 'Chưa có xác nhận khách hàng'}
                  </span>
                  {change.consent_recorded_by && (
                    <span>
                      Ghi nhận: {change.consent_recorded_by} · {change.consent_note}
                    </span>
                  )}
                  {change.decision_note && <span>Kết quả: {change.decision_note}</span>}
                </div>
                {pending && (
                  <div className="lifecycle-actions">
                    {user?.role === 'CUSTOMER' && !change.customer_accepted_by && (
                      <Button className="small" onClick={() => action(change, true)}>
                        <Check size={15} />
                        Chấp thuận báo giá
                      </Button>
                    )}
                    {['OWNER', 'MANAGER'].includes(user!.role) && !change.customer_accepted_by && (
                      <Button className="secondary small" onClick={() => consent(change)}>
                        Ghi nhận khách chấp thuận
                      </Button>
                    )}
                    {staff && (
                      <>
                        <Button
                          className="small"
                          disabled={!change.customer_accepted_by}
                          onClick={() => decide(change, true)}
                        >
                          Duyệt áp dụng
                        </Button>
                        <Button className="secondary small" onClick={() => decide(change, false)}>
                          Từ chối
                        </Button>
                      </>
                    )}
                    {(user?.role === 'CUSTOMER' ||
                      change.requested_by === user?.username ||
                      ['OWNER', 'MANAGER'].includes(user!.role)) && (
                      <Button className="secondary small" onClick={() => action(change, false)}>
                        Rút / từ chối đề nghị
                      </Button>
                    )}
                  </div>
                )}
              </article>
            );
          })}
          <div className="revision-list">
            {q.data?.revisions.map((rev) => {
              const snapshot = parse(rev.snapshot_json);
              return (
                <details key={rev.revision}>
                  <summary>
                    <FileClock size={17} />
                    <strong>Thỏa thuận #{rev.revision}</strong>
                    <span>{date(rev.accepted_at, true)}</span>
                  </summary>
                  <div className="revision-content">
                    <p>{rev.terms}</p>
                    {snapshot.startsAt && (
                      <p>
                        {date(snapshot.startsAt, true)} → {date(snapshot.endsAt, true)}
                      </p>
                    )}
                    {snapshot.address && <p>Địa chỉ: {snapshot.address}</p>}
                    {snapshot.rentalTotal != null && (
                      <p>
                        Tiền thuê: <strong>{money(snapshot.rentalTotal)}</strong> · Cọc:{' '}
                        {money(snapshot.depositRequired)}
                      </p>
                    )}
                    {snapshot.items?.map((item: Row) => (
                      <p key={item.itemId}>
                        {item.equipmentCode} · {item.name} · {money(item.rentalAmount)}
                      </p>
                    ))}
                    <p className="muted">
                      Người ghi nhận: {rev.staff_confirmed_by ?? rev.accepted_by}
                      {rev.customer_confirmed_by ? ' · Khách: ' + rev.customer_confirmed_by : ''}
                    </p>
                    {rev.consent_note && <p>{rev.consent_note}</p>}
                  </div>
                </details>
              );
            })}
          </div>
        </div>
      </QueryState>
      {modal}
    </Panel>
  );
}

function ChangeDialog({ detail, onClose }: { detail: RentalDetail; onClose: () => void }) {
  const { user } = useApp(),
    command = useCommand();
  const assets = useQuery<Equipment[]>(user?.role !== 'CUSTOMER' ? '/equipment' : null);
  const o = detail.order,
    beforeHandover = !detail.items.some((i) => i.handed_at);
  const [kind, setKind] = useState('EXTEND'),
    [from, setFrom] = useState(localInput(new Date(o.startsAt))),
    [until, setUntil] = useState(localInput(new Date(new Date(o.endsAt).getTime() + 86400000))),
    [address, setAddress] = useState(o.address),
    [reason, setReason] = useState(''),
    [itemId, setItemId] = useState(detail.items[0]?.id ?? ''),
    [equipmentId, setEquipmentId] = useState(''),
    [quote, setQuote] = useState<Row | null>(null),
    [quotedFor, setQuotedFor] = useState(''),
    [accepted, setAccepted] = useState(false),
    [quoting, setQuoting] = useState(false),
    [error, setError] = useState('');
  const selected = detail.items.find((i) => i.id === itemId);
  const iso = (value: string) => {
    const time = shopDate(value);
    return Number.isFinite(time.getTime()) ? time.toISOString() : null;
  };
  const body = {
    kind,
    expectedVersion: o.version,
    startsAt: kind === 'RESCHEDULE' ? iso(from) : null,
    endsAt: ['EXTEND', 'RESCHEDULE'].includes(kind) && until ? iso(until) : null,
    address: kind === 'ADDRESS' ? address : null,
    swaps: kind === 'SWAP' ? [{ itemId, equipmentId }] : [],
    reason,
    acceptTerms: true,
  };
  const signature = JSON.stringify(body),
    currentQuote = quote && quotedFor === signature ? quote : null;
  useEffect(() => setAccepted(false), [signature]);
  async function preview() {
    setQuoting(true);
    setError('');
    try {
      const data = await request<Row>(`/rentals/${o.id}/changes/quote`, { method: 'POST', body });
      setQuote(data);
      setQuotedFor(signature);
    } catch (e) {
      setQuote(null);
      setError((e as Error).message);
    } finally {
      setQuoting(false);
    }
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!currentQuote || !accepted) return;
    if (
      (
        await command.run(
          `/rentals/${o.id}/changes`,
          body,
          'Đã gửi đề nghị; lịch hiện tại chưa thay đổi.',
        )
      ).ok
    )
      onClose();
  }
  return (
    <Dialog
      title="Đề nghị thay đổi đơn thuê"
      description="Xem báo giá, xác nhận của khách rồi cửa hàng mới duyệt áp dụng. Đề nghị chưa giữ chỗ trong lịch mới."
      onClose={() => {
        if (!command.busy && !quoting) onClose();
      }}
    >
      <form className="form-stack" onSubmit={submit}>
        <label className="field">
          <span>Nội dung thay đổi</span>
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="EXTEND">Gia hạn thuê</option>
            {beforeHandover && (
              <>
                <option value="RESCHEDULE">Đổi lịch thuê</option>
                <option value="ADDRESS">Đổi địa chỉ nhận</option>
                {user?.role !== 'CUSTOMER' && (
                  <option value="SWAP">Đổi mã thiết bị cùng loại</option>
                )}
              </>
            )}
          </select>
        </label>
        {kind === 'RESCHEDULE' && (
          <label className="field">
            <span>Ngày giờ nhận mới (giờ Việt Nam)</span>
            <input
              type="datetime-local"
              required
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
            {iso(from) && <small>Giờ nhận: {date(iso(from), true)} · UTC+7</small>}
          </label>
        )}
        {['EXTEND', 'RESCHEDULE'].includes(kind) && (
          <label className="field">
            <span>Ngày giờ trả mới (giờ Việt Nam)</span>
            <input
              type="datetime-local"
              required
              value={until}
              onChange={(e) => setUntil(e.target.value)}
            />
            {iso(until) && <small>Giờ trả: {date(iso(until), true)} · UTC+7</small>}
          </label>
        )}
        {kind === 'ADDRESS' && (
          <label className="field">
            <span>Địa chỉ nhận mới</span>
            <textarea
              required
              rows={3}
              maxLength={400}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
            />
          </label>
        )}
        {kind === 'SWAP' && (
          <>
            <label className="field">
              <span>Thiết bị cần thay</span>
              <select
                value={itemId}
                onChange={(e) => {
                  setItemId(e.target.value);
                  setEquipmentId('');
                }}
              >
                {detail.items.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.equipment_code} · {i.type_name}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Mã thay thế cùng loại</span>
              <select required value={equipmentId} onChange={(e) => setEquipmentId(e.target.value)}>
                <option value="">Chọn mã thay thế</option>
                {assets.data
                  ?.filter(
                    (a) =>
                      a.type_id === selected?.type_id &&
                      a.state === 'READY' &&
                      !detail.items.some((i) => i.equipment_id === a.id),
                  )
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} · {a.name}
                    </option>
                  ))}
              </select>
              {assets.error && <small className="inline-error">{assets.error}</small>}
            </label>
          </>
        )}
        <label className="field">
          <span>Lý do và nội dung đề nghị</span>
          <textarea
            required
            rows={3}
            maxLength={1000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <InfoNote>
          Gia hạn tính lại phần thiết bị còn thuê theo đơn giá đã thỏa thuận. Thiết bị đã trả giữ
          nguyên tiền thuê của dòng đó. Không tự cộng phí trả trễ hay bồi thường.
        </InfoNote>
        <Button
          type="button"
          className="secondary"
          busy={quoting}
          disabled={
            !reason.trim() ||
            (kind === 'SWAP' && !equipmentId) ||
            !until ||
            (kind === 'RESCHEDULE' && !from)
          }
          onClick={preview}
        >
          <RefreshCw size={16} />
          Xem báo giá thay đổi
        </Button>
        {error && (
          <div className="inline-error" role="alert">
            {error}
          </div>
        )}
        {currentQuote && (
          <div className="change-quote">
            <div>
              <span>Tiền thuê hiện tại</span>
              <strong>{money(currentQuote.previousTotal)}</strong>
            </div>
            <div>
              <span>Tiền thuê sau thay đổi</span>
              <strong>{money(currentQuote.rentalTotal)}</strong>
            </div>
            <div>
              <span>Chênh lệch tiền thuê</span>
              <strong>{money(currentQuote.rentalTotal - currentQuote.previousTotal)}</strong>
            </div>
            <div>
              <span>Tiền cọc</span>
              <strong>{money(currentQuote.depositRequired)}</strong>
            </div>
          </div>
        )}
        <label className="checkbox-field">
          <input
            type="checkbox"
            checked={accepted}
            disabled={!currentQuote}
            onChange={(e) => setAccepted(e.target.checked)}
          />
          <span>
            {user?.role === 'CUSTOMER'
              ? 'Tôi chấp thuận báo giá và nội dung đề nghị trên.'
              : 'Tôi đã đối chiếu nội dung báo giá. Khách cần chấp thuận riêng trước khi duyệt.'}
          </span>
        </label>
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={command.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button
            type="submit"
            busy={command.busy}
            disabled={!accepted || !currentQuote || quoting}
          >
            Gửi đề nghị
            <ArrowRight size={16} />
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
