package poker.poker.websocket;

import lombok.Data;

@Data
public class WsRequest {
    private String type;      // "action"
    private ActionData data;  // вложенный объект

    @Data
    public static class ActionData {
        private int action;   // 0-5 (FOLD, CHECK, CALL, BET, RAISE, ALL_IN)
        private int amount;   // сумма (для BET, RAISE, ALL_IN)
    }
}