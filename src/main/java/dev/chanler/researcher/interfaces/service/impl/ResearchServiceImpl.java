package dev.chanler.researcher.interfaces.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.chanler.researcher.application.data.WorkflowStatus;
import dev.chanler.researcher.application.state.DeepResearchState;
import dev.chanler.researcher.application.workflow.AgentPipeline;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.domain.entity.ResearchSession;
import dev.chanler.researcher.domain.entity.WorkflowEvent;
import dev.chanler.researcher.domain.entity.Model;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.domain.mapper.ChatMessageMapper;
import dev.chanler.researcher.domain.mapper.ResearchSessionMapper;
import dev.chanler.researcher.domain.mapper.WorkflowEventMapper;
import dev.chanler.researcher.infra.exception.ResearchException;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.SendMessageRespDTO;
import dev.chanler.researcher.infra.config.BudgetProps;
import dev.chanler.researcher.infra.util.CacheUtil;
import dev.chanler.researcher.interfaces.service.ResearchService;
import dev.chanler.researcher.interfaces.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Chanler
 */
@Service
@RequiredArgsConstructor
public class ResearchServiceImpl implements ResearchService {

    private final ResearchSessionMapper researchSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WorkflowEventMapper workflowEventMapper;
    private final AgentPipeline agentPipeline;
    private final CacheUtil cacheUtil;
    private final ModelHandler modelHandler;
    private final BudgetProps budgetConfig;
    private final ModelService modelService;

    @Override
    public CreateResearchRespDTO createResearch(Long userId, Integer num) {
        LambdaQueryWrapper<ResearchSession> queryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .eq(ResearchSession::getStatus, WorkflowStatus.NEW);
        List<ResearchSession> researchSessionList = researchSessionMapper.selectList(queryWrapper);
        int oldNum;
        if (researchSessionList == null || researchSessionList.isEmpty()) {
            oldNum = 0;
        } else {
            oldNum = researchSessionList.size();
        }
        if (num > oldNum) {
            for (int i = 0; i < num - oldNum; i++) {
                ResearchSession researchSession = ResearchSession.builder()
                        .userId(userId)
                        .status(WorkflowStatus.NEW)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();
                researchSessionMapper.insert(researchSession);
                researchSessionList.add(researchSession);
            }
        }
        List<String> researchIds = researchSessionList.stream()
                .sorted((o1, o2) -> o1.getCreateTime().compareTo(o2.getCreateTime()))
                .map(ResearchSession::getId)
                .limit(num)
                .collect(Collectors.toList());
        return CreateResearchRespDTO.builder()
                .researchIds(researchIds)
                .build();
    }

    @Override
    public List<ResearchStatusRespDTO> getResearchList(Long userId) {
        LambdaQueryWrapper<ResearchSession> queryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .orderByDesc(ResearchSession::getUpdateTime);
        List<ResearchSession> sessions = researchSessionMapper.selectList(queryWrapper);
        
        return sessions.stream().map(session -> {
            return ResearchStatusRespDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .model(session.getModelId())
                .status(session.getStatus())
                .startTime(session.getStartTime())
                .updateTime(session.getUpdateTime())
                .completeTime(session.getCompleteTime())
                .totalInputTokens(session.getTotalInputTokens())
                .totalOutputTokens(session.getTotalOutputTokens())
                .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ResearchStatusRespDTO getResearchStatus(Long userId, String researchId) {
        LambdaQueryWrapper<ResearchSession> queryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getId, researchId)
                .eq(ResearchSession::getUserId, userId);
        ResearchSession researchSession = researchSessionMapper.selectOne(queryWrapper);
        if (researchSession == null) {
            throw new ResearchException("研究任务不存在");
        }
        return ResearchStatusRespDTO.builder()
                .id(researchSession.getId())
                .title(researchSession.getTitle())
                .model(researchSession.getModelId())
                .status(researchSession.getStatus())
                .startTime(researchSession.getStartTime())
                .updateTime(researchSession.getUpdateTime())
                .completeTime(researchSession.getCompleteTime())
                .totalInputTokens(researchSession.getTotalInputTokens())
                .totalOutputTokens(researchSession.getTotalOutputTokens())
                .build();
    }

    @Override
    public ResearchMessageRespDTO getResearchMessages(Long userId, String researchId) {
        // TODO：权限验证，该 Research 属于该用户，升级到缓存，在获取状态时 add 缓存
        LambdaQueryWrapper<ResearchSession> checkQueryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .eq(ResearchSession::getId, researchId);
        ResearchSession researchSession = researchSessionMapper.selectOne(checkQueryWrapper);
        if (researchSession == null) {
            throw new ResearchException("研究任务不存在");
        }

        // TODO: 缓存优化
        LambdaQueryWrapper<ChatMessage> chatMessageQueryWrapper = Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getResearchId, researchId);

        LambdaQueryWrapper<WorkflowEvent> workflowEventQueryWrapper = Wrappers.lambdaQuery(WorkflowEvent.class)
                .eq(WorkflowEvent::getResearchId, researchId);

        return ResearchMessageRespDTO.builder()
                .id(researchSession.getId())
                .status(researchSession.getStatus())
                .messages(chatMessageMapper.selectList(chatMessageQueryWrapper))
                .events(workflowEventMapper.selectList(workflowEventQueryWrapper))
                .startTime(researchSession.getStartTime())
                .updateTime(researchSession.getUpdateTime())
                .completeTime(researchSession.getCompleteTime())
                .totalInputTokens(researchSession.getTotalInputTokens())
                .totalOutputTokens(researchSession.getTotalOutputTokens())
                .build();
    }

    @Override
    public SendMessageRespDTO sendMessage(Long userId, String researchId, SendMessageReqDTO sendMessageReqDTO) {
        // CAS 更新状态，幂等处理
        int affected = researchSessionMapper.casUpdateToQueue(researchId, userId);
        if (affected == 0) {
            throw new ResearchException("启动研究异常");
        }
        
        ResearchSession session = researchSessionMapper.selectById(researchId);
        if (session == null) {
            throw new ResearchException("研究不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new ResearchException("无权访问此研究");
        }
        
        String modelId = session.getModelId();
        String budget = session.getBudget();

        // 新会话
        if (modelId == null) {
            if (sendMessageReqDTO.getModelId() == null || sendMessageReqDTO.getModelId().isBlank()) {
                throw new ResearchException("模型不应为空");
            }
            modelId = sendMessageReqDTO.getModelId();
            String title = sendMessageReqDTO.getContent().length() > 20
                ? sendMessageReqDTO.getContent().substring(0, 20)
                : sendMessageReqDTO.getContent();
            budget = sendMessageReqDTO.getBudget();
            if (budget == null || budget.isBlank()) {
                budget = "HIGH";
            }
            researchSessionMapper.setInfoIfNull(researchId, modelId, budget, title);
        }

        Model model = modelService.getModelById(userId, modelId);

        // 注册模型
        modelHandler.addModel(researchId, model);
        
        BudgetProps.BudgetLevel budgetLevel = budgetConfig.getLevel(budget);

        // 保存用户消息
        cacheUtil.saveMessage(researchId, "user", sendMessageReqDTO.getContent());

        // 查询历史消息并转换为 langchain4j ChatMessage
        LambdaQueryWrapper<ChatMessage> historyQuery = Wrappers.lambdaQuery(ChatMessage.class)
                .eq(ChatMessage::getResearchId, researchId)
                .orderByAsc(ChatMessage::getSequenceNo);
        List<ChatMessage> dbMessages = chatMessageMapper.selectList(historyQuery);
        
        List<dev.langchain4j.data.message.ChatMessage> chatHistory =  new ArrayList<>();
        for (ChatMessage msg : dbMessages) {
            if ("user".equals(msg.getRole())) {
                chatHistory.add(UserMessage.from(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                chatHistory.add(AiMessage.from(msg.getContent()));
            }
        }

        // 构建 state 并启动研究流程
        DeepResearchState state = DeepResearchState.builder()
                .researchId(researchId)
                .chatHistory(chatHistory)
                .status(WorkflowStatus.QUEUE)
                // Budget 配置
                .budget(budgetLevel)
                // Supervisor 阶段
                .supervisorIterations(0)
                .conductCount(0)
                .supervisorNotes(new ArrayList<>())
                // Researcher 阶段
                .researcherIterations(0)
                .searchCount(0)
                .researcherNotes(new ArrayList<>())
                // Search 阶段
                .searchResults(new HashMap<>())
                .searchNotes(new ArrayList<>())
                // Token 统计
                .totalInputTokens(0L)
                .totalOutputTokens(0L)
                .build();
        agentPipeline.run(state);

        return SendMessageRespDTO.builder()
                .id(researchId)
                .content("已接受任务")
                .build();
    }
}
