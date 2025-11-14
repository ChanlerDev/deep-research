package dev.chanler.researcher.application.state;

import java.util.List;
import java.util.Map;

import dev.chanler.researcher.application.schema.ScopeSchema;
import dev.chanler.researcher.infra.client.TavilyClient;
import lombok.Builder;
import lombok.Data;

/**
 * State for deep research workflow
 * @author: Chanler
 */
@Data
@Builder
public class DeepResearchState {

    // === 基础信息 ===
    private String researchId;
    private String originalInput;

    // === Scope 阶段产物 ===
    private ScopeSchema.ClarifyWithUserSchema clarifyWithUserSchema;
    private ScopeSchema.ResearchQuestion researchQuestion;
    private String researchBrief;

    // === Supervisor 阶段 ===
    private Integer supervisorIterations;
    private List<String> supervisorNotes;

    // === Researcher 阶段 ===
    private String researchTopic;
    private Integer researcherIterations;
    private List<String> researcherNotes;
    private String compressedResearch;

    // === Search 阶段 ===
    private String query;
    private Integer maxResults;
    private String topic;
    private Map<String, TavilyClient.SearchResult> searchResults;
    private List<String> searchNotes;

    // === Report 阶段 ===
    private String report;
}
