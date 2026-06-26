package com.example.controller;

import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerWebSocket("/ws/persons")
public class PersonsWebSocket {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsWebSocket.class);
    private static final String PING_MESSAGE = "ping";
    private static final String PONG_MESSAGE = "pong";
    public static final String UPDATE_EVENT = "UPDATE";

    private final WebSocketBroadcaster broadcaster;

    public PersonsWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        if (PING_MESSAGE.equals(message)) {
            session.sendSync(PONG_MESSAGE);
        } else {
            LOG.debug("Ignoring unexpected /ws/persons message: {}", message);
        }
    }

    public void broadcastUpdate() {
        broadcaster.broadcastSync(UPDATE_EVENT);
    }
}
