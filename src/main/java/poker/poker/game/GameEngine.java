package poker.poker.game;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import poker.poker.domain.*;

import java.util.*;

public class GameEngine {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Игроки и их карты
    private final Map<String, Player> players;
    private final Map<String, List<Card>> privateHands;

    // Состояние игры
    private final PublicGameState state;
    private final CardStore cardStore;

    // Позиции за столом
    private int dealerSeat;
    private int smallBlindSeat;
    private int bigBlindSeat;

    // Очередь хода
    private List<String> activeOrder;     // порядок ходов в текущем раунде
    private int currentIdx;               // кто сейчас ходит (индекс в activeOrder)
    private String lastRaiser;            // последний, кто повысил ставку

    // Параметры ставок
    private long minRaise;                // минимальный рейз
    private boolean roundStarted;         // начался ли раунд торгов

    // Константы
    private static final long DEFAULT_SMALL_BLIND = 10;
    private static final long DEFAULT_BIG_BLIND = 20;
    private static final int MAX_PLAYERS = 9;

    public GameEngine() {
        this.players = new LinkedHashMap<>();  // сохраняет порядок добавления
        this.privateHands = new HashMap<>();
        this.cardStore = new CardStore();
        this.state = PublicGameState.builder()
                .players(new ArrayList<>())
                .communityCards(new ArrayList<>())
                .pot(0)
                .smallBlind(DEFAULT_SMALL_BLIND)
                .bigBlind(DEFAULT_BIG_BLIND)
                .phase(GamePhase.WAITING)
                .build();
    }

    /**
     * Добавляет игрока за стол
     * @param player игрок (должен иметь уникальный ID)
     * @return true если успешно
     */
    public boolean addPlayer(Player player) {
        if (players.size() >= MAX_PLAYERS) {
            return false;  // стол заполнен
        }

        if (players.containsKey(player.getId())) {
            return false;  // игрок уже за столом
        }

        player.setStatus(PlayerStatus.WAITING);
        players.put(player.getId(), player);

        // Обновляем публичное состояние
        updatePublicPlayers();
        return true;
    }

    // В GameEngine уже должны быть:
    public PublicGameState getPublicState() {
        return state;
    }

    public PrivateState getPrivateState(String playerId) {
        List<Card> hand = privateHands.get(playerId);
        if (hand == null || hand.size() < 2) {
            return null;
        }
        return new PrivateState(hand.get(0), hand.get(1));
    }

    /**
     * Удаляет игрока со стола
     */
    public boolean removePlayer(String playerId) {
        if (!players.containsKey(playerId)) {
            return false;
        }

        players.remove(playerId);
        privateHands.remove(playerId);
        updatePublicPlayers();
        return true;
    }

    /**
     * Обновляет список публичных игроков в состоянии
     */
    private void updatePublicPlayers() {
        List<Player> publicPlayers = new ArrayList<>();
        for (Player p : players.values()) {
            publicPlayers.add(new Player(
                    p.getId(),
                    p.getSeat(),
                    p.getStack(),
                    p.getCommitted(),
                    p.getStatus()
            ));
        }
        state.setPlayers(publicPlayers);
    }

    /**
     * Начинает новую раздачу
     */
    public void startHand() {
        lock.writeLock().lock();
        try {
            // 1. Сброс состояния
            cardStore.newDeck();
            cardStore.shuffle();
            cardStore.clearHands();
            privateHands.clear();
            state.getCommunityCards().clear();
            state.setPot(0);
            state.setCurrentBet(0);

            // 2. Активируем всех игроков с фишками
            for (Player player : players.values()) {
                if (player.getStack() > 0) {
                    player.setStatus(PlayerStatus.ACTIVE);
                    player.setCommitted(0);
                } else {
                    player.setStatus(PlayerStatus.WAITING);
                }
            }

            // 3. Двигаем дилера
            dealerSeat = findNextActiveSeat(dealerSeat);
            smallBlindSeat = findNextActiveSeat(dealerSeat);
            bigBlindSeat = findNextActiveSeat(smallBlindSeat);

            state.setDealerSeat(dealerSeat);

            // 4. Списываем блайнды
            Player sbPlayer = getPlayerBySeat(smallBlindSeat);
            if (sbPlayer != null) {
                long sbAmount = Math.min(DEFAULT_SMALL_BLIND, sbPlayer.getStack());
                sbPlayer.placeBet((int) sbAmount);
                state.setPot(state.getPot() + sbAmount);
            }

            Player bbPlayer = getPlayerBySeat(bigBlindSeat);
            if (bbPlayer != null) {
                long bbAmount = Math.min(DEFAULT_BIG_BLIND, bbPlayer.getStack());
                bbPlayer.placeBet((int) bbAmount);
                state.setPot(state.getPot() + bbAmount);
                state.setCurrentBet(bbAmount);
                lastRaiser = bbPlayer.getId();
            }

            // 5. Раздаём по 2 карты активным игрокам
            for (Player player : players.values()) {
                if (player.isActive()) {
                    cardStore.dealHand(player.getId());
                    privateHands.put(player.getId(), cardStore.getHand(player.getId()));
                }
            }

            // 6. Устанавливаем порядок ходов (начинаем после большого блайнда)
            activeOrder = buildActiveOrder(bigBlindSeat);
            currentIdx = 0;  // первый ходит тот, кто после BB
            state.setCurrentTurnSeat(getCurrentPlayerSeat());

            // 7. Начинаем префлоп
            state.setPhase(GamePhase.PREFLOP);
            minRaise = DEFAULT_BIG_BLIND;
            roundStarted = true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Находит следующего активного игрока после указанного места
     */
    private int findNextActiveSeat(int fromSeat) {
        for (int i = 1; i <= MAX_PLAYERS; i++) {
            int seat = (fromSeat + i) % MAX_PLAYERS;
            Player player = getPlayerBySeat(seat);
            if (player != null && player.isActive()) {
                return seat;
            }
        }
        return fromSeat;  // нет активных игроков
    }

    /**
     * Находит игрока по номеру места
     */
    private Player getPlayerBySeat(int seat) {
        for (Player player : players.values()) {
            if (player.getSeat() == seat) {
                return player;
            }
        }
        return null;
    }

    /**
     * Строит порядок ходов в текущем раунде
     * Все активные игроки по порядку от startingSeat
     */
    private List<String> buildActiveOrder(int startingSeat) {
        List<String> order = new ArrayList<>();
        int currentSeat = startingSeat;

        do {
            currentSeat = (currentSeat + 1) % MAX_PLAYERS;
            Player player = getPlayerBySeat(currentSeat);
            if (player != null && player.isActive() && !player.isAllIn()) {
                order.add(player.getId());
            }
        } while (currentSeat != startingSeat);

        return order;
    }

    /**
     * @return место текущего игрока
     */
    private Integer getCurrentPlayerSeat() {
        if (activeOrder == null || currentIdx >= activeOrder.size()) {
            return null;
        }
        String playerId = activeOrder.get(currentIdx);
        Player player = players.get(playerId);
        return player != null ? player.getSeat() : null;
    }

}