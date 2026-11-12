# XuanNhaShop — Hệ thống cho thuê trang thiết bị

**Triển khai VPS (12/11/2026):** bộ Docker Compose cho Ubuntu 4 GB và `mywebapp.id.vn` ở [deploy/compose.yml](deploy/compose.yml). Đọc [hướng dẫn triển khai](docs/VPS_DEPLOYMENT.md) và [kết quả rà soát/giới hạn](docs/PRODUCTION_REVIEW.md). Dùng DB sạch, profile prod và secret mới; không đưa dữ liệu `.dev` lên hệ thống thật. Tạo gói source bằng `python deploy/package-release.py`.

Bản phát triển **0.3.0**, hiện thực từng phần kiến trúc của nhóm 9 từ bài 1–5. Có giao diện khách thuê và giao diện vận hành, dữ liệu PostgreSQL thật và luồng thuê xuyên suốt. **Chưa hoàn thành toàn bộ 98 yêu cầu và chưa nghiệm thu production**.

## Chức năng bổ sung 09/11/2026

Đã triển khai chức năng chính cho 17 yêu cầu: kho/tiếp nhận/kiểm kê/thanh lý; chi phí/công nợ/đối soát; bảo hành; xác minh khách/hướng dẫn/phân công; khoản lương/phiếu lương riêng; báo cáo hiệu quả; backup/restore. Xem [phạm vi từng yêu cầu](docs/implementation-17.md), [cách dùng](docs/operations-expansion.md) và [phục hồi](docs/backup-recovery.md).

Dùng `npm.cmd run backup`, `backup:verify -- <folder>`, `backup:restore -- <folder>` hoặc `backup:drill`. Restore luôn tạo DB riêng.

## Mở bản đang chạy

**Checklist và lịch sử:** chủ cửa hàng vào **Kiểm tra & bảo trì → Checklist theo loại thiết bị** để cấu hình; kỹ thuật viên ghi kết quả tại **Thiết bị & kho → Kiểm tra**. Bấm **Lịch sử thiết bị** để xem các mốc và kết quả từng tiêu chí. Xem [hướng dẫn](docs/inspection-checklists.md).

**Điều chỉnh giá:** đăng nhập chủ cửa hàng → **Thiết bị & kho → Bảng giá sản phẩm → Điều chỉnh giá**. Sửa giá thuê/ngày, tiền cọc và lý do. Đơn cũ giữ giá đã chốt; xem [hướng dẫn và giới hạn](docs/product-prices.md).

- Cửa hàng và quản lý: **http://127.0.0.1:5175**
- API: `http://127.0.0.1:8085/api`
- Health: `http://127.0.0.1:8085/actuator/health`
- PostgreSQL dev riêng: `127.0.0.1:54329`, database `xuannhashop_dev`.

Thư mục tạo thực tế: `C:\ProjectALL\KienTrucThietKePhanMem\XuanNhaShop`. Ổ D ghi trong ngữ cảnh phiên này không truy cập được nên dùng thư mục đang chứa các kết quả bài 1–5. Các báo cáo gốc được giữ nguyên.

## Khởi động trên máy phát triển Windows

Cần Node.js (đã kiểm tra với 24.19), JDK 17, Maven 3.9 và PostgreSQL có các chương trình `initdb`, `pg_ctl`, `psql`, `createdb`, `dropdb`. Java, Maven, Node và npm phải có trên PATH. PostgreSQL được tìm mặc định tại `C:/Program Files/PostgreSQL/18/bin`; nếu khác, đặt biến `PG_BIN` tới thư mục `bin` tương ứng.

```powershell
cd C:\ProjectALL\KienTrucThietKePhanMem\XuanNhaShop
npm.cmd ci
npm.cmd run setup
npm.cmd run dev
```

Sau lần setup đầu, dùng `npm.cmd run dev`. Lệnh khởi động backend và Vite ở nền, chỉ nghe trên loopback. Script tạo cụm PostgreSQL riêng trong `.dev/postgres`; không sửa service PostgreSQL khác trên máy. Các cổng 5175, 8085 và 54329 cần dành cho dự án này.

```powershell
npm.cmd run status          # Kiểm tra frontend và backend
npm.cmd run dev:restart     # Build lại Java và khởi động lại backend của dự án
npm.cmd run build           # JAR + build TypeScript/Vite
npm.cmd run stop            # Dừng frontend, backend và cụm PostgreSQL của dự án
```

Vite cập nhật thay đổi frontend khi phát triển. Sau khi sửa Java, chạy `dev:restart`. Script giữ JAR đang chạy tại `.dev/runtime` để Maven có thể build trên Windows. Dừng/khởi động thông thường giữ dữ liệu; không có lệnh reset tự xóa dữ liệu.

## Tài khoản mẫu

Tất cả dùng mật khẩu dev **`XuanNhaDev!2026`**. Tài khoản, tên, địa chỉ, thiết bị và giao dịch mẫu đều phục vụ kiểm thử, không dùng dữ liệu khách thật.

| Tên đăng nhập | Vai trò |
|---|---|
| `owner` | Chủ cửa hàng, quản trị và phê duyệt |
| `accountant` | Kế toán: khoản thu, hoàn cọc, lương, báo cáo |
| `stock` | Kho: thiết bị, đơn thuê, chuẩn bị, nhập CSV staging |
| `delivery` | Giao nhận, biên bản giao/trả |
| `technician` | Kiểm tra an toàn, sự cố và bảo trì |
| `hr` | Hồ sơ nhân viên, dữ liệu làm việc; không xem mức lương |
| `customer` | Khách hàng: đặt thuê và xem hồ sơ của mình |

Đăng ký công khai luôn tạo vai trò khách hàng. Chủ cửa hàng tạo tài khoản nhân viên riêng và đổi vai trò tại **Tài khoản & chính sách**, quyền mới áp dụng ngay cho phiên hiện hành. Backend kiểm tra vai trò và phạm vi đối tượng. Chưa có phân quyền từng action/scope hoặc đa kho. Người lập yêu cầu hoàn cọc hoặc bảng lương không được tự duyệt.

## Luồng dùng thử

1. Khách mở cửa hàng, tìm thiết bị, chọn thời gian và tạo đơn. Lịch khả dụng được kiểm tra lại trong transaction khi giữ chỗ.
2. Kho/chủ cửa hàng mở đơn trong quản lý. Kế toán hoặc chủ cửa hàng ghi nhận cọc **sau khi đối chiếu tiền thực nhận**, rồi xác nhận đơn trước hạn giữ chỗ.
3. Trong khoảng chuẩn bị (30 phút trước giờ nhận), chuyển sang chuẩn bị và lập biên bản bàn giao các mã thiết bị thực tế.
4. Ghi nhận tiền thuê; thu hồi toàn bộ hoặc từng phần. Thiết bị trả về chuyển sang chờ kiểm tra.
5. Kỹ thuật viên/chủ cửa hàng kiểm tra ba tiêu chí nền và toàn bộ tiêu chí bổ sung theo loại. Không đạt thì chuyển bảo trì, chặn cấp lịch mới; đạt thì sẵn sàng.
6. Kế toán/chủ cửa hàng lập yêu cầu hoàn cọc; đăng nhập bằng **người khác có quyền** để duyệt, ghi nhận khoản hoàn thủ công đã đối chiếu. Tất toán sau khi trả đủ, thu đủ tiền thuê và xử lý hết cọc.

Chính sách **DEV-1** để chạy thử: mỗi 24 giờ bắt đầu tính một ngày; giữ chỗ 20 phút; buffer chuẩn bị/vệ sinh 30 phút mỗi đầu. Báo giá được chụp lại theo đơn. Đây không phải chính sách kinh doanh đã được cơ sở phê duyệt. Giờ nhập trên giao diện theo Việt Nam (UTC+7), API lưu thời điểm UTC.

## Chức năng bổ sung trong dev 0.2

- Đề nghị gia hạn, đổi lịch/địa chỉ hoặc đổi mã cùng loại trước bàn giao; báo giá theo đơn giá cũ, xác nhận khách, duyệt kiểm tra lại lịch, lưu mọi phiên bản thỏa thuận.
- Trang **Lịch cần chú ý** cho quá hạn, báo mất chờ duyệt và đơn sau bị ảnh hưởng; nhắc trả trong cửa sổ 24 giờ mẫu qua thông báo/outbox dev.
- Phương án mất/hư hỏng có người khác duyệt, khoản bồi thường riêng, kiểm tra kỹ thuật trước duyệt hư hỏng. Mất được xử lý bằng nghĩa vụ riêng, không ghi nhận là đã trả.
- Khách tự cập nhật liên hệ tại `/my-profile`, giữ nguyên địa chỉ trong đơn cũ.

Chi tiết và giới hạn tại [docs/release-0.2.md](docs/release-0.2.md).

Dev 0.3 bổ sung **QR và ghi sổ SePay theo nghĩa vụ**, **tạo/đổi vai trò nhân viên** và **lịch bảo trì dự kiến chặn từng mã thiết bị**. Khách lấy QR ở hồ sơ đơn; kế toán theo dõi **Thu chi & tiền cọc → Webhook SePay**; kỹ thuật quản lý kế hoạch tại **Kiểm tra & bảo trì**. Chi tiết tại [docs/release-0.3.md](docs/release-0.3.md), cấu hình SePay tại [docs/sepay-setup.md](docs/sepay-setup.md).

## Kiểm thử

```powershell
npm.cmd run test:backend
cd web-ui
npx.cmd playwright install chromium   # Lần đầu trên máy mới
cd ..
npm.cmd run test:e2e                  # Cần npm run dev đang chạy
```

`test:backend` tạo một database PostgreSQL tạm `xuannhashop_test_<timestamp>`, chạy test rồi xóa đúng database tạm đó; không dùng database dev. Không chạy trực tiếp Maven test với URL của dữ liệu cần giữ: các integration test chủ động tạo fixture và trigger gây lỗi trong database test.

Playwright chạy trên bản dev, tạo các hồ sơ có ghi `E2E DEV`, một đơn hủy, một vòng thuê hoàn tất và batch CSV không hợp lệ để kiểm tra. Những bản ghi này được giữ để truy vết. Không dùng môi trường có dữ liệu thật cho bộ test này.

Bộ kiểm tra gồm **107 test Java và 24 kịch bản Chromium**, bao phủ các luồng mới của 17 yêu cầu và hồi quy giá/checklist/SePay/quyền/vòng thuê. Có diễn tập khôi phục DB+tệp và khởi động API. Kiểm thử backend dùng DB tạm; UI dùng fixture E2E DEV riêng (có khoản chi mô phỏng, không chuyển ngân hàng). Chi tiết bằng chứng và giới hạn ở [docs/verification.md](docs/verification.md).

## Mã nguồn và liên kết kiến trúc

| Vị trí | Nội dung |
|---|---|
| `web-ui/src/features` | React/TypeScript: khách hàng và vận hành |
| `backend/src/main/java/vn/xuannhashop/presentation` | Controller, DTO và scheduler |
| `backend/src/main/java/vn/xuannhashop/business` | B01–B12: application service, domain và repository ports |
| `backend/src/main/java/vn/xuannhashop/infrastructure` | JDBC/PostgreSQL, bảo mật, tệp riêng tư, adapter dev |
| `backend/src/main/java/vn/xuannhashop/business/rules` và `business/*/plugins` | Hợp đồng và plug-in giá, checklist, lương biên dịch cùng JAR |
| `backend/src/main/resources/db/migration` | Schema có phiên bản bằng Flyway |
| `docs` | Kiến trúc, tiến độ 98 yêu cầu, 18 ràng buộc, 50 kịch bản và kế hoạch tiếp theo |

Xem [docs/architecture.md](docs/architecture.md), [docs/requirements.md](docs/requirements.md) và [docs/DEV_STATUS.md](docs/DEV_STATUS.md). CSV truy vết có UTF-8 BOM để mở trong Excel. Tài liệu gốc bài 1–5 là nguồn thiết kế; các quy tắc demo được ghi riêng, không sửa ngầm nguồn thiết kế.

## Dữ liệu cục bộ và giới hạn

`.dev/config.json` giữ mật khẩu database sinh ngẫu nhiên; `.dev/postgres` giữ DB; `.dev/private-files` giữ bằng chứng; `.dev/notifications.ndjson` ghi kết quả adapter thông báo dev. Toàn bộ `.dev`, build, dependencies và test artifacts được loại khỏi Git. Không đưa thư mục `.dev` lên kho công khai.

Đã có đối chiếu/ghi sổ SePay khi khớp tài khoản, mã, số tiền và thời hạn; sai lệch chờ kế toán xử lý. Chưa có API đối soát khi thiếu webhook, cổng merchant, email/SMS hoặc chuyển/hoàn tiền ngân hàng. `.dev/sepay.json` giữ HMAC riêng; tài khoản nhận và tùy chọn ghi sổ lưu trong DB. HTTPS hiện qua tunnel tạm. Adapter thông báo chỉ ghi tệp cục bộ. Chưa có production, TLS production, backup/PITR, failover hoặc số đo hiệu năng/RTO/RPO. Phần còn thiếu ở `docs/DEV_STATUS.md`.
