package vn.xuannhashop;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;

import vn.xuannhashop.infrastructure.security.AuthenticationRateLimitFilter;
import vn.xuannhashop.infrastructure.persistence.Sql;
import vn.xuannhashop.infrastructure.config.*;
import vn.xuannhashop.business.inventory.application.EquipmentApplication;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ProductionReadinessTest {
 @Test void productionGuardRejectsUnsafeOverrides(){
  var runner=new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(ProductionConfiguration.class).withInitializer(c->c.getEnvironment().setActiveProfiles("prod")).withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/production","server.servlet.session.cookie.secure=true","app.seed=false");
  runner.run(c->assertThat(c).hasNotFailed());
  runner.withPropertyValues("app.seed=true").run(c->assertThat(c).hasFailed());
  runner.withPropertyValues("server.servlet.session.cookie.secure=false").run(c->assertThat(c).hasFailed());
  runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost/xuannhashop_dev").run(c->assertThat(c).hasFailed());
 }
 @Test void pdfCanBeDisabledUntilMalwareScanningIsAvailable()throws Exception{
  var folder=java.nio.file.Files.createTempDirectory("xuan-upload-test");
  try{var repository=new vn.xuannhashop.infrastructure.files.PrivateFileRepository(folder.toString());org.springframework.test.util.ReflectionTestUtils.setField(repository,"allowPdf",false);assertThatThrownBy(()->repository.store("%PDF-1.4 fixture".getBytes(),"fixture.pdf")).isInstanceOf(vn.xuannhashop.business.shared.DomainException.class);}finally{java.nio.file.Files.delete(folder);}
 }
 @Autowired Sql sql;@Autowired JdbcTemplate db;@Autowired MockMvc mvc;
 @Test void rateLimitIsSharedBetweenInstancesAndExpires()throws Exception{
  db.update("delete from authentication_rate_limit");
  for(int i=0;i<11;i++){var request=new MockHttpServletRequest("POST","/api/auth/login");request.setRemoteAddr("192.0.2.8");var response=new MockHttpServletResponse();new AuthenticationRateLimitFilter(sql).doFilter(request,response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(i<10?200:429);}
  db.update("update authentication_rate_limit set window_start=clock_timestamp()-interval '2 minutes'");
  var request=new MockHttpServletRequest("POST","/api/auth/login");request.setRemoteAddr("192.0.2.8");var response=new MockHttpServletResponse();new AuthenticationRateLimitFilter(sql).doFilter(request,response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(200);
 }
 @Test void ownerCanCreateTypeButCustomerCannotAndRetryDoesNotDuplicate()throws Exception{
  String code="PROD-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),key=UUID.randomUUID().toString();
  String body="{\"code\":\""+code+"\",\"name\":\"Production test\",\"category\":\"Test\",\"description\":\"Test\",\"specifications\":\"Test\",\"icon\":\"fan\",\"dailyRate\":100,\"deposit\":0}";
  mvc.perform(post("/api/equipment-types").with(user("customer")).with(csrf()).contentType("application/json").content(body).header("Idempotency-Key",key)).andExpect(status().isForbidden());
  for(int i=0;i<2;i++)mvc.perform(post("/api/equipment-types").with(user("owner")).with(csrf()).contentType("application/json").content(body).header("Idempotency-Key",key)).andExpect(status().isOk());
  assertThat(db.queryForObject("select count(*) from equipment_type where code=?",Integer.class,code)).isEqualTo(1);
 }
 @Test void productionProfileDisablesDemoSeedAndRequiresSecureCookies()throws Exception{
  try(var input=getClass().getResourceAsStream("/application-prod.yml")){String s=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertThat(s).contains("seed: false","secure: true","auth-rate-limit: true","tracking-modes: cookie");}
 }
}
