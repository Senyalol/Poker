package poker.poker.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Player {

    private final String id;
    private int seat;
    private int stack;
    private int committed;
    private PlayerStatus status;

    public boolean placeBet(int amount) {
        if (amount > stack) return false;
        stack -= amount;
        committed += amount;
        return true;
    }

    public void addChips(int amount) { this.stack += amount; }
    public void resetCommitted() { this.committed = 0; }

    public boolean isActive() { return status == PlayerStatus.ACTIVE; }
    public boolean isFolded() { return status == PlayerStatus.FOLDED; }
    public boolean isAllIn() { return status == PlayerStatus.ALL_IN; }

    @Override
    public String toString() {
        return "Player{id='" + id + "', seat=" + seat +
                ", stack=" + stack + ", status=" + status + "}";
    }

}