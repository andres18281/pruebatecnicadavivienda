package com.techdocs.notify.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.contracts.DocumentProgressEvent;
import com.techdocs.contracts.DocumentStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StatusWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StatusWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<WebSocketSession>> byDocument = new ConcurrentHashMap<>();

    public StatusWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        send(session, new TextMessage("{\"type\":\"connected\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String action = node.path("action").asText("");
        String documentId = node.path("documentId").asText("");
        if (documentId.isBlank()) {
            return;
        }
        if ("subscribe".equals(action)) {
            byDocument.computeIfAbsent(documentId, key -> ConcurrentHashMap.newKeySet()).add(session);
            log.info("evento=suscripcion documentId={}", documentId);
        } else if ("unsubscribe".equals(action)) {
            Set<WebSocketSession> subscribers = byDocument.get(documentId);
            if (subscribers != null) {
                subscribers.remove(session);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        byDocument.values().forEach(subscribers -> subscribers.remove(session));
    }

    public void broadcast(DocumentStatusChangedEvent event) {
        push(event.documentId(), event);
    }

    public void broadcast(DocumentProgressEvent event) {
        push(event.documentId(), event);
    }

    @Scheduled(fixedDelayString = "${app.heartbeat-ms}")
    public void heartbeat() {
        PingMessage ping = new PingMessage();
        for (WebSocketSession session : sessions) {
            send(session, ping);
        }
    }

    private void push(String documentId, Object event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException exception) {
            log.warn("No se pudo serializar el evento");
            return;
        }
        Set<WebSocketSession> targets = new LinkedHashSet<>();
        add(targets, documentId);
        add(targets, "*");
        TextMessage payload = new TextMessage(json);
        log.info("evento=aviso documentId={} destinatarios={}", documentId, targets.size());
        for (WebSocketSession session : targets) {
            send(session, payload);
        }
    }

    private void add(Set<WebSocketSession> targets, String documentId) {
        Set<WebSocketSession> subscribers = byDocument.get(documentId);
        if (subscribers != null) {
            targets.addAll(subscribers);
        }
    }

    private void send(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException exception) {
            log.debug("No se pudo escribir en la sesion {}", session.getId());
        }
    }
}
