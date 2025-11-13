package dev.chanler.researcher.application.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * @author: Chanler
 */
@Data
public class SummarySchema {
    @JsonPropertyDescription("A comprehensive summary of the webpage content, highlighting the main points and key information.")
    private String summary;
    
    @JsonProperty("key_excerpts")
    @JsonPropertyDescription("Important direct quotes or excerpts from the content that support the summary.")
    private String keyExcerpts;
}
