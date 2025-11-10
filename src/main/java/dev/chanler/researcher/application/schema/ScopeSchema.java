package dev.chanler.researcher.application.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

/**
 * @author: Chanler
 */
public class ScopeSchema {

    public record ClarifyWithUserSchema(
            @JsonProperty(value = "need_clarification")
            @JsonPropertyDescription("Whether the user needs to be asked a clarifying question.")
            @NotNull
            boolean needClarification,

            @JsonProperty(value = "question")
            @JsonPropertyDescription("A question to ask the user to clarify the report scope")
            String question,

            @JsonProperty(value = "verification")
            @JsonPropertyDescription("Verify message that we will start research after the user has provided the necessary information.")
            String verification
    ) {
    }

    public record ResearchQuestion(
            @JsonProperty(value = "research_brief")
            @JsonPropertyDescription("A research question that will be used to guide the research.")
            @NotNull
            String researchBrief
    ) {
    }
}
