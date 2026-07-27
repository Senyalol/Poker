package poker.poker.domain;

public enum GamePhase {
    WAITING,    // Ожидание начала раздачи
    PREFLOP,    // Префлоп — у игроков по 2 карты, общих нет
    FLOP,       // Флоп — 3 общие карты на столе
    TURN,       // Тёрн — 4-я общая карта
    RIVER,      // Ривер — 5-я общая карта
    SHOWDOWN    // Вскрытие карт и определение победителя
}