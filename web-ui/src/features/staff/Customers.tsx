import { useState } from 'react';
import { Plus, Users } from 'lucide-react';
import type { Customer } from '../../shared/types';
import { useApp, useCommand, useQuery } from '../../shared/state';
import {
  Button,
  FormDialog,
  PageHeader,
  Panel,
  QueryState,
  SearchInput,
  Table,
} from '../../shared/ui';
export function Customers() {
  const q = useQuery<Customer[]>('/customers'),
    cmd = useCommand();
  const { user } = useApp();
  const [search, setSearch] = useState(''),
    [open, setOpen] = useState(false);
  const rows = (q.data ?? []).filter((c) =>
    (c.name + ' ' + c.phone + ' ' + c.address)
      .toLocaleLowerCase('vi')
      .includes(search.toLocaleLowerCase('vi')),
  );
  return (
    <>
      <PageHeader
        eyebrow="QUẢN LÝ THÔNG TIN"
        title="Khách hàng"
        description="Thông tin liên hệ để tư vấn và đối chiếu các lần thuê."
        action={
          ['OWNER', 'MANAGER', 'INVENTORY'].includes(user!.role) && (
            <Button onClick={() => setOpen(true)}>
              <Plus size={17} />
              Thêm khách hàng
            </Button>
          )
        }
      />
      <Panel>
        <div className="table-toolbar">
          <SearchInput
            value={search}
            onChange={setSearch}
            placeholder="Tìm tên, điện thoại hoặc địa chỉ…"
          />
          <span className="muted">{rows.length} khách hàng</span>
        </div>
        <QueryState loading={q.loading} error={q.error}>
          <Table headers={['Khách hàng', 'Điện thoại', 'Địa chỉ', 'Email']} empty={!rows.length}>
            {rows.map((c) => (
              <tr key={c.id}>
                <td>
                  <div className="person-cell">
                    <span className="avatar">{c.name.split(' ').at(-1)?.[0]}</span>
                    <strong>{c.name}</strong>
                  </div>
                </td>
                <td>{c.phone}</td>
                <td className="wrap-cell">{c.address}</td>
                <td>{c.email || 'Chưa cung cấp'}</td>
              </tr>
            ))}
          </Table>
        </QueryState>
      </Panel>
      {open && (
        <FormDialog
          title="Thêm khách hàng"
          description="Nhập thông tin liên hệ dùng cho tư vấn và giao nhận."
          onClose={() => setOpen(false)}
          fields={[
            { key: 'name', label: 'Họ và tên' },
            {
              key: 'phone',
              label: 'Số điện thoại',
              type: 'tel',
              help: '8–15 chữ số, có thể bắt đầu bằng dấu +.',
            },
            { key: 'address', label: 'Địa chỉ', type: 'textarea' },
          ]}
          onSubmit={async (v) => (await cmd.run('/customers', v, 'Đã tạo hồ sơ khách hàng.')).ok}
        />
      )}
    </>
  );
}
