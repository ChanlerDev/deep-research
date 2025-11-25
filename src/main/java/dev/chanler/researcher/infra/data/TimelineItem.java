package dev.chanler.researcher.infra.data;

import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.domain.entity.WorkflowEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: Chanler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineItem {
    private String kind;
    private String researchId;
    private Integer sequenceNo;
    private ChatMessage message;
    private WorkflowEvent event;
}
