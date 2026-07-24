package poker.poker.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WsResponse {
    private String type;  // "game_state", "private_state", "error"
    private Object data;  // любой объект (PublicGameState, PrivateState, Map с ошибкой)
}