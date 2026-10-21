package io.symmetry.events;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tools.jackson.databind.ObjectMapper;

/**
 * In-process fan-out for server-sent events. One JVM is enough for V1; when the
 * API is horizontally scaled this is replaced by Redis pub/sub without changing
 * the endpoints or the UI.
 */
@Service
public class EventService {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> channels = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public EventService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public SseEmitter subscribe(String channel) {
        SseEmitter emitter = new SseEmitter(0L);
        channels.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(channel, emitter));
        emitter.onTimeout(() -> remove(channel, emitter));
        emitter.onError(error -> remove(channel, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            remove(channel, emitter);
        }
        return emitter;
    }

    public void publish(String channel, String type, Object data) {
        List<SseEmitter> subscribers = channels.get(channel);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        String payload = json(data);
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name(type).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(channel, emitter);
            }
        }
    }

    private String json(Object data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    private void remove(String channel, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = channels.get(channel);
        if (subscribers != null) {
            subscribers.remove(emitter);
            if (subscribers.isEmpty()) {
                channels.remove(channel, subscribers);
            }
        }
    }
}
