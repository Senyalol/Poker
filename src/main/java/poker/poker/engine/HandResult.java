package poker.poker.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Класс для сравнения сильнейшей комбинации стола
 */
@Data
@AllArgsConstructor
public class HandResult implements Comparable<HandResult>{

    private HandRank rank;
    private List<Integer> values;

    /**
     * Сравнивает две руки.
     * @return 1 если эта рука сильнее, -1 если слабее, 0 если одинаково
     */
    @Override
    public int compareTo(HandResult other) {
        // Сначала сравниваем ранг комбинации
        int rankCompare = Integer.compare(this.rank.ordinal(), other.rank.ordinal());
        if (rankCompare != 0) {
            return rankCompare;
        }

        // Если ранги равны — сравниваем по очереди все 5 карт
        for (int i = 0; i < this.values.size(); i++) {
            int cardCompare = Integer.compare(this.values.get(i), other.values.get(i));
            if (cardCompare != 0) {
                return cardCompare;
            }
        }

        return 0;  // Полностью одинаковые руки — дележка банка
    }



}