from pathlib import Path
import json,csv,hashlib,re
root=Path(__file__).resolve().parent.parent
source=root.parent/'BaiSo5/work/inputs.json'
data=json.loads(source.read_text(encoding='utf-8'))
docs=root/'docs'; docs.mkdir(exist_ok=True)
# Each row records a scope and a specific gap; DEV is evidence for this implementation, not acceptance.
raw='''1|P|B02|Danh mục, tìm kiếm, thông số và giá mẫu|Chưa lọc công suất/kích thước/ngân sách theo trường riêng
2|P|B02/B07|Tình trạng từng chiếc, thông số và kiểm tra an toàn|Phụ kiện chưa có sổ lượng và checklist theo loại
3|P|B03|Tra khoảng thuê có buffer, khóa DB khi giữ/cấp lịch|Chưa tự cập nhật lịch bị ảnh hưởng bởi trả muộn/bảo trì tương lai
4|P|B04/B05|Báo giá tiền thuê/cọc trước giữ chỗ|Chưa phí giao/lắp, trả trễ hoặc bồi thường
5|P|B01/B04/B08|Đăng ký và đặt thuê từ xa|Chưa lựa chọn/kết nối thanh toán trực tuyến
6|D|B03/B04|Giữ chỗ 20 phút, cọc đủ mới xác nhận, hết hạn không cấp lại|Chính sách DEV-1 cần được cơ sở xác nhận
7|P|B04/B06|Địa chỉ, giờ thỏa thuận và giao/trả thực tế|Chưa phân công và bảo đảm giao/thu đúng hẹn
8|P|B06/B08|Biên bản từng mã, bằng chứng và sổ cọc|Chưa phụ kiện có cấu trúc/chữ ký số hai bên
9|N|B02/B06|Thông số chỉ hỗ trợ tham khảo|Chưa tài liệu hướng dẫn và xác nhận đã hướng dẫn
10|P|B07/B10|Ticket hỗ trợ và phiếu lỗi thiết bị|Chưa SLA, đổi thiết bị và phương án bồi hoàn
11|N|B03/B10|Có giờ trả trên hồ sơ|Chưa nhắc trả hoặc quy trình trả muộn
12|N|B03/B04|Chưa có endpoint gia hạn|Cần kiểm tra lịch mới, phí và xác nhận thỏa thuận
13|N|B04|Snapshot thỏa thuận ban đầu|Chưa revision khi đổi đơn và xác nhận lại
14|P|B04/B08|Hủy trước giao, giải phóng lịch, giữ nghĩa vụ tiền|Chưa chính sách phí hủy theo từng giai đoạn
15|P|B04/B06/B08|Trả từng phần theo dòng, tiền/cọc toàn đơn không tự mất|Chưa phân bổ phí/hoàn cọc từng phần theo chính sách
16|D|B04/B08|Khách xem thỏa thuận, tiền thuê đã thu và cọc đang giữ của đơn mình|Chưa chứng từ thanh toán đối tác hoặc revision chỉnh sửa
17|N|B01|Có hồ sơ khách khi tạo/đăng ký|Chưa màn hình/API khách tự sửa liên hệ
18|P|Presentation|Web responsive và kiểm thử Chromium 375px/desktop|Chưa kiểm thử ma trận thiết bị/trình duyệt thực tế đầy đủ
19|P|B01/B06/X01|Session, CSRF, quyền đối tượng, tệp riêng tư|Chưa HTTPS, quản lý lưu giữ/consent hoặc rà soát production
20|P|B02/B07|Tiếp nhận chờ nghiệm thu và kiểm tra trước READY|Chưa đề nghị/duyệt mua và chứng từ nguồn nhập
21|P|B02/B03|Theo từng mã, tổng số và khả dụng theo khoảng|Chưa phụ kiện theo lượng, kiểm kê hoặc đa kho
22|P|B02/B06/B08|Mã riêng, biên bản, sổ cọc và audit|Chưa xử lý thất lạc, kiểm kê và phụ kiện
23|P|B05|Pricing plug-in và snapshot DEV-1|Chưa chính sách duyệt/ngày hiệu lực, khuyến mãi/bồi thường
24|P|B08/B11|Sổ tiền phân loại, cọc và CSV theo kỳ|Chưa đầy đủ phụ phí/công nợ và doanh thu kế toán dồn tích
25|N|B08/B11|Chỉ có khoản thuê thực thu|Chưa đủ chi phí/khấu hao để báo cáo lợi nhuận
26|N|B02/B11|Dashboard nhóm thiết bị theo trạng thái|Chưa tỷ lệ khai thác theo thời gian hoặc đề xuất thay thế
27|P|B11|CSV sổ tiền có bộ lọc kỳ, kiểm tra quyền và audit|Chưa đủ báo cáo tồn/bảo trì/lương/công nợ
28|N|B02/X01|Có nhãn trạng thái DISPOSED trong schema|Chưa luồng đề nghị/duyệt thanh lý hoặc giá trị thu hồi
29|N|Deployment|Cấu trúc module để tiếp tục mở rộng|Chưa đa điểm phục vụ hoặc kiểm chứng tải tăng
30|P|B04/B06|Danh sách và lịch thuê 7 ngày|Chưa hàng đợi quá hạn/phân công nhân lực
31|N|B03/B07|Chặn bàn giao nếu tình trạng vật lý không phù hợp|Chưa xử lý đầy đủ lịch xung đột/đổi thiết bị/xác nhận mới
32|N|B04/B10|Có thể ghi ticket chung|Chưa quy trình mất/không trả và phê duyệt nghĩa vụ
33|P|X01/B10|Audit hủy và nội dung phiếu lỗi|Chưa nguyên nhân hủy/giao trễ có cấu trúc và phân tích
34|U|Runtime|Khóa DB, Hikari pool và giới hạn dữ liệu|Chưa đo 100 đồng thời, p95 3s/5s theo C13
35|U|Runtime|Health, readiness và log local|Chưa đo 99,5% hoặc triển khai hạ tầng dự phòng
36|P|B02|Thông số/giá/lịch để tư vấn|Chưa dữ liệu điều kiện sử dụng và công cụ tư vấn theo nhu cầu
37|N|B01|Kiểm tra định dạng đăng ký|Chưa xác minh danh tính/điều kiện thuê theo chính sách
38|P|B03/B04|Lịch được khóa, thời gian và địa chỉ trước xác nhận|Chưa xác minh điều kiện tiếp cận/giao lắp
39|P|B04/B05|Thỏa thuận DEV-1 nhất quán trong báo giá/đơn|Chưa điều khoản gia hạn/trả trễ đã phê duyệt
40|P|B04|Điều khoản snapshot và khách chấp nhận trước giữ chỗ|Chưa hợp đồng đầy đủ, chữ ký và quy trình duyệt pháp lý
41|P|B04/B08|Trạng thái đơn, tiền thiếu và yêu cầu duyệt hoàn|Chưa hàng đợi chung dữ liệu thiếu/phê duyệt toàn nghiệp vụ
42|P|B02|Tiếp nhận loại và mã riêng ở AWAIT_ACCEPTANCE|Chưa số sê-ri, giá mua, lô/chứng từ/nhà cung cấp
43|P|B04/B06|Danh sách từng mã phải giao của đơn|Chưa phụ kiện và phân công chuẩn bị có cấu trúc
44|P|B03/B04|Buffer 30 phút mỗi đầu, guard giờ chuẩn bị|Chưa buffer theo từng loại và lịch nhân lực
45|P|B02/B03/B07|MAINTENANCE/không đạt bị chặn cấp lịch mới|Chưa xử lý tự động toàn bộ lịch tương lai bị ảnh hưởng
46|P|B06|Chọn từng mã giao/trả và ghi tình trạng|Chưa đối chiếu số lượng phụ kiện
47|N|B02|Một kho mẫu|Chưa chuyển kho, điểm đi/đến và trách nhiệm giao
48|N|B02|Danh sách tài sản để tra cứu|Chưa phiên kiểm kê và duyệt chênh lệch
49|N|B06|Có thể xem lịch đơn thuê|Chưa phân công giao nhận/lộ trình theo nhân viên
50|P|B01/B04/B06|Liên hệ khách, địa chỉ và giờ nhận/trả|Chưa điều kiện tiếp cận/lắp đặt và khung giao được xác nhận
51|P|B06|Mã thiết bị trong biên bản|Chưa phụ kiện/yêu cầu lắp đặt theo điểm giao
52|P|B06|Thời điểm, người thực hiện và tên người xác nhận biên bản|Chưa chữ ký/xác thực độc lập của hai bên
53|N|B04/B10|Outbox sự kiện tạo/xác nhận/hủy|Chưa sửa lịch/địa chỉ hoặc thông báo thay đổi giao lắp
54|P|B07|Checklist an toàn/vệ sinh/đủ phụ kiện cho thiết bị mới|Chưa checklist khác nhau theo loại và nghiệm thu mua hàng
55|P|B07|Inspection plug-in và lịch sử kết quả|Chưa kiểm tra bắt buộc có thời hạn trước từng bàn giao
56|P|B07|Lịch sử kiểm tra, phiếu kỹ thuật và thuê có thể tra riêng|Chưa timeline hợp nhất, lịch bảo dưỡng/chi phí
57|D|B02/B07|Không đạt chuyển MAINTENANCE, kiểm tra đạt mới READY|Chưa workflow bảo hành/đối tác ngoài
58|N|B07/B08|Ghi nhận GOOD/DAMAGED khi trả|Chưa đánh giá hao mòn, định giá và duyệt bồi thường
59|P|B03/B07|Kỹ thuật xem lịch và kiểm tra thiết bị|Chưa ưu tiên công việc tự động theo lịch sắp giao
60|P|B04/B10|Ticket gắn đơn và tra thỏa thuận/hồ sơ đơn|Chưa timeline phản hồi và diễn biến hỗ trợ chi tiết
61|P|B06/B08/B10|Biên bản, bằng chứng và sổ tiền có quyền tra cứu|Chưa luồng tranh chấp/điều tra và lưu giữ được phê duyệt
62|P|B10|Ticket mở/đã xử lý, nội dung kết quả|Chưa hẹn liên hệ lại, phân công và chuyển bộ phận
63|D|B08|Đối chiếu thuê phải thu/đã thu và cọc giữ/hoàn theo đơn|Chưa phụ phí hoặc các loại nghĩa vụ chưa triển khai
64|P|B08/X01|Ghi căn cứ khoản thu, duyệt hoàn khác người lập|Chưa chứng từ chuẩn, phụ phí và bồi thường
65|P|B04/B08|Trạng thái chờ tất toán và số còn phải nhận|Chưa cảnh báo hạn thanh toán theo chính sách
66|N|B08|Sổ khoản thu/hoàn thực nhận được ghi tay|Chưa ShiftReconciliation và đối chiếu tiền thực tế theo ca/ngày
67|N|B08|Có loại chi lương|Chưa Expense mua/bảo trì/vệ sinh/vận chuyển và phân bổ
68|N|B08|Chưa có supplier/payable|Cần chứng từ nguồn mua/dịch vụ và khoản phải trả riêng
69|P|B11|CSV giao dịch theo kỳ|Chưa bộ báo cáo kế toán đầy đủ và đối chiếu tổng hợp
70|P|B09|Giờ đã duyệt nhân mức mẫu, giữ snapshot kỳ lương|Chưa chính sách lương đầy đủ đã phê duyệt
71|N|B09|Chỉ một dòng lương giờ|Chưa phụ cấp/thưởng/tạm ứng/điều chỉnh
72|P|B08/B09|Theo dõi DRAFT/APPROVED/PAID và chi một lần|Chưa chi từng phần hoặc dư phải trả từng nhân viên
73|P|X02|Validation đầu vào, guard nghiệp vụ, CSV từng dòng|Chưa quy tắc cho các chức năng chưa triển khai
74|P|B09|Hồ sơ nhân viên mẫu và bộ phận|Chưa CRUD hồ sơ, vị trí, hợp đồng và trạng thái làm việc
75|P|B09|Ghi giờ theo ngày, người khác duyệt|Chưa ca/nghỉ/ngày công/OT đầy đủ
76|P|B09/X01|Lập/duyệt khác người, snapshot, khóa công sau chốt|Chưa quy trình điều chỉnh sau duyệt
77|N|B09|Chưa trang lương cá nhân|Cần quyền theo nhân viên và yêu cầu đối chiếu sai lệch
78|N|B02/B08|Tiếp nhận đơn chiếc tối thiểu|Chưa lô hàng/số lượng/giá trị/chứng từ và nhà cung cấp
79|N|B02/B07|Có phiếu kỹ thuật chung|Chưa hồ sơ bảo hành và thời hạn/đối tác/kết quả
80|P|B08|Khoản ghi sổ nội bộ có loại, operation key và audit|Chưa trạng thái/callback/đối soát cổng thanh toán thật
81|N|R03/B08|Không gửi giao dịch ra bên ngoài trong dev|Chưa tích hợp an toàn, chữ ký và xử lý lệch đối soát
82|P|B01|Vai trò cố định, khóa tài khoản vô hiệu phiên hiện hành|Chưa cấp/đổi action-scope và quyền đa cơ sở
83|P|X01|Audit đơn, thiết bị, tiền, lương và nhập dữ liệu|Chưa audit thay giá hoặc snapshot before/after đầy đủ
84|P|Runtime/X01|Health/readiness, metrics OWNER và log lỗi có mã|Chưa cảnh báo bên ngoài và tương quan giao dịch toàn hệ
85|N|Deployment|Dữ liệu dev lưu bền qua restart bình thường|Chưa backup/PITR/restore hoặc hỗ trợ offline
86|P|K01/B05/B07/B09|Plug-in biên dịch và kiểm thử trước build|Chưa quản trị chính sách/danh mục ngày hiệu lực và rollout
87|P|B12|CSV khách hàng staging/đối chiếu/duyệt/commit nguyên tử|Chưa thiết bị/đơn mở/cọc/công nợ và cân đối số dư
88|P|B11|CSV sổ tiền dùng được bên ngoài|Chưa export đầy đủ nghiệp vụ để chuyển hệ thống
89|P|B03/B04/B08|Guard vòng thuê và operation key chống xử lý trùng|Chưa gia hạn/đổi đơn/mất thiết bị và mọi trạng thái đối tác
90|P|Docs|Giữ baseline yêu cầu, nguồn kiến trúc và bảng tiến độ|Chưa đặc tả thực thi toàn bộ ngoại lệ của 98 yêu cầu
91|P|Docs|SHA-256 nguồn và trạng thái triển khai riêng|Chưa workflow duyệt/thay đổi yêu cầu trên sản phẩm
92|P|K01/B05|Ba contract/plug-in tách biệt và snapshot|Chưa API/UI đổi chính sách và các formula mở rộng
93|P|R03/B10|Port thông báo và adapter local tách khỏi nghiệp vụ|Chưa payment/email/SMS thật và timeout/circuit/đối soát đầy đủ
94|P|Dev tooling|Build, database test tách biệt, browser test, migration version|Chưa CI/CD, staging production, rollback migration và restore
95|D|Tests|Unit, kiến trúc, tích hợp PostgreSQL và E2E có expected assertions|Mới kiểm chứng phạm vi dev; không bao phủ toàn 98 yêu cầu/50 kịch bản
96|P|DevSeed/Tests|Fixture tổng hợp, tệp riêng tư, không dùng dữ liệu khách thật|Chưa đủ tải/biên mọi nghiệp vụ và chính sách dữ liệu test production
97|P|B04/Docs|Thỏa thuận DEV-1 có snapshot và chấp nhận|Chưa rà soát/phê duyệt điều khoản bồi thường/tranh chấp
98|P|B04/B06/B08|Tra hồ sơ/thỏa thuận/tiền/tệp, không xóa qua UI|Chưa thời hạn được phê duyệt, legal hold, archive và restore'''
status={'P':'MỘT_PHẦN','D':'CÓ_TRONG_DEV','N':'CHƯA_TRIỂN_KHAI','U':'CHƯA_KIỂM_CHỨNG'}
progress={}
for line in raw.splitlines():
    n,st,module,current,gap=line.split('|');progress[int(n)]={'status':status[st],'module':module,'current':current,'gap':gap}

# Dev 0.2: keep the approved source text; update implementation evidence only.
updates = {11: ('P', 'B03/B10', 'Nhắc trước giờ trả trong cửa sổ 24 giờ mẫu; thông báo quá hạn và danh sách cần chú ý', 'Chỉ thông báo trong sản phẩm/outbox dev; chưa email/SMS thật hoặc phí trả muộn'), 12: ('D', 'B03/B04', 'Đề nghị gia hạn trước hạn, báo giá theo đơn giá cũ, khách chấp thuận, cửa hàng duyệt kiểm tra lại lịch', 'Chưa gia hạn hồi tố sau hạn hoặc công thức thương mại khác DEV-1'), 13: ('P', 'B03/B04', 'Đổi lịch/địa chỉ/thiết bị cùng loại trước bàn giao; snapshot và xác nhận hai phía, kiểm tra phiên bản', 'Chưa đổi khác loại/số lượng hoặc đổi thiết bị sau khi đã giao'), 15: ('P', 'B04/B06/B08', 'Trả từng phần; xử lý nghĩa vụ thiết bị mất được duyệt riêng, theo dõi thuê/cọc/bồi thường', 'Chưa phân bổ cọc/hoàn tiền thuê từng phần hoặc tự cấn trừ cọc'), 16: ('D', 'B04/B08', 'Khách xem mọi phiên bản thỏa thuận, chấp thuận thay đổi, tiền thuê/cọc/bồi thường của đơn mình', 'Chưa chứng từ/chữ ký số hoặc giao dịch cổng thanh toán'), 17: ('D', 'B01', 'Khách sửa tên, điện thoại, email và địa chỉ của mình; optimistic version, audit, không sửa địa chỉ đơn cũ', 'Chưa xác minh số điện thoại/email thực tế'), 30: ('P', 'B04/B06', 'Danh sách/lịch thuê và hàng đợi quá hạn/lịch bị ảnh hưởng có mã thiết bị', 'Chưa phân công nhân lực/lộ trình'), 31: ('P', 'B03/B04/B07', 'Chặn lịch mới khi trả muộn/báo mất chờ duyệt, cảnh báo đơn sau; đổi lịch/mã trước giao có xác nhận', 'Chưa tự đề xuất thay thế khác loại/đa kho hoặc giải quyết sau bàn giao'), 32: ('P', 'B04/B08', 'Lập phương án mất/không thể thu hồi, người khác duyệt, ngừng khai thác và xử lý nghĩa vụ; không ghi giả đã trả', 'Chưa workflow liên hệ nhiều lần, truy tìm, bảo hiểm hoặc hoàn tiền khi tìm lại sau xử lý'), 40: ('P', 'B04', 'Snapshot ban đầu và revision sau đổi đơn, khách chấp thuận trực tuyến hoặc căn cứ xác nhận thủ công', 'Chưa hợp đồng/chữ ký số đầy đủ và rà soát pháp lý'), 53: ('P', 'B04/B10', 'Outbox đề nghị và thỏa thuận thay đổi, thông báo trong đơn và cảnh báo nội bộ', 'Chưa gửi SMS/email hoặc quản lý giao lắp'), 58: ('P', 'B04/B07/B08', 'Hư hỏng có biên bản DAMAGED, kiểm tra sau trả, phương án/mức bồi thường theo thỏa thuận và người khác duyệt', 'Chưa tự phân loại hao mòn theo loại thiết bị/chính sách đã duyệt'), 63: ('D', 'B08', 'Theo dõi thuê, cọc giữ/hoàn và bồi thường đã duyệt/đã thu; chặn hoàn cọc/tất toán khi thiếu nghĩa vụ', 'Chưa công nợ nhà cung cấp, cấn trừ cọc hoặc các phụ phí khác'), 64: ('P', 'B04/B08/X01', 'Căn cứ thu, duyệt hoàn, sự cố/bồi thường khác người lập; operation key chống ghi lặp', 'Chưa chứng từ chuẩn/chữ ký số/phí khác'), 73: ('P', 'X02', 'Validation, trạng thái, chống đề nghị lỗi thời, kiểm tra lại lịch, inspection trước duyệt hư hỏng, CSV từng dòng', 'Chưa quy tắc cho các chức năng chưa triển khai'), 83: ('P', 'X01/B04', 'Audit đơn/thiết bị/tiền/lương/import/liên hệ; immutable agreement snapshots và người xác nhận', 'Chưa before/after toàn trường hoặc audit thay chính sách giá'), 89: ('P', 'B03/B04/B08', 'Guard vòng thuê, gia hạn/đổi trước giao, nghĩa vụ mất/hỏng, version và operation key', 'Chưa đổi thiết bị sau giao, mọi phí và lifecycle thanh toán đối tác'), 95: ('D', 'Tests', '35 test Java và kịch bản browser cho vòng thuê/thay đổi/liên hệ/sự cố; có tranh lịch và fault injection', 'Chưa bao phủ toàn 98 yêu cầu/50 kịch bản hoặc production'), 97: ('P', 'B04/Docs', 'DEV-1, snapshot và xác nhận đổi đơn; phương án mất/hỏng có căn cứ và khoản bồi thường tách riêng', 'Chưa điều khoản thương mại được cơ sở/pháp lý phê duyệt')}
updates.update({
 3:('P','B03/B07','Tra lịch có buffer, khóa DB khi giữ/cấp/đổi lịch; kế hoạch bảo trì chặn từng mã, quá giờ vẫn chặn đến khi xử lý','Chưa tự thay thế mọi đơn tương lai bị ảnh hưởng/đa kho'),
 5:('P','B01/B04/B08','Đặt thuê từ xa và yêu cầu chuyển khoản SePay/QR riêng cho cọc, thuê, bồi thường; tự đối chiếu điều kiện và hàng đợi sai lệch','Chưa mọi phương thức thanh toán, hoàn qua ngân hàng hoặc nghiệm thu giao dịch thật'),
 21:('P','B02/B03/B07','Từng mã và khả dụng theo khoảng, trừ lịch thuê/buffer/bảo trì; kế hoạch quá giờ vẫn chặn','Chưa phụ kiện theo lượng, kiểm kê hoặc đa kho'),
 45:('P','B03/B07','Chặn thiết bị không an toàn và kế hoạch bảo trì, tranh lịch dùng cùng khóa DB; kiểm tra đạt trước READY','Chưa tự xử lý hết đơn tương lai bị ảnh hưởng hoặc checklist từng loại'),
 59:('P','B03/B07','Xem lịch, lập/bắt đầu/kết thúc/hủy bảo trì từng mã, kiểm tra xung đột và kết quả an toàn','Chưa ưu tiên nhân lực tự động, bảo hành hoặc chi phí bảo trì'),
 80:('P','B08/R03','SePay HMAC/inbox, mã thanh toán riêng, trạng thái đối soát/ghi sổ; chống trùng và cọc đến muộn không phục hồi lịch','Chưa tự hoàn tiền, API đối soát khi webhook thất lạc hoặc xác thực dòng tiền thật'),
 81:('P','B08/R03/X01','HMAC raw body/timestamp; allowlist tài khoản, đối chiếu mã/số tiền/thời hạn, hàng đợi sai lệch và audit quyết định','Chưa gọi API truy vấn ngân hàng, cảnh báo ngoài hệ thống hoặc xử lý tách khoản thừa'),
 82:('P','B01','Chủ cửa hàng tạo tài khoản nhân viên BCrypt, đổi vai trò có kiểm tra bản cũ, khóa/mở; quyền mới áp dụng phiên hiện hành','Chưa RoleGrant từng action/scope, đa kho, reset mật khẩu hoặc phiếu lương tự phục vụ'),
 83:('P','X01/B01/B04/B07/B08','Audit tiền SePay, quyết định đối soát, cấu hình tài khoản nhận, cấp vai trò và kế hoạch bảo trì cùng các luồng dev 0.2','Chưa before/after toàn trường và audit chính sách giá'),
 89:('P','B03/B04/B07/B08','Guard vòng thuê/thay đổi/sự cố, operation key, lịch bảo trì và inbox/payment ID chống ghi tiền trùng','Chưa đổi sau giao, mọi phụ phí hoặc lifecycle thanh toán ngoài hệ thống đầy đủ'),
 93:('P','R03/B08/B10','Adapter SePay HMAC/JSON, inbox bền vững, đối chiếu/ghi sổ và QR; NotificationPort/local adapter','Chưa API ngân hàng chủ động, email/SMS thật hoặc SLA đối tác'),
 95:('D','Tests','68 test Java và 13 kịch bản Chromium cho vòng thuê, SePay, cấp quyền và bảo trì; tranh lịch/tiền, rollback DB và axe','Chưa bao phủ toàn 98 yêu cầu/50 kịch bản hoặc production'),
})
updates.update({
 23:('P','B05','OWNER điều chỉnh giá thuê/ngày và cọc áp dụng ngay; phiên bản/lịch sử trước-sau/audit; snapshot đơn cũ và kiểm tra báo giá lỗi thời','Chưa duyệt tách người, lịch hiệu lực tương lai, khuyến mãi/phụ phí/bồi thường'),
 83:('P','X01/B01/B04/B05/B07/B08','Audit các luồng dev 0.3; điều chỉnh giá có before/after, actor/time/reason và lịch sử phiên bản cùng transaction','Chưa before/after toàn trường cho mọi nghiệp vụ'),
 95:('D','Tests','78 test Java và 15 kịch bản Chromium; thêm giá/phiên bản/báo giá lỗi thời, tranh đổi giá với đặt thuê và rollback','Chưa bao phủ toàn 98 yêu cầu/50 kịch bản hoặc production'),
})
updates.update({
 2:('P','B02/B07','Tình trạng từng chiếc, thông số, checklist theo loại có phiên bản và snapshot kết quả từng tiêu chí','Chưa sổ phụ kiện theo lượng hoặc xác minh checklist chuyên môn mọi loại'),
 45:('P','B03/B07','Chặn thiết bị không an toàn và kế hoạch bảo trì; checklist theo loại và khóa DB, kiểm tra đạt trước READY','Chưa tự xử lý hết đơn tương lai bị ảnh hưởng hoặc bắt buộc inspection còn hiệu lực trước từng giao'),
 54:('P','B07','Nghiệm thu thiết bị mới dùng ba tiêu chí nền và checklist bổ sung theo loại; lưu từng kết quả/phiên bản/căn cứ','Chưa chứng từ nghiệm thu mua hàng, phụ kiện theo lượng hoặc checklist được thẩm định chuyên môn'),
 55:('P','B07','Inspection nền và checklist theo loại có phiên bản; snapshot từng kết quả, từ chối biểu mẫu lỗi thời','Chưa kiểm tra bắt buộc còn hiệu lực trước từng bàn giao, ảnh/chữ ký từng inspection'),
 56:('P','B07/B11','Lịch sử thiết bị hợp nhất thuê/giao/trả/xử lý mất, kiểm tra, phiếu lỗi và kế hoạch bảo trì; cursor và snapshot tiêu chí','Chưa chi phí/bảo hành, đầy đủ audit mọi biến động tài sản; inspection cũ chỉ có kết quả tổng'),
 57:('D','B02/B07','Không đạt tiêu chí nền hoặc tiêu chí bắt buộc theo loại chuyển MAINTENANCE; kiểm tra đạt mới READY','Chưa workflow bảo hành/đối tác ngoài; lịch bảo trì khác còn hiệu lực vẫn chặn'),
 59:('P','B03/B07','Lập/bắt đầu/kết thúc/hủy kế hoạch theo mã, chặn lịch; kết thúc dùng checklist theo loại/phiên bản và lưu kết quả nguyên tử','Chưa ưu tiên/phân công nhân lực tự động, bảo hành hoặc chi phí bảo trì'),
 83:('P','X01/B01/B04/B05/B07/B08','Audit luồng dev, lịch sử giá trước/sau, phiên bản checklist và snapshot kết quả bất biến trong transaction','Chưa before/after toàn trường cho mọi nghiệp vụ'),
 95:('D','Tests','89 test Java; 18 kịch bản Chromium gồm checklist/snapshot/lịch sử, cạnh tranh phiên bản, rollback và giữ vị trí cuộn khi kiểm tra thanh toán','Chưa bao phủ toàn 98 yêu cầu/50 kịch bản hoặc production'),
})
updates.update({
 9:('P','B02/B06','Hướng dẫn theo loại có phiên bản/nguồn/an toàn, xác nhận trên đơn và chặn giao thiếu xác nhận bản hiện hành khi đã cấu hình','Cần shop công bố nội dung đúng từng loại; loại chưa cấu hình cảnh báo, chưa chặn giao'),
 25:('P','B08/B09/B11','Báo cáo tháng: thuê/bồi thường thực thu, chi phí đã duyệt, nhân sự, phân bổ giá mua và kết quả quản trị; loại cọc khỏi doanh thu','Thiết bị cũ còn thiếu căn cứ giá/ngày mua; chưa báo cáo kế toán dồn tích/thuế chuẩn'),
 26:('P','B02/B07/B11','Giờ giao thực tế/tổng giờ sở hữu theo nhóm, phiếu lỗi và chi phí bảo trì gắn mã làm căn cứ đầu tư','Chưa ngưỡng tuổi thọ/hiệu quả hay đề xuất thay thế tự động; thiếu ngày mua được cảnh báo'),
 28:('P','B02/X01','Đề nghị/duyệt khác người, căn cứ và giá trị thu hồi dự kiến; chặn mã còn nghĩa vụ, ngừng khai thác khi duyệt','Chưa chứng từ bán/thu hồi thực tế hoặc ghi khoản tiền thu thanh lý'),
 29:('P','B02/Deployment','Thêm nhiều kho/điểm, vị trí từng mã và luồng chuyển; tiếp nhận lô tối đa 100 mã','Chưa scope nhân viên/giữ chỗ theo chi nhánh, phân trang mọi danh sách hoặc đo tải tăng'),
 37:('P','B01/B04/B06','Chính sách công bố theo phiên bản, xác minh thủ công có tham chiếu che/hạn; chặn xác nhận/giao nếu chính sách bắt buộc chưa đạt','Mặc định chưa bắt buộc trước khi shop công bố; chưa dịch vụ xác thực giấy tờ/điện thoại'),
 47:('D','B02/B03','Chuyển kho có kho đi/đến, người lập/nhận khác nhau, lý do/thời điểm; chặn khai thác khi chờ và kiểm tra lại khi nhận','Chưa theo dõi vận chuyển/GPS hoặc giới hạn quyền theo kho'),
 48:('P','B02/B03','Kiểm kê snapshot từng mã, thiếu/thừa, người khác duyệt; chặn bản lỗi thời và ngừng khai thác mã thiếu','Mã thừa xác minh riêng, chưa tự tạo hồ sơ hay phân bổ tổn thất/tiền'),
 49:('D','B06','Phân công giao/thu theo nhân viên và khung giờ/lộ trình, chống trùng; nhân viên xem việc mình, hoàn thành gắn biên bản thực tế','Chưa tối ưu bản đồ/lộ trình hoặc tự xử lý mọi thay đổi đơn'),
 66:('P','B08','Đối soát ca/ngày có đầu kỳ/thực tế/kỳ vọng/chênh lệch, dấu kiểm sổ và người khác duyệt; không tự tạo tiền','Sổ tổng hợp các kênh, chưa đối soát riêng quỹ/ngân hàng hay import sao kê'),
 67:('D','B08/B09/B11','Chi phí mua/bảo trì/vệ sinh/vận chuyển/khác có căn cứ, duyệt/chi từng phần; nhân sự từ bảng lương, đầu tư tách phân bổ','Chưa thuế/VAT/hóa đơn điện tử, điều chỉnh chi phí đã duyệt hoặc phân bổ chi phí chung theo đơn'),
 68:('D','B08','Hồ sơ nhà cung cấp, khoản phải trả gắn chứng từ/lô mua, duyệt và các lần chi; tính số còn nợ từ sổ','Chưa nhập công nợ đầu kỳ, hoàn nhà cung cấp hay xác nhận điện tử đối tác'),
 71:('P','B09/B08','Phụ cấp/thưởng/khấu trừ/tạm ứng có căn cứ và duyệt khác người; snapshot dòng lương, tạm ứng ghi chi và trừ một lần','Chưa thuế/bảo hiểm, chuyển dư tạm ứng hoặc điều chỉnh sau chốt'),
 77:('D','B09/B01','Liên kết tài khoản đúng hồ sơ, chỉ xem phiếu đã duyệt của mình; gửi yêu cầu đối chiếu và nhận phản hồi','Phản hồi không sửa bảng lương đã duyệt; chưa workflow truy lĩnh/truy thu'),
 78:('D','B02/B08','Lô nhận mã/sê-ri, số lượng/giá trị/chứng từ/nhà cung cấp; duyệt khác người, tạo nguyên tử chờ nghiệm thu; liên kết khoản mua','Chưa đề nghị đặt mua trước nhận, tiếp nhận một phần lô hoặc kho tệp chứng từ mua hàng'),
 79:('D','B07/B02/B03','Điều kiện/thời hạn/đối tác, snapshot hồ sơ xử lý; gửi/nhận và kết quả, chặn khai thác rồi kiểm tra lại','Chưa kết nối đối tác; đổi máy ghi biên bản, chưa tự thay sê-ri/hồ sơ tài sản'),
 85:('P','Deployment','Backup DB và tệp tham chiếu/checksum, restore DB riêng/đối chiếu 61 bảng; diễn tập khởi động, đăng nhập, API và tải tệp đạt','Chưa backup ngoài máy/tự động, PITR/offline/failover hoặc chứng minh RTO/RPO production'),
 20:('P','B02/B07','Tiếp nhận lô có chứng từ/giá/sê-ri và người khác duyệt; mã chờ nghiệm thu, checklist trước READY','Chưa quy trình đề nghị/đặt mua trước khi nhận hoặc nhận một phần lô'),
 21:('P','B02/B03/B07','Từng mã, khả dụng có buffer/bảo trì, nhiều kho, chuyển kho và kiểm kê','Chưa phụ kiện theo lượng hoặc chọn kho trong đơn thuê'),
 22:('P','B02/B06/B08','Mã riêng, biên bản/cọc/audit, xử lý mất và kiểm kê/chuyển kho','Chưa phụ kiện theo lượng hoặc truy tìm/bảo hiểm đầy đủ'),
 30:('P','B04/B06','Lịch thuê, hàng đợi quá hạn và phân công giao/thu theo nhân viên','Chưa điều phối tự động/lộ trình tối ưu'),
 42:('D','B02','Tiếp nhận lô có mã/sê-ri/ngày/giá/chứng từ/nhà cung cấp/kho, chờ nghiệm thu','Thiết bị cũ chưa bổ sung đủ căn cứ; chưa nghiệp vụ thay nhận dạng sau đổi máy'),
 59:('P','B03/B07/B08','Bảo trì có lịch/khóa/kiểm tra, hồ sơ bảo hành và chi phí gắn thiết bị','Chưa tự ưu tiên/phân công kỹ thuật theo lịch giao'),
 63:('D','B08','Theo dõi thuê/cọc/bồi thường và công nợ nhà cung cấp riêng; guard hoàn/tất toán','Chưa cấn trừ cọc, phụ phí hoặc công nợ đầu kỳ'),
 95:('D','Tests','107 test Java, 24 kịch bản Chromium và diễn tập backup/restore dev','Chưa bao phủ toàn 98 yêu cầu/50 kịch bản hay production')
})
for number,(st,module,current,gap) in updates.items():
    progress[number]={"status":status[st],"module":module,"current":current,"gap":gap}
assert set(progress)==set(range(1,99))
testmap={3:'IntegrationTest.concurrentHoldsNeverExceedPhysicalCapacity',6:'IntegrationTest.lateDepositDoesNotResurrectExpiredReservation',15:'IntegrationTest.fullRentalSupportsPartialReturnInspectionRefundAndSettlement',19:'IntegrationTest.customerScopeAndBackendRolesAreEnforced;privateFile*',57:'RulePluginsTest',63:'IntegrationTest.fullRentalSupportsPartialReturnInspectionRefundAndSettlement',70:'IntegrationTest (payroll)',76:'IntegrationTest (payroll)',80:'IntegrationTest.concurrentPaymentRetryCreatesOneLedgerEntry',82:'IntegrationTest.lockedAccountLosesExistingSessionAccess',87:'IntegrationTest (CSV staging/rollback)',89:'IntegrationTest (rental/idempotency)',94:'ArchitectureTest; Playwright',95:'backend/src/test; web-ui/e2e/product.spec.ts',96:'DevSeed; IntegrationTest'}
rows=[]
testmap.update({3:'MaintenancePlanningTest; IntegrationTest',5:'SePayPaymentTest; payments-access-maintenance.spec.ts',45:'MaintenancePlanningTest',59:'MaintenancePlanningTest',80:'SePayWebhookTest; SePayPaymentTest',81:'SePayWebhookTest; SePayPaymentTest',82:'AccountAdministrationTest; payments-access-maintenance.spec.ts',83:'AccountAdministrationTest; MaintenancePlanningTest; SePayPaymentTest',93:'SePayWebhookTest; SePayPaymentTest'})
testmap.update({23:'ProductPriceTest; product-prices.spec.ts',83:'ProductPriceTest; AccountAdministrationTest; MaintenancePlanningTest; SePayPaymentTest'})
testmap.update({n:'InspectionChecklistTest; checklists-history.spec.ts' for n in [2,45,54,55,56,57,59]})
testmap[83] += '; InspectionChecklistTest'
testmap[95] = 'backend/src/test; web-ui/e2e; docs/verification.md'
testmap.update({n:'ExpansionWorkflowTest; expansion.spec.ts; docs/operations-expansion.md' for n in [9,25,26,28,29,37,47,48,49,66,67,68,71,77,78,79]})
testmap[85]='scripts/recovery.mjs; scripts/recovery-drill.mjs; docs/backup-recovery.md; .dev/expansion-recovery-drill.log'
for r in data['requirements']:
    rows.append({'id':r['id'],'stakeholder':r['stakeholder'],'priority':r['priority'],'requirement':r['text'],'original_note':r['note'],**progress[r['id']],'evidence':testmap.get(r['id'],'Xem package/module và danh sách kiểm chứng; chưa có test riêng cho toàn yêu cầu')})
def writecsv(name,items):
    with (docs/name).open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(items[0]));w.writeheader();w.writerows(items)
writecsv('requirements.csv',rows)
(docs/'requirements.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
md=['# Truy vết 98 yêu cầu bài 1 → dev 0.3','',
'Giữ nguyên nội dung và mức ưu tiên bài 1. `CÓ_TRONG_DEV` nghĩa là chức năng nêu ở cột hiện có đã chạy/kiểm tra trong phạm vi demo, không phải biên bản nghiệm thu toàn yêu cầu. `MỘT_PHẦN` còn tiêu chí chưa làm; `CHƯA_KIỂM_CHỨNG` không được xem là đạt. Không tính tỷ lệ hoàn tất từ số màn hình hay số module.','',
'CSV đầy đủ gồm stakeholder, ghi chú gốc và bằng chứng: [requirements.csv](requirements.csv). Ánh xạ 36 lớp ở [domain-model.md](domain-model.md); 50 kịch bản ở [scenarios.csv](scenarios.csv).','',
'| YC | Yêu cầu gốc | Trạng thái / module | Hiện có | Còn thiếu / giới hạn |','|---|---|---|---|---|']
for r in rows:md.append(f"| {r['id']:02} | {r['requirement']} | {r['status']} · {r['module']} | {r['current']} | {r['gap']} |")
(docs/'requirements.md').write_text('\n'.join(md)+'\n',encoding='utf-8')
constraint_notes=[
'Phù hợp mô hình tự sở hữu, không ký gửi/chia doanh thu.',
'Có mã riêng/thông số; chưa phụ kiện theo lượng.',
'Một kho dev; chưa chuyển kho/nguồn mua/chứng từ.',
'DEV-1 tính ngày/buffer/snapshot; chưa chính sách được duyệt/ngày hiệu lực.',
'Giữ 20 phút/cọc đủ trước xác nhận/hoàn khác người; cần duyệt chính sách thực tế.',
'Có hủy trước giao, gia hạn/đổi lịch/mã cùng loại/địa chỉ trước giao với revision và xác nhận; chưa phí hủy hoặc đổi sau giao.',
'Có trả từng phần/kiểm tra và phương án mất/hư hỏng có bồi thường duyệt riêng; chưa phí trả trễ hoặc cấn trừ cọc.',
'Có vai trò server và phân tách lập/duyệt; chưa RoleGrant động/action-scope đa kho.',
'Có công/lương giờ mẫu; hồ sơ/nghỉ/OT chưa đầy đủ.',
'Công thức lương demo, không phải chính sách đã phê duyệt.',
'Dashboard và CSV tiền; chưa chi phí/lợi nhuận/khai thác/lương đầy đủ.',
'Một máy dev và adapter local; chưa quy mô/ngân sách/kênh chính thức.',
'Chưa load test 100 concurrent hoặc đo p95 3s/5s.',
'Chưa đo 99,5%, diễn tập RTO 4h/RPO 15m hoặc backup/PITR.',
'Đã test đặt đồng thời, retry khoản thu nội bộ và rollback transaction; chưa callback đối tác thật.',
'Có quyền đối tượng và tệp riêng; chưa duyệt thời hạn lưu hoặc rà soát nghĩa vụ áp dụng.',
'Có web 375px/desktop, CSV khách; chưa thiết bị thật/offline/toàn bộ số dư đầu kỳ.',
'Chưa có kế hoạch ngân sách/thời hạn và biên bản nghiệm thu của cơ sở.'
]
cs=[]
constraint_notes[7]='Có tạo tài khoản nhân viên, đổi vai trò có expectedRole và audit, kiểm tra quyền server mỗi request; chưa RoleGrant action-scope/đa kho.'
constraint_notes[14]='Đã kiểm tra tranh lịch thuê/bảo trì, retry SePay/inbox, tranh thanh toán và rollback DB; chưa giao dịch thật hoặc crash nhiều instance.'
constraint_notes[2]='Có nhiều kho, chuyển/kiểm kê/tiếp nhận lô có giá/sê-ri/chứng từ và bảo hành; chưa nghiệp vụ đặt mua/nhận một phần.'
constraint_notes[9]='Lương giờ và phụ cấp/thưởng/khấu trừ/tạm ứng có căn cứ, duyệt/snapshot; chưa chính sách đầy đủ được cơ sở phê duyệt.'
constraint_notes[10]='Có báo cáo tháng thu/chi phí/lương/phân bổ/kết quả quản trị/khai thác; chưa báo cáo kế toán đầy đủ, thiếu căn cứ tài sản cũ.'
constraint_notes[13]='Diễn tập backup/restore DB+tệp và khởi động API local đạt; chưa RTO/RPO production, backup ngoài máy/PITR hoặc đo 99,5%.'
for c,note in zip(data['constraints'],constraint_notes):cs.append({**c,'dev_status':'CHƯA_KIỂM_CHỨNG' if c['id'] in ['C13','C14','C18'] else 'MỘT_PHẦN','implementation_note':note})
writecsv('constraints.csv',cs)
scenario_notes={
'M09':('CÓ_BẰNG_CHỨNG_DEV','ArchitectureTest kiểm tra chiều phụ thuộc, không gọi repository khác module, không chu trình. Chưa thay thế toàn review kiến trúc.'),
'S01':('CÓ_BẰNG_CHỨNG_DEV','IntegrationTest kiểm tra đơn của khách khác bị 404, bằng chứng theo quyền đối tượng.'),
'S02':('MỘT_PHẦN','API HR không trả mức lương hoặc payroll; chưa có tự phục vụ lương theo nhân viên.'),
'S03':('CÓ_BẰNG_CHỨNG_DEV','IntegrationTest từ chối người lập tự duyệt hoàn/lương; E2E đổi tài khoản duyệt hoàn.'),
'S04':('CÓ_BẰNG_CHỨNG_DEV','IntegrationTest khóa tài khoản rồi kiểm tra request phiên hiện hành bị từ chối.'),
'S06':('MỘT_PHẦN','Test từ chối tệp giả ảnh/khách khác tải; chưa malware scan/PDF parsing hoặc kiểm chứng tất cả attack vectors.'),
'S07':('CÓ_BẰNG_CHỨNG_DEV','IntegrationTest và E2E gọi finance API bằng stock được 403.'),
'C05':('MỘT_PHẦN','CSV bị giới hạn 1MB/1000 dòng, không streaming job lớn; chưa đo heap khi tải xấu.'),
'C07':('MỘT_PHẦN','8 yêu cầu đồng thời tranh 3 thiết bị chỉ thành công 3; chưa chạy 100 người tranh chiếc cuối.'),
'R01':('MỘT_PHẦN','Test đặt đồng thời PostgreSQL không quá 3 tài sản; chưa mọi lịch buffer/đổi đơn/đa instance.'),
'R02':('MỘT_PHẦN','Trigger DB chứng minh rollback tạo đơn và rollback xử lý mất khi ghi khoản bồi thường lỗi; test duyệt gia hạn tranh lịch. Chưa kill JVM.'),
'R03':('MỘT_PHẦN','6 lệnh thu đồng thời cùng operation key chỉ một giao dịch; chưa callback nhiều eventId của đối tác.'),
'R04':('MỘT_PHẦN','Tiền thủ công đến muộn được ghi sổ, xác nhận bị từ chối và lịch được giải phóng; chưa callback thật.'),
'R06':('MỘT_PHẦN','Test trả từng phần, hư hỏng có inspection/phê duyệt/thu bồi thường và thiết bị vẫn MAINTENANCE; mất không ghi giả đã trả; chưa mọi chính sách thương mại.'),
'R07':('CHƯA_KIỂM_CHỨNG','Có rollback cleanup tệp và checksum; chưa fault injection/crash giữa filesystem và DB.'),
'R08':('CÓ_BẰNG_CHỨNG_DEV','CSV staging/khác người duyệt/nhập trùng/đổi điện thoại sau staging gây rollback; mới phạm vi khách hàng.'),
'R09':('CÓ_BẰNG_CHỨNG_DEV','Test khóa công sau chốt, snapshot và chi lương không lặp; chỉ công thức giờ mẫu.')}
sr=[]
scenario_notes.update({
 'R03':('MỘT_PHẦN','SePayWebhookTest/SePayPaymentTest: retry HMAC, sáu callback đồng thời một receipt, nhiều giao dịch tranh một yêu cầu chỉ một dòng tiền; chưa tất cả eventId/cổng khác.'),
 'R04':('MỘT_PHẦN','SePayPaymentTest: cọc đến muộn vào REVIEW; kế toán đối soát giữ cọc để hoàn, đơn EXPIRED không phục hồi lịch; chưa giao dịch ngân hàng thật.'),
 'S04':('CÓ_BẰNG_CHỨNG_DEV','AccountAdministrationTest và E2E kiểm tra đổi vai trò áp dụng phiên hiện hành, khóa tài khoản từ chối request tiếp theo; chưa reset/MFA.'),
})
scenario_notes['S02']=('MỘT_PHẦN','HR không nhận mức lương chung; B09 phiếu riêng theo staff.user_id, test cách ly tài khoản và đối chiếu sai lệch. Chưa mọi phạm vi quyền production.')
for s in data['scenarios']:
    st,note=scenario_notes.get(s['id'],('CHƯA_KIỂM_CHỨNG','Chưa có bằng chứng chạy đúng toàn kịch bản bài 4; xem backlog và kiến trúc dev.'))
    sr.append({**s,'dev_status':st,'dev_evidence_and_limit':note})
writecsv('scenarios.csv',sr)
manifest={}
for k,f in data['files'].items():
    p=Path(f['path']); current=hashlib.sha256(p.read_bytes()).hexdigest() if p.exists() else None
    manifest[k]={**f,'current_sha256':current,'matches_bai5_snapshot':current==f['sha256']}
for name in ['output/bai5/Nhom9_Bai5_Task01_TaiLieuKienTrucPhanMem.docx']:
    p=root.parent/name
    if p.exists():manifest['bai5_final']={'path':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
(docs/'source-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'requirements':len(rows),'constraints':len(cs),'scenarios':len(sr),'source_mismatches':[k for k,v in manifest.items() if v.get('matches_bai5_snapshot') is False]},ensure_ascii=False))
