package dev.chanler.researcher.interfaces.service;

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

    CreateResearchRespDTO createResearch(Long userId, Integer num);

    List<ResearchStatusRespDTO> getResearchList(Long userId);

    ResearchStatusRespDTO getResearchStatus(Long userId, String researchId);

    ResearchMessageRespDTO getResearchMessages(Long userId, String researchId);

    SendMessageRespDTO sendMessage(Long userId, String researchId, SendMessageReqDTO sendMessageReqDTO);
}
