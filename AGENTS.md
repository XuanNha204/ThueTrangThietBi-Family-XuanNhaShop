# Quy ước phát triển XuanNhaShop

- Giữ kiến trúc bài 1–5: React/TypeScript, một Spring Boot JAR, PostgreSQL; B01–B12 và Layered. Đọc `docs/architecture.md` trước khi thay đổi ranh giới module.
- Presentation dùng application ports, không gọi JDBC/repository implementation. Business không import Infrastructure/Presentation. Plugin `rules` không I/O.
- B03 là đầu mối ghi reservation. Lệnh giữ lịch/tiền/audit/outbox cần transaction, khóa DB và operation key, không dùng khóa JVM làm bảo đảm duy nhất.
- Không sửa migration đã áp dụng; thêm migration phiên bản mới. Không sửa báo cáo nguồn bài 1–5 để khớp code.
- Chạy `npm run test:backend` để dùng PostgreSQL test riêng. Không chạy integration test với URL dev hoặc dữ liệu cần giữ.
- Tệp và log `.dev` là cục bộ; không commit mật khẩu, session, dữ liệu khách hoặc database.
- Khi mở rộng tính năng, cập nhật trạng thái yêu cầu và bằng chứng kiểm thử; không đánh dấu đủ 98 yêu cầu khi còn thiếu tiêu chí.
