import { useState } from 'react';
import type { ReactNode } from 'react';
import { Check, Plus, Wallet } from 'lucide-react';
import type { Row } from '../../shared/types';
import { date, money } from '../../shared/api';
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
export function Payroll() {
  const { user } = useApp();
  const q = useQuery<{ staff: Row[]; work: Row[]; payroll: Row[]; policy: string }>('/payroll');
  const cmd = useCommand();
  const [tab, setTab] = useState('work'),
    [modal, setModal] = useState<ReactNode>(null);
  const close = () => setModal(null);
  const canFinance = ['OWNER', 'ACCOUNTANT'].includes(user!.role),
    canWork = ['OWNER', 'HR'].includes(user!.role);
  function work() {
    setModal(
      <FormDialog
        title="Ghi nhận công làm việc"
        description="Công cần một người có quyền khác xác nhận trước khi dùng để tính lương."
        onClose={close}
        fields={[
          {
            key: 'staffId',
            label: 'Nhân viên',
            options: (q.data?.staff ?? []).map((s) => ({
              value: s.id,
              label: s.name + ' · ' + s.position,
            })),
          },
          {
            key: 'date',
            label: 'Ngày làm việc',
            type: 'date',
            max: new Date().toISOString().slice(0, 10),
            defaultValue: new Date().toISOString().slice(0, 10),
          },
          {
            key: 'hours',
            label: 'Số giờ thực tế',
            type: 'number',
            min: 0.25,
            max: 24,
            step: 0.25,
            defaultValue: 8,
          },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              '/payroll/work',
              { ...v, hours: Number(v.hours) },
              'Đã ghi nhận công, chờ người khác xác nhận.',
            )
          ).ok
        }
      />,
    );
  }
  function draft() {
    setModal(
      <FormDialog
        title="Lập bảng lương mẫu"
        description="Chốt số giờ đã duyệt và phiên bản đơn giá. Không được thay đổi dữ liệu công của kỳ sau khi đã lập bảng lương."
        onClose={close}
        fields={[
          {
            key: 'staffId',
            label: 'Nhân viên',
            options: (q.data?.staff ?? []).map((s) => ({ value: s.id, label: s.name })),
          },
          {
            key: 'period',
            label: 'Kỳ lương',
            type: 'month',
            defaultValue: new Date().toISOString().slice(0, 7),
          },
        ]}
        onSubmit={async (v) =>
          (await cmd.run('/payroll', v, 'Đã lập bảng lương mẫu từ công đã duyệt.')).ok
        }
      />,
    );
  }
  function confirm(path: string, title: string, description: string) {
    setModal(
      <ConfirmDialog
        title={title}
        description={description}
        onClose={close}
        onConfirm={async () => (await cmd.run(path, undefined, 'Đã ghi nhận thay đổi.')).ok}
      />,
    );
  }
  return (
    <>
      <PageHeader
        eyebrow="QUẢN LÝ"
        title="Nhân sự & lương"
        description="Tách rõ ghi công, xác nhận công, duyệt lương và ghi nhận chi trả."
        action={
          <div className="button-group">
            {canWork && (
              <Button className="secondary" onClick={work}>
                <Plus size={16} />
                Ghi công
              </Button>
            )}
            {canFinance && (
              <Button onClick={draft}>
                <Plus size={16} />
                Lập bảng lương
              </Button>
            )}
          </div>
        }
      />
      <InfoNote>
        {q.data?.policy ?? 'Đang tải chính sách lương mẫu…'} Đây là quy tắc thử nghiệm, cần xác nhận
        C09–C10 trước vận hành.
      </InfoNote>
      <Panel>
        <div className="tabs">
          <button className={tab === 'work' ? 'active' : ''} onClick={() => setTab('work')}>
            Công làm việc
          </button>
          {canFinance && (
            <button className={tab === 'payroll' ? 'active' : ''} onClick={() => setTab('payroll')}>
              Bảng lương
            </button>
          )}
          <button className={tab === 'staff' ? 'active' : ''} onClick={() => setTab('staff')}>
            Hồ sơ nhân viên
          </button>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          {tab === 'staff' ? (
            <Table
              headers={[
                'Nhân viên',
                'Vị trí',
                ...(canFinance ? ['Đơn giá mẫu / giờ'] : []),
                'Trạng thái',
              ]}
              empty={!q.data?.staff.length}
            >
              {q.data?.staff.map((s) => (
                <tr key={s.id}>
                  <td>
                    <strong>{s.name}</strong>
                  </td>
                  <td>{s.position}</td>
                  {canFinance && <td>{money(s.hourly_rate)}</td>}
                  <td>
                    <span className="badge status-ready">
                      <i />
                      Đang làm việc
                    </span>
                  </td>
                </tr>
              ))}
            </Table>
          ) : tab === 'work' ? (
            <Table
              headers={['Nhân viên', 'Ngày làm việc', 'Giờ công', 'Người ghi', 'Xác nhận', '']}
              empty={!q.data?.work.length}
            >
              {q.data?.work.map((w) => (
                <tr key={w.id}>
                  <td>
                    <strong>{w.staff_name}</strong>
                  </td>
                  <td>{date(w.work_date)}</td>
                  <td>{w.hours} giờ</td>
                  <td>{w.recorded_by}</td>
                  <td>
                    {w.approved_by ? (
                      <span className="state-text green">{w.approved_by}</span>
                    ) : (
                      <Badge status="PENDING" />
                    )}
                  </td>
                  <td>
                    {!w.approved_by && canFinance && w.recorded_by !== user?.username && (
                      <Button
                        className="secondary small"
                        onClick={() =>
                          confirm(
                            '/payroll/work/' + w.id + '/approve',
                            'Xác nhận giờ công?',
                            'Chỉ xác nhận sau khi đã đối chiếu ngày và giờ làm thực tế.',
                          )
                        }
                      >
                        <Check size={15} />
                        Xác nhận
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          ) : (
            <Table
              headers={[
                'Nhân viên / kỳ',
                'Giờ đã duyệt',
                'Số tiền mẫu',
                'Trạng thái',
                'Lập / duyệt',
                'Thao tác',
              ]}
              empty={!q.data?.payroll.length}
            >
              {q.data?.payroll.map((p) => (
                <tr key={p.id}>
                  <td>
                    <strong>{p.staff_name}</strong>
                    <small>
                      {p.period} · {p.policy_version}
                    </small>
                  </td>
                  <td>{p.approved_hours} giờ</td>
                  <td className="amount">{money(p.amount)}</td>
                  <td>
                    <Badge status={p.status} />
                  </td>
                  <td>
                    {p.prepared_by}
                    <small>
                      {p.approved_by ? 'Duyệt: ' + p.approved_by : 'Chờ người khác duyệt'}
                    </small>
                  </td>
                  <td>
                    {p.status === 'DRAFT' && p.prepared_by !== user?.username && (
                      <Button
                        className="secondary small"
                        onClick={() =>
                          confirm(
                            '/payroll/' + p.id + '/approve',
                            'Duyệt bảng lương?',
                            'Đối chiếu số giờ, đơn giá và căn cứ trước khi phê duyệt.',
                          )
                        }
                      >
                        <Check size={15} />
                        Duyệt
                      </Button>
                    )}
                    {p.status === 'APPROVED' && (
                      <Button
                        className="small"
                        onClick={() =>
                          confirm(
                            '/payroll/' + p.id + '/pay',
                            'Ghi nhận đã chi lương?',
                            'Chỉ xác nhận sau khi đã chi thực tế. Bản dev ghi sổ một lần cho bảng lương, không thực hiện chuyển khoản ngân hàng.',
                          )
                        }
                      >
                        <Wallet size={15} />
                        Ghi nhận đã chi
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </QueryState>
      </Panel>
      {modal}
    </>
  );
}
