package dev.chanler.researcher.interfaces.service;

import dev.chanler.researcher.domain.entity.Model;
import dev.chanler.researcher.interfaces.dto.req.AddModelReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.ModelRespDTO;

import java.util.List;

/**
 * Model service interface
 * @author: Chanler
 */
public interface ModelService {
    
    List<ModelRespDTO> getAvailableModels(Long userId);
    
    String addCustomModel(Long userId, AddModelReqDTO req);
    
    void deleteCustomModel(Long userId, String modelId);
    
    Model getModelById(Long userId, String modelId);
}
