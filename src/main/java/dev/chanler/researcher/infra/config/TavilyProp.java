package dev.chanler.researcher.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Tavily API
 * @author: Chanler
 */
@Configuration
@ConfigurationProperties(prefix = "tavily")
@Data
public class TavilyProp {
    private String apiKey;
    private String baseUrl;
}
