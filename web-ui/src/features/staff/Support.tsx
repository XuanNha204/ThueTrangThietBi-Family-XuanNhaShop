import { useState } from 'react';
import type { ReactNode } from 'react';
import { Plus, Check, Headphones } from 'lucide-react';
import type { Row, RentalSummary } from '../../shared/types';
import { date } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Badge, Button, FormDialog, PageHeader, Panel, QueryState, Table } from '../../shared/ui';
import { CustomerHeader } from '../customer/Catalog';
export function Support({ customer = false }: { customer?: boolean }) {
  const q = useQuery<Row[]>('/support'),
    orders = useQuery<RentalSummary[]>('/rentals');
  const { user } = useApp();
  const cmd = useCommand();
  const [modal, setModal] = useState<ReactNode>(null);
  const close = () => setModal(null);
  function create() {
    setModal(
      <FormDialog
        title="Gửi yêu cầu hỗ trợ"
        description="Ghi rõ đơn thuê và nội dung để cửa hàng đối chiếu, liên hệ xử lý."
        onClose={close}
        fields={[
          {
            key: 'orderId',
            label: 'Đơn thuê liên quan',
            required: false,
            options: (orders.data ?? []).map((o) => ({
              value: o.id,
              label: o.code + ' · ' + o.customer_name,
            })),
          },
          { key: 'subject', label: 'Tiêu đề' },
          { key: 'message', label: 'Nội dung cần hỗ trợ', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              '/support',
              { ...v, orderId: v.orderId || null },
              'Đã gửi yêu cầu hỗ trợ.',
            )
          ).ok
        }
      />,
    );
  }
  function resolve(t: Row) {
    setModal(
      <FormDialog
        title="Ghi nhận kết quả xử lý"
        description={t.subject}
        onClose={close}
        fields={[
          { key: 'resolution', label: 'Kết quả và nội dung đã liên hệ lại', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (await cmd.run('/support/' + t.id + '/resolve', v, 'Đã lưu kết quả xử lý hỗ trợ.')).ok
        }
      />,
    );
  }
  const content = (
    <>
      <PageHeader
        eyebrow="LẮNG NGHE & ĐỒNG HÀNH"
        title={customer ? 'Yêu cầu hỗ trợ của tôi' : 'Chăm sóc khách hàng'}
        description="Theo dõi nội dung, người xử lý và kết quả liên hệ lại."
        action={
          <Button onClick={create}>
            <Plus size={17} />
            Tạo yêu cầu hỗ trợ
          </Button>
        }
      />
      <QueryState loading={q.loading} error={q.error}>
        <div className="support-grid">
          {q.data?.length ? (
            q.data.map((t) => (
              <Panel key={t.id} className="support-card">
                <div className="support-card-top">
                  <span className="support-icon">
                    <Headphones size={20} />
                  </span>
                  <Badge status={t.status} />
                </div>
                <h2>{t.subject}</h2>
                <span className="subtle-code">
                  {t.order_code ?? 'Yêu cầu chung'} · {date(t.created_at, true)}
                </span>
                <p>{t.message}</p>
                <div className="support-meta">
                  Người gửi: <strong>{t.created_by}</strong>
                </div>
                {t.resolution && (
                  <div className="support-resolution">
                    <Check size={17} />
                    <div>
                      <strong>Kết quả xử lý · {t.resolved_by}</strong>
                      <p>{t.resolution}</p>
                    </div>
                  </div>
                )}
                {t.status === 'OPEN' && ['OWNER', 'MANAGER'].includes(user!.role) && (
                  <Button className="secondary small" onClick={() => resolve(t)}>
                    Ghi nhận kết quả
                    <Check size={15} />
                  </Button>
                )}
              </Panel>
            ))
          ) : (
            <Panel>
              <div className="empty">
                <Headphones size={32} />
                <h3>Chưa có yêu cầu hỗ trợ</h3>
                <p>Tạo yêu cầu khi bạn cần tư vấn hoặc xử lý vấn đề về đơn thuê.</p>
              </div>
            </Panel>
          )}
        </div>
      </QueryState>
      {modal}
    </>
  );
  return customer ? (
    <>
      <CustomerHeader />
      <main className="store-container customer-content">{content}</main>
    </>
  ) : (
    content
  );
}
