package dev.chanler.researcher.application.model;

import dev.chanler.researcher.infra.config.DefaultModelProps;
import dev.chanler.researcher.infra.config.ModelProp;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.stereotype.Component;

import dev.chanler.researcher.interfaces.dto.resp.FreeModelRespDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: Chanler
 */
@Component
@Data
public class ModelFactory {
    private final DefaultModelProps defaultModelProps;
    private final Map<String, ModelProp> modelPropsMap;

    private final Map<String, ChatModel> ChatModelCache;
    private final Map<String, StreamingChatModel> StreamingChatModelCache;
    private final List<FreeModelRespDTO> freeModelList;

    public ModelFactory(DefaultModelProps defaultModelProps) {
        this.defaultModelProps = defaultModelProps;
        this.modelPropsMap = defaultModelProps.getConfig().stream()
                .collect(Collectors.toMap(ModelProp::getName, modelProp -> modelProp));

        this.ChatModelCache = new HashMap<>();
        this.StreamingChatModelCache = new HashMap<>();
        
        for (ModelProp modelProp : defaultModelProps.getConfig()) {
            ChatModelCache.put(modelProp.getName(), buildChatModel(modelProp));
            StreamingChatModelCache.put(modelProp.getName(), buildStreamingChatModel(modelProp));
        }

        this.freeModelList = defaultModelProps.getConfig().stream()
                .map(prop -> FreeModelRespDTO.builder()
                        .modelName(prop.getName())
                        .model(prop.getModel())
                        .build())
                .collect(Collectors.toUnmodifiableList());
    }

    public ChatModel createChatModel(ModelProp modelProp) {
        // 检查是否是默认模型
        if (isDefaultModel(modelProp)) {
            return ChatModelCache.get(modelProp.getName());
        }
        // 用户自定义模型，创建新实例
        return buildChatModel(modelProp);
    }

    public StreamingChatModel createStreamingChatModel(ModelProp modelProp) {
        // 检查是否是默认模型
        if (isDefaultModel(modelProp)) {
            return StreamingChatModelCache.get(modelProp.getName());
        }
        // 用户自定义模型，创建新实例
        return buildStreamingChatModel(modelProp);
    }
    
    /**
     * 判断是否是默认模型（apiKey 为空且 modelName 一样表示用默认配置）
     */
    private boolean isDefaultModel(ModelProp modelProp) {
        return modelProp.getApiKey() == null &&
               modelPropsMap.containsKey(modelProp.getName()) && 
               modelPropsMap.get(modelProp.getName()).equals(modelProp);
    }
    
    /**
     * 构建 ChatModel 实例
     */
    private ChatModel buildChatModel(ModelProp modelProp) {
        return OpenAiChatModel.builder()
                .baseUrl(modelProp.getBaseUrl())
                .apiKey(modelProp.getApiKey())
                .modelName(modelProp.getModel())
                .build();
    }
    
    /**
     * 构建 StreamingChatModel 实例
     */
    private StreamingChatModel buildStreamingChatModel(ModelProp modelProp) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(modelProp.getBaseUrl())
                .apiKey(modelProp.getApiKey())
                .modelName(modelProp.getModel())
                .build();
    }

    /**
     * 获取免费模型列表（预构建，直接返回缓存）
     */
    public List<FreeModelRespDTO> getFreeModelList() {
        return freeModelList;
    }
}
