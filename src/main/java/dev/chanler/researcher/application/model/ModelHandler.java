package dev.chanler.researcher.application.model;

import dev.chanler.researcher.infra.config.ModelProp;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Chanler
 */
@Component
public class ModelHandler {
    private final ModelFactory modelFactory;
    private final ConcurrentHashMap<String, ChatModel> modelPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModelPool = new ConcurrentHashMap<>();

    public ModelHandler(ModelFactory modelFactory) {
        this.modelFactory = modelFactory;
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
