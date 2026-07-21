package poker.poker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import poker.poker.domain.PlayerAction;
import poker.poker.game.GameEngine;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final GameEngine engine;
    private final ObjectMapper objectMapper;

    // Храним все активные сессии: playerId → сессия
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameEngine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    /**
     * Игрок подключился
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Получаем playerId из URL: /ws?playerId=player1
        String playerId = getPlayerId(session);

        if (playerId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // Сохраняем сессию
        sessions.put(playerId, session);
        log.info("Игрок {} подключился", playerId);

        // Сразу отправляем текущее состояние игры
        sendPublicState(session);
        sendPrivateState(session, playerId);
    }

    /**
     * Получено сообщение от игрока
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String playerId = getPlayerId(session);
        String payload = message.getPayload();

        // Парсим JSON
        WsRequest request = objectMapper.readValue(payload, WsRequest.class);

        if ("action".equals(request.getType())) {
            handleAction(playerId, request);
        }
    }

    /**
     * Игрок отключился
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String playerId = getPlayerId(session);
        if (playerId != null) {
            sessions.remove(playerId);
            log.info("Игрок {} отключился", playerId);
        }
    }

    /**
     * Обработка действия игрока
     */
    private void handleAction(String playerId, WsRequest request) throws IOException {
        int actionCode = request.getData().getAction();
        int amount = request.getData().getAmount();

        PlayerAction action = PlayerAction.values()[actionCode];

        boolean success = engine.processAction(playerId, action, amount);

        if (success) {
            // Рассылаем обновления всем игрокам
            broadcastPublicState();
            // Отправляем приватное состояние игроку, который сделал ход
            sendPrivateState(sessions.get(playerId), playerId);
        } else {
            sendError(sessions.get(playerId), "Недопустимое действие");
        }
    }

    /**
     * Отправить публичное состояние одному игроку
     */
    private void sendPublicState(WebSocketSession session) throws IOException {
        WsResponse response = new WsResponse("game_state", engine.getPublicState());
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Отправить приватное состояние (карты) одному игроку
     */
    private void sendPrivateState(WebSocketSession session, String playerId) throws IOException {
        WsResponse response = new WsResponse("private_state", engine.getPrivateState(playerId));
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Отправить ошибку одному игроку
     */
    private void sendError(WebSocketSession session, String message) throws IOException {
        WsResponse response = new WsResponse("error", Map.of("message", message));
        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Разослать публичное состояние ВСЕМ игрокам
     */
    private void broadcastPublicState() throws IOException {
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                sendPublicState(session);
            }
        }
    }

    /**
     * Извлечь playerId из URL
     */
    private String getPlayerId(WebSocketSession session) {
        String query = session.getUri().getQuery();  // "playerId=player1"
        if (query != null && query.startsWith("playerId=")) {
            return query.substring(9);  // всё после "playerId="
        }
        return null;
    }
}