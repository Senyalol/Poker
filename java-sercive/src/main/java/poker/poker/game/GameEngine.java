package poker.poker.game;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import poker.poker.domain.*;
import poker.poker.engine.HandEvaluator;
import poker.poker.engine.HandResult;

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
        state.setPlayers(new ArrayList<>(players.values()));
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
            updatePublicPlayers();

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Обрабатывает действие игрока (fold, check, call, bet, raise, all-in)
     *
     * @param amount целевая сумма committed за раунд (для BET/RAISE), для остальных игнорируется
     */
    public boolean processAction(String playerId, PlayerAction action, int amount) {
        lock.writeLock().lock();
        try {
            GamePhase phase = state.getPhase();
            if (phase == GamePhase.WAITING || phase == GamePhase.SHOWDOWN) {
                return false;
            }

            String currentId = getCurrentPlayerId();
            if (currentId == null || !currentId.equals(playerId)) {
                return false;
            }

            Player player = players.get(playerId);
            if (player == null || !player.isActive()) {
                return false;
            }

            if (!validateAction(player, action, amount)) {
                return false;
            }

            applyAction(player, action, amount);

            if (countNotFolded() <= 1) {
                awardPotToSingleWinner();
                updatePublicPlayers();
                return true;
            }

            if (action == PlayerAction.FOLD) {
                removeFromActiveOrder(playerId);
            } else {
                nextPlayer();
            }

            if (isRoundComplete(playerId)) {
                nextPhase();
            } else {
                state.setCurrentTurnSeat(getCurrentPlayerSeat());
            }

            updatePublicPlayers();
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean validateAction(Player player, PlayerAction action, int amount) {
        long needToCall = state.getCurrentBet() - player.getCommitted();

        return switch (action) {
            case FOLD -> true;
            case CHECK -> needToCall == 0;
            case CALL -> needToCall > 0 && player.getStack() > 0;
            case BET -> state.getCurrentBet() == 0
                    && amount >= state.getBigBlind()
                    && amount - player.getCommitted() <= player.getStack();
            case RAISE -> amount > state.getCurrentBet()
                    && amount - state.getCurrentBet() >= minRaise
                    && amount - player.getCommitted() <= player.getStack()
                    && amount - player.getCommitted() > 0;
            case ALL_IN -> player.getStack() > 0;
        };
    }

    private void applyAction(Player player, PlayerAction action, int amount) {
        switch (action) {
            case FOLD -> player.setStatus(PlayerStatus.FOLDED);
            case CHECK -> { /* ставки уже уравнены */ }
            case CALL -> {
                long toCall = Math.min(state.getCurrentBet() - player.getCommitted(), player.getStack());
                commitChips(player, toCall);
            }
            case BET -> {
                long toPut = amount - player.getCommitted();
                commitChips(player, toPut);
                state.setCurrentBet(amount);
                lastRaiser = player.getId();
                minRaise = amount;
            }
            case RAISE -> {
                long previousBet = state.getCurrentBet();
                long toPut = amount - player.getCommitted();
                commitChips(player, toPut);
                minRaise = amount - previousBet;
                state.setCurrentBet(amount);
                lastRaiser = player.getId();
            }
            case ALL_IN -> {
                long toPut = player.getStack();
                long newCommitted = player.getCommitted() + toPut;
                commitChips(player, toPut);
                if (newCommitted > state.getCurrentBet()) {
                    long raiseIncrement = newCommitted - state.getCurrentBet();
                    if (raiseIncrement >= minRaise) {
                        minRaise = raiseIncrement;
                        lastRaiser = player.getId();
                    }
                    state.setCurrentBet(newCommitted);
                }
                player.setStatus(PlayerStatus.ALL_IN);
            }
        }
    }

    private void commitChips(Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        player.placeBet((int) amount);
        state.setPot(state.getPot() + amount);
        if (player.getStack() == 0) {
            player.setStatus(PlayerStatus.ALL_IN);
        }
    }

    private void nextPlayer() {
        if (activeOrder == null || activeOrder.isEmpty()) {
            return;
        }

        int start = currentIdx;
        do {
            currentIdx = (currentIdx + 1) % activeOrder.size();
            Player next = players.get(activeOrder.get(currentIdx));
            if (next != null && !next.isFolded() && !next.isAllIn()) {
                return;
            }
        } while (currentIdx != start);
    }

    private boolean isRoundComplete(String lastActorId) {
        if (!allBetsMatched()) {
            return false;
        }

        if (lastRaiser != null) {
            if (lastActorId.equals(lastRaiser)) {
                return true;
            }
            String nextActor = getCurrentPlayerId();
            return lastRaiser.equals(nextActor);
        }

        return currentIdx == 0;
    }

    private boolean allBetsMatched() {
        for (Player player : players.values()) {
            if (player.isFolded() || player.getStatus() == PlayerStatus.WAITING) {
                continue;
            }
            if (!player.isAllIn() && player.getCommitted() < state.getCurrentBet()) {
                return false;
            }
        }
        return true;
    }

    private void nextPhase() {
        for (Player player : players.values()) {
            if (!player.isFolded() && player.getStatus() != PlayerStatus.WAITING) {
                player.resetCommitted();
            }
        }
        state.setCurrentBet(0);
        lastRaiser = null;
        minRaise = DEFAULT_BIG_BLIND;

        if (countNotFolded() <= 1) {
            awardPotToSingleWinner();
            return;
        }

        switch (state.getPhase()) {
            case PREFLOP -> {
                state.setPhase(GamePhase.FLOP);
                dealCommunityCards(3);
            }
            case FLOP -> {
                state.setPhase(GamePhase.TURN);
                dealCommunityCards(1);
            }
            case TURN -> {
                state.setPhase(GamePhase.RIVER);
                dealCommunityCards(1);
            }
            case RIVER -> {
                state.setPhase(GamePhase.SHOWDOWN);
                determineAndPayWinners();
                return;
            }
            default -> {
                return;
            }
        }

        if (countCanAct() == 0) {
            nextPhase();
            return;
        }

        activeOrder = buildActiveOrder(dealerSeat);
        currentIdx = 0;
        state.setCurrentTurnSeat(getCurrentPlayerSeat());
    }

    private void dealCommunityCards(int count) {
        for (int i = 0; i < count; i++) {
            Card card = cardStore.dealCard();
            if (card != null) {
                state.getCommunityCards().add(card);
            }
        }
    }

    private void determineAndPayWinners() {
        List<Player> contenders = new ArrayList<>();
        for (Player player : players.values()) {
            if (!player.isFolded() && player.getStatus() != PlayerStatus.WAITING) {
                contenders.add(player);
            }
        }

        if (contenders.size() == 1) {
            awardPotToSingleWinner();
            return;
        }

        HandResult best = null;
        List<Player> winners = new ArrayList<>();

        for (Player player : contenders) {
            List<Card> hand = privateHands.get(player.getId());
            if (hand == null || hand.size() < 2) {
                continue;
            }
            HandResult result = HandEvaluator.evaluate(hand, state.getCommunityCards());
            if (best == null || result.compareTo(best) > 0) {
                best = result;
                winners.clear();
                winners.add(player);
            } else if (result.compareTo(best) == 0) {
                winners.add(player);
            }
        }

        if (winners.isEmpty()) {
            state.setCurrentTurnSeat(null);
            return;
        }

        long share = state.getPot() / winners.size();
        long remainder = state.getPot() % winners.size();
        for (int i = 0; i < winners.size(); i++) {
            int payout = (int) share + (i == 0 ? (int) remainder : 0);
            winners.get(i).addChips(payout);
        }
        state.setPot(0);
        state.setCurrentTurnSeat(null);
    }

    private void awardPotToSingleWinner() {
        for (Player player : players.values()) {
            if (!player.isFolded() && player.getStatus() != PlayerStatus.WAITING) {
                player.addChips((int) state.getPot());
                state.setPot(0);
                state.setPhase(GamePhase.SHOWDOWN);
                state.setCurrentTurnSeat(null);
                return;
            }
        }
    }

    private int countNotFolded() {
        int count = 0;
        for (Player player : players.values()) {
            if (!player.isFolded() && player.getStatus() != PlayerStatus.WAITING) {
                count++;
            }
        }
        return count;
    }

    private int countCanAct() {
        int count = 0;
        for (Player player : players.values()) {
            if (!player.isFolded() && !player.isAllIn() && player.getStatus() != PlayerStatus.WAITING) {
                count++;
            }
        }
        return count;
    }

    private void removeFromActiveOrder(String playerId) {
        int idx = activeOrder.indexOf(playerId);
        if (idx < 0) {
            return;
        }
        activeOrder.remove(idx);
        if (activeOrder.isEmpty()) {
            return;
        }
        if (currentIdx > idx) {
            currentIdx--;
        }
        currentIdx = currentIdx % activeOrder.size();
    }

    private String getCurrentPlayerId() {
        if (activeOrder == null || activeOrder.isEmpty() || currentIdx >= activeOrder.size()) {
            return null;
        }
        return activeOrder.get(currentIdx);
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