import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { History, Pencil, RefreshCw } from 'lucide-react';
import type { Product } from '../../shared/types';
import { date, money } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Button, Dialog, InfoNote, Panel, QueryState, SearchInput, Table } from '../../shared/ui';

type PriceChange = {
  id: string;
  version: number;
  old_daily_rate: number;
  daily_rate: number;
  old_deposit: number;
  deposit: number;
  changed_by: string;
  changed_at: string;
  reason: string;
};

export function ProductPrices() {
  const { user, refresh } = useApp();
  const q = useQuery<Product[]>('/catalog');
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState<Product | null>(null);
  const [history, setHistory] = useState<Product | null>(null);
  const rows = (q.data ?? []).filter((p) =>
    (p.code + ' ' + p.name).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')),
  );
  return (
    <Panel className="product-prices">
      <div className="table-toolbar">
        <div>
          <h2>Bảng giá sản phẩm</h2>
          <p className="muted">Giá theo một thiết bị. Chủ cửa hàng có quyền điều chỉnh.</p>
        </div>
        <Button className="secondary small" onClick={refresh} disabled={q.loading}>
          <RefreshCw size={15} aria-hidden="true" />
          Tải lại bảng giá
        </Button>
      </div>
      <InfoNote>
        Giá mới áp dụng ngay cho đơn mới. Đơn đã tạo và gia hạn của đơn đó giữ giá đã chốt.
      </InfoNote>
      <div className="table-toolbar">
        <SearchInput
          value={search}
          onChange={setSearch}
          placeholder="Tìm sản phẩm trong bảng giá…"
        />
      </div>
      <QueryState loading={q.loading} error={q.error}>
        <Table
          headers={['Sản phẩm', 'Giá thuê / ngày', 'Tiền cọc / chiếc', 'Thao tác']}
          empty={!rows.length}
        >
          {rows.map((p) => (
            <tr key={p.id}>
              <td>
                <strong>{p.name}</strong>
                <small>
                  {p.code} · Bản giá {p.priceVersion}
                </small>
              </td>
              <td>{money(p.dailyRate)}</td>
              <td>{money(p.deposit)}</td>
              <td>
                <div className="row-actions">
                  {user?.role === 'OWNER' && (
                    <Button className="secondary small" onClick={() => setEditing(p)}>
                      <Pencil size={15} aria-hidden="true" />
                      Điều chỉnh giá
                    </Button>
                  )}
                  <Button className="secondary small" onClick={() => setHistory(p)}>
                    <History size={15} aria-hidden="true" />
                    Lịch sử giá
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </Table>
      </QueryState>
      {editing && <PriceDialog product={editing} onClose={() => setEditing(null)} />}
      {history && <PriceHistory product={history} onClose={() => setHistory(null)} />}
    </Panel>
  );
}

function PriceDialog({ product, onClose }: { product: Product; onClose: () => void }) {
  const command = useCommand();
  const [daily, setDaily] = useState(String(product.dailyRate));
  const [deposit, setDeposit] = useState(String(product.deposit));
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const errorRef = useRef<HTMLDivElement>(null);
  const changed = Number(daily) !== product.dailyRate || Number(deposit) !== product.deposit;
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (command.busy || !changed) return;
    setError('');
    const result = await command.run(
      `/catalog/${product.id}/price`,
      {
        expectedVersion: product.priceVersion,
        dailyRate: Number(daily),
        deposit: Number(deposit),
        reason,
      },
      'Đã cập nhật giá sản phẩm. Các đơn đã tạo giữ nguyên giá.',
    );
    if (result.ok) onClose();
    else {
      setError(result.error ?? 'Chưa lưu được giá. Vui lòng thử lại.');
      requestAnimationFrame(() => errorRef.current?.focus());
    }
  }
  return (
    <Dialog
      title={'Điều chỉnh giá: ' + product.name}
      showToastError={false}
      description="Áp dụng ngay khi lưu cho đơn mới. Nhập số tiền VND nguyên, không dùng dấu phân cách."
      onClose={() => {
        if (!command.busy) onClose();
      }}
    >
      <form className="form-stack" onSubmit={submit}>
        {error && (
          <div className="inline-error" role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </div>
        )}
        <label className="field">
          <span>Giá thuê / ngày (VND)</span>
          <input
            type="number"
            inputMode="numeric"
            min="1"
            max="999999999999"
            step="1"
            required
            value={daily}
            onChange={(e) => setDaily(e.target.value)}
          />
          <small>Hiện tại: {money(product.dailyRate)}</small>
        </label>
        <label className="field">
          <span>Tiền cọc / chiếc (VND)</span>
          <input
            type="number"
            inputMode="numeric"
            min="0"
            max="999999999999"
            step="1"
            required
            value={deposit}
            onChange={(e) => setDeposit(e.target.value)}
          />
          <small>Hiện tại: {money(product.deposit)}. Có thể đặt bằng 0.</small>
        </label>
        <label className="field">
          <span>Lý do điều chỉnh</span>
          <textarea
            required
            maxLength={500}
            rows={3}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <InfoNote>
          Giá sau khi lưu: {money(Number(daily) || 0)} / ngày; cọc {money(Number(deposit) || 0)} /
          chiếc. Áp dụng cho mọi thiết bị thuộc loại {product.code}.
        </InfoNote>
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={command.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={command.busy} disabled={!changed || !reason.trim()}>
            Lưu giá mới
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function PriceHistory({ product, onClose }: { product: Product; onClose: () => void }) {
  const q = useQuery<PriceChange[]>(`/catalog/${product.id}/price/history`);
  return (
    <Dialog
      title={'Lịch sử giá: ' + product.name}
      description="100 lần điều chỉnh gần nhất, mới nhất ở trên. Giá khởi tạo chưa có bản ghi điều chỉnh."
      onClose={onClose}
    >
      <QueryState loading={q.loading} error={q.error}>
        {!q.data?.length && <InfoNote>Chưa có lần điều chỉnh giá nào.</InfoNote>}
        {q.data?.map((c) => (
          <article key={c.id} className="price-history-entry">
            <strong>
              Bản giá {c.version} · {date(c.changed_at, true)}
            </strong>
            <p>
              Giá thuê / ngày: {money(c.old_daily_rate)} → {money(c.daily_rate)}
            </p>
            <p>
              Tiền cọc / chiếc: {money(c.old_deposit)} → {money(c.deposit)}
            </p>
            <p>{c.reason}</p>
            <small>Người thay đổi: {c.changed_by}</small>
          </article>
        ))}
      </QueryState>
      <div className="dialog-actions">
        <Button className="secondary" onClick={onClose}>
          Đóng lịch sử
        </Button>
      </div>
    </Dialog>
  );
}
