package dev.chanler.researcher.application.state;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * State for report agent, generate final report
 * @author: Chanler
 */
@Data
@Builder
public class ReportState {
    private String researchId;
    private String researchTopic;
    private String researchBrief;
    private List<String> notes;
    private String report;
}
