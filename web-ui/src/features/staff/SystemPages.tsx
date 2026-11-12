import { useState } from 'react';
import { passwordHint, passwordPattern } from '../../shared/password';
import type { ReactNode } from 'react';
import { Download, FileBarChart, LockKeyhole, ShieldCheck, Plus } from 'lucide-react';
import type { Row, User } from '../../shared/types';
import { date, money, roles } from '../../shared/api';
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
  Table,
} from '../../shared/ui';
export function Reports() {
  const today = new Date(),
    first = new Date(today.getFullYear(), today.getMonth(), 1);
  const ymd = (v: Date) =>
    new Date(v.getTime() - v.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const [from, setFrom] = useState(ymd(first)),
    [until, setUntil] = useState(ymd(today));
  const valid = !!from && !!until && until >= from;
  return (
    <>
      <PageHeader
        eyebrow="QUẢN LÝ"
        title="Báo cáo & xuất dữ liệu"
        description="Xuất sổ tiền theo kỳ để đối chiếu, giữ rõ người lập và thời điểm báo cáo."
      />
      <Panel className="report-panel">
        <span className="report-icon">
          <FileBarChart size={30} />
        </span>
        <div>
          <h2>Sổ tiền theo kỳ</h2>
          <p>Khoản thu tiền thuê, nhận/hoàn cọc và chi lương. Tiền cọc được phân loại riêng.</p>
          <div className="form-grid">
            <label className="field">
              <span>Từ ngày</span>
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} required />
            </label>
            <label className="field">
              <span>Đến hết ngày</span>
              <input
                type="date"
                value={until}
                onChange={(e) => setUntil(e.target.value)}
                required
              />
            </label>
          </div>
          {valid ? (
            <a className="button" href={`/api/reports/finance.csv?from=${from}&until=${until}`}>
              <Download size={17} />
              Tải báo cáo CSV
            </a>
          ) : (
            <p className="inline-error">Chọn khoảng ngày hợp lệ.</p>
          )}
          <small className="report-help">
            CSV UTF-8, tối đa 366 ngày và 10.000 dòng trong bản dev. Cần kiểm tra số dòng khi đối
            chiếu.
          </small>
        </div>
      </Panel>
      <InfoNote>
        Báo cáo hiện là sổ giao dịch, chưa phải báo cáo lợi nhuận hoặc báo cáo thuế. Các khoản chi
        phí, công nợ nhà cung cấp và phân bổ tài sản sẽ được bổ sung theo yêu cầu bài 1.
      </InfoNote>
    </>
  );
}
const actions: Record<string, string> = {
  INSPECTION_CHECKLIST_PUBLISHED: 'Áp dụng phiên bản checklist',
  PRODUCT_PRICE_CHANGED: 'Điều chỉnh giá sản phẩm',
  DEV_DATA_SEEDED: 'Khởi tạo dữ liệu dev',
  CUSTOMER_CREATED: 'Tạo khách hàng',
  ORDER_HELD: 'Giữ chỗ đơn thuê',
  ORDER_CONFIRMED: 'Xác nhận đơn thuê',
  ORDER_PREPARING: 'Chuẩn bị đơn',
  ORDER_CANCELLED: 'Hủy đơn thuê',
  ORDER_COMPLETED: 'Tất toán đơn',
  EQUIPMENT_STATE: 'Đổi tình trạng thiết bị',
  EQUIPMENT_RECEIVED: 'Tiếp nhận thiết bị',
  MONEY_RECEIVED: 'Ghi nhận tiền thu',
  REFUND_REQUESTED: 'Lập yêu cầu hoàn cọc',
  REFUND_APPROVED: 'Duyệt & ghi hoàn cọc',
  REFUND_REJECTED: 'Từ chối hoàn cọc',
  HANDOVER_RECORDED: 'Bàn giao thiết bị',
  RETURN_RECORDED: 'Nhận trả thiết bị',
  INSPECTION_RECORDED: 'Kiểm tra thiết bị',
  MAINTENANCE_OPENED: 'Ghi nhận sự cố',
  EVIDENCE_ATTACHED: 'Đính kèm bằng chứng',
  ACCOUNT_STATUS: 'Cập nhật tài khoản',
  STAFF_ACCOUNT_CREATED: 'Tạo tài khoản nhân viên',
  ACCOUNT_ROLE_CHANGED: 'Thay đổi vai trò nhân viên',
  SEPAY_BANK_CONFIGURED: 'Cấu hình tài khoản SePay',
  BANK_PAYMENT_REQUESTED: 'Tạo yêu cầu chuyển khoản',
  BANK_PAYMENT_CANCELLED: 'Hủy yêu cầu chuyển khoản',
  SEPAY_PAYMENT_POSTED: 'Ghi sổ thanh toán SePay',
  SEPAY_EVENT_IGNORED: 'Đối soát không ghi sổ',
  SEPAY_RECHECK_REQUESTED: 'Yêu cầu kiểm tra lại SePay',
  SUPPORT_CREATED: 'Tạo hỗ trợ',
  SUPPORT_RESOLVED: 'Xử lý hỗ trợ',
  IMPORT_STAGED: 'Nhập vùng chờ',
  IMPORT_COMMITTED: 'Duyệt nhập dữ liệu',
  HOLD_EXPIRED: 'Hết hạn giữ chỗ',
  WORK_RECORDED: 'Ghi công',
  WORK_APPROVED: 'Duyệt công',
  PAYROLL_DRAFTED: 'Lập bảng lương',
  PAYROLL_APPROVED: 'Duyệt lương',
  PAYROLL_PAID: 'Ghi nhận chi lương',
};
export function Audit() {
  const q = useQuery<Row[]>('/audit');
  return (
    <>
      <PageHeader
        eyebrow="TRUY VẾT & ĐỐI CHIẾU"
        title="Lịch sử thao tác"
        description="Theo dõi người thực hiện, thời điểm và căn cứ của các thay đổi đã ghi nhận."
      />
      <Panel>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            headers={['Thời điểm', 'Người thực hiện', 'Thao tác', 'Đối tượng', 'Chi tiết']}
            empty={!q.data?.length}
          >
            {q.data?.map((a) => (
              <tr key={a.id}>
                <td>{date(a.created_at, true)}</td>
                <td>
                  <strong>{a.actor}</strong>
                </td>
                <td>{actions[a.action] ?? a.action}</td>
                <td>
                  <span className="mono" title={a.target_id}>
                    {a.target_id?.slice(0, 8) ?? 'Hệ thống'}
                  </span>
                </td>
                <td className="wrap-cell">{a.detail}</td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
    </>
  );
}
export function Notifications() {
  const q = useQuery<Row[]>('/notifications');
  const events: Record<string, string> = {
    ORDER_HELD: 'Giữ chỗ đơn thuê',
    ORDER_CONFIRMED: 'Xác nhận đơn',
    ORDER_CANCELLED: 'Hủy đơn',
    PAYMENT_RECORDED: 'Ghi nhận khoản thu',
    HANDED_OVER: 'Bàn giao',
    RETURNED: 'Nhận trả',
  };
  return (
    <>
      <PageHeader
        eyebrow="THEO DÕI XỬ LÝ"
        title="Thông báo đơn thuê"
        description="Kiểm tra các thông báo chờ xử lý, đã xử lý hoặc cần can thiệp."
      />
      <InfoNote>
        Ở môi trường dev, thông báo được lưu vào nhật ký tại máy để kiểm tra. Trạng thái đã xử lý
        không có nghĩa khách đã nhận email hoặc SMS.
      </InfoNote>
      <Panel>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            headers={['Đơn thuê', 'Nội dung', 'Trạng thái', 'Lần thử', 'Tạo lúc', 'Xử lý lúc']}
            empty={!q.data?.length}
          >
            {q.data?.map((n) => (
              <tr key={n.id}>
                <td>
                  <strong>{n.order_code ?? '—'}</strong>
                </td>
                <td>
                  {events[n.event_type] ?? n.event_type}
                  {n.last_error && <small>{n.last_error}</small>}
                </td>
                <td>
                  <Badge status={n.status} />
                </td>
                <td>{n.attempts}</td>
                <td>{date(n.created_at, true)}</td>
                <td>{date(n.sent_at, true)}</td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
    </>
  );
}
export function Settings() {
  const policy = useQuery<{ holdMinutes: number; bufferMinutes: number }>('/catalog/policy');
  const q = useQuery<User[]>('/accounts'),
    { user } = useApp(),
    cmd = useCommand();
  const [modal, setModal] = useState<ReactNode>(null);
  const staffRoles = Object.entries(roles)
    .filter(([role]) => role !== 'CUSTOMER')
    .map(([value, label]) => ({ value, label }));
  function createStaff() {
    setModal(
      <FormDialog
        title="Tạo tài khoản nhân viên"
        description="Tài khoản riêng cho vận hành, không đổi tài khoản khách thành nhân viên."
        submitLabel="Tạo tài khoản"
        onClose={() => setModal(null)}
        fields={[
          { key: 'username', label: 'Tên đăng nhập' },
          { key: 'displayName', label: 'Họ tên nhân viên' },
          { key: 'password', label: 'Mật khẩu', type: 'password', minLength: 8, maxLength: 72, pattern: passwordPattern, help: passwordHint },
          { key: 'role', label: 'Vai trò', options: staffRoles },
        ]}
        onSubmit={async (v) => (await cmd.run('/accounts', v, 'Đã tạo tài khoản nhân viên.')).ok}
      />,
    );
  }
  function changeRole(a: User) {
    setModal(
      <FormDialog
        title={`Đổi vai trò: ${a.username}`}
        description="Quyền mới áp dụng từ yêu cầu tiếp theo, kể cả phiên đang đăng nhập. Các quy tắc tách người lập/người duyệt vẫn được giữ."
        submitLabel="Lưu vai trò"
        onClose={() => setModal(null)}
        fields={[
          { key: 'role', label: 'Vai trò mới', defaultValue: a.role, options: staffRoles },
          { key: 'reason', label: 'Lý do và căn cứ cấp quyền', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              `/accounts/${a.id}/role`,
              { ...v, expectedRole: a.role },
              'Đã cập nhật vai trò nhân viên.',
            )
          ).ok
        }
      />,
    );
  }
  function toggle(a: User) {
    setModal(
      <ConfirmDialog
        title={a.enabled ? 'Khóa tài khoản này?' : 'Mở lại tài khoản?'}
        description={
          a.enabled
            ? `Tài khoản ${a.username} sẽ bị từ chối ở yêu cầu tiếp theo, kể cả đang có phiên đăng nhập.`
            : `Tài khoản ${a.username} có thể đăng nhập và sử dụng quyền của vai trò hiện tại.`
        }
        onClose={() => setModal(null)}
        onConfirm={async () =>
          (
            await cmd.run(
              '/accounts/' + a.id,
              { enabled: !a.enabled },
              'Đã cập nhật trạng thái tài khoản.',
              'PATCH',
            )
          ).ok
        }
      />,
    );
  }
  return (
    <>
      <PageHeader
        eyebrow="HỆ THỐNG"
        title="Tài khoản & chính sách"
        description="Tạo tài khoản nhân viên, cấp vai trò và quản lý trạng thái truy cập."
        action={
          <Button onClick={createStaff}>
            <Plus size={16} />
            Tạo nhân viên
          </Button>
        }
      />
      <Panel>
        <div className="panel-heading">
          <h2>Tài khoản người dùng</h2>
          <LockKeyhole size={18} />
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            headers={['Người dùng', 'Đăng nhập', 'Vai trò', 'Trạng thái', '']}
            empty={!q.data?.length}
          >
            {q.data?.map((a) => (
              <tr key={a.id}>
                <td>
                  <strong>{a.displayName}</strong>
                </td>
                <td className="mono">{a.username}</td>
                <td>{roles[a.role]}</td>
                <td>
                  <span className={`badge ${a.enabled ? 'status-ready' : 'status-cancelled'}`}>
                    <i />
                    {a.enabled ? 'Đang hoạt động' : 'Đã khóa'}
                  </span>
                </td>
                <td>
                  {a.id !== user?.id && a.role !== 'CUSTOMER' && (
                    <Button className="secondary small" onClick={() => changeRole(a)}>
                      Đổi vai trò
                    </Button>
                  )}
                  <Button
                    className="secondary small"
                    disabled={a.id === user?.id}
                    onClick={() => toggle(a)}
                  >
                    {a.enabled ? 'Khóa' : 'Mở lại'}
                  </Button>
                </td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
      <div className="policy-grid">
        <Panel>
          <h2>Thuê thiết bị · DEV-1</h2>
          <ul>
            <li>Mỗi 24 giờ bắt đầu tính một ngày thuê.</li>
            <li>
              Giữ chỗ {policy.data?.holdMinutes ?? '…'} phút; chuẩn bị/vệ sinh{' '}
              {policy.data?.bufferMinutes ?? '…'} phút mỗi đầu.
            </li>
            <li>Giá và cọc giữ theo phiên bản lúc tạo đơn.</li>
            <li>Hoàn cọc cần người duyệt khác người lập.</li>
          </ul>
        </Panel>
        <Panel>
          <h2>Lương · DEV-HOURLY-1</h2>
          <ul>
            <li>Số giờ đã duyệt nhân đơn giá mẫu của nhân viên.</li>
            <li>Chốt căn cứ khi lập bảng lương.</li>
            <li>Tách lập, duyệt và ghi nhận đã chi.</li>
            <li>Chưa gồm thuế, bảo hiểm, phụ cấp hoặc thưởng.</li>
          </ul>
        </Panel>
      </div>
      <InfoNote>
        Các giá trị trên là cấu hình mẫu cho môi trường phát triển. Cần xác nhận các chính sách
        C04–C10, C16 và phạm vi vận hành trước khi dùng cho kinh doanh thực tế.
      </InfoNote>
      {modal}
    </>
  );
}
