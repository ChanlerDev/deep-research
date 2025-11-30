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
import dev.chanler.researcher.infra.config.ModelProp;
import dev.chanler.researcher.application.model.ModelHandler;
import dev.chanler.researcher.domain.mapper.ChatMessageMapper;
import dev.chanler.researcher.domain.mapper.ResearchSessionMapper;
import dev.chanler.researcher.domain.mapper.WorkflowEventMapper;
import dev.chanler.researcher.infra.exception.ResearchException;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.FreeModelRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.SendMessageRespDTO;
import dev.chanler.researcher.application.model.ModelFactory;
import dev.chanler.researcher.infra.util.CacheUtil;
import dev.chanler.researcher.interfaces.service.ResearchService;
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
    private final ModelFactory modelFactory;

    @Override
    public CreateResearchRespDTO createResearch(Integer userId, Integer num) {
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
    public List<ResearchStatusRespDTO> getResearchList(Integer userId) {
        LambdaQueryWrapper<ResearchSession> queryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .orderByDesc(ResearchSession::getUpdateTime);
        List<ResearchSession> sessions = researchSessionMapper.selectList(queryWrapper);
        
        return sessions.stream().map(session -> {
            return ResearchStatusRespDTO.builder()
                .id(session.getId())
                .title(session.getTitle())
                .status(session.getStatus())
                .startTime(session.getStartTime())
                .updateTime(session.getUpdateTime())
                .completeTime(session.getCompleteTime())
                .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ResearchStatusRespDTO getResearchStatus(Integer userId, String researchId) {
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
                .model(researchSession.getModel())
                .status(researchSession.getStatus())
                .startTime(researchSession.getStartTime())
                .updateTime(researchSession.getUpdateTime())
                .completeTime(researchSession.getCompleteTime())
                .build();
    }

    @Override
    public ResearchMessageRespDTO getResearchMessages(Integer userId, String researchId) {
        // TODO：权限验证，该 Research 属于该用户，升级到缓存，在获取状态时 add 缓存
        LambdaQueryWrapper<ResearchSession> checkQueryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .eq(ResearchSession::getId, researchId);
        ResearchSession researchSession = researchSessionMapper.selectOne(checkQueryWrapper);
        if (researchSession == null) {
            throw new ResearchException("研究任务不存在");
        }

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
                .build();
    }

    @Override
    public SendMessageRespDTO sendMessage(Integer userId, String researchId, SendMessageReqDTO sendMessageReqDTO) {
        // CAS 更新状态，幂等处理
        int affected = researchSessionMapper.casUpdateToQueue(researchId, userId);
        if (affected == 0) {
            throw new ResearchException("启动研究异常");
        }

        // 解析并注册模型
        ModelProp modelProp = ModelProp.builder()
                .name(sendMessageReqDTO.getModelName())
                .model(sendMessageReqDTO.getModel())
                .baseUrl(sendMessageReqDTO.getBaseUrl())
                .apiKey(sendMessageReqDTO.getApiKey())
                .build();
        modelHandler.addModel(researchId, modelProp);
        String title = sendMessageReqDTO.getContent().length() > 20
                ? sendMessageReqDTO.getContent().substring(0, 20)
                : sendMessageReqDTO.getContent();
        researchSessionMapper.setModelAndTitleIfNull(researchId, sendMessageReqDTO.getModel(), title);

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
                .supervisorIterations(0)
                .researcherIterations(0)
                .supervisorNotes(new ArrayList<>())
                .researcherNotes(new ArrayList<>())
                .searchResults(new HashMap<>())
                .searchNotes(new ArrayList<>())
                .totalInputTokens(0L)
                .totalOutputTokens(0L)
                .build();
        agentPipeline.run(state);

        return SendMessageRespDTO.builder()
                .id(researchId)
                .content("已接受任务")
                .build();
    }

    @Override
    public List<FreeModelRespDTO> getFreeModelList() {
        return modelFactory.getFreeModelList();
    }
}
