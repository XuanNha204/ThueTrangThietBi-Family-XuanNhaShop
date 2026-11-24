import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Check, Minus, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import { CustomerHeader } from './Catalog';
import type { Customer, Product, Quote } from '../../shared/types';
import { localInput, shopDate, money, request } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { EligibilityNotice } from './EligibilityNotice';
import { Button, InfoNote, PageHeader, QueryState } from '../../shared/ui';
import { ProductImage, ProductPhotoCredit } from '../../shared/ProductImage';
export function Booking() {
  const { user } = useApp();
  const [params] = useSearchParams(),
    navigate = useNavigate();
  const products = useQuery<Product[]>('/catalog');
  const customers = useQuery<Customer[]>(user?.role !== 'CUSTOMER' ? '/customers' : null);
  const [lines, setLines] = useState<{ typeId: string; quantity: number }[]>(
    params.get('type') ? [{ typeId: params.get('type')!, quantity: 1 }] : [],
  );
  const now = new Date(Date.now() + 10 * 60000);
  const [from, setFrom] = useState(params.get('from') || localInput(now)),
    [until, setUntil] = useState(
      params.get('to') || localInput(new Date(now.getTime() + 86400000)),
    ),
    [address, setAddress] = useState(''),
    [customerId, setCustomerId] = useState(''),
    [accept, setAccept] = useState(false),
    [quote, setQuote] = useState<Quote | null>(null),
    [quoteError, setQuoteError] = useState(''),
    [quoting, setQuoting] = useState(false),
    [quoteRevision, setQuoteRevision] = useState(0);
  const cmd = useCommand();
  useEffect(() => {
    if (products.data?.length && !lines.length)
      setLines([{ typeId: products.data[0].id, quantity: 1 }]);
  }, [products.data]);
  useEffect(() => {
    const a = shopDate(from),
      b = shopDate(until);
    if (
      !lines.length ||
      !from ||
      !until ||
      !Number.isFinite(a.getTime()) ||
      !Number.isFinite(b.getTime()) ||
      b <= a
    ) {
      setQuote(null);
      setQuoting(false);
      setQuoteError('Chọn thời gian nhận và trả hợp lệ.');
      return;
    }
    const controller = new AbortController();
    setQuoting(true);
    const timer = window.setTimeout(() => {
      request<Quote>('/catalog/quote', {
        method: 'POST',
        body: { lines, startsAt: a.toISOString(), endsAt: b.toISOString() },
        signal: controller.signal,
      })
        .then((q) => {
          setQuote(q);
          setQuoteError('');
        })
        .catch((e) => {
          if (!controller.signal.aborted) {
            setQuoteError(e.message);
            setQuote(null);
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setQuoting(false);
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [from, until, JSON.stringify(lines), quoteRevision]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!quote || quoting) return;
    const result = await cmd.run<{ id: string }>(
      '/rentals',
      {
        customerId: customerId || null,
        lines: lines.map((l) => ({
          ...l,
          priceVersion: quote.lines.find((q) => q.typeId === l.typeId)?.priceVersion,
        })),
        startsAt: shopDate(from).toISOString(),
        endsAt: shopDate(until).toISOString(),
        address,
        acceptTerms: accept,
      },
      'Đã giữ chỗ. Bạn có thể theo dõi và đối soát cọc trong đơn thuê.',
    );
    if (result.ok)
      navigate((user?.role === 'CUSTOMER' ? '/my-rentals/' : '/app/orders/') + result.data!.id);
    else if (result.status === 409) {
      setAccept(false);
      setQuoteRevision((v) => v + 1);
    }
  }
  function change(i: number, patch: Partial<{ typeId: string; quantity: number }>) {
    setLines((l) => l.map((v, n) => (n === i ? { ...v, ...patch } : v)));
  }
  const insufficient = quote?.lines.some((l) => l.available < l.quantity);
  return (
    <>
      <CustomerHeader />
      <main className="store-container booking-page">
        <Link to="/" className="text-link">
          <ArrowLeft size={16} />
          Tiếp tục chọn thiết bị
        </Link>
        <PageHeader
          eyebrow="CHUẨN BỊ CHO NHU CẦU CỦA BẠN"
          title="Tạo đơn thuê"
          description="Xem báo giá rõ ràng trước khi xác nhận giữ chỗ."
        />
        <EligibilityNotice />
        <QueryState loading={products.loading} error={products.error}>
          <form className="booking-layout" onSubmit={submit}>
            <div className="booking-main">
              <section className="panel booking-section">
                <div className="section-title">
                  <span className="step-number">01</span>
                  <h2>Thiết bị bạn chọn</h2>
                </div>
                {lines.map((line, i) => {
                  const product = products.data?.find((p) => p.id === line.typeId);
                  return (
                    <div className="booking-line" key={i}>
                      <div className="mini-art">
                        <ProductImage product={product} />
                      </div>
                      <div className="booking-line-detail">
                        {product?.photo?.illustrative && <small>Ảnh minh họa</small>}
                        {product?.photo && <ProductPhotoCredit photo={product.photo} />}
                        <label className="field">
                          <span>Thiết bị {i + 1}</span>
                          <select
                            value={line.typeId}
                            onChange={(e) => change(i, { typeId: e.target.value })}
                          >
                            {products.data
                              ?.filter(
                                (p) =>
                                  p.id === line.typeId || !lines.some((l) => l.typeId === p.id),
                              )
                              .map((p) => (
                                <option key={p.id} value={p.id}>
                                  {p.name}
                                </option>
                              ))}
                          </select>
                        </label>
                        <small>
                          {money(product?.dailyRate)} / ngày · Cọc {money(product?.deposit)}
                        </small>
                      </div>
                      <label className="field qty-field">
                        <span>Số lượng</span>
                        <input
                          type="number"
                          min={1}
                          max={20}
                          value={line.quantity}
                          onChange={(e) => change(i, { quantity: Number(e.target.value) })}
                          required
                        />
                      </label>
                      {lines.length > 1 && (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`Bỏ thiết bị ${i + 1}`}
                          onClick={() => setLines((v) => v.filter((_, n) => n !== i))}
                        >
                          <Trash2 size={17} />
                        </button>
                      )}
                    </div>
                  );
                })}
                <Button
                  type="button"
                  className="text-button"
                  disabled={lines.length >= (products.data?.length ?? 0)}
                  onClick={() => {
                    const p = products.data?.find((p) => !lines.some((l) => l.typeId === p.id));
                    if (p) setLines([...lines, { typeId: p.id, quantity: 1 }]);
                  }}
                >
                  <Plus size={16} />
                  Thêm thiết bị
                </Button>
              </section>
              <section className="panel booking-section">
                <div className="section-title">
                  <span className="step-number">02</span>
                  <h2>Thời gian & nhận thiết bị</h2>
                </div>
                <div className="form-grid">
                  <label className="field">
                    <span>Ngày giờ nhận</span>
                    <input
                      type="datetime-local"
                      required
                      value={from}
                      onChange={(e) => setFrom(e.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Ngày giờ trả dự kiến</span>
                    <input
                      type="datetime-local"
                      required
                      value={until}
                      onChange={(e) => setUntil(e.target.value)}
                    />
                  </label>
                </div>
                {user?.role !== 'CUSTOMER' && (
                  <label className="field">
                    <span>Khách hàng</span>
                    <select
                      value={customerId}
                      required
                      onChange={(e) => setCustomerId(e.target.value)}
                    >
                      <option value="">Chọn khách hàng</option>
                      {customers.data?.map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name} · {c.phone}
                        </option>
                      ))}
                    </select>
                    {customers.error && <small className="inline-error">{customers.error}</small>}
                  </label>
                )}
                <label className="field">
                  <span>Địa chỉ nhận / địa điểm sử dụng</span>
                  <textarea
                    required
                    rows={3}
                    maxLength={400}
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                    placeholder="Số nhà, đường, phường/xã, quận/huyện…"
                  />
                </label>
                <InfoNote>
                  Thời gian chuẩn bị và kiểm tra/vệ sinh được tính vào lịch thiết bị. Cửa hàng xác
                  nhận phương án giao nhận với bạn trước khi bàn giao.
                </InfoNote>
              </section>
            </div>
            <aside className="booking-summary panel">
              <span className="eyebrow">BÁO GIÁ DỰ KIẾN</span>
              <h2>Mọi chi phí, thật rõ ràng.</h2>
              {quoting ? (
                <p className="muted">Đang tính báo giá…</p>
              ) : quote ? (
                <>
                  <div className="quote-lines">
                    {quote.lines.map((l, i) => (
                      <div key={i}>
                        <div>
                          <strong>{l.name}</strong>
                          <small>
                            {l.quantity} thiết bị × {l.days} ngày
                          </small>
                        </div>
                        <span>{money(l.rental)}</span>
                      </div>
                    ))}
                  </div>
                  <div className="summary-line">
                    <span>Tiền thuê</span>
                    <strong>{money(quote.rentalTotal)}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Cọc thiết bị</span>
                    <strong>{money(quote.depositRequired)}</strong>
                  </div>
                  <p className="deposit-note">
                    Cọc được theo dõi riêng và xử lý sau khi trả, kiểm tra thiết bị.
                  </p>
                  <div className="summary-line total">
                    <span>Thuê + cọc</span>
                    <strong>{money(quote.rentalTotal + quote.depositRequired)}</strong>
                  </div>
                  <div className="policy-note">
                    Chính sách mẫu {quote.policyVersion}: mỗi 24 giờ bắt đầu tính một ngày; giữ chỗ{' '}
                    {quote.holdMinutes} phút; chuẩn bị/vệ sinh {quote.bufferMinutes} phút mỗi đầu.
                  </div>
                </>
              ) : null}
              {quoteError && (
                <div role="alert" className="inline-error">
                  {quoteError}
                </div>
              )}
              {insufficient && (
                <div role="alert" className="inline-error">
                  Thiết bị không còn đủ trong khoảng đã chọn. Hãy đổi ngày hoặc giảm số lượng.
                </div>
              )}
              <Button
                type="button"
                className="secondary"
                disabled={quoting || cmd.busy}
                onClick={() => {
                  setAccept(false);
                  setQuoteRevision((v) => v + 1);
                }}
              >
                Cập nhật báo giá
              </Button>
              <label className="checkbox-field">
                <input
                  type="checkbox"
                  checked={accept}
                  onChange={(e) => setAccept(e.target.checked)}
                  required
                />
                <span>
                  Tôi đã đọc và đồng ý báo giá, thời gian giữ chỗ và điều khoản thuê mẫu bên trên.
                </span>
              </label>
              <Button
                busy={cmd.busy}
                type="submit"
                disabled={!accept || !quote || quoting || insufficient}
              >
                Giữ chỗ thiết bị
                <ArrowRight size={18} />
              </Button>
              <div className="secure-note">
                <ShieldCheck size={15} />
                Mở chi tiết đơn sau khi giữ chỗ để xem hướng dẫn thanh toán.
              </div>
            </aside>
          </form>
        </QueryState>
      </main>
    </>
  );
}
