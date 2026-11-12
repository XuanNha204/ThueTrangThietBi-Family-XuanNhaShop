import { useState } from 'react';
import type { ReactNode } from 'react';
import { FormDialog } from '../../shared/ui';
import type { FieldSpec } from '../../shared/ui';
import { useCommand } from '../../shared/state';
import type { Row } from '../../shared/types';
export function useWorkflowForm() {
  const [modal, setModal] = useState<ReactNode>(null);
  const cmd = useCommand();
  function open(
    title: string,
    path: string,
    fields: FieldSpec[],
    transform: (v: Record<string, string>) => unknown = (v) => v,
    description?: string,
  ) {
    setModal(
      <FormDialog
        title={title}
        description={description}
        fields={fields}
        onClose={() => setModal(null)}
        onSubmit={async (v) => (await cmd.run(path, transform(v))).ok}
      />,
    );
  }
  function decide(title: string, path: string, accepted: boolean, description?: string) {
    open(
      title,
      path,
      [{ key: 'note', label: 'Căn cứ và kết luận', type: 'textarea' }],
      (v) => ({ accepted, note: v.note }),
      description,
    );
  }
  return { modal, open, decide };
}
export const choices = (rows: Row[], label: (r: Row) => string, value = 'id') =>
  rows.map((r) => ({ value: String(r[value]), label: label(r) }));
export const today = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
export const month = () => today().slice(0, 7);
export const optional = (value: string) => value || null;
export const workflowLabels: Record<string, string> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  ACCEPTED: 'Đã tiếp nhận',
  REJECTED: 'Đã từ chối',
  COMPLETED: 'Đã hoàn thành',
  OPEN: 'Đang xử lý',
  CLOSED: 'Đã kết thúc',
  RESOLVED: 'Đã phản hồi',
  ASSIGNED: 'Đã phân công',
  CANCELLED: 'Đã hủy',
  TRANSFER: 'Chuyển kho',
  DISPOSE: 'Thanh lý',
  OUT: 'Giao thiết bị',
  IN: 'Thu hồi',
  PURCHASE: 'Mua thiết bị',
  MAINTENANCE: 'Bảo trì',
  CLEANING: 'Vệ sinh',
  TRANSPORT: 'Vận chuyển',
  OTHER: 'Khác',
  ALLOWANCE: 'Phụ cấp',
  BONUS: 'Thưởng',
  DEDUCTION: 'Khấu trừ',
  ADVANCE: 'Tạm ứng',
  BASIC: 'Lương công',
  REPAIRED: 'Đã sửa chữa',
  REPLACED: 'Đã thay thế',
  DECLINED: 'Từ chối bảo hành',
  UNRESOLVED: 'Chưa khắc phục',
};
export const enumChoices = (keys: string[]) =>
  keys.map((value) => ({ value, label: workflowLabels[value] || value }));
