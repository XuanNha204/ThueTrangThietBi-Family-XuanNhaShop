import type { Row } from '../../shared/types';
import { useApp, useQuery } from '../../shared/state';
import { date } from '../../shared/api';
import { PageHeader, Panel, Button, QueryState, Table, InfoNote } from '../../shared/ui';
import {
  choices,
  enumChoices,
  today,
  useWorkflowForm,
  workflowLabels as label,
} from './WorkflowForms';
export function Warranty() {
  const { user } = useApp(),
    q = useQuery<Row>('/warranty'),
    assets = useQuery<Row[]>('/equipment'),
    form = useWorkflowForm();
  const can = ['OWNER', 'TECHNICIAN'].includes(user!.role);
  function coverage(r?: Row) {
    form.open(
      'Hồ sơ bảo hành',
      '/warranty/coverage',
      [
        {
          key: 'equipmentId',
          label: 'Thiết bị',
          options: choices(
            (assets.data || []).filter((a) => !r || a.id === r.equipment_id),
            (a) => a.code + ' · ' + a.name,
          ),
          defaultValue: r?.equipment_id,
        },
        { key: 'provider', label: 'Đơn vị bảo hành', defaultValue: r?.provider },
        { key: 'documentRef', label: 'Chứng từ / mã hồ sơ', defaultValue: r?.document_ref },
        {
          key: 'terms',
          label: 'Điều kiện và phạm vi bảo hành',
          type: 'textarea',
          defaultValue: r?.terms,
        },
        {
          key: 'startsOn',
          label: 'Ngày bắt đầu',
          type: 'date',
          defaultValue: r?.starts_on || today(),
        },
        { key: 'endsOn', label: 'Ngày kết thúc', type: 'date', defaultValue: r?.ends_on },
      ],
      (v) => ({ ...v, expectedVersion: r?.version || 0 }),
      'Ghi theo hồ sơ được cấp. Nếu đổi thiết bị, hãy đóng cửa sổ và thêm hồ sơ mới.',
    );
  }
  return (
    <>
      <PageHeader
        title="Bảo hành thiết bị"
        description="Điều kiện, thời hạn, hồ sơ đối tác và kết quả xử lý."
      />
      <QueryState loading={q.loading} error={q.error}>
        <Panel className="workflow-panel">
          {can && <Button onClick={() => coverage()}>Thêm hồ sơ bảo hành</Button>}
          <InfoNote>
            Thiết bị gửi bảo hành bị chặn cho thuê. Khi nhận về, phải kiểm tra an toàn trước khi đưa
            vào khai thác; kết quả của đối tác không tự thay thế kiểm tra tại shop.
          </InfoNote>
          <Table
            headers={['Thiết bị / đơn vị', 'Thời hạn / căn cứ', 'Thao tác']}
            empty={!q.data?.coverage?.length}
          >
            {q.data?.coverage?.map((r: Row) => (
              <tr key={r.equipment_id}>
                <td>
                  {r.code}
                  <small>
                    {r.provider} · phiên bản {r.version}
                  </small>
                </td>
                <td>
                  {date(r.starts_on)} → {date(r.ends_on)}
                  <small>{r.document_ref}</small>
                  <details>
                    <summary>Điều kiện bảo hành</summary>
                    <p className="preserve-lines">{r.terms}</p>
                  </details>
                </td>
                <td>
                  {can && (
                    <div className="workflow-actions">
                      <Button className="secondary" onClick={() => coverage(r)}>
                        Sửa hồ sơ
                      </Button>
                      <Button
                        onClick={() =>
                          form.open(
                            'Gửi bảo hành',
                            '/warranty/cases',
                            [
                              {
                                key: 'problem',
                                label: 'Vấn đề và tình trạng gửi đi',
                                type: 'textarea',
                              },
                              { key: 'partnerReference', label: 'Mã tiếp nhận của đối tác' },
                            ],
                            (v) => ({ ...v, equipmentId: r.equipment_id }),
                            'Chỉ gửi khi còn thời hạn và đã xử lý lịch thuê/bảo trì liên quan.',
                          )
                        }
                      >
                        Gửi bảo hành
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </Table>
          <h2>Lần xử lý bảo hành</h2>
          <Table
            headers={['Thiết bị / hồ sơ', 'Vấn đề / thời điểm', 'Kết quả', 'Thao tác']}
            empty={!q.data?.cases?.length}
          >
            {q.data?.cases?.map((r: Row) => (
              <tr key={r.id}>
                <td>
                  {r.code}
                  <small>
                    {r.provider} · {r.partner_reference}
                  </small>
                  <small>Hồ sơ phiên bản {r.coverage_version}</small>
                </td>
                <td>
                  {r.problem}
                  <small>
                    {date(r.opened_at, true)} · {r.opened_by}
                  </small>
                </td>
                <td>
                  {label[r.status]}
                  <small>
                    {label[r.outcome]} {r.result}
                  </small>
                </td>
                <td>
                  {can && r.status === 'OPEN' && (
                    <Button
                      onClick={() =>
                        form.open(
                          'Nhận lại từ bảo hành',
                          '/warranty/cases/' + r.id + '/close',
                          [
                            {
                              key: 'outcome',
                              label: 'Kết quả của đối tác',
                              options: enumChoices([
                                'REPAIRED',
                                'REPLACED',
                                'DECLINED',
                                'UNRESOLVED',
                              ]),
                            },
                            {
                              key: 'result',
                              label: 'Biên bản nhận lại và việc cần xử lý',
                              type: 'textarea',
                            },
                          ],
                          (v) => v,
                          'Chỉ xác nhận khi thiết bị đã được nhận lại. Nếu đối tác đổi máy, ghi rõ sê-ri/mã nhận dạng trong biên bản; kiểm tra và cập nhật hồ sơ tài sản trước khai thác.',
                        )
                      }
                    >
                      Nhận lại / kết thúc
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </Table>
        </Panel>
      </QueryState>
      {form.modal}
    </>
  );
}
