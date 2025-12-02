package dev.chanler.researcher.interfaces.dto.resp;

import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.domain.entity.WorkflowEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author: Chanler
 */
@Data
@Builder
public class ResearchMessageRespDTO {
    private String id;
    private String status;
    private List<ChatMessage> messages;
    private List<WorkflowEvent> events;
    private LocalDateTime startTime;
    private LocalDateTime updateTime;
    private LocalDateTime completeTime;
    private Long totalInputTokens;
    private Long totalOutputTokens;
}
