package dev.chanler.researcher.infra.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author: Chanler
 */

@Data
@EqualsAndHashCode
public class ModelProp {
    private String name;
    private String model;
    private String baseUrl;
    private String apiKey;
}
