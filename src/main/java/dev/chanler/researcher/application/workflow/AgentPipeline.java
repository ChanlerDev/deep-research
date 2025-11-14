package dev.chanler.researcher.application.workflow;

import dev.chanler.researcher.application.agent.ScopeAgent;
import dev.chanler.researcher.application.agent.SupervisorAgent;
import dev.chanler.researcher.application.agent.ReportAgent;
import dev.chanler.researcher.application.data.PipelineIn;
import dev.chanler.researcher.application.state.DeepResearchState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.stereotype.Component;

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

    public void run(PipelineIn pipelineIn) {
        // 使用统一的 DeepResearchState 维护整个研究流程的状态
        DeepResearchState state = DeepResearchState.builder()
                .researchId(pipelineIn.getResearchId())
                .originalInput(pipelineIn.getContent())
                .build();

        // Phase 1: Scope - 确定研究范围和问题
        scopeAgent.run(state);

        if (state.getClarifyWithUserSchema() == null) {
            log.warn("ScopeAgent did not produce ClarifyWithUserSchema for researchId={}", pipelineIn.getResearchId());
            return;
        }
        if (state.getClarifyWithUserSchema().needClarification()) {
            return;
        }

        if (state.getResearchQuestion() == null) {
            log.warn("ScopeAgent did not produce ResearchQuestion for researchId={}", pipelineIn.getResearchId());
            return;
        }

        String researchBrief = state.getResearchQuestion().researchBrief();
        state.setResearchBrief(researchBrief);

        // Phase 2: Supervisor - 执行研究并收集信息
        supervisorAgent.run(state);

        // Phase 3: Report - 生成最终报告
        reportAgent.run(state);
        log.info("Final report generated for researchId={}", pipelineIn.getResearchId());
    }
}
