package dev.chanler.researcher.infra.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 统一设置服务端 JSON 输出的默认时区
 * @author: Chanler
 */
@Configuration
public class TimezoneConfig {

    public static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    @Value("${app.time-zone:" + DEFAULT_ZONE_ID + "}")
    private String configuredZoneId;

    @PostConstruct
    public void alignSystemTimezone() {
        TimeZone zone = TimeZone.getTimeZone(configuredZoneId);
        TimeZone.setDefault(zone);
        System.setProperty("user.timezone", zone.getID());
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonTimezoneCustomizer() {
        return builder -> builder.timeZone(TimeZone.getTimeZone(configuredZoneId));
    }
}
