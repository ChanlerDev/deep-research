package dev.chanler.researcher.application.state;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * State for researcher agent, tracks research iterations and findings
 * @author: Chanler
 */
@Data
@Builder
public class ResearcherState {
    private String researchId;
    private String researchTopic;
    private Integer researchIterations;
    private List<String> rawNotes;
    private String compressedResearch;
}
