package dev.chanler.researcher.interfaces.service;

import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.SendMessageRespDTO;

import java.util.List;

/**
 * @author: Chanler
 */
public interface ResearchService {

    CreateResearchRespDTO createResearch(Integer userId, Integer num);

    List<ResearchStatusRespDTO> getResearchList(Integer userId);

    ResearchStatusRespDTO getResearchStatus(Integer userId, String researchId);

    ResearchMessageRespDTO getResearchMessages(Integer userId, String researchId);

    SendMessageRespDTO sendMessage(Integer userId, String researchId, SendMessageReqDTO sendMessageReqDTO);
}
