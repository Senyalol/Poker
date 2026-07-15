package poker.poker.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicGameState {

    private List<Player> players = new ArrayList<>();

    private List<Card> communityCards = new ArrayList<>();

    private long pot;

    private int dealerSeat;

    private Integer currentTurnSeat;  // Integer, а не int — может быть null

    private GamePhase phase;

    private long smallBlind;

    private long bigBlind;

    private long currentBet;

}