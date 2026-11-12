import { useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Check, Plus, ShieldAlert } from 'lucide-react';
import type { RentalDetail, Row } from '../../shared/types';
import { date, money } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Badge, Button, Dialog, FormDialog, InfoNote, Panel, QueryState } from '../../shared/ui';

export function RentalIncidents({ detail }: { detail: RentalDetail }) {
  const q = useQuery<Row[]>('/rentals/' + detail.order.id + '/incidents'),
    command = useCommand(),
    { user } = useApp();
  const [modal, setModal] = useState<ReactNode>(null);
  const canReport =
    ['OWNER', 'MANAGER', 'INVENTORY', 'DELIVERY', 'TECHNICIAN'].includes(user!.role) &&
    ['RENTED', 'PARTIAL_RETURN', 'AWAIT_SETTLEMENT'].includes(detail.order.status);
  function decide(incident: Row, approved: boolean) {
    setModal(
      <FormDialog
        title={approved ? 'Duyệt phương án xử lý sự cố' : 'Từ chối phương án xử lý'}
        description={
          approved
            ? `Bồi thường đã thỏa thuận: ${money(incident.amount)}. ${incident.kind === 'LOST' ? 'Thiết bị mất sẽ ngừng khai thác, nghĩa vụ thiết bị được xử lý theo quyết định; không ghi nhận là đã trả.' : 'Duyệt khoản bồi thường không đưa thiết bị hỏng trở lại trạng thái sẵn sàng.'} Tiền cọc vẫn theo dõi riêng.`
            : 'Giữ nguyên nghĩa vụ và hồ sơ để tiếp tục xác minh.'
        }
        onClose={() => setModal(null)}
        submitLabel={approved ? 'Duyệt phương án' : 'Lưu từ chối'}
        fields={[
          { key: 'note', label: 'Căn cứ quyết định và kết quả đối chiếu', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await command.run(
              `/rentals/${detail.order.id}/incidents/${incident.id}/decide`,
              { approved, note: v.note },
              approved
                ? 'Đã duyệt phương án, cập nhật nghĩa vụ và khoản phải thu.'
                : 'Đã từ chối phương án.',
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
          <h2>Sự cố & nghĩa vụ thiết bị</h2>
          <p>Mất, hư hỏng và khoản bồi thường có căn cứ riêng</p>
        </div>
        {canReport && (
          <Button
            className="secondary small"
            onClick={() =>
              setModal(<IncidentDialog detail={detail} onClose={() => setModal(null)} />)
            }
          >
            <Plus size={16} />
            Lập phương án xử lý
          </Button>
        )}
      </div>
      <QueryState loading={q.loading} error={q.error}>
        <div className="lifecycle-body">
          {!q.data?.length && (
            <p className="muted">Chưa có phương án xử lý mất hoặc hư hỏng được lập cho đơn này.</p>
          )}
          {q.data?.map((incident) => (
            <article key={incident.id} className="change-card">
              <div className="lifecycle-card-heading">
                <strong>
                  <ShieldAlert size={16} />
                  {incident.kind === 'LOST'
                    ? 'Mất / không thể thu hồi'
                    : 'Hư hỏng đã thu hồi'} · {incident.equipment_code}
                </strong>
                <Badge status={incident.status} />
              </div>
              <p>{incident.description}</p>
              <p>
                Mức bồi thường thỏa thuận: <strong>{money(incident.amount)}</strong>
              </p>
              <p>Người xác nhận: {incident.customer_name}</p>
              <p>Căn cứ: {incident.agreement_basis}</p>
              <div className="change-meta">
                <span>
                  Người lập: {incident.requested_by} · {date(incident.created_at, true)}
                </span>
                {incident.decided_by && (
                  <span>
                    Người duyệt: {incident.decided_by} · {incident.decision_note}
                  </span>
                )}
              </div>
              {incident.status === 'PENDING' && ['OWNER', 'ACCOUNTANT'].includes(user!.role) && (
                <div className="lifecycle-actions">
                  {incident.requested_by === user?.username ? (
                    <InfoNote>
                      Cần người có quyền khác người lập kiểm tra và duyệt phương án này.
                    </InfoNote>
                  ) : (
                    <>
                      <Button className="small" onClick={() => decide(incident, true)}>
                        <Check size={15} />
                        Duyệt phương án
                      </Button>
                      <Button className="secondary small" onClick={() => decide(incident, false)}>
                        Từ chối phương án
                      </Button>
                    </>
                  )}
                </div>
              )}
            </article>
          ))}
        </div>
      </QueryState>
      {modal}
    </Panel>
  );
}
function IncidentDialog({ detail, onClose }: { detail: RentalDetail; onClose: () => void }) {
  const command = useCommand();
  const [kind, setKind] = useState('LOST'),
    [itemId, setItemId] = useState(''),
    [amount, setAmount] = useState('0'),
    [description, setDescription] = useState(''),
    [customerName, setCustomerName] = useState(detail.order.customerName),
    [agreementBasis, setBasis] = useState('');
  const eligible = detail.items.filter(
    (i) =>
      i.handed_at &&
      !i.resolved_at &&
      (kind === 'LOST' ? !i.returned_at : i.returned_at && i.return_condition === 'DAMAGED'),
  );
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (
      (
        await command.run(
          `/rentals/${detail.order.id}/incidents`,
          { kind, itemId, amount: Number(amount), description, customerName, agreementBasis },
          'Đã lập phương án, chờ người khác có quyền duyệt.',
        )
      ).ok
    )
      onClose();
  }
  return (
    <Dialog
      title="Lập phương án xử lý sự cố"
      description="Ghi nhận diễn biến và thỏa thuận đã đối chiếu. Chưa thay đổi nghĩa vụ hoặc thu tiền cho đến khi được duyệt."
      onClose={() => {
        if (!command.busy) onClose();
      }}
    >
      <form className="form-stack" onSubmit={submit}>
        <label className="field">
          <span>Loại sự cố</span>
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              setItemId('');
            }}
          >
            <option value="LOST">Mất / không thể thu hồi</option>
            <option value="DAMAGE">Hư hỏng đã thu hồi</option>
          </select>
        </label>
        <label className="field">
          <span>Thiết bị liên quan</span>
          <select required value={itemId} onChange={(e) => setItemId(e.target.value)}>
            <option value="">Chọn thiết bị</option>
            {eligible.map((i) => (
              <option key={i.id} value={i.id}>
                {i.equipment_code} · {i.type_name}
              </option>
            ))}
          </select>
          <small>
            {kind === 'DAMAGE'
              ? 'Chỉ chọn thiết bị đã trả với biên bản ghi nhận hư hỏng.'
              : 'Chỉ chọn thiết bị đã giao, chưa trả và chưa xử lý mất.'}
          </small>
        </label>
        <label className="field">
          <span>Diễn biến và tình trạng thực tế</span>
          <textarea
            required
            rows={3}
            maxLength={2000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Bồi thường đã thỏa thuận (VND)</span>
          <input
            required
            type="number"
            min={0}
            max={999999999999}
            step={1}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Khách xác nhận phương án</span>
          <input
            required
            maxLength={120}
            value={customerName}
            onChange={(e) => setCustomerName(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Căn cứ thỏa thuận: biên bản, chứng từ hoặc trao đổi đã đối chiếu</span>
          <textarea
            required
            rows={3}
            maxLength={2000}
            value={agreementBasis}
            onChange={(e) => setBasis(e.target.value)}
          />
        </label>
        <InfoNote>
          Không tự suy ra mức bồi thường từ giá thiết bị. Mức 0 chỉ dùng khi có căn cứ miễn khoản
          bồi thường. Sau duyệt, kế toán thu khoản đã thỏa thuận; không tự khấu trừ cọc.
        </InfoNote>
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={command.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={command.busy} disabled={!itemId}>
            Lập phương án
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
