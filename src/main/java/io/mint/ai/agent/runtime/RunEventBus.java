package io.mint.ai.agent.runtime;

import io.mint.ai.agent.model.AgUiEventEnvelope;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RunEventBus {

    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(ex -> remove(runId, emitter));
        return emitter;
    }

    public void publish(String runId, AgUiEventEnvelope event) {
        List<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("agui_event")
                        .id(event.eventId())
                        .data(event));
            } catch (IOException e) {
                remove(runId, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    public void complete(String runId) {
        List<SseEmitter> emitters = subscribers.remove(runId);
        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
    }

    private void remove(String runId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) {
            return;
        }

        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(runId);
        }
    }
}
