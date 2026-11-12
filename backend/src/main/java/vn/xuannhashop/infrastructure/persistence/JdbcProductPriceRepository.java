package vn.xuannhashop.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.pricing.application.ProductPriceRepository;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;

@Repository
public class JdbcProductPriceRepository implements ProductPriceRepository {
    private final Sql s;public JdbcProductPriceRepository(Sql s){this.s=s;}
    public Price get(UUID type,boolean lock){var row=s.one("select price_version,daily_rate,deposit from equipment_type where id=:id and active"+(lock?" for update":""),p("id",type));return row==null?null:new Price(((Number)row.get("price_version")).longValue(),(BigDecimal)row.get("daily_rate"),(BigDecimal)row.get("deposit"));}
    public void lockForRental(List<UUID> types){s.rows("select id from equipment_type where id in (:ids) order by id for share",p("ids",types));}
    public void change(UUID id,UUID type,Price old,BigDecimal daily,BigDecimal deposit,String reason,String actor){
        var args=p("id",id,"type",type,"version",old.version()+1,"oldDaily",old.dailyRate(),"oldDeposit",old.deposit(),"daily",daily,"deposit",deposit,"reason",reason,"actor",actor);
        s.update("update equipment_type set daily_rate=:daily,deposit=:deposit,price_version=:version where id=:type",args);
        s.update("insert into product_price_change(id,type_id,version,old_daily_rate,daily_rate,old_deposit,deposit,reason,changed_by) values(:id,:type,:version,:oldDaily,:daily,:oldDeposit,:deposit,:reason,:actor)",args);
    }
    public List<Map<String,Object>> history(UUID type){return s.rows("select * from product_price_change where type_id=:type order by version desc limit 100",p("type",type));}
}
