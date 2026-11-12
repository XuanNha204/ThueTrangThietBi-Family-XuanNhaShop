import { useEffect, useRef, useState } from 'react';
import type { ReactNode, FormEvent } from 'react';
import {
  AlertCircle,
  ArrowRight,
  Check,
  ChevronRight,
  LoaderCircle,
  PackageOpen,
  Search,
  X,
} from 'lucide-react';
import { statusLabels } from './api';
import { useApp } from './state';
export function Badge({ status }: { status: string }) {
  return (
    <span className={`badge status-${status.toLowerCase()}`}>
      <i />
      {statusLabels[status] ?? status}
    </span>
  );
}
export function Button({
  children,
  className = '',
  busy = false,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { busy?: boolean }) {
  return (
    <button {...props} disabled={props.disabled || busy} className={`button ${className}`}>
      {busy && <LoaderCircle size={16} className="spin" />}
      {children}
    </button>
  );
}
export function PageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {action && <div className="heading-action">{action}</div>}
    </div>
  );
}
export function Panel({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`panel ${className}`}>{children}</section>;
}
export function Empty({
  title = 'Chưa có dữ liệu',
  description = 'Dữ liệu sẽ xuất hiện tại đây sau khi bạn tạo mới.',
  action,
}: {
  title?: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-icon">
        <PackageOpen size={30} />
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={24} />
      <span>Đang tải dữ liệu…</span>
    </div>
  );
}
export function ErrorState({ message }: { message: string }) {
  const { refresh } = useApp();
  return (
    <div className="error-state" role="alert">
      <AlertCircle />
      <p>{message}</p>
      <Button className="secondary" onClick={refresh}>
        Thử lại
      </Button>
    </div>
  );
}
export function QueryState({
  loading,
  error,
  children,
}: {
  loading: boolean;
  error: string;
  children: ReactNode;
}) {
  return loading ? <Loading /> : error ? <ErrorState message={error} /> : <>{children}</>;
}
export function SearchInput({
  value,
  onChange,
  placeholder = 'Tìm kiếm…',
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <label className="search-box">
      <Search size={18} />
      <input
        type="search"
        aria-label={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </label>
  );
}
export function Dialog({
  title,
  description,
  children,
  onClose,
  wide = false,
  showToastError = true,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
  showToastError?: boolean;
}) {
  const { errorToast } = useApp();
  const [openedAt] = useState(Date.now);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    d?.showModal();
    return () => d?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      aria-label={title}
      className={`dialog ${wide ? 'wide' : ''}`}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="dialog-heading">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        <button type="button" className="icon-button" aria-label="Đóng hộp thoại" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {showToastError && errorToast && errorToast.id >= openedAt && (
        <div className="inline-error dialog-local-error" role="alert">
          {errorToast.message}
        </div>
      )}
      {children}
    </dialog>
  );
}
export type FieldSpec = {
  key: string;
  label: string;
  type?: string;
  defaultValue?: string | number;
  options?: { value: string; label: string }[];
  required?: boolean;
  min?: string | number;
  max?: string | number;
  maxLength?: number;
  minLength?: number;
  pattern?: string;
  step?: string | number;
  help?: string;
};
export function FormDialog({
  title,
  description,
  fields,
  submitLabel = 'Lưu thông tin',
  onSubmit,
  onClose,
}: {
  title: string;
  description?: string;
  fields: FieldSpec[];
  submitLabel?: string;
  onSubmit: (values: Record<string, string>) => Promise<boolean>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    const values = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setBusy(true);
    try {
      if (await onSubmit(values)) onClose();
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={title}
      description={description}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <form onSubmit={submit} className="form-stack">
        {fields.map((f) => (
          <label className="field" key={f.key}>
            <span>
              {f.label}
              {f.required !== false && <b aria-hidden="true"> *</b>}
            </span>
            {f.options ? (
              <select
                name={f.key}
                required={f.required !== false}
                defaultValue={f.defaultValue ?? ''}
              >
                <option value="">Chọn {f.label.toLowerCase()}</option>
                {f.options.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            ) : f.type === 'textarea' ? (
              <textarea
                name={f.key}
                required={f.required !== false}
                defaultValue={f.defaultValue}
                rows={3}
                maxLength={f.maxLength ?? 2000}
              />
            ) : (
              <input
                name={f.key}
                type={f.type ?? 'text'}
                required={f.required !== false}
                defaultValue={f.defaultValue}
                min={f.min}
                max={f.max}
                minLength={f.minLength}
                pattern={f.pattern}
                title={f.pattern ? f.help : undefined}
                step={f.step}
                maxLength={f.maxLength ?? (f.type === 'text' || !f.type ? 400 : undefined)}
              />
            )}{' '}
            {f.help && <small>{f.help}</small>}
          </label>
        ))}
        <div className="dialog-actions">
          <Button type="button" className="secondary" disabled={busy} onClick={onClose}>
            Quay lại
          </Button>
          <Button busy={busy} type="submit">
            {submitLabel}
            <Check size={16} />
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
export function ConfirmDialog({
  title,
  description,
  confirm = 'Xác nhận',
  onConfirm,
  onClose,
}: {
  title: string;
  description: string;
  confirm?: string;
  onConfirm: () => Promise<boolean>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  return (
    <Dialog
      title={title}
      description={description}
      onClose={() => {
        if (!busy) onClose();
      }}
    >
      <div className="dialog-actions">
        <Button className="secondary" disabled={busy} onClick={onClose}>
          Quay lại
        </Button>
        <Button
          busy={busy}
          onClick={async () => {
            setBusy(true);
            try {
              if (await onConfirm()) onClose();
            } finally {
              setBusy(false);
            }
          }}
        >
          {confirm}
          <ArrowRight size={16} />
        </Button>
      </div>
    </Dialog>
  );
}
export function Table({
  headers,
  children,
  empty = false,
}: {
  headers: string[];
  children: ReactNode;
  empty?: boolean;
}) {
  return empty ? (
    <Empty />
  ) : (
    <div
      className="table-scroll"
      tabIndex={0}
      role="region"
      aria-label={headers.filter(Boolean).join(', ')}
    >
      <table>
        <thead>
          <tr>
            {headers.map((h, i) => (
              <th key={h || i} scope="col">
                {h || <span className="sr-only">Thao tác</span>}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
export function Breadcrumb({ children }: { children: ReactNode }) {
  return (
    <div className="breadcrumb">
      XuanNhaShop
      <ChevronRight size={14} />
      <span>{children}</span>
    </div>
  );
}
export function InfoNote({ children }: { children: ReactNode }) {
  return (
    <div className="info-note">
      <AlertCircle size={18} />
      <div>{children}</div>
    </div>
  );
}
