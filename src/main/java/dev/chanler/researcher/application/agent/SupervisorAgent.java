package dev.chanler.researcher.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import dev.chanler.researcher.infra.data.EventType;
import dev.chanler.researcher.application.data.WorkflowStatus;
import dev.chanler.researcher.infra.util.EventPublisher;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.chanler.researcher.application.tool.annotation.SupervisorTool;
import dev.chanler.researcher.infra.exception.WorkflowException;
import dev.chanler.researcher.application.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.chanler.researcher.application.prompt.SupervisorPrompts.LEAD_RESEARCHER_PROMPT;

/**
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
// TODO: 实现 token 统计
public class SupervisorAgent {
    private final ModelHandler modelHandler;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final ResearcherAgent researcherAgent;
    private final EventPublisher eventPublisher;
    private Integer max_concurrent_research_units = 3;
    private Integer max_researcher_iterations = 6;

    private static final String SUPERVISOR_STAGE = SupervisorTool.class.getSimpleName();

    public void run(DeepResearchState state) {
        state.setStatus(WorkflowStatus.IN_RESEARCH);
        Long supervisorEventId = eventPublisher.publishEvent(state.getResearchId(), 
                EventType.SUPERVISOR, "开始规划研究路线...", state.getResearchBrief());
        state.setCurrentSupervisorEventId(supervisorEventId);
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(state.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(state.getResearchId()))
                .build();
        SystemMessage systemMessage = SystemMessage.from(
                StrUtil.format(LEAD_RESEARCHER_PROMPT,DateUtil.today(), max_concurrent_research_units, max_researcher_iterations));
        agent.getMemory().add(systemMessage);
        agent.getMemory().add(UserMessage.from(state.getResearchBrief()));
        plan(agent, state);
    }

    private void plan(AgentAbility agent, DeepResearchState state) {
        while (state.getSupervisorIterations() < max_researcher_iterations) {
            // 1. 获取决策
            List<ToolSpecification> toolSpecifications = toolRegistry.getToolSpecifications(SUPERVISOR_STAGE);
            ChatRequest chatRequest = ChatRequest.builder()
                    .toolSpecifications(toolSpecifications)
                    .messages(agent.getMemory().messages())
                    .build();
            ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            state.setTotalInputTokens(state.getTotalInputTokens() + tokenUsage.inputTokenCount());
            state.setTotalOutputTokens(state.getTotalOutputTokens() + tokenUsage.outputTokenCount());
            agent.getMemory().add(chatResponse.aiMessage());

            // 2. 执行工具
            action(agent, chatResponse.aiMessage().toolExecutionRequests(), state);
            
            // 3. 是否终止
            if (!chatResponse.aiMessage().hasToolExecutionRequests()) {
                break;
            }
            if (chatResponse.aiMessage().toolExecutionRequests().stream()
                    .anyMatch(toolRequest -> "researchComplete".equals(toolRequest.name()))) {
                break;
            }
            
            state.setSupervisorIterations(state.getSupervisorIterations() + 1);
        }
    }

    private void action(AgentAbility agent, List<ToolExecutionRequest> toolExecutionRequests, DeepResearchState state) {
        if (toolExecutionRequests == null || toolExecutionRequests.isEmpty()) {
            return;
        }
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            String result;
            
            if ("conductResearch".equals(toolExecutionRequest.name())) {
                String researchTopic = null;
                try {
                    var argsNode = objectMapper.readTree(toolExecutionRequest.arguments());
                    researchTopic = argsNode.get("researchTopic").asText();
                } catch (Exception e) {
                    log.error("Failed to parse conductResearch arguments", e);
                    throw new WorkflowException("Failed to parse conductResearch arguments", e);
                }
                
                Long planEventId = eventPublisher.publishEvent(state.getResearchId(), EventType.SUPERVISOR,
                        "正在研究: " + researchTopic, null, state.getCurrentSupervisorEventId());
                state.setCurrentResearchEventId(planEventId);
                
                // 设置 researcher 相关字段
                state.setResearchTopic(researchTopic);
                state.setResearcherIterations(0);
                state.setResearcherNotes(new ArrayList<>());
                
                result = researcherAgent.run(state);
            } else {
                var executor = toolRegistry.getExecutor(toolExecutionRequest.name());
                if (executor == null) {
                    log.warn("No executor found for tool {} in stage {}", toolExecutionRequest.name(), SUPERVISOR_STAGE);
                    continue;
                }
                result = executor.execute(toolExecutionRequest, null);
            }
            
            if (toolExecutionRequest.name().equals("thinkTool")) {
                eventPublisher.publishEvent(state.getResearchId(), EventType.SUPERVISOR,
                        "思考中...", result, state.getCurrentSupervisorEventId());
                state.getSupervisorNotes().add(result);
            } else if (toolExecutionRequest.name().equals("conductResearch")) {
                state.getSupervisorNotes().add(result);
            }
            
            agent.getMemory().add(ToolExecutionResultMessage.from(toolExecutionRequest, result));
        }
    }
}
