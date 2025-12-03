package dev.chanler.researcher.interfaces.dto.req;

import lombok.Data;

/**
 * Add model request DTO
 * @author: Chanler
 */
@Data
public class AddModelReqDTO {
    
    private String name;
    private String model;
    private String baseUrl;
    private String apiKey;
}
