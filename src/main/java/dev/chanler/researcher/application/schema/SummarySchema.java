package dev.chanler.researcher.application.schema;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * @author: Chanler
 */
@Data
public class SummarySchema {
    @Description("A comprehensive summary of the webpage content, highlighting the main points and key information.")
    private String summary;
    
    @Description("Important direct quotes or excerpts from the content that support the summary.")
    private String keyExcerpts;
}
