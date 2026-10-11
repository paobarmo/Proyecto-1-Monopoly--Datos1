import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable record of a player's move on the Board: the squares passed
 * through without stopping (in order) and the square the player landed on.
 * The square the move started from is not included.
 */
public final class MoveResult {

    private final List<Casilla> passedSquares;
    private final Casilla landingSquare;

    public MoveResult(List<Casilla> passedSquares, Casilla landingSquare) {
        if (passedSquares == null || landingSquare == null) {
            throw new IllegalArgumentException("Passed squares and landing square cannot be null.");
        }
        this.passedSquares = Collections.unmodifiableList(new ArrayList<>(passedSquares));
        this.landingSquare = landingSquare;
    }

    /**
     * Squares crossed without stopping, in the order they were crossed.
     */
    public List<Casilla> getPassedSquares() {
        return passedSquares;
    }

    /**
     * The square where the move ended.
     */
    public Casilla getLandingSquare() {
        return landingSquare;
    }
}
