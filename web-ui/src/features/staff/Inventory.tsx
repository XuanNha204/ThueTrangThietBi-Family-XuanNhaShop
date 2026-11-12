import { useState } from 'react';
import type { ReactNode } from 'react';
import { Plus, ShieldCheck, History } from 'lucide-react';
import type { Equipment, Product, Row } from '../../shared/types';
import { date, statusLabels } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Badge,
  Button,
  FormDialog,
  InfoNote,
  PageHeader,
  Panel,
  QueryState,
  SearchInput,
  Table,
} from '../../shared/ui';
import { DeviceArt } from '../../shared/DeviceArt';
import { MaintenancePlans } from './MaintenancePlans';
import { ProductPrices } from './ProductPrices';
import { InspectionDialog } from './InspectionDialog';
import { ChecklistManagement } from './ChecklistManagement';
import { EquipmentHistoryDialog } from './EquipmentHistory';
export function Inventory() {
  const { user } = useApp();
  const q = useQuery<Equipment[]>('/equipment'),
    products = useQuery<Product[]>('/catalog'),
    warehouses = useQuery<Row[]>('/warehouses');
  const [search, setSearch] = useState(''),
    [state, setState] = useState('ALL'),
    [modal, setModal] = useState<ReactNode>(null);
  const command = useCommand();
  const close = () => setModal(null),
    canInspect = ['OWNER', 'TECHNICIAN'].includes(user!.role),
    canReceive = ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role);
  const rows = (q.data ?? []).filter(
    (e) =>
      (state === 'ALL' || e.state === state) &&
      (e.code + ' ' + e.name).toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi')),
  );
  function createType() {
    setModal(
      <FormDialog
        title="Thêm loại thiết bị"
        onClose={close}
        fields={[
          { key: 'code', label: 'Mã loại thiết bị' },
          { key: 'name', label: 'Tên loại thiết bị' },
          { key: 'category', label: 'Nhóm thiết bị' },
          { key: 'description', label: 'Mô tả', type: 'textarea' },
          { key: 'specifications', label: 'Thông số kỹ thuật', type: 'textarea' },
          {
            key: 'icon',
            label: 'Biểu tượng',
            options: [
              'cleaner',
              'projector',
              'vacuum',
              'purifier',
              'grill',
              'speaker',
              'drill',
              'fan',
            ].map((value) => ({ value, label: value })),
          },
          {
            key: 'dailyRate',
            label: 'Giá thuê mỗi ngày (đ)',
            type: 'number',
            min: 0.01,
            step: 0.01,
          },
          { key: 'deposit', label: 'Tiền cọc (đ)', type: 'number', min: 0, step: 0.01 },
        ]}
        onSubmit={async (v) =>
          (
            await command.run(
              '/equipment-types',
              { ...v, dailyRate: Number(v.dailyRate), deposit: Number(v.deposit) },
              'Đã thêm loại thiết bị.',
            )
          ).ok
        }
      />,
    );
  }
  function receive() {
    setModal(
      <FormDialog
        title="Tiếp nhận thiết bị mới"
        description="Thiết bị mới ở trạng thái chờ nghiệm thu. Kỹ thuật viên cần kiểm tra trước khi đưa vào lịch thuê."
        onClose={close}
        fields={[
          {
            key: 'warehouseId',
            label: 'Kho tiếp nhận',
            options: (warehouses.data ?? []).map((w) => ({ value: w.id, label: w.name })),
            defaultValue: warehouses.data?.length === 1 ? warehouses.data[0].id : undefined,
          },
          {
            key: 'typeId',
            label: 'Loại thiết bị',
            options: (products.data ?? []).map((p) => ({ value: p.id, label: p.name })),
          },
          {
            key: 'code',
            label: 'Mã thiết bị riêng',
            help: '3–40 ký tự: chữ hoa, số và gạch ngang. Ví dụ TB01-005.',
          },
        ]}
        onSubmit={async (v) =>
          (await command.run('/equipment', v, 'Đã tiếp nhận thiết bị, đang chờ nghiệm thu.')).ok
        }
      />,
    );
  }
  return (
    <>
      <PageHeader
        eyebrow="VẬN HÀNH"
        title="Thiết bị & kho"
        description="Mỗi thiết bị một mã riêng. Theo dõi rõ tình trạng và khả năng phục vụ."
        action={
          canReceive && (
            <Button onClick={receive}>
              <Plus size={17} />
              Tiếp nhận thiết bị
            </Button>
          )
        }
      />
      {['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role) && <ProductPrices />}
      {user?.role === 'OWNER' && (
        <Button className="secondary" onClick={createType}>
          Thêm loại thiết bị
        </Button>
      )}
      <div className="inventory-summary">
        {[
          ['ALL', 'Tất cả thiết bị'],
          ['READY', 'Sẵn sàng'],
          ['RENTED', 'Đang cho thuê'],
          ['AWAIT_INSPECTION', 'Chờ kiểm tra'],
          ['MAINTENANCE', 'Bảo trì'],
        ].map(([s, t]) => (
          <button key={s} className={state === s ? 'active' : ''} onClick={() => setState(s)}>
            <span>{t}</span>
            <strong>
              {s === 'ALL'
                ? (q.data?.length ?? 0)
                : (q.data?.filter((e) => e.state === s).length ?? 0)}
            </strong>
          </button>
        ))}
      </div>
      <Panel>
        <div className="table-toolbar">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Tìm mã hoặc tên thiết bị…"
          />
          <select
            value={state}
            onChange={(e) => setState(e.target.value)}
            aria-label="Lọc tình trạng thiết bị"
          >
            <option value="ALL">Tất cả tình trạng</option>
            {[
              'READY',
              'PREPARING',
              'RENTED',
              'AWAIT_ACCEPTANCE',
              'AWAIT_INSPECTION',
              'MAINTENANCE',
              'SUSPENDED',
              'DISPOSED',
            ].map((s) => (
              <option value={s} key={s}>
                {statusLabels[s]}
              </option>
            ))}
          </select>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <Table
            headers={['Thiết bị', 'Mã riêng', 'Nhóm', 'Vị trí', 'Tình trạng', '']}
            empty={!rows.length}
          >
            {rows.map((e) => (
              <tr key={e.id}>
                <td>
                  <div className="product-cell">
                    <div className="tiny-art">
                      <DeviceArt kind={products.data?.find((p) => p.id === e.type_id)?.icon} />
                    </div>
                    <strong>{e.name}</strong>
                  </div>
                </td>
                <td className="mono">{e.code}</td>
                <td>{e.category}</td>
                <td>{e.warehouse}</td>
                <td>
                  <Badge status={e.state} />
                </td>
                <td>
                  {['OWNER', 'MANAGER', 'INVENTORY', 'TECHNICIAN'].includes(user!.role) && (
                    <Button
                      className="secondary small"
                      onClick={() =>
                        setModal(<EquipmentHistoryDialog equipmentId={e.id} onClose={close} />)
                      }
                    >
                      <History size={15} />
                      Lịch sử thiết bị
                    </Button>
                  )}
                  {canInspect &&
                    [
                      'READY',
                      'AWAIT_ACCEPTANCE',
                      'AWAIT_INSPECTION',
                      'MAINTENANCE',
                      'SUSPENDED',
                    ].includes(e.state) && (
                      <Button
                        className="secondary small"
                        onClick={() => setModal(<InspectionDialog equipment={e} onClose={close} />)}
                      >
                        <ShieldCheck size={15} />
                        Kiểm tra
                      </Button>
                    )}
                </td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
      {modal}
    </>
  );
}
export function Maintenance() {
  const q = useQuery<{ inspections: Row[]; tickets: Row[] }>('/maintenance'),
    assets = useQuery<Equipment[]>('/equipment');
  const { user } = useApp();
  const cmd = useCommand();
  const [modal, setModal] = useState<ReactNode>(null),
    [tab, setTab] = useState('tickets');
  const can = ['OWNER', 'TECHNICIAN'].includes(user!.role);
  function open() {
    setModal(
      <FormDialog
        title="Ghi nhận sự cố kỹ thuật"
        description="Thiết bị sẽ ngừng nhận lịch thuê mới cho đến khi được kiểm tra đạt."
        onClose={() => setModal(null)}
        fields={[
          {
            key: 'equipment',
            label: 'Thiết bị',
            options: (assets.data ?? [])
              .filter((e) =>
                ['READY', 'AWAIT_ACCEPTANCE', 'AWAIT_INSPECTION', 'SUSPENDED'].includes(e.state),
              )
              .map((e) => ({ value: e.id, label: e.code + ' · ' + e.name })),
          },
          { key: 'fault', label: 'Mô tả sự cố', type: 'textarea' },
        ]}
        onSubmit={async (v) =>
          (
            await cmd.run(
              '/equipment/' + v.equipment + '/maintenance',
              { fault: v.fault },
              'Đã ghi nhận sự cố và chặn lịch thuê mới.',
            )
          ).ok
        }
      />,
    );
  }
  return (
    <>
      <PageHeader
        eyebrow="AN TOÀN TRƯỚC MỖI LẦN CHO THUÊ"
        title="Kiểm tra & bảo trì"
        description="Chỉ đưa thiết bị trở lại khai thác sau khi có kết quả kiểm tra thực tế."
        action={
          can && (
            <Button onClick={open}>
              <Plus size={17} />
              Ghi nhận sự cố
            </Button>
          )
        }
      />
      <InfoNote>
        Thiết bị mới và thiết bị trả về cần kiểm tra. Phiếu sửa chữa được đóng sau kiểm tra đạt. Kế
        hoạch bảo trì có lịch chặn riêng và cần kết thúc ở phần Kế hoạch bảo trì.
      </InfoNote>
      <ChecklistManagement />
      <MaintenancePlans />
      <Panel>
        <div className="tabs">
          <button className={tab === 'tickets' ? 'active' : ''} onClick={() => setTab('tickets')}>
            Phiếu kỹ thuật
          </button>
          <button
            className={tab === 'inspections' ? 'active' : ''}
            onClick={() => setTab('inspections')}
          >
            Lịch sử kiểm tra
          </button>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          {tab === 'tickets' ? (
            <Table
              headers={['Thiết bị', 'Vấn đề', 'Thời điểm', 'Người ghi nhận', 'Trạng thái']}
              empty={!q.data?.tickets.length}
            >
              {q.data?.tickets.map((t) => (
                <tr key={t.id}>
                  <td>
                    <strong>{t.name}</strong>
                    <small>{t.equipment_code}</small>
                    <Button
                      className="ghost small"
                      onClick={() =>
                        setModal(
                          <EquipmentHistoryDialog
                            equipmentId={t.equipment_id}
                            onClose={() => setModal(null)}
                          />,
                        )
                      }
                    >
                      Lịch sử thiết bị
                    </Button>
                  </td>
                  <td className="wrap-cell">{t.fault}</td>
                  <td>{date(t.opened_at, true)}</td>
                  <td>{t.opened_by}</td>
                  <td>
                    <Badge status={t.status} />
                  </td>
                </tr>
              ))}
            </Table>
          ) : (
            <Table
              headers={['Thiết bị', 'Kết quả', 'Ghi nhận thực tế', 'Kỹ thuật viên', 'Thời điểm']}
              empty={!q.data?.inspections.length}
            >
              {q.data?.inspections.map((t) => (
                <tr key={t.id}>
                  <td>
                    <strong>{t.name}</strong>
                    <small>{t.equipment_code}</small>
                    <Button
                      className="ghost small"
                      onClick={() =>
                        setModal(
                          <EquipmentHistoryDialog
                            equipmentId={t.equipment_id}
                            onClose={() => setModal(null)}
                          />,
                        )
                      }
                    >
                      Lịch sử thiết bị
                    </Button>
                  </td>
                  <td>
                    <span className={`badge ${t.passed ? 'status-ready' : 'status-maintenance'}`}>
                      <i />
                      {t.passed ? 'Đạt' : 'Cần xử lý'}
                    </span>
                  </td>
                  <td className="wrap-cell">{t.findings}</td>
                  <td>{t.inspected_by}</td>
                  <td>{date(t.created_at, true)}</td>
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
