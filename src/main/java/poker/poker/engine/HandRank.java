package poker.poker.engine;

/**
 * Enum для оценок комбинаций
 */
public enum HandRank {
    HIGH_CARD,       // 0 - Ничего, просто старшая карта
    ONE_PAIR,        // 1 - Одна пара
    TWO_PAIR,        // 2 - Две пары
    THREE_OF_A_KIND, // 3 - Тройка (сет)
    STRAIGHT,        // 4 - Стрит (5 карт подряд)
    FLUSH,           // 5 - Флеш (5 карт одной масти)
    FULL_HOUSE,      // 6 - Фулл-хаус (тройка + пара)
    FOUR_OF_A_KIND,  // 7 - Каре (4 одинаковых)
    STRAIGHT_FLUSH,  // 8 - Стрит-флеш (5 подряд одной масти)
    ROYAL_FLUSH      // 9 - Роял-флеш (10-J-Q-K-A одной масти)
}