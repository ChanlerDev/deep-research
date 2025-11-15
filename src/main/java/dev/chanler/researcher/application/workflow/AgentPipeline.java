package dev.chanler.researcher.application.workflow;

import dev.chanler.researcher.application.agent.ScopeAgent;
import dev.chanler.researcher.application.agent.SupervisorAgent;
import dev.chanler.researcher.application.agent.ReportAgent;
import dev.chanler.researcher.application.data.PipelineIn;
import dev.chanler.researcher.application.data.WorkflowStatus;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.chanler.researcher.infra.exception.WorkflowException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentPipeline {
    private final ScopeAgent scopeAgent;
    private final SupervisorAgent supervisorAgent;
    private final ReportAgent reportAgent;

    // TODO: 应当是传入 DeepResearchState，在 Service 层查询对应 id 的 State，没有就创建
    public void run(PipelineIn pipelineIn) {
        // 使用统一的 DeepResearchState 维护整个研究流程的状态
        DeepResearchState state = DeepResearchState.builder()
                .researchId(pipelineIn.getResearchId())
                .originalInput(pipelineIn.getContent())
                .status(WorkflowStatus.QUEUE)
                .supervisorIterations(0)
                .researcherIterations(0)
                .supervisorNotes(new ArrayList<>())
                .researcherNotes(new ArrayList<>())
                .searchResults(new HashMap<>())
                .searchNotes(new ArrayList<>())
                .build();

        try {
            state.setStatus(WorkflowStatus.START);

            // Phase 1: Scope - 确定研究范围和问题
            scopeAgent.run(state);

            String status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Scope phase failed for researchId={}, status={} ", pipelineIn.getResearchId(), status);
                return;
            }
            if (WorkflowStatus.NEED_CLARIFICATION.equals(status)) {
                log.info("Scope phase requires clarification for researchId={}", pipelineIn.getResearchId());
                return;
            }
            if (!WorkflowStatus.IN_SCOPE.equals(status)) {
                log.warn("Unexpected status after Scope phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                return;
            }

            // Phase 2: Supervisor - 执行研究并收集信息
            supervisorAgent.run(state);

            status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Supervisor phase failed for researchId={}, status={}", pipelineIn.getResearchId(), status);
                return;
            }
            if (!WorkflowStatus.IN_RESEARCH.equals(status)) {
                log.warn("Unexpected status after Supervisor phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                return;
            }

            // Phase 3: Report - 生成最终报告
            reportAgent.run(state);

            status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Report phase failed for researchId={}, status={}", pipelineIn.getResearchId(), status);
                return;
            }
            if (!WorkflowStatus.IN_REPORT.equals(status)) {
                log.warn("Unexpected status after Report phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                return;
            }

            state.setStatus(WorkflowStatus.COMPLETED);
            log.info("Final report generated for researchId={}", pipelineIn.getResearchId());
        } catch (WorkflowException e) {
            state.setStatus(WorkflowStatus.FAILED);
            log.error("Workflow failed for researchId={}", pipelineIn.getResearchId(), e);
        }
    }
}
