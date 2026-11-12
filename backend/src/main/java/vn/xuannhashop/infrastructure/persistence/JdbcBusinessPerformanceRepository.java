package vn.xuannhashop.infrastructure.persistence;
import java.util.*;import java.time.*;import org.springframework.stereotype.Repository;
import vn.xuannhashop.business.reporting.application.BusinessPerformanceRepository;import static vn.xuannhashop.infrastructure.persistence.Sql.p;
@Repository public class JdbcBusinessPerformanceRepository implements BusinessPerformanceRepository {
    private final Sql s;public JdbcBusinessPerformanceRepository(Sql s){this.s=s;}
    public Map<String,Object> report(String period,Instant from,Instant until,LocalDate start,LocalDate end){var params=p("period",period,"f",from,"t",until,"start",start,"end",end);
        var f=s.one("""
          select coalesce(sum(amount) filter(where kind='RENTAL_IN'),0) rental_received,
            coalesce(sum(amount) filter(where kind='CHARGE_IN'),0) compensation_received,
            coalesce(sum(amount) filter(where kind='DEPOSIT_IN'),0)-coalesce(sum(amount) filter(where kind='DEPOSIT_REFUND'),0) deposit_movement,
            (select coalesce(sum(amount),0) from expense where status='APPROVED' and category<>'PURCHASE' and incurred_on>=:start and incurred_on<:end) operating_expense,
            (select coalesce(sum(amount),0) from expense where status='APPROVED' and category='PURCHASE' and incurred_on>=:start and incurred_on<:end) capital_purchases,
            (select coalesce(sum(l.amount),0) from payroll_line l join payroll p on p.id=l.payroll_id where p.period=:period and p.status in ('APPROVED','PAID') and l.kind<>'ADVANCE') payroll_cost,
            (select count(*) from equipment where state<>'DISPOSED' and (acquired_on is null or purchase_cost=0 or useful_life_months=0)) missing_cost_basis,
            (select coalesce(round(sum(e.purchase_cost * greatest(0,least(cast(:end as date),(clock_timestamp() at time zone 'Asia/Ho_Chi_Minh')::date+1,(e.acquired_on+make_interval(months=>e.useful_life_months))::date,coalesce((select min((a.decided_at at time zone 'Asia/Ho_Chi_Minh')::date) from stock_action a where a.equipment_id=e.id and a.kind='DISPOSE' and a.status='COMPLETED'),cast(:end as date)))-greatest(cast(:start as date),e.acquired_on)) / nullif((e.acquired_on+make_interval(months=>e.useful_life_months))::date-e.acquired_on,0)),2),0) from equipment e where e.acquired_on is not null and e.useful_life_months>0) depreciation
          from money_transaction where created_at>=:f and created_at<:t
          """,params);
        var utilization=s.rows("""
          with assets as (
            select e.*,t.name type_name,t.category,
              greatest(cast(:f as timestamptz),coalesce(e.acquired_on::timestamp at time zone 'Asia/Ho_Chi_Minh',cast(:f as timestamptz))) live_from,
              least(cast(:t as timestamptz),clock_timestamp(),coalesce((select min(a.decided_at) from stock_action a where a.equipment_id=e.id and a.kind='DISPOSE' and a.status='COMPLETED'),case when e.state='DISPOSED' then cast(:f as timestamptz) else cast(:t as timestamptz) end)) live_until
            from equipment e join equipment_type t on t.id=e.type_id
          ), usage as (
            select e.*,greatest(0,extract(epoch from live_until-live_from))/3600 possible_hours,
              (select coalesce(sum(greatest(0,extract(epoch from least(coalesce(i.returned_at,i.resolved_at,clock_timestamp()),e.live_until)-greatest(i.handed_at,e.live_from)))/3600),0) from rental_item i where i.equipment_id=e.id and i.handed_at is not null) rented_hours,
              (select count(*) from maintenance_ticket m where m.equipment_id=e.id and m.opened_at>=:f and m.opened_at<:t) faults,
              (select coalesce(sum(x.amount),0) from expense x where x.equipment_id=e.id and x.status='APPROVED' and x.category='MAINTENANCE' and x.incurred_on>=:start and x.incurred_on<:end) maintenance_cost
            from assets e
          ) select type_id,type_name,category,count(*) asset_count,round(sum(possible_hours),2) possible_hours,round(sum(rented_hours),2) rented_hours,
            round(100*sum(rented_hours)/nullif(sum(possible_hours),0),2) utilization_percent,sum(faults) faults,sum(maintenance_cost) maintenance_cost,
            count(*) filter(where acquired_on is null) unknown_acquisition_dates
          from usage group by type_id,type_name,category order by sum(rented_hours) desc,type_name
          """,params);
        return Map.of("financial",f,"utilization",utilization,"expensesByCategory",s.rows("select category,sum(amount) amount from expense where status='APPROVED' and incurred_on>=:start and incurred_on<:end group by category order by category",params));
    }
}
