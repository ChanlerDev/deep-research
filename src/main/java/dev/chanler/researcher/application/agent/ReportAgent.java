package dev.chanler.researcher.application.agent;

import org.springframework.stereotype.Component;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static dev.chanler.researcher.application.prompt.ReportPrompts.REPORT_AGENT_PROMPT;

/**
 * Report Agent - generates a final report based on researchers' notes
 * @author: Chanler
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportAgent {
    private final ModelHandler modelHandler;

    public String run(DeepResearchState state) {
        AgentAbility agent = AgentAbility.builder()
                .memory(MessageWindowChatMemory.withMaxMessages(100))
                .chatModel(modelHandler.getModel(state.getResearchId()))
                .streamingChatModel(modelHandler.getStreamModel(state.getResearchId()))
                .build();
        UserMessage userMessage = UserMessage.from(
            StrUtil.format(REPORT_AGENT_PROMPT,
                state.getResearchBrief(),
                DateUtil.today(),
                StrUtil.join("\n", state.getSupervisorNotes())
        ));
        agent.getMemory().add(userMessage);
        action(agent, state);
        return state.getReport();
    }

    public void action(AgentAbility agent, DeepResearchState state) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(agent.getMemory().messages())
                .build();
        ChatResponse chatResponse = agent.getChatModel().doChat(chatRequest);
        TokenUsage tokenUsage = chatResponse.tokenUsage();
        agent.getMemory().add(chatResponse.aiMessage());
        state.setReport(chatResponse.aiMessage().text());
    }
}
