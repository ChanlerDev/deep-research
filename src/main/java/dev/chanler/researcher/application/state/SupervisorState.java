package dev.chanler.researcher.application.state;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author: Chanler
 */
@Data
@Builder
public class SupervisorState {
    private String researchId;
    private String researchBrief;
    private Integer researchIterations;
    private List<String> notes;
}
