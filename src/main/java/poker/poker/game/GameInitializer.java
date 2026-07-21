package poker.poker.game;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import poker.poker.domain.Player;
import poker.poker.domain.PlayerStatus;

@Configuration
public class GameInitializer {

    private final GameEngine engine;

    public GameInitializer(GameEngine engine) {
        this.engine = engine;
    }

    @PostConstruct
    public void init() {

        engine.addPlayer(new Player("player1", 0, 1000, 0, PlayerStatus.WAITING));
        engine.addPlayer(new Player("player2", 1, 1000, 0, PlayerStatus.WAITING));
        engine.addPlayer(new Player("player3", 2, 1000, 0, PlayerStatus.WAITING));

        // Запускаем первую раздачу
        engine.startHand();
    }
}