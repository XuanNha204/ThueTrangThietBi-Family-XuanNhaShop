import { useState } from 'react';
import type { ReactNode } from 'react';
import { CalendarPlus } from 'lucide-react';
import { InspectionDialog } from './InspectionDialog';
import type { Equipment, Row } from '../../shared/types';
import { date, localInput, shopDate } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Button,
  ConfirmDialog,
  FormDialog,
  InfoNote,
  Panel,
  QueryState,
  Table,
} from '../../shared/ui';

export function MaintenancePlans() {
  const { user } = useApp(),
    plans = useQuery<Row[]>('/maintenance/plans'),
    assets = useQuery<Equipment[]>('/equipment'),
    cmd = useCommand();
  const [modal, setModal] = useState<ReactNode>(null);
  const can = !!user && ['OWNER', 'TECHNICIAN'].includes(user.role),
    close = () => setModal(null);
  const states: Record<string, string> = {
    SCHEDULED: 'Đã lên lịch',
    IN_PROGRESS: 'Đang bảo trì',
    COMPLETED: 'Đã kết thúc công việc',
    CANCELLED: 'Đã hủy kế hoạch',
  };
  function schedule() {
    const from = new Date(Date.now() + 86400000),
      to = new Date(from.getTime() + 7200000);
    setModal(
      <FormDialog
        title="Lập kế hoạch bảo trì"
        description="Hệ thống khóa lịch từng mã thiết bị, kiểm tra cả khoảng chuẩn bị/vệ sinh của đơn thuê. Không chèn đè lịch đã giữ."
        onClose={close}
        submitLabel="Lưu và chặn lịch"
        fields={[
          {
            key: 'equipmentId',
            label: 'Thiết bị bảo trì',
            options: (assets.data || [])
              .filter((e) => !['DISPOSED', 'SUSPENDED'].includes(e.state))
              .map((e) => ({ value: e.id, label: e.code + ' · ' + e.name })),
          },
          {
            key: 'startsAt',
            label: 'Bắt đầu dự kiến',
            type: 'datetime-local',
            defaultValue: localInput(from),
          },
          {
            key: 'endsAt',
            label: 'Kết thúc dự kiến',
            type: 'datetime-local',
            defaultValue: localInput(to),
          },
          { key: 'reason', label: 'Hạng mục và căn cứ bảo trì', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              '/maintenance/plans',
              {
                ...v,
                startsAt: shopDate(v.startsAt).toISOString(),
                endsAt: shopDate(v.endsAt).toISOString(),
              },
              'Đã lên kế hoạch và chặn lịch bảo trì.',
            )
          ).ok
        }
      />,
    );
  }
  function cancel(p: Row) {
    setModal(
      <FormDialog
        title={`Hủy kế hoạch ${p.equipment_code}`}
        description="Chỉ hủy kế hoạch chưa bắt đầu. Thiết bị không tự đổi tình trạng kỹ thuật."
        onClose={close}
        fields={[{ key: 'reason', label: 'Lý do và căn cứ hủy', type: 'textarea' }]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              `/maintenance/plans/${p.id}/cancel`,
              v,
              'Đã hủy kế hoạch và giải phóng khoảng chặn lịch.',
            )
          ).ok
        }
      />,
    );
  }
  function complete(p: Row) {
    setModal(
      <InspectionDialog
        equipment={{ id: p.equipment_id, type_id: p.type_id, code: p.equipment_code, name: p.name }}
        planId={p.id}
        onClose={close}
      />,
    );
  }

  return (
    <Panel className="maintenance-plans">
      <div className="panel-heading">
        <div>
          <h2>Kế hoạch bảo trì</h2>
          <p>Khoảng chặn lịch gắn với từng mã thiết bị</p>
        </div>
        {can && (
          <Button className="secondary small" onClick={schedule}>
            <CalendarPlus size={16} />
            Lập kế hoạch bảo trì
          </Button>
        )}
      </div>
      <InfoNote>
        Đến giờ bảo trì mà chưa hoàn thành hoặc hủy, thiết bị tiếp tục bị chặn cấp lịch mới, kể cả
        quá giờ kết thúc dự kiến. Bắt đầu bảo trì chuyển thiết bị sang Bảo trì; kết thúc cần kiểm
        tra an toàn.
      </InfoNote>
      <QueryState loading={plans.loading} error={plans.error}>
        <Table
          headers={['Thiết bị', 'Khoảng dự kiến', 'Trạng thái', 'Nội dung / kết quả', 'Thao tác']}
          empty={!plans.data?.length}
        >
          {plans.data?.map((p) => (
            <tr key={p.id}>
              <td>
                <strong>{p.equipment_code}</strong>
                <small>{p.name}</small>
              </td>
              <td>
                {date(p.starts_at, true)}
                <small>Đến {date(p.ends_at, true)}</small>
              </td>
              <td>
                {states[p.status]}
                {p.overdue_start && <small>Đã đến giờ — cần xử lý</small>}
                {p.status === 'COMPLETED' && (
                  <small>{p.passed ? 'Kiểm tra đạt' : 'Chưa đạt — vẫn bảo trì'}</small>
                )}
              </td>
              <td className="wrap-cell">
                {p.reason}
                {p.completion_note && <small>{p.completion_note}</small>}
              </td>
              <td>
                {can && (
                  <div className="bank-payment-actions">
                    {p.status === 'SCHEDULED' && (
                      <>
                        <Button
                          className="secondary small"
                          disabled={new Date(p.starts_at).getTime() > Date.now()}
                          onClick={() =>
                            setModal(
                              <ConfirmDialog
                                title={`Bắt đầu bảo trì ${p.equipment_code}?`}
                                description="Thiết bị chuyển sang Bảo trì. Không bắt đầu nếu thiết bị đang được giao hoặc đang thuê."
                                onClose={close}
                                onConfirm={async () =>
                                  (
                                    await cmd.run(
                                      `/maintenance/plans/${p.id}/start`,
                                      undefined,
                                      'Đã bắt đầu bảo trì.',
                                    )
                                  ).ok
                                }
                              />,
                            )
                          }
                        >
                          Bắt đầu
                        </Button>
                        <Button className="ghost small" onClick={() => cancel(p)}>
                          Hủy kế hoạch
                        </Button>
                      </>
                    )}
                    {p.status === 'IN_PROGRESS' && (
                      <Button className="secondary small" onClick={() => complete(p)}>
                        Kết thúc và kiểm tra
                      </Button>
                    )}
                  </div>
                )}
              </td>
            </tr>
          ))}
        </Table>
      </QueryState>
      {modal}
    </Panel>
  );
}
