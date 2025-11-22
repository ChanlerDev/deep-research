package dev.chanler.researcher.application.data;

import lombok.Builder;
import lombok.Data;

/**
 * @author: Chanler
 */
@Data
@Builder
public class PipelineIn {
    private String researchId;
    private Integer userId;
    private String content;
}
