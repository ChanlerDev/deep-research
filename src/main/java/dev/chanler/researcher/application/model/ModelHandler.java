package dev.chanler.researcher.application.model;

import dev.chanler.researcher.infra.config.ModelProp;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;


/**
 * @author: Chanler
 */
@Component
public class ModelHandler {
    private final ModelFactory modelFactory;
    private final ConcurrentHashMap<String, ChatModel> modelPool;
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModelPool;

    public ModelHandler(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
        this.modelPool = new ConcurrentHashMap<>();
        this.streamingModelPool = new ConcurrentHashMap<>();
    }

    public ChatModel getModel(String researchId) {
        return modelPool.get(researchId);
    }

    public StreamingChatModel getStreamModel(String researchId) {
        return streamingModelPool.get(researchId);
    }

    public void addModel(String researchId, ModelProp modelProp) {
        ChatModel chatModel = modelFactory.createChatModel(modelProp);
        StreamingChatModel streamingChatModel = modelFactory.createStreamingChatModel(modelProp);
        modelPool.put(researchId, chatModel);
        streamingModelPool.put(researchId, streamingChatModel);
    }

    public void removeModel(String researchId) {
        modelPool.remove(researchId);
        streamingModelPool.remove(researchId);
    }
}
