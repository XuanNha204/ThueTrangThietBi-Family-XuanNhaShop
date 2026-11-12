import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  Check,
  ChevronRight,
  House,
  Leaf,
  PackageCheck,
  Search,
  ShieldCheck,
  SlidersHorizontal,
} from 'lucide-react';
import type { Product } from '../../shared/types';
import { date, localInput, shopDate, money, request, clearCsrf } from '../../shared/api';
import { useApp, useQuery } from '../../shared/state';
import { Button, Empty, QueryState } from '../../shared/ui';
import { DeviceArt } from '../../shared/DeviceArt';
export function Brand() {
  return (
    <Link to="/" className="brand" aria-label="XuanNhaShop — Trang chủ">
      <span className="brand-mark">
        <House size={23} />
      </span>
      <span>
        XuanNha<span className="brand-light">Shop</span>
        <small>THUÊ THIẾT BỊ GIA ĐÌNH</small>
      </span>
    </Link>
  );
}
export function CustomerHeader() {
  const { user, setUser, toast } = useApp();
  const navigate = useNavigate();
  async function logout() {
    try {
      await request('/auth/logout', { method: 'POST' });
      clearCsrf();
      setUser(null);
      navigate('/login', { replace: true, state: null });
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }
  return (
    <header className="store-header">
      <div className="store-container store-nav">
        <Brand />
        <nav>
          <a href="/#catalog">Thiết bị cho thuê</a>
          <Link to={user?.role === 'CUSTOMER' ? '/my-rentals' : '/app'}>
            {user?.role === 'CUSTOMER' ? 'Đơn thuê của tôi' : 'Khu vực quản lý'}
          </Link>
          <a href="/#how-it-works" className="hide-mobile">
            Cách thuê
          </a>
        </nav>
        {user ? (
          <details className="account-menu">
            <summary className="button small">
              {user.displayName.split(' ').slice(-2).join(' ')}
              <ChevronRight size={15} />
            </summary>
            <div>
              <Link to={user.role === 'CUSTOMER' ? '/my-rentals' : '/app'}>
                {user.role === 'CUSTOMER' ? 'Đơn thuê của tôi' : 'Không gian quản lý'}
              </Link>
              {user.role === 'CUSTOMER' && <Link to="/my-support">Yêu cầu hỗ trợ</Link>}
              {user.role === 'CUSTOMER' && <Link to="/my-profile">Thông tin liên hệ</Link>}
              <button onClick={logout}>Đăng xuất</button>
            </div>
          </details>
        ) : (
          <Link className="button small" to="/login">
            Đăng nhập
            <ArrowUpRight size={16} />
          </Link>
        )}
      </div>
    </header>
  );
}
export function Catalog() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? ''),
    [category, setCategory] = useState('Tất cả'),
    [sort, setSort] = useState('default');
  const start = new Date();
  start.setDate(start.getDate() + 1);
  start.setHours(9, 0, 0, 0);
  const finish = new Date(start.getTime() + 86400000);
  const [from, setFrom] = useState(localInput(start)),
    [to, setTo] = useState(localInput(finish));
  const valid = from && to && shopDate(to) > shopDate(from);
  const query = useQuery<Product[]>(
    '/catalog' +
      (valid
        ? `?from=${encodeURIComponent(shopDate(from).toISOString())}&to=${encodeURIComponent(shopDate(to).toISOString())}`
        : ''),
  );
  const categories = ['Tất cả', ...new Set((query.data ?? []).map((p) => p.category))];
  const products = useMemo(() => {
    const q = search.toLocaleLowerCase('vi');
    let rows = (query.data ?? []).filter(
      (p) =>
        (category === 'Tất cả' || p.category === category) &&
        (p.name + ' ' + p.description + ' ' + p.code).toLocaleLowerCase('vi').includes(q),
    );
    if (sort === 'asc') rows = [...rows].sort((a, b) => a.dailyRate - b.dailyRate);
    if (sort === 'desc') rows = [...rows].sort((a, b) => b.dailyRate - a.dailyRate);
    return rows;
  }, [query.data, search, category, sort]);
  return (
    <>
      <CustomerHeader />
      <main>
        <section className="store-hero store-container">
          <div className="hero-copy">
            <span className="pill">
              <Leaf size={14} /> Sở hữu ít hơn, tận hưởng nhiều hơn
            </span>
            <h1>
              Nhà thêm tiện nghi.
              <br />
              <span>Bạn thêm thảnh thơi.</span>
            </h1>
            <p>
              Thiết bị bạn cần, vào đúng lúc bạn cần.
              <br />
              Từ dọn dẹp tổ ấm đến những buổi sum vầy, thuê gọn gàng cùng XuanNhaShop.
            </p>
            <a className="button" href="#catalog">
              Khám phá thiết bị
              <ArrowRight size={18} />
            </a>
            <div className="hero-trust">
              <span>
                <ShieldCheck size={17} /> Kiểm tra trước khi giao
              </span>
              <span>
                <PackageCheck size={17} /> Chi phí rõ ràng
              </span>
            </div>
          </div>
          <div className="hero-art">
            <div className="art-circle" />
            <span className="art-note note-top">
              <i />
              Thảnh thơi hơn mỗi ngày
            </span>
            <DeviceArt kind="purifier" className="hero-purifier" />
            <DeviceArt kind="cleaner" className="hero-cleaner" />
            <DeviceArt kind="projector" className="hero-projector" />
            <span className="art-note note-bottom">
              <Check size={17} /> Vừa đủ cho nhu cầu của bạn
            </span>
            <span className="hero-art-label">XuanNha · THUÊ VỪA ĐỦ</span>
          </div>
        </section>
        <div className="benefit-strip">
          <div className="store-container">
            <span>
              <PackageCheck />
              Thiết bị có mã riêng, lịch thuê rõ ràng
            </span>
            <span>
              <ShieldCheck />
              Kiểm tra an toàn trước khi sử dụng
            </span>
            <span>
              <CalendarDays />
              Chọn ngày, xem khả năng phục vụ
            </span>
          </div>
        </div>
        <section className="store-container catalog-section" id="catalog">
          <div className="section-heading">
            <div className="eyebrow">MỘT CHÚT TIỆN NGHI CHO NHÀ BẠN</div>
            <h2>Hôm nay, bạn cần gì?</h2>
            <p>Chọn thiết bị phù hợp. Phần còn lại, để XuanNha cùng bạn chuẩn bị.</p>
          </div>
          <div className="rental-search">
            <label className="search-box">
              <Search size={19} />
              <input
                type="search"
                placeholder="Tìm thiết bị cho ngôi nhà của bạn…"
                aria-label="Tìm thiết bị"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
            <label className="date-field">
              <span>Ngày nhận</span>
              <input
                type="datetime-local"
                aria-label="Ngày nhận"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </label>
            <label className="date-field">
              <span>Ngày trả dự kiến</span>
              <input
                type="datetime-local"
                aria-label="Ngày trả dự kiến"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </label>
          </div>
          {!valid && (
            <div className="inline-error">
              Chọn ngày trả sau ngày nhận để xem số lượng có thể phục vụ.
            </div>
          )}
          <div className="catalog-toolbar">
            <div className="category-tabs">
              {categories.map((c) => (
                <button
                  key={c}
                  aria-pressed={category === c}
                  className={category === c ? 'active' : ''}
                  onClick={() => setCategory(c)}
                >
                  {c}
                </button>
              ))}
            </div>
            <label className="sort">
              <SlidersHorizontal size={15} />
              <select
                aria-label="Sắp xếp thiết bị"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                <option value="default">Đề xuất</option>
                <option value="asc">Giá thấp đến cao</option>
                <option value="desc">Giá cao đến thấp</option>
              </select>
            </label>
          </div>
          <QueryState loading={query.loading} error={query.error}>
            {products.length ? (
              <div className="product-grid">
                {products.map((p, index) => (
                  <article className="product-card" key={p.id}>
                    <div className={`product-visual tone-${index % 4}`}>
                      <span className={`availability ${p.available === 0 ? 'unavailable' : ''}`}>
                        <i />
                        {p.available === undefined
                          ? 'Chọn ngày để xem lịch'
                          : p.available > 0
                            ? `Còn ${p.available} thiết bị`
                            : 'Hết trong khoảng này'}
                      </span>
                      <DeviceArt kind={p.icon} />
                      <span className="product-code">{p.code}</span>
                    </div>
                    <div className="product-info">
                      <div className="product-category">{p.category}</div>
                      <h3>{p.name}</h3>
                      <p>{p.description}</p>
                      <div className="product-price">
                        <span>
                          {money(p.dailyRate)}
                          <small> / ngày</small>
                        </span>
                        <Button
                          className="icon-rent"
                          aria-label={`Đặt thuê ${p.name}`}
                          disabled={p.available === 0}
                          onClick={() =>
                            navigate(
                              `/book?type=${p.id}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
                            )
                          }
                        >
                          <ArrowUpRight size={20} />
                        </Button>
                      </div>
                      <div className="product-deposit">Cọc thiết bị {money(p.deposit)}</div>
                      <details className="specifications">
                        <summary>Thông số & phụ kiện</summary>
                        {p.specifications}
                      </details>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <Empty
                title="Chưa tìm thấy thiết bị phù hợp"
                description="Thử từ khóa khác hoặc chọn lại nhóm thiết bị."
              />
            )}
          </QueryState>
        </section>
        <section className="how-section store-container" id="how-it-works">
          <div>
            <div className="eyebrow">ĐƠN GIẢN TỪ LÚC CHỌN ĐẾN KHI TRẢ</div>
            <h2>Thuê gọn trong ba bước.</h2>
          </div>
          <div className="how-grid">
            {[
              [
                '01',
                'Chọn thiết bị & ngày thuê',
                'Xem khả năng phục vụ và báo giá trước khi giữ chỗ.',
              ],
              [
                '02',
                'Xác nhận & nhận thiết bị',
                'Cửa hàng đối soát cọc, chuẩn bị và lập biên bản bàn giao.',
              ],
              [
                '03',
                'Trả & đối chiếu rõ ràng',
                'Kiểm tra thiết bị, xử lý cọc và hoàn tất đơn thuê.',
              ],
            ].map(([n, t, d]) => (
              <div key={n}>
                <span>{n}</span>
                <h3>{t}</h3>
                <p>{d}</p>
              </div>
            ))}
          </div>
        </section>
      </main>
      <footer className="store-footer">
        <div className="store-container">
          <Brand />
          <p>Thuê vừa đủ. Sống tiện nghi.</p>
          <span>Bản dev · Dữ liệu minh họa</span>
          <span>© {new Date().getFullYear()} XuanNhaShop</span>
        </div>
      </footer>
    </>
  );
}
