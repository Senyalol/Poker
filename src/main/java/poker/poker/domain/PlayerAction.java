package poker.poker.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlayerAction {

    FOLD(0),
    CHECK(1),
    CALL(2),
    BET(3),
    RAISE(4),
    ALL_IN(5);

    private final int code;

    @JsonValue  // В JSON пойдёт число, а не строка (нужно для твоего index.html)
    public int getCode() {
        return code;
    }

}