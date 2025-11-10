package dev.chanler.researcher.application.agent;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.application.schema.ScopeSchema;
import dev.chanler.researcher.application.state.ScopeState;
import dev.chanler.researcher.infra.util.MemoryUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.output.JsonSchemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static dev.chanler.researcher.application.prompt.ScopePrompts.CLARIFY_WITH_USER_INSTRUCTIONS;
import static dev.chanler.researcher.application.prompt.ScopePrompts.TRANSFORM_MESSAGES_INTO_RESEARCH_TOPIC_PROMPT;

/**
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
// TODO: 实现 token 统计
public class ScopeAgent {
    private final ModelHandler modelHandler;
    private final ObjectMapper objectMapper;

    public void run(ScopeState scopeState) {
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(scopeState.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(scopeState.getResearchId()))
                .build();
        clarifyUserInstructions(agent, scopeState);
        if (scopeState.getClarifyWithUserSchema().needClarification()) {
            return;
        }
        writeResearchBrief(agent, scopeState);
    }

    private void clarifyUserInstructions(AgentAbility agent, ScopeState scopeState) {
        agent.getMemory().add(UserMessage.from(scopeState.getInput()));
        String messages = MemoryUtil.toBufferString(agent.getMemory());
        UserMessage userMessage = UserMessage.from(
                StrUtil.format(CLARIFY_WITH_USER_INSTRUCTIONS, messages, DateUtil.today()));
        JsonSchema jsonSchema = JsonSchemas.jsonSchemaFrom(ScopeSchema.ClarifyWithUserSchema.class)
                .orElseThrow(() -> new IllegalStateException("Failed to generate JSON schema for ClarifyWithUserSchema"));
        ResponseFormat responseFormat = ResponseFormat.builder()
                .jsonSchema(jsonSchema)
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();
        ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
        TokenUsage tokenUsage = chatResponse.tokenUsage();
        String jsonResponse = chatResponse.aiMessage().text();
        try {
            ScopeSchema.ClarifyWithUserSchema clarifyResult = objectMapper.readValue(
                    jsonResponse, ScopeSchema.ClarifyWithUserSchema.class);
            if (clarifyResult.needClarification()) {
                agent.getMemory().add(AiMessage.from(clarifyResult.question()));
            } else {
                agent.getMemory().add(AiMessage.from(clarifyResult.verification()));
            }
            // TODO: 推送
            scopeState.setClarifyWithUserSchema(clarifyResult);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
        }
    }

    private void writeResearchBrief(AgentAbility agent, ScopeState scopeState) {
        String messages = MemoryUtil.toBufferString(agent.getMemory());
        UserMessage userMessage = UserMessage.from(
                StrUtil.format(TRANSFORM_MESSAGES_INTO_RESEARCH_TOPIC_PROMPT, messages, DateUtil.today()));
        JsonSchema jsonSchema = JsonSchemas.jsonSchemaFrom(ScopeSchema.ResearchQuestion.class)
                .orElseThrow(() -> new IllegalStateException("Failed to generate JSON schema for ResearchQuestion"));
        ResponseFormat responseFormat = ResponseFormat.builder()
                .jsonSchema(jsonSchema)
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .responseFormat(responseFormat)
                .build();
        ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
        TokenUsage tokenUsage = chatResponse.tokenUsage();
        String jsonResponse = chatResponse.aiMessage().text();
        try {
            ScopeSchema.ResearchQuestion researchQuestion = objectMapper.readValue(
                    jsonResponse, ScopeSchema.ResearchQuestion.class);
            agent.getMemory().add(AiMessage.from(researchQuestion.researchBrief()));
            // TODO: 推送
            scopeState.setResearchQuestion(researchQuestion);
        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", jsonResponse, e);
        }
    }
}
