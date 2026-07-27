package poker.poker.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Card {

    private Suit suit;
    private Rank rank;

}