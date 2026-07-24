package poker.poker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import poker.poker.domain.Player;
import poker.poker.domain.PlayerStatus;
import poker.poker.game.GameEngine;

@Configuration
public class GameConfig {

    @Bean
    public GameEngine gameEngine() {
        GameEngine engine = new GameEngine();

        engine.addPlayer(new Player("player1", 0, 1000, 0, PlayerStatus.WAITING));
        engine.addPlayer(new Player("player2", 1, 1000, 0, PlayerStatus.WAITING));
        engine.addPlayer(new Player("player3", 2, 1000, 0, PlayerStatus.WAITING));

        engine.startHand();
        return engine;
    }
}
