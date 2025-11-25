package dev.chanler.researcher.infra.sse;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import dev.chanler.researcher.infra.data.TimelineItem;
import dev.chanler.researcher.infra.util.CacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Chanler
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SseHub {

    private static final Long SSE_TIMEOUT_MS = 0L;
    // researchId -> (clientId -> emitter)
    private final Map<String, Map<String, SseEmitter>> researchEmitters = new ConcurrentHashMap<>();
    private final CacheUtil cacheUtil;

    public SseEmitter connect(Integer userId, String researchId, String clientId, String lastEventId) {
        // TODO: 权限校验
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> remove(researchId, clientId));
        emitter.onTimeout(() -> remove(researchId, clientId));
        emitter.onError(ex -> remove(researchId, clientId));

        researchEmitters
                .computeIfAbsent(researchId, k -> new ConcurrentHashMap<>())
                .put(clientId, emitter);

        replayIfNeeded(userId, researchId, emitter, lastEventId);

        return emitter;
    }

    private void remove(String researchId, String clientId) {
        Map<String, SseEmitter> clients = researchEmitters.get(researchId);
        if(CollectionUtil.isEmpty(clients)) {
            return;
        }

        clients.remove(clientId);
        if (clients.isEmpty()) {
            researchEmitters.remove(researchId);
        }
    }

    public void sendTimelineItem(String researchId, TimelineItem item) {
        if (item == null || item.getSequenceNo() == null) {
            return;
        }
        String eventId = item.getSequenceNo().toString();
        Map<String, SseEmitter> clients = researchEmitters.get(researchId);
        if(CollectionUtil.isEmpty(clients)) {
            return;
        }

        for (Map.Entry<String, SseEmitter> entry : clients.entrySet()) {
            String clientId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            if (emitter == null) {
                continue;
            }
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .id(eventId)
                        .name(item.getKind())
                        .data(item);
                emitter.send(builder);
            } catch (IOException e) {
                log.error("SSE 时间线推送失败，researchId={}, clientId={}", researchId, clientId, e);
                remove(researchId, clientId);
                // 关闭
            }
        }
    }

    public void sendReportStream(String researchId, String partialText) {
        if (StrUtil.isEmptyIfStr(partialText)) {
            return;
        }
        Map<String, SseEmitter> clients = researchEmitters.get(researchId);
        if(CollectionUtil.isEmpty(clients)) {
            return;
        }

        for (Map.Entry<String, SseEmitter> entry : clients.entrySet()) {
            String clientId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            if (emitter == null) {
                continue;
            }
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .name("report-stream")
                        .data(partialText);
                emitter.send(builder);
            } catch (IOException e) {
                log.error("SSE 报告流推送失败，researchId={}, clientId={}", researchId, clientId, e);
                remove(researchId, clientId);
            }
        }
    }

    private void replayIfNeeded(Integer userId, String researchId, SseEmitter emitter, String lastEventId) {
        if (StrUtil.isEmptyIfStr(lastEventId)) {
            return;
        }

        Integer lastSeq = NumberUtil.parseInt(lastEventId.trim(), 0);

        List<TimelineItem> items = cacheUtil.getTimeline(researchId, lastSeq);
        if(CollectionUtil.isEmpty(items)) {
            return;
        }

        for (TimelineItem item : items) {
            try {
                String eventId = item.getSequenceNo().toString();
                SseEmitter.SseEventBuilder builder = SseEmitter.event()
                        .id(eventId)
                        .name(item.getKind())
                        .data(item);
                emitter.send(builder);
            } catch (IOException e) {
                log.error("重放失败 userId={}, researchId={}", userId, researchId, e);
                break;
            }
        }
    }
}

