import { useState } from 'react';
import type { FormEvent } from 'react';
import { Check, Download, FileInput, Upload } from 'lucide-react';
import type { Row } from '../../shared/types';
import { date } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Button,
  ConfirmDialog,
  Dialog,
  InfoNote,
  PageHeader,
  Panel,
  QueryState,
  Table,
} from '../../shared/ui';
export function Imports() {
  const q = useQuery<Row[]>('/imports');
  const [upload, setUpload] = useState(false),
    [selected, setSelected] = useState<string | null>(null);
  return (
    <>
      <PageHeader
        eyebrow="CHUYỂN ĐỔI CÓ ĐỐI CHIẾU"
        title="Nhập dữ liệu khách hàng"
        description="Đọc CSV, kiểm tra từng dòng và đưa vào vùng chờ trước khi duyệt nhập."
        action={
          <Button onClick={() => setUpload(true)}>
            <Upload size={17} />
            Nhập tệp CSV
          </Button>
        }
      />
      <InfoNote>
        Bản dev đang hỗ trợ khách hàng qua CSV UTF-8, tối đa 1 MB / 1.000 dòng. Batch được nhập
        nguyên tử sau khi người khác duyệt. Thiết bị, đơn đang thuê, tiền cọc và công nợ đầu kỳ chưa
        được hỗ trợ.
      </InfoNote>
      <Panel>
        <div className="panel-heading">
          <div>
            <h2>Các batch đã chuẩn bị</h2>
            <p>Mã nguồn được giữ để đối chiếu, chống nhập lại</p>
          </div>
          <a href="/templates/customers.csv" download className="button secondary small">
            <Download size={16} />
            Tệp mẫu
          </a>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            headers={['Mã batch / tệp', 'Hợp lệ', 'Trạng thái', 'Người chuẩn bị', 'Thời điểm', '']}
            empty={!q.data?.length}
          >
            {q.data?.map((b) => (
              <tr key={b.id}>
                <td>
                  <strong>{b.source_key}</strong>
                  <small>{b.filename}</small>
                </td>
                <td>
                  {b.valid_rows} / {b.total_rows} dòng
                </td>
                <td>
                  <ImportStatus status={b.status} />
                </td>
                <td>
                  {b.prepared_by}
                  <small>{b.approved_by ? 'Duyệt: ' + b.approved_by : ''}</small>
                </td>
                <td>{date(b.created_at, true)}</td>
                <td>
                  <Button className="secondary small" onClick={() => setSelected(b.id)}>
                    Đối chiếu
                  </Button>
                </td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
      {upload && <ImportUpload onClose={() => setUpload(false)} onUploaded={setSelected} />}
      {selected && <ImportPreview id={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
function ImportStatus({ status }: { status: string }) {
  return (
    <span
      className={`badge ${status === 'COMMITTED' ? 'status-ready' : status === 'INVALID' ? 'status-maintenance' : 'status-pending'}`}
    >
      <i />
      {status === 'COMMITTED'
        ? 'Đã nhập'
        : status === 'INVALID'
          ? 'Có lỗi cần xử lý'
          : 'Chờ đối chiếu / duyệt'}
    </span>
  );
}
function ImportUpload({
  onClose,
  onUploaded,
}: {
  onClose: () => void;
  onUploaded: (id: string) => void;
}) {
  const cmd = useCommand();
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const r = await cmd.run<{ id: string }>(
      '/imports',
      new FormData(e.currentTarget),
      'Đã kiểm tra và đưa tệp vào vùng chờ.',
    );
    if (r.ok) {
      onClose();
      onUploaded(r.data!.id);
    }
  }
  return (
    <Dialog
      title="Chuẩn bị nhập khách hàng"
      description="Bốn cột bắt buộc: sourceId, name, phone, address. Dùng mã nguồn ổn định từ hệ thống cũ."
      onClose={() => {
        if (!cmd.busy) onClose();
      }}
    >
      <form onSubmit={submit} className="form-stack">
        <label className="field">
          <span>Mã batch nguồn</span>
          <input name="sourceKey" required maxLength={100} placeholder="Ví dụ KHACH-HANG-DOT-01" />
        </label>
        <label className="field file-drop">
          <span>Tệp CSV UTF-8</span>
          <FileInput size={30} />
          <input type="file" name="file" accept=".csv,text/csv" required />
          <small>Tối đa 1 MB, 1.000 dòng. Chưa ghi vào dữ liệu khách hàng.</small>
        </label>
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={cmd.busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button type="submit" busy={cmd.busy}>
            <Upload size={16} />
            Kiểm tra & đưa vào vùng chờ
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
function ImportPreview({ id, onClose }: { id: string; onClose: () => void }) {
  const q = useQuery<{ batch: Row; rows: Row[] }>('/imports/' + id),
    cmd = useCommand(),
    { user } = useApp();
  const [confirm, setConfirm] = useState(false);
  const b = q.data?.batch;
  return (
    <Dialog
      wide
      title="Đối chiếu batch khách hàng"
      description={b ? `${b.source_key} · ${b.valid_rows}/${b.total_rows} dòng hợp lệ` : undefined}
      onClose={onClose}
    >
      <QueryState loading={q.loading} error={q.error}>
        {b && (
          <>
            <div className="preview-summary">
              <ImportStatus status={b.status} />
              <span>Người chuẩn bị: {b.prepared_by}</span>
            </div>
            <Table
              headers={['Dòng', 'Mã nguồn', 'Họ tên', 'Điện thoại', 'Địa chỉ', 'Kiểm tra']}
              empty={!q.data?.rows.length}
            >
              {q.data?.rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.row_no}</td>
                  <td>{r.source_id}</td>
                  <td>{r.name}</td>
                  <td>{r.phone}</td>
                  <td className="wrap-cell">{r.address}</td>
                  <td className="wrap-cell">
                    {r.errors ? (
                      <span className="inline-error">{r.errors}</span>
                    ) : (
                      <span className="state-text green">Hợp lệ</span>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <div className="dialog-actions">
              <Button className="secondary" onClick={onClose}>
                Đóng
              </Button>
              {b.status === 'STAGED' &&
                user?.role === 'OWNER' &&
                b.prepared_by !== user.username && (
                  <Button busy={cmd.busy} onClick={() => setConfirm(true)}>
                    <Check size={16} />
                    Duyệt nhập nguyên tử
                  </Button>
                )}
            </div>
            {b.prepared_by === user?.username && b.status === 'STAGED' && (
              <InfoNote>
                Bạn là người chuẩn bị. Cần tài khoản chủ cửa hàng khác người lập để phê duyệt.
              </InfoNote>
            )}
            {confirm && (
              <ConfirmDialog
                title="Duyệt đưa dữ liệu vào vận hành?"
                description="Hệ thống kiểm tra lại mã nguồn và điện thoại. Nếu có một dòng không còn hợp lệ, toàn batch sẽ được rollback."
                onClose={() => setConfirm(false)}
                onConfirm={async () => {
                  const r = await cmd.run(
                    '/imports/' + id + '/commit',
                    undefined,
                    'Đã duyệt và nhập khách hàng.',
                  );
                  if (r.ok) onClose();
                  return r.ok;
                }}
              />
            )}
          </>
        )}
      </QueryState>
    </Dialog>
  );
}
