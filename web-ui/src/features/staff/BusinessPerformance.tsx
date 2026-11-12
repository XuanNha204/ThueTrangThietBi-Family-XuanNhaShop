import { useState } from 'react';
import type { Row } from '../../shared/types';
import { useQuery } from '../../shared/state';
import { money } from '../../shared/api';
import { PageHeader, Panel, QueryState, Table, InfoNote } from '../../shared/ui';
import { month, workflowLabels as label } from './WorkflowForms';
export function BusinessPerformance() {
  const [period, setPeriod] = useState(month()),
    q = useQuery<Row>('/reports/performance?month=' + period),
    f = q.data?.financial;
  return (
    <>
      <PageHeader
        title="Hiệu quả kinh doanh"
        description="Dữ liệu thu, chi phí, phân bổ tài sản và mức sử dụng thiết bị theo tháng."
      />
      <Panel className="workflow-panel">
        <label className="field">
          <span>Kỳ báo cáo</span>
          <input
            type="month"
            value={period}
            max={month()}
            onChange={(e) => {
              if (e.target.value) setPeriod(e.target.value);
            }}
          />
        </label>
        <QueryState loading={q.loading} error={q.error}>
          {f && (
            <>
              <InfoNote>{q.data?.basis}</InfoNote>
              <dl className="performance-metrics">
                {[
                  ['Thuê thực thu', 'rental_received'],
                  ['Bồi thường thực thu', 'compensation_received'],
                  ['Chi phí hoạt động đã duyệt', 'operating_expense'],
                  ['Chi phí lương trước trừ tạm ứng', 'payroll_cost'],
                  ['Phân bổ giá mua', 'depreciation'],
                  ['Kết quả quản trị tạm tính', 'management_result'],
                ].map(([name, key]) => (
                  <div key={key}>
                    <dt>{name}</dt>
                    <dd>{money(f[key])}</dd>
                  </div>
                ))}
              </dl>
              <p>
                Đầu tư thiết bị đã duyệt: {money(f.capital_purchases)}. Biến động cọc:{' '}
                {money(f.deposit_movement)} — không cộng vào doanh thu.
              </p>
              {Number(f.missing_cost_basis) > 0 && (
                <InfoNote>
                  Còn {f.missing_cost_basis} tài sản chưa đủ ngày nhận/giá mua/thời gian phân bổ.
                  Kết quả chưa phản ánh đủ chi phí tài sản.
                </InfoNote>
              )}
              <h2>Chi phí theo nhóm</h2>
              <Table
                headers={['Nhóm', 'Giá trị đã duyệt']}
                empty={!q.data?.expensesByCategory?.length}
              >
                {q.data?.expensesByCategory?.map((r: Row) => (
                  <tr key={r.category}>
                    <td>{label[r.category]}</td>
                    <td>{money(r.amount)}</td>
                  </tr>
                ))}
              </Table>
              <h2>Khai thác thiết bị</h2>
              <InfoNote>
                Tỷ lệ = giờ đã giao thực tế / giờ tài sản thuộc phạm vi theo dõi trong kỳ. Bao gồm
                cả thời gian ngừng khai thác để thấy mức sử dụng trên tổng tài sản. Thiết bị thiếu
                ngày nhận được tính từ đầu kỳ. Số lỗi và chi phí bảo trì là căn cứ rà soát, không tự
                quyết định mua hoặc thanh lý.
              </InfoNote>
              <Table
                headers={[
                  'Loại thiết bị',
                  'Số mã / giờ theo dõi',
                  'Giờ thuê / tỷ lệ',
                  'Lỗi / chi phí bảo trì',
                ]}
                empty={!q.data?.utilization?.length}
              >
                {q.data?.utilization?.map((r: Row) => (
                  <tr key={r.type_id}>
                    <td>
                      {r.type_name}
                      <small>{r.category}</small>
                    </td>
                    <td>
                      {r.asset_count} mã · {r.possible_hours} giờ
                      {Number(r.unknown_acquisition_dates) > 0 && (
                        <small>{r.unknown_acquisition_dates} mã thiếu ngày nhận</small>
                      )}
                    </td>
                    <td>
                      {r.rented_hours} giờ · {r.utilization_percent ?? '—'}%
                    </td>
                    <td>
                      {r.faults} lỗi · {money(r.maintenance_cost)}
                    </td>
                  </tr>
                ))}
              </Table>
            </>
          )}
        </QueryState>
      </Panel>
    </>
  );
}
