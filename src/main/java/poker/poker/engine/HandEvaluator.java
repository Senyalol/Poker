package poker.poker.engine;

import poker.poker.domain.Card;
import poker.poker.domain.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.*;

/**
 * Определитель комбинации
 */
public class HandEvaluator {

    /**
     * Главный метод — оценить руку из 2 карт игрока + 5 общих
     */
    public static HandResult evaluate(List<Card> holeCards, List<Card> communityCards) {
        // Объединяем все 7 карт в один список
        List<Card> allCards = new ArrayList<>();
        allCards.addAll(holeCards);
        allCards.addAll(communityCards);

        return findBestHand(allCards);
    }

    /**
     * Перебирает все возможные комбинации из 5 карт и находит лучшую
     */
    private static HandResult findBestHand(List<Card> allCards) {
        HandResult best = null;

        // Перебираем ВСЕ сочетания для 5 игроков
        for (int i = 0; i < allCards.size(); i++) {
            for (int j = i + 1; j < allCards.size(); j++) {
                for (int k = j + 1; k < allCards.size(); k++) {
                    for (int l = k + 1; l < allCards.size(); l++) {
                        for (int m = l + 1; m < allCards.size(); m++) {

                            List<Card> fiveCards = Arrays.asList(
                                    allCards.get(i), allCards.get(j),
                                    allCards.get(k), allCards.get(l), allCards.get(m)
                            );

                            HandResult current = evaluateFiveCards(fiveCards);

                            if (best == null || current.compareTo(best) > 0) {
                                best = current;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    /**
     * Оценивает конкретные 5 карт и определяет комбинацию
     */
    private static HandResult evaluateFiveCards(List<Card> cards) {
        // Сортируем карты по убыванию достоинства
        List<Integer> values = cards.stream()
                .map(c -> c.getRank().getValue())
                .sorted(Comparator.reverseOrder())
                .toList();

        boolean flush = isFlush(cards);
        boolean straight = isStraight(values);

        // 1. Роял-флеш: стрит от 10 до туза + все одной масти
        if (flush && straight && values.get(0) == 14 && values.get(1) == 13) {
            return new HandResult(HandRank.ROYAL_FLUSH, values);
        }

        // 2. Стрит-флеш: просто стрит + флеш
        if (flush && straight) {
            return new HandResult(HandRank.STRAIGHT_FLUSH, values);
        }

        // 3. Каре: 4 одинаковых
        if (hasNOfAKind(values, 4)) {
            List<Integer> ordered = orderByGroup(values, 4);
            return new HandResult(HandRank.FOUR_OF_A_KIND, ordered);
        }

        // 4. Фулл-хаус: тройка + пара
        if (isFullHouse(values)) {
            List<Integer> ordered = orderFullHouse(values);
            return new HandResult(HandRank.FULL_HOUSE, ordered);
        }

        // 5. Флеш: 5 карт одной масти
        if (flush) {
            return new HandResult(HandRank.FLUSH, values);
        }

        // 6. Стрит: 5 карт подряд
        if (straight) {
            return new HandResult(HandRank.STRAIGHT, values);
        }

        // 7. Тройка: 3 одинаковых
        if (hasNOfAKind(values, 3)) {
            List<Integer> ordered = orderByGroup(values, 3);
            return new HandResult(HandRank.THREE_OF_A_KIND, ordered);
        }

        // 8. Две пары
        if (isTwoPair(values)) {
            List<Integer> ordered = orderTwoPair(values);
            return new HandResult(HandRank.TWO_PAIR, ordered);
        }

        // 9. Одна пара
        if (hasNOfAKind(values, 2)) {
            List<Integer> ordered = orderByGroup(values, 2);
            return new HandResult(HandRank.ONE_PAIR, ordered);
        }

        // 10. Ничего — старшая карта
        return new HandResult(HandRank.HIGH_CARD, values);
    }

    /**
     * Проверка: все 5 карт одной масти?
     */
    private static boolean isFlush(List<Card> cards) {
        Suit firstSuit = cards.get(0).getSuit();
        return cards.stream().allMatch(c -> c.getSuit() == firstSuit);
    }

    private static boolean isStraight(List<Integer> values) {
        // Обычный стрит: 5 карт подряд
        boolean normal = true;
        for (int i = 0; i < values.size() - 1; i++) {
            if (values.get(i) - values.get(i + 1) != 1) {
                normal = false;
                break;
            }
        }
        if (normal) return true;

        // Специальный случай: "колесо" A-2-3-4-5
        // Туз (14) идёт как младшая карта
        return values.get(0) == 14
                && values.get(1) == 5
                && values.get(2) == 4
                && values.get(3) == 3
                && values.get(4) == 2;
    }

    private static boolean hasNOfAKind(List<Integer> values, int n) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int v : values) {
            countMap.put(v, countMap.getOrDefault(v, 0) + 1);
        }
        return countMap.containsValue(n);
    }

    private static boolean isFullHouse(List<Integer> values) {
        return hasNOfAKind(values, 3) && hasNOfAKind(values, 2);
    }

    private static boolean isTwoPair(List<Integer> values) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int v : values) {
            countMap.put(v, countMap.getOrDefault(v, 0) + 1);
        }

        int pairCount = 0;
        for (int count : countMap.values()) {
            if (count == 2) pairCount++;
        }
        return pairCount == 2;
    }

    /**
     * Выносит группу из n одинаковых карт вперёд списка,
     * остальные сортирует по убыванию
     */
    private static List<Integer> orderByGroup(List<Integer> values, int n) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int v : values) {
            countMap.put(v, countMap.getOrDefault(v, 0) + 1);
        }

        List<Integer> result = new ArrayList<>();
        List<Integer> kickers = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() == n) {
                // Добавляем n одинаковых карт в начало
                for (int i = 0; i < n; i++) {
                    result.add(entry.getKey());
                }
            } else {
                kickers.add(entry.getKey());
            }
        }

        kickers.sort(Comparator.reverseOrder());
        result.addAll(kickers);
        return result;
    }

    private static List<Integer> orderTwoPair(List<Integer> values) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int v : values) {
            countMap.put(v, countMap.getOrDefault(v, 0) + 1);
        }

        List<Integer> pairs = new ArrayList<>();
        List<Integer> kickers = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() == 2) {
                pairs.add(entry.getKey());
            } else {
                kickers.add(entry.getKey());
            }
        }

        pairs.sort(Comparator.reverseOrder());  // старшая пара первой
        List<Integer> result = new ArrayList<>();
        result.addAll(pairs);  // обе пары
        result.addAll(kickers);  // кикер в конце
        return result;
    }

    private static List<Integer> orderFullHouse(List<Integer> values) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (int v : values) {
            countMap.put(v, countMap.getOrDefault(v, 0) + 1);
        }

        int trips = 0, pair = 0;
        for (Map.Entry<Integer, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() == 3) trips = entry.getKey();
            if (entry.getValue() == 2) pair = entry.getKey();
        }

        return Arrays.asList(trips, trips, trips, pair, pair);
    }

}