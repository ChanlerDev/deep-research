package dev.chanler.researcher.application.workflow;

import dev.chanler.researcher.application.agent.ScopeAgent;
import dev.chanler.researcher.application.agent.SupervisorAgent;
import dev.chanler.researcher.application.agent.ReportAgent;
import dev.chanler.researcher.infra.data.EventType;
import dev.chanler.researcher.application.data.PipelineIn;
import dev.chanler.researcher.application.data.WorkflowStatus;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.chanler.researcher.domain.mapper.ResearchSessionMapper;
import dev.chanler.researcher.infra.exception.WorkflowException;
import dev.chanler.researcher.infra.sse.SseHub;
import dev.chanler.researcher.infra.util.EventPublisher;
import dev.chanler.researcher.infra.util.SequenceUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
    private final SequenceUtil sequenceUtil;
    private final SseHub sseHub;
    private final ResearchSessionMapper researchSessionMapper;
    private final EventPublisher eventPublisher;

    // TODO: 应当是传入 DeepResearchState，在 Service 层查询对应 id 的 State，没有就创建
    // TODO：后续应当支持接着发送消息，而非只能启动一次研究
    // TODO：实现自定义线程池运行，实现排队限流
    @Async
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
                .totalInputTokens(0L)
                .totalOutputTokens(0L)
                .build();

        try {
            state.setStatus(WorkflowStatus.START);
            updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.START, state);

            // Phase 1: Scope - 确定研究范围和问题
            scopeAgent.run(state);

            String status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Scope phase failed for researchId={}, status={} ", pipelineIn.getResearchId(), status);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "范围分析失败", null);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }
            if (WorkflowStatus.NEED_CLARIFICATION.equals(status)) {
                log.info("Scope phase requires clarification for researchId={}", pipelineIn.getResearchId());
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.NEED_CLARIFICATION, state);
                return;
            }
            if (!WorkflowStatus.IN_SCOPE.equals(status)) {
                log.warn("Unexpected status after Scope phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                state.setStatus(WorkflowStatus.FAILED);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "范围分析状态异常", "status=" + status);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }

            // Phase 2: Supervisor - 执行研究并收集信息
            supervisorAgent.run(state);

            status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Supervisor phase failed for researchId={}, status={}", pipelineIn.getResearchId(), status);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "研究规划失败", null);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }
            if (!WorkflowStatus.IN_RESEARCH.equals(status)) {
                log.warn("Unexpected status after Supervisor phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                state.setStatus(WorkflowStatus.FAILED);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "研究规划状态异常", "status=" + status);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }

            // Phase 3: Report - 生成最终报告
            reportAgent.run(state);

            status = state.getStatus();
            if (WorkflowStatus.FAILED.equals(status)) {
                log.warn("Report phase failed for researchId={}, status={}", pipelineIn.getResearchId(), status);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "报告生成失败", null);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }
            if (!WorkflowStatus.IN_REPORT.equals(status)) {
                log.warn("Unexpected status after Report phase for researchId={}, status={}", pipelineIn.getResearchId(), status);
                state.setStatus(WorkflowStatus.FAILED);
                eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR, "报告生成状态异常", "status=" + status);
                updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
                return;
            }

            state.setStatus(WorkflowStatus.COMPLETED);
            updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.COMPLETED, state);
            log.info("Final report generated for researchId={}", pipelineIn.getResearchId());
        } catch (WorkflowException e) {
            state.setStatus(WorkflowStatus.FAILED);
            eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR,
                    "研究过程中发生错误", e.getMessage());
            updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
            log.error("Workflow failed for researchId={}", pipelineIn.getResearchId(), e);
        } catch (Exception e) {
            state.setStatus(WorkflowStatus.FAILED);
            eventPublisher.publishEvent(pipelineIn.getResearchId(), EventType.ERROR,
                    "系统错误", e.getMessage());
            updateResearchSession(pipelineIn.getResearchId(), WorkflowStatus.FAILED, state);
            log.error("Unexpected error for researchId={}", pipelineIn.getResearchId(), e);
        } finally {
            sequenceUtil.reset(pipelineIn.getResearchId());
            sseHub.complete(pipelineIn.getResearchId(), state.getStatus());
        }
    }

    private void updateResearchSession(String researchId, String status, DeepResearchState state) {
        boolean setStartTime = WorkflowStatus.START.equals(status);
        boolean setCompleteTime = WorkflowStatus.COMPLETED.equals(status)
                || WorkflowStatus.FAILED.equals(status)
                || WorkflowStatus.NEED_CLARIFICATION.equals(status);
        researchSessionMapper.updateSession(researchId, status, setStartTime, setCompleteTime,
                state.getTotalInputTokens(), state.getTotalOutputTokens());
    }
}
