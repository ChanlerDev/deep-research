package dev.chanler.researcher.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.application.state.ResearcherState;
import dev.chanler.researcher.application.state.SupervisorState;
import dev.chanler.researcher.application.tool.annotation.SupervisorTool;
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
    private Integer max_concurrent_research_units = 3;
    private Integer max_researcher_iterations = 6;

    private static final String SUPERVISOR_STAGE = SupervisorTool.class.getSimpleName();

    public void run(SupervisorState supervisorState) {
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(supervisorState.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(supervisorState.getResearchId()))
                .build();
        SystemMessage systemMessage = SystemMessage.from(
                StrUtil.format(LEAD_RESEARCHER_PROMPT,DateUtil.today(), max_concurrent_research_units, max_researcher_iterations));
        agent.getMemory().add(systemMessage);
        agent.getMemory().add(UserMessage.from(supervisorState.getResearchBrief()));
        plan(agent, supervisorState);
    }

    private void plan(AgentAbility agent, SupervisorState supervisorState) {
        while (supervisorState.getResearchIterations() < max_researcher_iterations) {
            // 1. 获取决策
            List<ToolSpecification> toolSpecifications = toolRegistry.getToolSpecifications(SUPERVISOR_STAGE);
            ChatRequest chatRequest = ChatRequest.builder()
                    .toolSpecifications(toolSpecifications)
                    .messages(agent.getMemory().messages())
                    .build();
            ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            agent.getMemory().add(chatResponse.aiMessage());

            // 2. 执行工具
            action(agent, chatResponse.aiMessage().toolExecutionRequests(), supervisorState);
            
            // 3. 是否终止
            if (!chatResponse.aiMessage().hasToolExecutionRequests()) {
                break;
            }
            if (chatResponse.aiMessage().toolExecutionRequests().stream()
                    .anyMatch(toolRequest -> "researchComplete".equals(toolRequest.name()))) {
                break;
            }
            
            supervisorState.setResearchIterations(supervisorState.getResearchIterations() + 1);
        }
    }

    private void action(AgentAbility agent, List<ToolExecutionRequest> toolExecutionRequests, SupervisorState supervisorState) {
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
                    continue;
                }
                
                ResearcherState researcherState = ResearcherState.builder()
                        .researchId(supervisorState.getResearchId())
                        .researchTopic(researchTopic)
                        .researchIterations(0)
                        .rawNotes(new ArrayList<>())
                        .build();
                
                result = researcherAgent.run(researcherState);
            } else {
                var executor = toolRegistry.getExecutor(toolExecutionRequest.name());
                if (executor == null) {
                    log.warn("No executor found for tool {} in stage {}", toolExecutionRequest.name(), SUPERVISOR_STAGE);
                    continue;
                }
                result = executor.execute(toolExecutionRequest, null);
            }
            
            if (toolExecutionRequest.name().equals("thinkTool") 
                    || toolExecutionRequest.name().equals("conductResearch")) {
                supervisorState.getNotes().add(result);
            }
            
            agent.getMemory().add(ToolExecutionResultMessage.from(toolExecutionRequest, result));
        }
    }
}
