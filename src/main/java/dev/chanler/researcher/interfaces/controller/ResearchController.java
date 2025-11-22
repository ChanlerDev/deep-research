package dev.chanler.researcher.interfaces.controller;

import dev.chanler.researcher.domain.entity.ChatMessage;
import dev.chanler.researcher.infra.common.Result;
import dev.chanler.researcher.infra.common.Results;
import dev.chanler.researcher.interfaces.dto.req.SendMessageReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.CreateResearchRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchMessageRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.ResearchStatusRespDTO;
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
// TODO: 使用 ThreadLocal 在拦截器中保存 userId
public class ResearchController {

    private final ResearchService researchService;
//    private final SSEHub sseHub;

    @GetMapping("/create")
    public Result<CreateResearchRespDTO> createResearch(
            @RequestHeader("X-User-Id") Integer userId, @RequestParam Integer num) {
        return Results.success(researchService.createResearch(userId,num));
    }

    @GetMapping("/research/{researchId}")
    public Result<ResearchStatusRespDTO> getResearchStatus(
            @RequestHeader("X-User-Id") Integer userId, @PathVariable String researchId) {
        return Results.success(researchService.getResearchStatus(userId, researchId));
    }

    @GetMapping("/research/{researchId}/messages")
    public Result<ResearchMessageRespDTO> getResearchMessages(
            @RequestHeader("X-User-Id") Integer userId, @PathVariable String researchId) {
        return Results.success(researchService.getResearchMessages(userId, researchId));
    }

    @PostMapping("/research/{researchId}/messages")
    public Result<SendMessageRespDTO> sendMessage(
            @RequestHeader("X-User-Id") Integer userId, @PathVariable String researchId,
            @RequestBody SendMessageReqDTO sendMessageReqDTO) {
        return Results.success(researchService.sendMessage(userId, researchId, sendMessageReqDTO));
    }

//    @GetMapping("/sse")
//    public SseEmitter stream(@RequestHeader("X-User-Id") Integer userId,
//            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
//        return sseHub.connect(userId, null, lastEventId);
//    }
}
