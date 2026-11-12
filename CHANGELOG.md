# Lịch sử phát triển

## Chuẩn bị Ubuntu VPS 4 GB / mywebapp.id.vn — 12/11/2026

- Thêm prod profile, guard chống seed/dev/DB dev/cookie không an toàn, bootstrap owner/kho sạch, adapter spool nội bộ; V13 giới hạn login/register qua DB.
- OWNER thêm loại thiết bị qua port/API/UI, validation và idempotency/audit; siết register, tắt sourcemap và PDF prod chưa được quét; cấu hình thread/RAM.
- Docker Compose, Caddy TLS và CSP đúng host QR, DB owner/runtime tách riêng, private volumes/secrets, backup/restore cách ly/timer, gói ZIP không dữ liệu dev/bí mật.
- 112 test Java đạt; build, smoke prod JAR/HTTPS Linux containers và khôi phục DB+tệp đã chạy. Các giới hạn cần nghiệm thu trên VPS tại [PRODUCTION_REVIEW.md](docs/PRODUCTION_REVIEW.md).

## Bổ sung chức năng chính 17 yêu cầu — 09/11/2026

- V9–V12: đa kho, nhận lô/kiểm kê/chuyển/thanh lý; chi phí/công nợ/đối soát; bảo hành, chính sách xác minh khách, hướng dẫn có phiên bản và phân công; khoản lương bổ sung/phiếu riêng; báo cáo hiệu quả tháng.
- Lệnh có transaction, audit, chống lặp và kiểm tra quyền; quyết định tài sản/tiền/lương tách người lập/duyệt. Chặn mã còn lịch thuê/bảo trì/bảo hành, kiểm tra lại sau chuyển hoặc bảo hành.
- Giao diện bổ sung theo vai trò; giữ quy trình SePay và polling không làm mất vị trí cuộn. Hướng dẫn tại [operations-expansion.md](docs/operations-expansion.md).
- Backup DB+tệp, checksum/restore vào DB riêng và diễn tập ứng dụng. 107 test Java, 24 kịch bản Chromium đạt; [verification.md](docs/verification.md). Giới hạn từng yêu cầu: [implementation-17.md](docs/implementation-17.md); không đánh dấu hoàn tất toàn 98 yêu cầu.

## Checklist theo loại và lịch sử thiết bị — 07/11/2026

- V8 thêm phiên bản checklist, tiêu chí bắt buộc/theo dõi và snapshot kết quả; giữ ba tiêu chí nền và dữ liệu inspection cũ. OWNER cấu hình có căn cứ, version/idempotency/audit.
- Kiểm tra và kết thúc bảo trì dùng cùng checklist; từ chối mẫu lỗi thời, rollback nguyên tử. Tiêu chí bắt buộc không đạt giữ thiết bị MAINTENANCE.
- B11 hợp nhất lịch sử thuê/giao/trả/sự cố/kiểm tra/bảo trì theo mã, phân trang cursor và giới hạn vai trò. Giao diện desktop/mobile, lịch sử phiên bản và kết quả cũ.
- Thêm 11 test Java (tổng 89 đạt), 2 kịch bản trình duyệt; chi tiết kết quả tại [verification.md](docs/verification.md). Giữ nguyên báo cáo nguồn bài 1–5; phần còn thiếu tại [DEV_STATUS.md](docs/DEV_STATUS.md).

## Sửa thời gian đối chiếu MBBank — 07/11/2026

- Chấp nhận timestamp MBBank ở giây 00 trong cùng phút tạo QR; trước đó kiểm tra chênh 1 giây khiến một webhook thật bị REVIEW/INVALID_TIME dù đúng mã và tiền.
- Giữ kiểm tra phút trước, timestamp chính xác, tương lai, mã/tài khoản/số tiền/hạn và chống trùng. Không sửa payload gốc; xử lý lại receipt qua API retry có audit.
- 2 test hồi quy mới; tổng 78 test backend đạt. Không thay giao diện hoặc schema.

## Bổ sung giá sản phẩm — 07/11/2026

- OWNER chỉnh giá thuê/ngày và cọc; lịch sử trước/sau, lý do, actor/time, optimistic version và chống lặp. Migration V7, giữ V1–V6.
- Khóa giá khi tạo đơn để tổng/dòng nhất quán; giữ snapshot đơn/gia hạn; khách cần xem và đồng ý lại khi báo giá đổi.
- Bảng giá và lịch sử trên Thiết bị & kho; kho/quản lý chỉ xem. 8 test tích hợp mới, 2 kịch bản trình duyệt mới. Chi tiết [product-prices.md](docs/product-prices.md).

## 0.3.0 — 06/11/2026

- SePay HMAC/inbox, tài khoản nhận có version, mã thanh toán và QR theo nghĩa vụ; tự ghi sổ có điều kiện, đối soát sai lệch và cọc đến muộn, chống lặp/ghi đè và rollback.
- OWNER tạo tài khoản nhân viên BCrypt và đổi vai trò có căn cứ; quyền cập nhật ngay cho phiên hiện hành, bảo vệ tài khoản chủ cửa hàng và tài khoản khách.
- Kế hoạch bảo trì gắn từng mã; B03 giữ khoảng chặn lịch, tranh cùng khóa DB với đặt thuê, quá giờ vẫn chặn; kết thúc có inspection.
- Migration V5/V6, 68 test Java và 13 kịch bản Chromium; axe desktop/mobile, giữ nguyên báo cáo nguồn bài 1–5 và cập nhật truy vết.
- Chưa chuyển/hoàn tiền ngân hàng, API đối soát chủ động hoặc nghiệm thu production. Phần còn lại ở DEV_STATUS.

## 0.2.0 — 06/11/2026

- Đề nghị gia hạn/đổi trước giao, khách chấp thuận, cửa hàng duyệt, kiểm tra version/lịch và giữ các phiên bản thỏa thuận.
- Theo dõi quá hạn, báo mất chờ duyệt và đơn tương lai bị ảnh hưởng; nhắc trả/cảnh báo có dedupe và phạm vi khách/nội bộ.
- Phương án mất/hư hỏng, tách người lập/duyệt, kiểm tra kỹ thuật, khoản bồi thường riêng; LOST không ghi giả đã trả.
- Liên hệ khách tự phục vụ có version, giữ địa chỉ đơn cũ; chuyển hướng đăng nhập phù hợp vai trò.
- Quyền duyệt hoàn/sổ tiền giới hạn chủ cửa hàng và kế toán theo C08.
- Migration V3 giữ V1/V2 đã áp dụng; 35 kiểm thử Java và 10 kịch bản trình duyệt.
- Cập nhật truy vết 98 yêu cầu, 18 ràng buộc và 50 kịch bản, ghi rõ giới hạn còn lại.

## 0.1.0 — 06/11/2026

- Tạo dự án dev theo Layered/B01–B12, một Spring Boot JAR và React/TypeScript/PostgreSQL.
- Luồng thuê chính, giao/trả từng phần, kiểm tra an toàn, cọc/hoàn/tất toán, lương mẫu, hỗ trợ và import CSV khách hàng.
- Sửa thống nhất tên thương hiệu XuanNha/XuanNhaShop, cập nhật kho dev bằng migration V2.
