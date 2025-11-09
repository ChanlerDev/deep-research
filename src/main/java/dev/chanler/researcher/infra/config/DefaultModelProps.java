package dev.chanler.researcher.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author: Chanler
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Data
public class DefaultModelProps {
    private List<ModelProp> config;
}
