# XuanNhaShop — Hệ thống cho thuê trang thiết bị

Ứng dụng cho thuê trang thiết bị với React/TypeScript, Spring Boot/JDK17 và PostgreSQL. Giữ kiến trúc Layered, một backend và các module nghiệp vụ B01–B12.

Website: **https://mywebapp.id.vn**.

## Triển khai

Hướng dẫn trong [deploy/README.md](deploy/README.md). Bộ triển khai có profile production, Docker Compose, TLS, DB runtime/migration riêng, bí mật ngoài Git, backup/restore và lịch backup.

VPS đã có Nginx cần dùng cả `compose.yml` và `compose.nginx.yml`: ứng dụng chỉ nghe tại `127.0.0.1:8088`, Nginx kết thúc HTTPS. Cấu hình mẫu: [nginx.mywebapp.conf](deploy/nginx.mywebapp.conf). Không công bố cổng DB/backend.

```bash
git clone https://github.com/XuanNha204/ThueTrangThietBi-Family-XuanNhaShop.git
cd ThueTrangThietBi-Family-XuanNhaShop
bash deploy/prepare.sh
# Nhập cấu hình thật trong deploy/.env và giữ secrets ngoài Git.
cd deploy
docker compose -f compose.yml -f compose.nginx.yml build
docker compose -f compose.yml -f compose.nginx.yml up -d
```

Khởi tạo production tạo một tài khoản chủ cửa hàng và kho, không thêm dữ liệu mẫu. Sau đăng nhập đầu tiên tắt BOOTSTRAP_ENABLED. Thêm danh mục, thiết bị và cấu hình nghiệp vụ thật trước nhận đơn. SePay cần cấu hình ngân hàng/HMAC và webhook của môi trường production.

## Phát triển và kiểm thử

Trên Windows cần Node.js24, JDK17, Maven và PostgreSQL. Chạy `npm.cmd ci`, `npm.cmd run setup`, `npm.cmd run dev`. Bộ kiểm thử backend dùng DB tạm riêng qua `npm.cmd run test:backend`; frontend build bằng `npm.cmd --prefix web-ui run build`.

112 kiểm thử Java đã đạt khi chuẩn bị bản production. Đã kiểm tra HTTPS/cookie Secure, quyền truy cập, bootstrap sạch, tạo danh mục/thiết bị, giữ chỗ và backup/restore trong môi trường riêng. Các quy trình nghiệp vụ và tải thực tế vẫn cần nghiệm thu theo hoạt động của cửa hàng.

## Dữ liệu và tài liệu

Theo yêu cầu chủ dự án, thư mục `Docs`/`docs` không đưa lên repo. Tài liệu bài tập gốc giữ ở workspace riêng. Không commit `.dev`, `TaiLieu.md`, `.env`, secret, database, bằng chứng khách hàng, backup, node_modules hoặc build output. Không sử dụng dữ liệu dev làm số dư kinh doanh thật.
