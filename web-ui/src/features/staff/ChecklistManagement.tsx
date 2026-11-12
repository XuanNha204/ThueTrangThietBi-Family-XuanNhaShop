import { useState } from 'react';
import type { FormEvent } from 'react';
import { Plus, Pencil, History, Trash2 } from 'lucide-react';
import type { Product, Row } from '../../shared/types';
import { date } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Button, Dialog, InfoNote, Panel, QueryState } from '../../shared/ui';
import type { Checklist } from './InspectionDialog';

export function ChecklistManagement() {
  const { user } = useApp();
  const products = useQuery<Product[]>('/catalog');
  const [type, setType] = useState(''),
    [modal, setModal] = useState<'edit' | 'history' | null>(null);
  const q = useQuery<Checklist>(type ? `/maintenance/checklists/${type}` : null);
  const product = products.data?.find((p) => p.id === type);
  return (
    <Panel className="checklist-management">
      <div className="panel-heading">
        <div>
          <h2>Checklist theo loại thiết bị</h2>
          <p>Tiêu chí bổ sung cho nghiệm thu, kiểm tra và kết thúc bảo trì</p>
        </div>
      </div>
      <div className="checklist-management-body">
        <label className="field">
          <span>Loại thiết bị áp dụng checklist</span>
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setModal(null);
            }}
          >
            <option value="">Chọn loại thiết bị</option>
            {products.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code} · {p.name}
              </option>
            ))}
          </select>
        </label>
        {products.error && (
          <p role="alert" className="inline-error">
            {products.error}
          </p>
        )}
        <InfoNote>
          Ba tiêu chí nền về an toàn, vệ sinh và phụ kiện luôn bắt buộc. Chủ cửa hàng cấu hình tiêu
          chí bổ sung dựa trên hướng dẫn của nhà sản xuất; phiên bản mới áp dụng cho lần kiểm tra
          tiếp theo.
        </InfoNote>
        {type && (
          <QueryState loading={q.loading} error={q.error}>
            {q.data && (
              <>
                <p>
                  Phiên bản {q.data.version}
                  {q.data.version === 0 ? ' · Chưa cấu hình tiêu chí bổ sung' : ''}
                </p>
                <ul className="checklist-definition">
                  {q.data.checks.map((c) => (
                    <li key={c.code}>
                      <strong>{c.label}</strong>
                      <span>{c.blocking ? 'Bắt buộc đạt' : 'Theo dõi'}</span>
                    </li>
                  ))}
                </ul>
                <div className="bank-payment-actions">
                  {user?.role === 'OWNER' && (
                    <Button className="secondary small" onClick={() => setModal('edit')}>
                      <Pencil size={15} />
                      Chỉnh checklist
                    </Button>
                  )}
                  <Button className="secondary small" onClick={() => setModal('history')}>
                    <History size={15} />
                    Lịch sử phiên bản
                  </Button>
                </div>
              </>
            )}
          </QueryState>
        )}
      </div>
      {modal === 'edit' && q.data && product && (
        <ChecklistEditor template={q.data} name={product.name} onClose={() => setModal(null)} />
      )}
      {modal === 'history' && product && (
        <ChecklistHistory type={type} name={product.name} onClose={() => setModal(null)} />
      )}
    </Panel>
  );
}
function ChecklistEditor({
  template,
  name,
  onClose,
}: {
  template: Checklist;
  name: string;
  onClose: () => void;
}) {
  const cmd = useCommand();
  const [checks, setChecks] = useState(template.checks),
    [reason, setReason] = useState(''),
    [error, setError] = useState('');
  function add() {
    let n = 1;
    while (checks.some((c) => c.code === `CHECK_${n}`)) n++;
    setChecks([...checks, { code: `CHECK_${n}`, label: '', blocking: true }]);
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    const result = await cmd.run(
      `/maintenance/checklists/${template.typeId}`,
      { expectedVersion: template.version, checks, reason },
      'Đã áp dụng phiên bản checklist mới.',
    );
    if (result.ok) onClose();
    else setError(result.error || 'Chưa lưu được checklist.');
  }
  return (
    <Dialog
      title={'Checklist: ' + name}
      description="Lưu phiên bản mới áp dụng ngay. Kết quả kiểm tra đã ghi nhận giữ nguyên nội dung cũ."
      onClose={() => {
        if (!cmd.busy) onClose();
      }}
      showToastError={false}
    >
      <form className="form-stack" onSubmit={submit}>
        {error && (
          <p className="inline-error" role="alert">
            {error}
          </p>
        )}
        <InfoNote>
          Không thể bỏ ba tiêu chí an toàn nền. Có thể để trống danh sách bổ sung để trở về chỉ dùng
          tiêu chí nền.
        </InfoNote>
        {checks.map((c, i) => (
          <fieldset className="inspection-criterion" key={c.code}>
            <legend>Tiêu chí {i + 1}</legend>
            <label className="field">
              <span>Nội dung tiêu chí {i + 1}</span>
              <input
                required
                maxLength={200}
                value={c.label}
                onChange={(e) =>
                  setChecks(checks.map((v, n) => (n === i ? { ...v, label: e.target.value } : v)))
                }
              />
            </label>
            <label className="checkbox-field">
              <input
                type="checkbox"
                checked={c.blocking}
                onChange={(e) =>
                  setChecks(
                    checks.map((v, n) => (n === i ? { ...v, blocking: e.target.checked } : v)),
                  )
                }
              />
              <span>Bắt buộc đạt trước khi cho thuê</span>
            </label>
            <Button
              type="button"
              className="ghost small"
              onClick={() => setChecks(checks.filter((_, n) => n !== i))}
            >
              <Trash2 size={15} />
              Bỏ tiêu chí {i + 1}
            </Button>
          </fieldset>
        ))}
        <Button type="button" className="secondary" disabled={checks.length >= 20} onClick={add}>
          <Plus size={16} />
          Thêm tiêu chí
        </Button>
        <label className="field">
          <span>Căn cứ áp dụng checklist</span>
          <textarea
            required
            maxLength={500}
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Hướng dẫn nhà sản xuất, quy trình đã thống nhất…"
          />
        </label>
        <div className="dialog-actions">
          <Button className="secondary" type="button" disabled={cmd.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={cmd.busy}>
            Áp dụng checklist
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
function ChecklistHistory({
  type,
  name,
  onClose,
}: {
  type: string;
  name: string;
  onClose: () => void;
}) {
  const q = useQuery<Row[]>(`/maintenance/checklists/${type}/history`);
  return (
    <Dialog
      title={'Lịch sử checklist: ' + name}
      description="50 phiên bản gần nhất. Nội dung mỗi phiên bản được giữ nguyên."
      onClose={onClose}
    >
      <QueryState loading={q.loading} error={q.error}>
        {!q.data?.length && <p>Chưa có phiên bản bổ sung.</p>}
        {q.data?.map((v) => (
          <article className="price-history-entry" key={v.id}>
            <strong>
              Phiên bản {v.version} · {date(v.created_at, true)}
            </strong>
            <p>{v.reason}</p>
            <small>{v.created_by}</small>
            <ul>
              {v.checks.map((c: Row) => (
                <li key={c.code}>
                  {c.label} · {c.blocking ? 'Bắt buộc đạt' : 'Theo dõi'}
                </li>
              ))}
            </ul>
            {!v.checks.length && <p>Chỉ dùng ba tiêu chí nền.</p>}
          </article>
        ))}
      </QueryState>
      <div className="dialog-actions">
        <Button onClick={onClose}>Đóng lịch sử</Button>
      </div>
    </Dialog>
  );
}
