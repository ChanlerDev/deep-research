package dev.chanler.researcher.application.state;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.chanler.researcher.infra.client.TavilyClient.SearchResult;

/**
 * State for search agent
 * @author: Chanler
 */
@Data
@Builder
public class SearchState {
    private String researchId;
    private String query;
    private Integer maxResults;
    private String topic;
    private Map<String, SearchResult> searchResults;
    private List<String> rawResults;
}
