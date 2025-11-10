package dev.chanler.researcher.application.state;

import dev.chanler.researcher.application.schema.ScopeSchema;
import lombok.Builder;
import lombok.Data;

/**
 * @author: Chanler
 */
@Data
@Builder
public class ScopeState {
    private String researchId;
    private String input;
    private String researchBrief;
    private ScopeSchema.ClarifyWithUserSchema clarifyWithUserSchema;
    private ScopeSchema.ResearchQuestion researchQuestion;
}
