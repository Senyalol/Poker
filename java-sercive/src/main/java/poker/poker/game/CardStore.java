package poker.poker.game;

import poker.poker.domain.Card;
import poker.poker.domain.Rank;
import poker.poker.domain.Suit;

import java.util.*;

public class CardStore {

    private final List<Card> deck;                              // сама колода
    private final Map<String, List<Card>> hands;

    /**
     * Конструктор создаёт пустую колоду и сразу заполняет её 52 картами
     */
    public CardStore() {
        this.deck = new ArrayList<>();
        this.hands = new HashMap<>();
        newDeck();  // сразу создаём колоду
    }

    /**
     * Создаёт новую колоду из 52 карт (4 масти × 13 достоинств)
     */
    public void newDeck() {
        deck.clear();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
    }

    /**
     * Перемешивает колоду
     */
    public void shuffle() {
        Collections.shuffle(deck);
    }

    /**
     * Сдаёт одну карту с верха колоды
     * @return карта или null, если колода пуста
     */
    public Card dealCard() {
        if (deck.isEmpty()) {
            return null;
        }
        return deck.remove(deck.size() - 1);  // берём верхнюю карту
    }

    /**
     * Раздаёт игроку 2 карты
     * @param playerId идентификатор игрока
     * @return true если успешно, false если недостаточно карт
     */
    public boolean dealHand(String playerId) {
        if (deck.size() < 2) {
            return false;
        }

        List<Card> hand = new ArrayList<>();
        hand.add(dealCard());
        hand.add(dealCard());
        hands.put(playerId, hand);
        return true;
    }

    /**
     * Возвращает карты конкретного игрока
     * @param playerId идентификатор игрока
     * @return список из 2 карт или пустой список
     */
    public List<Card> getHand(String playerId) {
        return hands.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * Очищает все руки (для новой раздачи)
     */
    public void clearHands() {
        hands.clear();
    }

    /**
     * @return сколько карт осталось в колоде
     */
    public int cardsRemaining() {
        return deck.size();
    }

}