import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the Monopoly game board as a circular doubly linked list.
 * Each node holds a reference to a Casilla (board square). The board also
 * keeps track of the current position of every Player on it.
 *
 * NOTE: This class depends on two types that are not yet implemented:
 *   - Casilla: base class for board squares (to be created next).
 *   - Player: represents a game player. A minimal contract is assumed here
 *             (equals/hashCode usable as a Map key). Replace or extend as
 *             the Player class is developed.
 */
public class Board {

    /**
     * Internal node of the circular doubly linked list.
     */
    private static class Node {
        Casilla data;
        Node prev;
        Node next;

        Node(Casilla data) {
            this.data = data;
        }
    }

    private Node first;
    private Node last;
    private int size;

    // Tracks each player's current node on the board.
    private final Map<Player, Node> playerPositions;

    public Board() {
        this.first = null;
        this.last = null;
        this.size = 0;
        // Read by the screen while a background thread may remove a player.
        this.playerPositions = new ConcurrentHashMap<>();
    }

    /**
     * Appends a new Casilla to the end of the board, preserving circularity.
     *
     * @param casilla the square to add
     */
    public void addCasilla(Casilla casilla) {
        if (casilla == null) {
            throw new IllegalArgumentException("Casilla cannot be null.");
        }

        Node newNode = new Node(casilla);

        if (first == null) {
            first = newNode;
            last = newNode;
            newNode.next = newNode;
            newNode.prev = newNode;
        } else {
            newNode.prev = last;
            newNode.next = first;
            last.next = newNode;
            first.prev = newNode;
            last = newNode;
        }

        size++;
    }

    /**
     * Returns the Casilla located at the given index, starting from the
     * first inserted square. Index is taken modulo the board size, so it
     * always resolves to a valid position.
     *
     * @param index position on the board
     * @return the Casilla at that position
     */
    public Casilla getCasilla(int index) {
        checkNotEmpty();

        Node current = first;
        int steps = Math.floorMod(index, size);

        for (int i = 0; i < steps; i++) {
            current = current.next;
        }

        return current.data;
    }

    /**
     * Places a player on the board at the first square (starting position).
     *
     * @param player the player to add
     */
    public void addPlayer(Player player) {
        checkNotEmpty();

        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null.");
        }

        playerPositions.put(player, first);
    }

    /**
     * Places a player on the board at a specific starting index.
     *
     * @param player the player to add
     * @param startIndex the index of the square where the player starts
     */
    public void addPlayer(Player player, int startIndex) {
        checkNotEmpty();

        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null.");
        }

        Node startNode = findNode(startIndex);
        playerPositions.put(player, startNode);
    }

    /**
     * Moves a player forward a given number of steps, wrapping around the
     * board when the end is reached, and records every square involved.
     *
     * Squares the player crosses without stopping are reported as "passed";
     * the final square is reported as the landing square. The square the
     * player started on is not included. This lets events such as Go be
     * triggered both when a player passes the square and when landing on it.
     *
     * @param player the player to move
     * @param steps number of squares to advance (must be positive)
     * @return the squares passed (in order) and the square landed on
     */
    public MoveResult movePlayer(Player player, int steps) {
        checkNotEmpty();

        Node currentNode = playerPositions.get(player);
        if (currentNode == null) {
            throw new IllegalArgumentException("Player is not on the board.");
        }
        if (steps < 1) {
            throw new IllegalArgumentException("Steps must be positive.");
        }

        List<Casilla> passed = new ArrayList<>();
        Node destination = currentNode;
        for (int i = 1; i <= steps; i++) {
            destination = destination.next;
            if (i < steps) {
                passed.add(destination.data);
            }
        }

        playerPositions.put(player, destination);
        return new MoveResult(passed, destination.data);
    }

    /**
     * Returns the Casilla a player is currently standing on.
     *
     * @param player the player to query
     * @return the current Casilla
     */
    public Casilla getCurrentCasilla(Player player) {
        Node node = playerPositions.get(player);
        if (node == null) {
            throw new IllegalArgumentException("Player is not on the board.");
        }
        return node.data;
    }

    /**
     * Removes a player from the board (e.g. when they lose the game). Their
     * token stops being on any square.
     *
     * @param player the player to remove
     */
    public void removePlayer(Player player) {
        playerPositions.remove(player);
    }

    /**
     * Returns every property square of the given color group, in board order.
     *
     * @param colorGroup the color group to look for
     * @return the properties of that group (empty if there are none)
     */
    public List<CasillaPropiedad> getPropertiesByColorGroup(String colorGroup) {
        List<CasillaPropiedad> properties = new ArrayList<>();
        if (first == null) {
            return properties;
        }

        Node current = first;
        for (int i = 0; i < size; i++) {
            if (current.data instanceof CasillaPropiedad) {
                CasillaPropiedad property = (CasillaPropiedad) current.data;
                if (property.getColorGroup().equals(colorGroup)) {
                    properties.add(property);
                }
            }
            current = current.next;
        }
        return properties;
    }

    /**
     * Returns the total number of squares on the board.
     *
     * @return board size
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns whether the board has no squares.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    private Node findNode(int index) {
        Node current = first;
        int steps = Math.floorMod(index, size);

        for (int i = 0; i < steps; i++) {
            current = current.next;
        }

        return current;
    }

    private void checkNotEmpty() {
        if (isEmpty()) {
            throw new IllegalStateException("Board has no squares yet.");
        }
    }
}
