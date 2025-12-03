package dev.chanler.researcher.interfaces.controller;

import dev.chanler.researcher.infra.common.Result;
import dev.chanler.researcher.infra.common.Results;
import dev.chanler.researcher.interfaces.dto.req.AddModelReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.ModelRespDTO;
import dev.chanler.researcher.interfaces.service.ModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Model controller
 * @author: Chanler
 */
@RestController
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    @GetMapping("/api/v1/models")
    public Result<List<ModelRespDTO>> getAvailableModels(
            @RequestAttribute("userId") Long userId) {
        return Results.success(modelService.getAvailableModels(userId));
    }

    @PostMapping("/api/v1/models")
    public Result<String> addCustomModel(
            @RequestAttribute("userId") Long userId,
            @RequestBody AddModelReqDTO req) {
        return Results.success(modelService.addCustomModel(userId, req));
    }

    @DeleteMapping("/api/v1/models/{modelId}")
    public Result<String> deleteCustomModel(
            @RequestAttribute("userId") Long userId,
            @PathVariable String modelId) {
        modelService.deleteCustomModel(userId, modelId);
        return Results.success("删除成功");
    }
}
