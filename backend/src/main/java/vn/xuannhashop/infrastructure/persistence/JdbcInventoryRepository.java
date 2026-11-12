package vn.xuannhashop.infrastructure.persistence;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.inventory.application.InventoryRepository;
import vn.xuannhashop.business.inventory.domain.*;
import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository
public class JdbcInventoryRepository implements InventoryRepository {
    private final Sql s;public JdbcInventoryRepository(Sql s){this.s=s;}
    private EquipmentType type(Map<String,Object> r){return new EquipmentType((UUID)r.get("id"),(String)r.get("code"),(String)r.get("name"),(String)r.get("category"),(String)r.get("description"),(String)r.get("specifications"),(String)r.get("icon"),(BigDecimal)r.get("daily_rate"),(BigDecimal)r.get("deposit"),(String)r.get("policy_version"),((Number)r.get("price_version")).longValue());}
    public List<EquipmentType> catalog(){return s.rows("select * from equipment_type where active order by code",Map.of()).stream().map(this::type).toList();}
    public EquipmentType type(UUID id){var r=s.one("select * from equipment_type where id=:id and active",p("id",id));return r==null?null:type(r);}
    public List<Map<String,Object>> assets(){return s.rows("select e.id,e.code,e.type_id,e.warehouse_id,e.state,e.version,e.serial_number,e.acquired_on,e.warranty_until,t.name,t.category,w.name warehouse from equipment e join equipment_type t on t.id=e.type_id join warehouse w on w.id=e.warehouse_id order by e.code",Map.of());}
    public Equipment asset(UUID id){var r=s.one("select * from equipment where id=:id",p("id",id));return r==null?null:new Equipment(id,(String)r.get("code"),(UUID)r.get("type_id"),(String)r.get("state"));}
    public void state(UUID id,String state){s.update("update equipment set state=:state,version=version+1 where id=:id",p("id",id,"state",state));}
    public UUID addAsset(UUID type,String code,UUID warehouse){var id=UUID.randomUUID();s.update("insert into equipment(id,code,type_id,warehouse_id,state) values(:id,:code,:type,:warehouse,'AWAIT_ACCEPTANCE')",p("id",id,"code",code,"type",type,"warehouse",warehouse));return id;}
    public List<Map<String,Object>> warehouses(){return s.rows("select * from warehouse order by name",Map.of());}
    public void createType(UUID id,vn.xuannhashop.business.inventory.application.EquipmentApplication.NewType c){s.update("insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit) values(:id,:code,:name,:category,:description,:specifications,:icon,:rate,:deposit)",p("id",id,"code",c.code(),"name",c.name(),"category",c.category(),"description",c.description(),"specifications",c.specifications(),"icon",c.icon(),"rate",c.dailyRate(),"deposit",c.deposit()));}
    public boolean movementPending(UUID id){return s.one("select 1 where exists(select 1 from stock_action where equipment_id=:id and status='PENDING') or exists(select 1 from warranty_case where equipment_id=:id and status='OPEN')",p("id",id))!=null;}
}
