package poker.poker.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrivateState {

    private Card firstCard;
    private Card secondCard;

}