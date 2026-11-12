export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
let csrf: { token: string; headerName: string } | null = null;
let pendingCsrf: Promise<{ token: string; headerName: string }> | null = null;
export function clearCsrf() {
  csrf = null;
  pendingCsrf = null;
}
async function token() {
  if (csrf) return csrf;
  pendingCsrf ??= fetch('/api/auth/csrf', { credentials: 'include', cache: 'no-store' }).then(
    async (r) => {
      if (!r.ok) throw new ApiError(r.status, 'Không lấy được mã phiên. Hãy tải lại trang.');
      return r.json();
    },
  );
  try {
    csrf = await pendingCsrf;
    return csrf!;
  } finally {
    pendingCsrf = null;
  }
}
export async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; key?: string; signal?: AbortSignal } = {},
): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (method !== 'GET') {
    const t = await token();
    headers[t.headerName] = t.token;
    if (options.key) headers['Idempotency-Key'] = options.key;
  }
  const isFile = options.body instanceof FormData;
  if (options.body !== undefined && !isFile) headers['Content-Type'] = 'application/json';
  let response: Response;
  try {
    response = await fetch('/api' + path, {
      method,
      credentials: 'include',
      cache: 'no-store',
      headers,
      body:
        options.body === undefined
          ? undefined
          : isFile
            ? (options.body as FormData)
            : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(
      0,
      'Chưa nhận được phản hồi. Kiểm tra kết nối và trạng thái trước khi thử lại.',
    );
  }
  const text = await response.text();
  let data: any = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { message: 'Máy chủ trả về phản hồi không hợp lệ.' };
  }
  if (!response.ok) {
    if (response.status === 403) clearCsrf();
    if (response.status === 401 && !path.startsWith('/auth/'))
      window.dispatchEvent(new Event('session-expired'));
    throw new ApiError(response.status, data?.message ?? 'Không thực hiện được yêu cầu.');
  }
  return data as T;
}
export const get = <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal });
export const money = (value: number | string | undefined | null) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));
export const number = (value: number | string) =>
  new Intl.NumberFormat('vi-VN').format(Number(value));
export const date = (value: string | undefined | null, withTime = false) =>
  value
    ? new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
      }).format(new Date(value))
    : '—';
export const localInput = (value: Date) => {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(value)
      .map((p) => [p.type, p.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
};
export const shopDate = (value: string) =>
  new Date(value + (value.length === 16 ? ':00' : '') + '+07:00');
export const roles: Record<string, string> = {
  OWNER: 'Chủ cửa hàng',
  MANAGER: 'Quản lý',
  ACCOUNTANT: 'Kế toán',
  INVENTORY: 'Nhân viên kho',
  DELIVERY: 'Giao nhận',
  TECHNICIAN: 'Kỹ thuật',
  HR: 'Nhân sự',
  CUSTOMER: 'Khách hàng',
};
export const statusLabels: Record<string, string> = {
  HELD: 'Đang giữ chỗ',
  CONFIRMED: 'Đã xác nhận',
  PREPARING: 'Đang chuẩn bị',
  RENTED: 'Đang cho thuê',
  PARTIAL_RETURN: 'Trả một phần',
  AWAIT_SETTLEMENT: 'Chờ tất toán',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết giữ chỗ',
  READY: 'Sẵn sàng',
  AWAIT_ACCEPTANCE: 'Chờ nghiệm thu',
  AWAIT_INSPECTION: 'Chờ kiểm tra',
  MAINTENANCE: 'Đang bảo trì',
  SUSPENDED: 'Ngừng khai thác',
  DISPOSED: 'Đã thanh lý',
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
  DRAFT: 'Bản nháp',
  PAID: 'Đã chi',
  OPEN: 'Chờ xử lý',
  RESOLVED: 'Đã xử lý',
  CLOSED: 'Đã đóng',
  SENT: 'Đã xử lý',
  FAILED: 'Cần kiểm tra',
  PROCESSING: 'Đang xử lý',
  APPLIED: 'Đã áp dụng',
};
export const moneyKinds: Record<string, string> = {
  DEPOSIT_IN: 'Nhận cọc',
  RENTAL_IN: 'Thu tiền thuê',
  DEPOSIT_REFUND: 'Hoàn cọc',
  PAYROLL_OUT: 'Chi lương',
  EXPENSE_OUT: 'Chi phí',
  PAYROLL_ADVANCE_OUT: 'Chi tạm ứng lương',
  RECOVERY_IN: 'Thu thanh lý',
  CHARGE_IN: 'Thu bồi thường đã duyệt',
};
