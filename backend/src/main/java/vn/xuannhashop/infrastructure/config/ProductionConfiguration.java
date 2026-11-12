package vn.xuannhashop.infrastructure.config;

import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.InitializingBean;

@Configuration
@Profile("prod")
public class ProductionConfiguration {
    @Bean InitializingBean productionGuard(Environment env) {
        return () -> {
            if (Arrays.asList(env.getActiveProfiles()).contains("dev") || env.getProperty("app.seed",Boolean.class,false))
                throw new IllegalStateException("Production must not enable dev profile or demo seed");
            if (!env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false))
                throw new IllegalStateException("Production requires Secure session cookies");
            if (env.getRequiredProperty("spring.datasource.url").contains("/xuannhashop_dev"))
                throw new IllegalStateException("Production must use a separate database");
            if (env.getProperty("app.sepay.enabled",Boolean.class,false) &&
                (env.getProperty("app.sepay.webhook-secret","").length()<32 || env.getProperty("app.sepay.channel","dev").equals("dev")))
                throw new IllegalStateException("Production SePay requires a dedicated channel and strong webhook secret");
        };
    }
}
