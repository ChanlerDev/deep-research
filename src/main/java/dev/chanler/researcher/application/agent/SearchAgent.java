package dev.chanler.researcher.application.agent;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chanler.researcher.infra.data.EventType;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.infra.util.EventPublisher;
import dev.chanler.researcher.application.schema.SummarySchema;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.chanler.researcher.infra.client.TavilyClient;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.output.JsonSchemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static dev.chanler.researcher.application.prompt.SearchPrompts.SUMMARIZE_WEBPAGE_PROMPT;

/**
 * Search Agent - performs web search and content summarization
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchAgent {
    private final ModelHandler modelHandler;
    private final TavilyClient tavilyClient;
    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;
    
    public String run(DeepResearchState state) {
        Long searchEventId = eventPublisher.publishEvent(state.getResearchId(), EventType.SEARCH,
                "正在搜索: " + state.getQuery(), null, state.getCurrentResearchEventId());
        state.setCurrentSearchEventId(searchEventId);
        
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(state.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(state.getResearchId()))
                .build();
            
        plan(state);
        action(agent, state);
        return summarize(agent, state);
    }
    
    private void plan(DeepResearchState state) {
        // execute Tavily search
        TavilyClient.TavilyResponse response = tavilyClient.search(
            state.getQuery(),
            state.getMaxResults(),
            state.getTopic(),
            true
        );
        
        if (response.results().isEmpty()) {
            log.warn("No search results for: {}", state.getQuery());
            return;
        }
        
        // 利用 URL 去重
        Map<String, TavilyClient.SearchResult> uniqueResults = new HashMap<>();
        for (TavilyClient.SearchResult result : response.results()) {
            if (result.url() != null && !uniqueResults.containsKey(result.url())) {
                uniqueResults.put(result.url(), result);
            }
        }
        
        state.setSearchResults(uniqueResults);
        eventPublisher.publishEvent(state.getResearchId(), EventType.SEARCH,
                "找到 " + uniqueResults.size() + " 个相关结果", null, state.getCurrentSearchEventId());
    }
    
    private void action(AgentAbility agent, DeepResearchState state) {
        // 空值判断
        if (state.getSearchResults().isEmpty()) {
            log.warn("No search results to process");
            return;
        }
        
        // 处理并总结结果
        for (TavilyClient.SearchResult result : state.getSearchResults().values()) {
            String content = result.rawContent() != null && !result.rawContent().isEmpty()
                ? result.rawContent()
                : result.content();
            
            if (content != null && content.length() > 500) {
                try {
                    SummarySchema summary = summarizeWebpage(agent, state, content);
                    String formatted = StrUtil.format(
                        "[%s]\nURL: %s\n<summary>%s</summary>\n<key_excerpts>%s</key_excerpts>",
                        result.title(), result.url(), summary.getSummary(), summary.getKeyExcerpts()
                    );
                    state.getSearchNotes().add(formatted);
                } catch (Exception e) {
                    log.warn("Failed to summarize {}", result.url());
                    state.getSearchNotes().add(StrUtil.format("[%s]\nURL: %s\n%s",
                        result.title(), result.url(), result.content()));
                }
            } else {
                state.getSearchNotes().add(StrUtil.format("[%s]\nURL: %s\n%s",
                    result.title(), result.url(), content));
            }
        }
    }
    
    private SummarySchema summarizeWebpage(AgentAbility agent, DeepResearchState state, String webpageContent) {
        try {
            String prompt = StrUtil.format(SUMMARIZE_WEBPAGE_PROMPT, webpageContent, DateUtil.today());
            
            JsonSchema jsonSchema = JsonSchemas.jsonSchemaFrom(SummarySchema.class)
                .orElseThrow(() -> new IllegalStateException("Failed to generate JSON schema"));
            
            ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
            
            ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .responseFormat(responseFormat)
                .build();
            
            ChatResponse chatResponse = agent.getChatModel().chat(chatRequest);
            TokenUsage tokenUsage = chatResponse.tokenUsage();
            state.setTotalInputTokens(state.getTotalInputTokens() + tokenUsage.inputTokenCount());
            state.setTotalOutputTokens(state.getTotalOutputTokens() + tokenUsage.outputTokenCount());
            return objectMapper.readValue(chatResponse.aiMessage().text(), SummarySchema.class);
            
        } catch (Exception e) {
            log.error("Webpage summarization failed", e);
            SummarySchema fallback = new SummarySchema();
            fallback.setSummary(webpageContent.substring(0, Math.min(1000, webpageContent.length())));
            fallback.setKeyExcerpts("");
            return fallback;
        }
    }
    
    private String summarize(AgentAbility agent, DeepResearchState state) {
        if (state.getSearchNotes().isEmpty()) {
            return "No search results found for: " + state.getQuery();
        }
        eventPublisher.publishEvent(state.getResearchId(), EventType.SEARCH,
                "已分析并整理搜索结果", null, state.getCurrentSearchEventId());
        
        StringBuilder output = new StringBuilder();
        output.append(StrUtil.format("Search results for query: '%s'\n\n", state.getQuery()));
        
        int num = 1;
        for (String result : state.getSearchNotes()) {
            output.append(StrUtil.format("\n--- SOURCE %d ---\n", num++));
            output.append(result);
            output.append("\n").append("-".repeat(80)).append("\n");
        }
        
        return output.toString();
    }
}
