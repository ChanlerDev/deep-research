package dev.chanler.researcher.interfaces.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import dev.chanler.researcher.application.data.PipelineIn;
import dev.chanler.researcher.application.data.WorkflowStatus;
import dev.chanler.researcher.application.workflow.AgentPipeline;
import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.domain.entity.ResearchSession;
import dev.chanler.researcher.domain.entity.WorkflowEvent;
import dev.chanler.researcher.domain.mapper.ChatMessageMapper;
import dev.chanler.researcher.domain.mapper.ResearchSessionMapper;
import dev.chanler.researcher.domain.mapper.WorkflowEventMapper;
import dev.chanler.researcher.infra.exception.ResearchException;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.SendMessageRespDTO;
import dev.chanler.researcher.interfaces.service.ResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Chanler
 */
@Service
@RequiredArgsConstructor
// TODO: 拦截异常
public class ResearchServiceImpl implements ResearchService {

    private final ResearchSessionMapper researchSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final WorkflowEventMapper workflowEventMapper;
    private final AgentPipeline agentPipeline;

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
        // TODO：权限验证，该 Research 属于该用户，升级到缓存，在获取状态时 add 缓存
        LambdaQueryWrapper<ResearchSession> checkQueryWrapper = Wrappers.lambdaQuery(ResearchSession.class)
                .eq(ResearchSession::getUserId, userId)
                .eq(ResearchSession::getId, researchId);
        ResearchSession researchSession = researchSessionMapper.selectOne(checkQueryWrapper);
        if (researchSession == null) {
            throw new ResearchException("研究任务不存在");
        }

        // TODO：后续应当支持历史研究继续研究，即构建 langchain4j 的 ChatMessage 进 DeepResearchState 调用 AgentPipeline
        if (!researchSession.getStatus().equals(WorkflowStatus.NEW)) {
            throw new ResearchException("研究任务状态异常，无法启动");
        }
        PipelineIn pipelineIn = PipelineIn.builder()
                .researchId(researchId)
                .userId(userId)
                .content(sendMessageReqDTO.getContent())
                .build();
        agentPipeline.run(pipelineIn);
        return SendMessageRespDTO.builder()
                .id(researchSession.getId())
                .content("排队中")
                .build();
    }
}
