package vn.xuannhashop.business.pricing.application;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.xuannhashop.business.shared.*;
import vn.xuannhashop.business.shared.audit.ActivityService;
import vn.xuannhashop.business.shared.security.ActorContext;

@Service
public class ProductPriceService implements ProductPriceApplication {
    private final ProductPriceRepository r; private final ActorContext actor; private final Commands commands; private final ActivityService audit;
    public ProductPriceService(ProductPriceRepository r,ActorContext actor,Commands commands,ActivityService audit){this.r=r;this.actor=actor;this.commands=commands;this.audit=audit;}
    @Transactional public UUID change(UUID type,Change c,String key){
        actor.require("OWNER");
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0)throw DomainException.bad("Thiếu phiên bản giá. Hãy tải lại bảng giá.");
        BigDecimal daily=amount(c.dailyRate(),false),deposit=amount(c.deposit(),true);
        String reason=Checks.text(c.reason(),500,"Lý do điều chỉnh giá");
        return commands.once(key,"PRODUCT_PRICE:"+type+":"+c.expectedVersion()+":"+daily+":"+deposit+":"+reason,()->{
            var old=r.get(type,true);if(old==null)throw DomainException.missing();
            if(old.version()!=c.expectedVersion())throw DomainException.conflict("Giá đã được người khác thay đổi. Đóng cửa sổ, tải lại bảng giá rồi kiểm tra trước khi lưu.");
            if(old.dailyRate().compareTo(daily)==0&&old.deposit().compareTo(deposit)==0)throw DomainException.bad("Giá thuê và tiền cọc chưa thay đổi.");
            UUID id=UUID.randomUUID();r.change(id,type,old,daily,deposit,reason,actor.username());
            audit.record("PRODUCT_PRICE_CHANGED",type,"v"+old.version()+" → v"+(old.version()+1)+"; thuê/ngày: "+old.dailyRate()+" → "+daily+"; cọc: "+old.deposit()+" → "+deposit+"; "+reason);
            return id;
        });
    }
    public List<Map<String,Object>> history(UUID type){actor.require("OWNER","MANAGER","INVENTORY");if(r.get(type,false)==null)throw DomainException.missing();return r.history(type);}
    private BigDecimal amount(BigDecimal n,boolean zeroAllowed){
        if(n==null||n.signum()<0||(!zeroAllowed&&n.signum()==0)||n.stripTrailingZeros().scale()>0||n.compareTo(new BigDecimal("999999999999"))>0)
            throw DomainException.bad(zeroAllowed?"Tiền cọc phải là số nguyên VND từ 0 đến 999.999.999.999.":"Giá thuê/ngày phải là số nguyên VND từ 1 đến 999.999.999.999.");
        return n.setScale(0);
    }
}
