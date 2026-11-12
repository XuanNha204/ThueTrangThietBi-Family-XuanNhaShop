import { useState } from 'react';
import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import { choices, enumChoices, useWorkflowForm, workflowLabels as label } from './WorkflowForms';
export function Dispatch() {
  const { user } = useApp(),
    q = useQuery<Row>('/dispatch'),
    orders = useQuery<Row[]>('/rentals'),
    catalog = useQuery<Row[]>('/catalog'),
    form = useWorkflowForm(),
    [type, setType] = useState('');
  const guide = useQuery<Row>(type ? '/catalog/' + type + '/guide' : null),
    canPlan = ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role),
    canGuide = ['OWNER', 'TECHNICIAN'].includes(user!.role);
  function publish() {
    form.open(
      'Công bố hướng dẫn sử dụng',
      '/dispatch/guides',
      [
        { key: 'title', label: 'Tên hướng dẫn', defaultValue: guide.data?.title },
        {
          key: 'instructions',
          label: 'Các bước sử dụng',
          type: 'textarea',
          maxLength: 12000,
          defaultValue: guide.data?.instructions,
        },
        {
          key: 'safety',
          label: 'Lưu ý an toàn và trường hợp ngừng sử dụng',
          type: 'textarea',
          maxLength: 6000,
          defaultValue: guide.data?.safety,
        },
        {
          key: 'source',
          label: 'Nguồn tài liệu / căn cứ đã đối chiếu',
          type: 'textarea',
          defaultValue: guide.data?.source,
        },
      ],
      (v) => ({ ...v, typeId: type, expectedVersion: guide.data?.version || 0 }),
      'Chỉ công bố nội dung đã đối chiếu với tài liệu nhà sản xuất. Đơn chưa giao phải xác nhận phiên bản mới.',
    );
  }
  function assign() {
    form.open(
      'Phân công giao nhận',
      '/dispatch/assignments',
      [
        {
          key: 'orderId',
          label: 'Đơn thuê',
          options: choices(
            (orders.data || []).filter((r) =>
              ['CONFIRMED', 'PREPARING', 'RENTED', 'PARTIAL_RETURN'].includes(r.status),
            ),
            (r) => r.code + ' · ' + r.customer_name,
          ),
        },
        { key: 'kind', label: 'Công việc', options: enumChoices(['OUT', 'IN']) },
        {
          key: 'assignee',
          label: 'Nhân viên giao nhận',
          options: choices(q.data?.staff || [], (r) => r.display_name, 'username'),
        },
        { key: 'startsAt', label: 'Bắt đầu dự kiến', type: 'datetime-local' },
        { key: 'endsAt', label: 'Kết thúc dự kiến', type: 'datetime-local' },
        { key: 'routeNote', label: 'Lộ trình, điểm đến và việc cần chuẩn bị', type: 'textarea' },
      ],
      (v) => ({
        ...v,
        startsAt: new Date(v.startsAt).toISOString(),
        endsAt: new Date(v.endsAt).toISOString(),
        orderVersion: orders.data?.find((o) => o.id === v.orderId)?.version,
      }),
      'Không phân công trùng giờ cho cùng nhân viên. Cần kiểm tra giờ thuê, vị trí tài sản và điều kiện tiếp cận trước khi giao việc.',
    );
  }
  return (
    <>
      <PageHeader
        title="Giao nhận & hướng dẫn"
        description="Phân công khung giờ, theo dõi công việc và hướng dẫn khách theo từng loại thiết bị."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          <h2>Phân công giao nhận</h2>
          {canPlan && <Button onClick={assign}>Phân công giao nhận</Button>}
          <InfoNote>
            Địa chỉ và phiên bản đơn được lưu khi phân công. Nếu đơn thay đổi, cần kiểm tra lại và
            hủy/lập phân công mới. Hoàn thành chỉ được ghi sau khi có đủ biên bản giao hoặc thu hồi
            thực tế.
          </InfoNote>
          <Table
            headers={[
              'Đơn / công việc',
              'Nhân viên / lịch',
              'Lộ trình / địa chỉ',
              'Trạng thái',
              'Thao tác',
            ]}
            empty={!q.data?.assignments?.length}
          >
            {q.data?.assignments?.map((r: Row) => (
              <tr key={r.id}>
                <td>
                  {r.order_code}
                  <small>{label[r.kind]}</small>
                </td>
                <td>
                  {r.assignee_name}
                  <small>
                    {date(r.starts_at, true)} → {date(r.ends_at, true)}
                  </small>
                </td>
                <td>
                  {r.route_note}
                  <small>{r.address_snapshot}</small>
                  {r.order_version !== r.current_order_version && (
                    <strong>Đơn đã đổi — cần đối chiếu lại</strong>
                  )}
                </td>
                <td>
                  {label[r.status]}
                  <small>{r.outcome}</small>
                </td>
                <td>
                  {r.status === 'ASSIGNED' && (canPlan || r.assignee === user?.username) && (
                    <div className="workflow-actions">
                      <Button
                        onClick={() =>
                          form.open(
                            'Hoàn thành công việc',
                            '/dispatch/assignments/' + r.id + '/finish',
                            [{ key: 'outcome', label: 'Kết quả thực tế', type: 'textarea' }],
                            (v) => ({ ...v, completed: true }),
                          )
                        }
                      >
                        Hoàn thành
                      </Button>
                      <Button
                        className="secondary"
                        onClick={() =>
                          form.open(
                            'Hủy phân công',
                            '/dispatch/assignments/' + r.id + '/finish',
                            [
                              {
                                key: 'outcome',
                                label: 'Lý do và phương án tiếp theo',
                                type: 'textarea',
                              },
                            ],
                            (v) => ({ ...v, completed: false }),
                          )
                        }
                      >
                        Hủy phân công
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </Table>
        </Panel>
        <Panel className="workflow-panel">
          <h2>Hướng dẫn theo loại thiết bị</h2>
          <label className="field">
            <span>Loại thiết bị xem hướng dẫn</span>
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">Chọn loại thiết bị</option>
              {catalog.data?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </label>
          <QueryState loading={guide.loading} error={guide.error}>
            {type && guide.data && (
              <>
                <p>Phiên bản {guide.data.version}</p>
                {guide.data.title ? (
                  <>
                    <h3>{guide.data.title}</h3>
                    <p className="preserve-lines">{guide.data.instructions}</p>
                    <h3>Lưu ý an toàn</h3>
                    <p className="preserve-lines">{guide.data.safety}</p>
                    <small>Nguồn: {guide.data.source}</small>
                  </>
                ) : (
                  <p>Chưa có hướng dẫn được công bố cho loại này.</p>
                )}
                {canGuide && <Button onClick={publish}>Công bố hướng dẫn sử dụng</Button>}
              </>
            )}
          </QueryState>
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
