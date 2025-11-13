package dev.chanler.researcher.application.agent;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.application.state.ResearcherState;
import dev.chanler.researcher.application.state.SearchState;
import dev.chanler.researcher.application.tool.annotation.ResearcherTool;
import dev.chanler.researcher.application.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
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
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static dev.chanler.researcher.application.prompt.ResearcherPrompts.*;

/**
 * Researcher Agent - performs iterative web searches and synthesis
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResearcherAgent {
    private final ModelHandler modelHandler;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final SearchAgent searchAgent;
    private Integer max_researcher_iterations = 10;

    private static final String RESEARCHER_STAGE = ResearcherTool.class.getSimpleName();

    public String run(ResearcherState researcherState) {
        log.info("ResearcherAgent run: researchId='{}', topic='{}'", researcherState.getResearchId(), researcherState.getResearchTopic());
        
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(researcherState.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(researcherState.getResearchId()))
                .build();
        
        SystemMessage systemMessage = SystemMessage.from(
            StrUtil.format(RESEARCH_AGENT_PROMPT, DateUtil.today())
        );
        agent.getMemory().add(systemMessage);
        agent.getMemory().add(UserMessage.from(researcherState.getResearchTopic()));
        
        plan(agent, researcherState);
        return compressResearch(agent, researcherState);
    }

    private void plan(AgentAbility agent, ResearcherState researcherState) {
        
        while (researcherState.getResearchIterations() < max_researcher_iterations) {
            // 1. 获取决策
            List<ToolSpecification> toolSpecifications = toolRegistry.getToolSpecifications(RESEARCHER_STAGE);
            ChatRequest chatRequest = ChatRequest.builder()
                    .toolSpecifications(toolSpecifications)
                    .build();
            ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            agent.getMemory().add(chatResponse.aiMessage());

            // 2. 执行工具
            action(agent, chatResponse.aiMessage().toolExecutionRequests(), researcherState);
            
            // 3. 检查是否继续
            if (!chatResponse.aiMessage().hasToolExecutionRequests()) {
                break;
            }
            
            researcherState.setResearchIterations(researcherState.getResearchIterations() + 1);
        }
    }

    private void action(AgentAbility agent, List<ToolExecutionRequest> toolExecutionRequests, ResearcherState researcherState) {
        if (toolExecutionRequests == null || toolExecutionRequests.isEmpty()) {
            return;
        }
        
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            String result;
            
            if ("tavilySearch".equals(toolExecutionRequest.name())) {
                try {
                    var argsNode = objectMapper.readTree(toolExecutionRequest.arguments());
                    String query = argsNode.get("query").asText();
                    int maxResults = argsNode.has("maxResults") ? argsNode.get("maxResults").asInt() : 3;
                    String topic = argsNode.has("topic") ? argsNode.get("topic").asText() : "general";
                    
                    SearchState searchState = SearchState.builder()
                        .researchId(researcherState.getResearchId())
                        .query(query)
                        .maxResults(maxResults)
                        .topic(topic)
                        .rawResults(new ArrayList<>())
                        .searchResults(new HashMap<>())
                        .build();
                    
                    result = searchAgent.run(searchState);
                } catch (Exception e) {
                    log.error("Failed to parse tavilySearch arguments", e);
                    continue;
                }
            } else {
                var executor = toolRegistry.getExecutor(toolExecutionRequest.name());
                if (executor == null) {
                    log.warn("No executor found for tool {} in stage {}", toolExecutionRequest.name(), RESEARCHER_STAGE);
                    continue;
                }
                result = executor.execute(toolExecutionRequest, null);
            }
            
            // 收集 rawNotes 即工具执行结果 ThinkTool 和 Search 结果
            researcherState.getRawNotes().add(String.format("[%s] %s", toolExecutionRequest.name(), result));
            
            agent.getMemory().add(ToolExecutionResultMessage.from(toolExecutionRequest, result));
        }
    }

    private String compressResearch(AgentAbility agent, ResearcherState researcherState) {
        String systemPrompt = StrUtil.format(COMPRESS_RESEARCH_SYSTEM_PROMPT, DateUtil.today());
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        // 跳过前两条（ResearcherAgent 的 system + user），只保留工具调用历史
        messages.addAll(agent.getMemory().messages().stream().skip(2).collect(Collectors.toList()));
        messages.add(UserMessage.from(StrUtil.format(COMPRESS_RESEARCH_HUMAN_MESSAGE, researcherState.getResearchTopic())));
        
        ChatRequest compressRequest = ChatRequest.builder()
                .messages(messages)
                .build();
        
        ChatResponse compressResponse = agent.getChatModel().doChat(compressRequest);
        TokenUsage tokenUsage = compressResponse.tokenUsage();
        String compressedResearch = compressResponse.aiMessage().text();
        
        researcherState.setCompressedResearch(compressedResearch);
        
        return compressedResearch;
    }
}
