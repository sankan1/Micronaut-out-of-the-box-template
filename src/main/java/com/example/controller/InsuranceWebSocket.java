package com.example.controller;

import com.example.business.mapper.InsuranceMapper;
import com.example.business.useCase.GetExpiringSoonInsurances;
import com.example.openapi.model.Insurance;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

import java.util.List;

@ServerWebSocket("/ws/insurance")
public class InsuranceWebSocket {

    private final GetExpiringSoonInsurances getExpiringSoonInsurances;
    private final WebSocketBroadcaster broadcaster;

    public InsuranceWebSocket(GetExpiringSoonInsurances getExpiringSoonInsurances, WebSocketBroadcaster broadcaster) {
        this.getExpiringSoonInsurances = getExpiringSoonInsurances;
        this.broadcaster = broadcaster;
    }

    /**
     * Every incoming message (including the client's periodic heartbeat ping) gets the current
     * "expiring soon" snapshot back, instead of a bare pong acknowledgement.
     */
    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        session.sendSync(currentSnapshot());
    }

    public void broadcastUpdate() {
        broadcaster.broadcastSync(currentSnapshot());
    }

    private List<Insurance> currentSnapshot() {
        return InsuranceMapper.mapToInsurances(getExpiringSoonInsurances.execute());
    }
}
