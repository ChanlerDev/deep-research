package dev.chanler.researcher.interfaces.controller;

import dev.chanler.researcher.infra.common.Result;
import dev.chanler.researcher.infra.common.Results;
import dev.chanler.researcher.infra.sse.SseHub;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.FreeModelRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.SendMessageRespDTO;
import dev.chanler.researcher.interfaces.service.ResearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * @author: Chanler
 */
@RestController
@RequestMapping("/api/v1/research")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchService researchService;
    private final SseHub sseHub;

    @GetMapping("/create")
    public Result<CreateResearchRespDTO> createResearch(
            @RequestAttribute("userId") Long userId, @RequestParam Integer num) {
        return Results.success(researchService.createResearch(userId, num));
    }

    @GetMapping("/list")
    public Result<List<ResearchStatusRespDTO>> getResearchList(
            @RequestAttribute("userId") Long userId) {
        return Results.success(researchService.getResearchList(userId));
    }

    @GetMapping("/research/{researchId}")
    public Result<ResearchStatusRespDTO> getResearchStatus(
            @RequestAttribute("userId") Long userId, @PathVariable String researchId) {
        return Results.success(researchService.getResearchStatus(userId, researchId));
    }

    @GetMapping("/research/{researchId}/messages")
    public Result<ResearchMessageRespDTO> getResearchMessages(
            @RequestAttribute("userId") Long userId, @PathVariable String researchId) {
        return Results.success(researchService.getResearchMessages(userId, researchId));
    }

    @PostMapping("/research/{researchId}/messages")
    public Result<SendMessageRespDTO> sendMessage(
            @RequestAttribute("userId") Long userId, @PathVariable String researchId,
            @RequestBody SendMessageReqDTO sendMessageReqDTO) {
        return Results.success(researchService.sendMessage(userId, researchId, sendMessageReqDTO));
    }

    @GetMapping("/models/free")
    public Result<List<FreeModelRespDTO>> getFreeModelList() {
        return Results.success(researchService.getFreeModelList());
    }

    @GetMapping("/sse")
    public SseEmitter stream(@RequestAttribute("userId") Long userId,
             @RequestHeader("X-Research-Id") String researchId,
            @RequestHeader("X-Client-Id") String clientId, // 前端记得分配
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return sseHub.connect(userId, researchId, clientId, lastEventId);
    }
}
