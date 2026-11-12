package vn.xuannhashop.business.support.application;
import java.util.UUID;
public interface NotificationPort { void send(UUID id,String event,String payload) throws Exception; }
