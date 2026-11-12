package vn.xuannhashop.infrastructure.config;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.infrastructure.persistence.Sql;
import vn.xuannhashop.business.rental.application.RentalRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Component
@Profile("dev")
@ConditionalOnProperty(name="app.seed",havingValue="true")
public class DevSeed implements CommandLineRunner {
    private final RentalRepository rentals;private final Sql s;private final PasswordEncoder encoder;private final String password;
    public DevSeed(Sql s,RentalRepository rentals,PasswordEncoder encoder,@Value("${app.dev-password}")String password){this.s=s;this.rentals=rentals;this.encoder=encoder;this.password=password;}
    public static UUID id(String name){return UUID.nameUUIDFromBytes(("xuannhashop-dev:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Transactional public void run(String... args){
        if(s.one("select id from user_account where username='owner'",Map.of())!=null)return;
        if(password.length()<12)throw new IllegalStateException("DEV_PASSWORD must be at least 12 characters");
        var warehouse=id("warehouse");s.update("insert into warehouse values(:id,'Kho XuanNha','Thủ Đức, TP. Hồ Chí Minh — địa chỉ dữ liệu mẫu')",p("id",warehouse));
        String[][] people={{"khach-an","Nguyễn Minh An","0901000001","Thủ Đức, TP. Hồ Chí Minh"},{"khach-linh","Trần Thảo Linh","0901000002","Bình Thạnh, TP. Hồ Chí Minh"},{"khach-hoang","Lê Quốc Hoàng","0901000003","Quận 7, TP. Hồ Chí Minh"}};
        for(var a:people)s.update("insert into customer(id,name,phone,address) values(:id,:n,:p,:a)",p("id",id(a[0]),"n",a[1],"p",a[2],"a",a[3]));
        String[][] accounts={{"owner","Huỳnh Xuân Nhã","OWNER"},{"accountant","Kế toán mẫu","ACCOUNTANT"},{"stock","Nhân viên kho mẫu","INVENTORY"},{"delivery","Nhân viên giao nhận mẫu","DELIVERY"},{"technician","Kỹ thuật viên mẫu","TECHNICIAN"},{"hr","Nhân sự mẫu","HR"},{"customer","Nguyễn Minh An","CUSTOMER"}};
        String hash=encoder.encode(password);
        for(var a:accounts)s.update("insert into user_account values(:id,:u,:p,:d,:r,:c,true)",p("id",id("user-"+a[0]),"u",a[0],"p",hash,"d",a[1],"r",a[2],"c",a[2].equals("CUSTOMER")?id("khach-an"):null));
        String[][] products={
            {"TB01","Máy giặt thảm & ghế sofa","Vệ sinh nhà cửa","Làm mới tổ ấm, nhẹ công dọn dẹp.","Công suất 1.200 W · Bình nước 4 L · Kèm đầu hút ghế","cleaner","250000","1000000","4"},
            {"TB02","Máy chiếu Full HD","Giải trí & sự kiện","Một rạp phim nhỏ, ngay trong nhà bạn.","Độ phân giải 1080p · HDMI · Kèm màn chiếu gấp","projector","300000","1500000","3"},
            {"TB03","Máy hút bụi khô & ướt","Vệ sinh nhà cửa","Gọn mọi góc nhà, sạch từ những điều nhỏ.","Dung tích 20 L · Công suất 1.400 W · Bộ đầu hút","vacuum","150000","500000","4"},
            {"TB04","Máy lọc không khí","Chăm sóc gia đình","Không gian trong lành cho cả gia đình.","Phòng đến 45 m² · Bộ lọc HEPA · Chế độ ban đêm","purifier","180000","800000","3"},
            {"TB05","Bếp nướng điện gia đình","Bếp & tiệc tại nhà","Thêm một lý do để cả nhà quây quần.","Công suất 2.000 W · Vỉ chống dính · Khay hứng mỡ","grill","120000","400000","3"},
            {"TB06","Loa di động kèm micro","Giải trí & sự kiện","Giai điệu cho những buổi gặp gỡ đáng nhớ.","Bluetooth · Hai micro không dây · Pin đến 6 giờ","speaker","220000","1000000","3"},
            {"TB07","Máy khoan cầm tay","Dụng cụ gia đình","Việc sửa chữa nhỏ, bạn có thể tự tay làm.","Pin 18 V · Hai pin · Bộ mũi khoan cơ bản","drill","100000","500000","3"},
            {"TB08","Quạt làm mát di động","Chăm sóc gia đình","Dịu mát vừa đủ cho ngày sum họp.","Ba tốc độ · Bình nước 20 L · Bánh xe di chuyển","fan","150000","600000","3"}
        };
        for(var a:products){var type=id(a[0]);s.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(:id,:c,:n,:g,:d,:s,:i,:r,:p)",p("id",type,"c",a[0],"n",a[1],"g",a[2],"d",a[3],"s",a[4],"i",a[5],"r",new BigDecimal(a[6]),"p",new BigDecimal(a[7])));for(int n=1;n<=Integer.parseInt(a[8]);n++)s.update("insert into equipment(id,code,type_id,warehouse_id,state) values(:id,:c,:t,:w,'READY')",p("id",id(a[0]+"-"+n),"c",a[0]+"-"+String.format("%03d",n),"t",type,"w",warehouse));}
        s.update("update equipment set state='AWAIT_INSPECTION' where id=:id",p("id",id("TB01-4")));
        s.update("update equipment set state='MAINTENANCE' where id=:id",p("id",id("TB07-3")));
        s.update("insert into maintenance_ticket(id,equipment_id,fault,status,opened_by) values(:id,:e,'Dữ liệu mẫu: kiểm tra pin trước khi đưa lại vào sử dụng','OPEN','technician')",p("id",id("maint-demo"),"e",id("TB07-3")));
        s.update("insert into salary_policy_version values('DEV-HOURLY-1','Chính sách mẫu theo giờ','Số giờ được duyệt nhân đơn giá; chưa có thuế, bảo hiểm, phụ cấp hoặc thưởng.')",Map.of());
        String[][] staff={{"staff-kho","Phạm Ngọc Mai","Kho & chuẩn bị","40000"},{"staff-delivery","Trần Đức Huy","Giao nhận","45000"},{"staff-tech","Võ Thanh Bình","Kỹ thuật","50000"}};
        for(var a:staff)s.update("insert into staff values(:id,:n,:p,true,:r)",p("id",id(a[0]),"n",a[1],"p",a[2],"r",new BigDecimal(a[3])));
        seedOrder("demo-1","TB03-1","TB03","khach-an","RENTED",Instant.now().minusSeconds(3600),Instant.now().plusSeconds(86400),new BigDecimal("300000"),new BigDecimal("500000"));
        seedOrder("demo-2","TB02-1","TB02","khach-linh","CONFIRMED",Instant.now().plusSeconds(86400),Instant.now().plusSeconds(172800),new BigDecimal("300000"),new BigDecimal("1500000"));
        s.update("insert into support_ticket(id,order_id,subject,message,created_by) values(:id,:o,'Hướng dẫn sử dụng đầu hút','Dữ liệu mẫu: khách cần tư vấn đầu hút phù hợp cho sofa.','customer')",p("id",id("ticket-demo"),"o",id("demo-1")));
        s.update("insert into audit_entry values(:id,'SYSTEM','DEV_DATA_SEEDED',null,'Dữ liệu minh họa; chưa phải giao dịch kinh doanh thực tế.',now())",p("id",UUID.randomUUID()));
    }
    private void seedOrder(String key,String asset,String type,String customer,String state,Instant from,Instant to,BigDecimal rent,BigDecimal deposit){
        UUID order=id(key),item=id(key+"-item");String code="XN-"+String.format("%06d",((Number)s.one("select nextval('rental_code_seq') n",Map.of()).get("n")).longValue());
        s.update("insert into rental_order(id,code,customer_id,status,starts_at,ends_at,expires_at,rental_total,deposit_required,address,terms,agreement_version,created_by) values(:id,:code,:c,:s,:f,:t,:x,:r,:d,'Địa chỉ giao nhận minh họa tại TP. Hồ Chí Minh','DEV-1 — đơn dữ liệu mẫu','DEV-1','owner')",p("id",order,"code",code,"c",id(customer),"s",state,"f",from,"t",to,"x",Instant.now().plusSeconds(1200),"r",rent,"d",deposit));
        s.update("insert into rental_item(id,order_id,equipment_id,type_name,daily_rate,rental_amount,deposit_amount,policy_version,handed_at) select :id,:o,:e,name,daily_rate,:r,:d,'DEV-1',:h from equipment_type where id=:type",p("id",item,"o",order,"e",id(asset),"r",rent,"d",deposit,"h",state.equals("RENTED")?from:null,"type",id(type)));
        s.update("insert into reservation(id,order_id,item_id,equipment_id,occupied_from,occupied_until,status,expires_at) values(:id,:o,:i,:e,:f,:t,'ALLOCATED',:x)",p("id",id(key+"-reservation"),"o",order,"i",item,"e",id(asset),"f",from.minusSeconds(1800),"t",to.plusSeconds(1800),"x",Instant.now().plusSeconds(1200)));
        s.update("insert into agreement_revision(id,order_id,revision,terms,accepted_by,accepted_at) values(:id,:o,1,'DEV-1 — dữ liệu mẫu','owner',now())",p("id",id(key+"-agreement"),"o",order));
        s.update("insert into money_transaction(id,order_id,kind,amount,operation_key,recorded_by,note) values(:id,:o,'DEPOSIT_IN',:d,:key,'accountant','Khoản cọc dữ liệu mẫu')",p("id",id(key+"-deposit"),"o",order,"d",deposit,"key",key+"-deposit"));
        if(state.equals("RENTED")){s.update("update equipment set state='RENTED' where id=:id",p("id",id(asset)));s.update("insert into handover values(:id,:o,'OUT','delivery','Nguyễn Minh An','Biên bản dữ liệu mẫu',:at)",p("id",id(key+"-handover"),"o",order,"at",from));s.update("insert into handover_line values(:id,:h,:i,'GOOD','Dữ liệu mẫu: đủ phụ kiện')",p("id",id(key+"-handover-line"),"h",id(key+"-handover"),"i",item));}
        rentals.captureInitialAgreement(order);
    }
}
