package dev.chanler.researcher.interfaces.dto.resp;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author: Chanler
 */
@Builder
public class ResearchStatusRespDTO {
    private String id;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
}
