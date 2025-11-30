package dev.chanler.researcher.interfaces.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author: Chanler
 */
@Data
@Builder
public class ResearchStatusRespDTO {
    private String id;
    private String title;
    private String model;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
}
