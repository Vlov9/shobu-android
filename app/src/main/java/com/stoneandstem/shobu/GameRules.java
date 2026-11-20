package com.stoneandstem.shobu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pure Shobu rules and state. Coordinates and board orientation are fixed for the whole game. */
public final class GameRules {
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int EMPTY = 0;
    public static final int BOARD_COUNT = 4;
    public static final int SIZE = 4;

    public enum Phase { SETUP, AGGRESSIVE, FINISHED }

    public static final class Move {
        public final int board, fromRow, fromCol, toRow, toCol, dx, dy, distance;

        public Move(int board, int fromRow, int fromCol, int toRow, int toCol,
                    int dx, int dy, int distance) {
            this.board = board;
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.dx = dx;
            this.dy = dy;
            this.distance = distance;
        }

        @Override public boolean equals(Object object) {
            if (!(object instanceof Move)) return false;
            Move other = (Move) object;
            return board == other.board && fromRow == other.fromRow && fromCol == other.fromCol
                    && toRow == other.toRow && toCol == other.toCol && dx == other.dx
                    && dy == other.dy && distance == other.distance;
        }

        @Override public int hashCode() {
            return Objects.hash(board, fromRow, fromCol, toRow, toCol, dx, dy, distance);
        }
    }

    /** pieces[board][row][column], with row 0 always at the top of every board. */
    public final int[][][] pieces = new int[BOARD_COUNT][SIZE][SIZE];
    public int currentPlayer = BLACK;
    public int turnNumber = 1;
    public int winner = EMPTY;
    public Phase phase = Phase.SETUP;
    private Move passiveMove;
    private List<Move> passiveOptions = Collections.emptyList();

    public GameRules() { reset(); }

    public void reset() {
        for (int b = 0; b < BOARD_COUNT; b++) {
            for (int c = 0; c < SIZE; c++) {
                pieces[b][0][c] = WHITE;
                pieces[b][1][c] = EMPTY;
                pieces[b][2][c] = EMPTY;
                pieces[b][3][c] = BLACK;
            }
        }
        currentPlayer = BLACK;
        turnNumber = 1;
        winner = EMPTY;
        phase = Phase.SETUP;
        passiveMove = null;
        passiveOptions = Collections.emptyList();
    }

    public static int opponent(int player) { return player == BLACK ? WHITE : BLACK; }

    /** Homeboards are the two boards on the player's side of the fixed horizontal home line. */
    public static boolean isHomeBoard(int board, int player) {
        return player == BLACK ? board / 2 == 1 : board / 2 == 0;
    }

    /** Alternating shades; diagonal boards share a shade. */
    public static int shade(int board) {
        int row = board / 2, col = board % 2;
        return (row + col) & 1;
    }

    public List<Move> passiveMovesFrom(int board, int row, int col) {
        if (phase != Phase.SETUP || !inside(board, row, col) || !isHomeBoard(board, currentPlayer)
                || pieces[board][row][col] != currentPlayer) return Collections.emptyList();
        ArrayList<Move> legal = new ArrayList<>();
        for (Move move : rawPassiveMoves(board, row, col)) {
            moveStone(move, currentPlayer);
            Move saved = passiveMove;
            passiveMove = move;
            boolean hasAttack = !rawAggressiveMoves(move).isEmpty();
            passiveMove = saved;
            moveStone(reverse(move), currentPlayer);
            if (hasAttack) legal.add(move);
        }
        return legal;
    }

    public boolean makePassive(Move move) {
        if (phase != Phase.SETUP || move == null || !passiveMovesFrom(move.board, move.fromRow, move.fromCol).contains(move))
            return false;
        moveStone(move, currentPlayer);
        passiveMove = move;
        phase = Phase.AGGRESSIVE;
        return true;
    }

    public List<Move> aggressiveMovesFrom(int board, int row, int col) {
        if (phase != Phase.AGGRESSIVE || passiveMove == null || !inside(board, row, col)
                || pieces[board][row][col] != currentPlayer) return Collections.emptyList();
        ArrayList<Move> result = new ArrayList<>();
        for (Move move : rawAggressiveMoves(passiveMove))
            if (move.board == board && move.fromRow == row && move.fromCol == col) result.add(move);
        return result;
    }

    public List<Move> allAggressiveMoves() {
        if (phase != Phase.AGGRESSIVE || passiveMove == null) return Collections.emptyList();
        return Collections.unmodifiableList(rawAggressiveMoves(passiveMove));
    }

    public boolean undoPassive() {
        if (phase != Phase.AGGRESSIVE || passiveMove == null) return false;
        moveStone(reverse(passiveMove), currentPlayer);
        passiveMove = null;
        passiveOptions = Collections.emptyList();
        phase = Phase.SETUP;
        return true;
    }

    public boolean makeAggressive(Move move) {
        if (phase != Phase.AGGRESSIVE || move == null || !rawAggressiveMoves(passiveMove).contains(move)) return false;
        executeAggressive(move, currentPlayer);
        int mover = currentPlayer;
        if (hasWon(mover)) {
            winner = mover;
            phase = Phase.FINISHED;
            passiveMove = null;
            return true;
        }
        currentPlayer = opponent(currentPlayer);
        turnNumber++;
        passiveMove = null;
        phase = Phase.SETUP;
        if (!hasAnyPassiveMove(currentPlayer)) {
            winner = opponent(currentPlayer);
            phase = Phase.FINISHED;
        }
        return true;
    }

    public boolean hasWon(int player) {
        int opposing = opponent(player);
        for (int b = 0; b < BOARD_COUNT; b++) {
            boolean foundOpponent = false;
            for (int r = 0; r < SIZE && !foundOpponent; r++)
                for (int c = 0; c < SIZE; c++) if (pieces[b][r][c] == opposing) { foundOpponent = true; break; }
            if (!foundOpponent) return true;
        }
        return false;
    }

    public int countPieces(int player) {
        int count = 0;
        for (int b = 0; b < BOARD_COUNT; b++) for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++) if (pieces[b][r][c] == player) count++;
        return count;
    }

    /** Test/setup helper: edit a position only when not in the middle of a turn. */
    public void setStone(int board, int row, int col, int player) {
        if (!inside(board, row, col) || (player != EMPTY && player != BLACK && player != WHITE))
            throw new IllegalArgumentException("Invalid board position or stone");
        pieces[board][row][col] = player;
    }

    private boolean hasAnyPassiveMove(int player) {
        int prior = currentPlayer;
        currentPlayer = player;
        for (int b = 0; b < BOARD_COUNT; b++) if (isHomeBoard(b, player))
            for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++)
                if (pieces[b][r][c] == player && !passiveMovesFrom(b, r, c).isEmpty()) {
                    currentPlayer = prior;
                    return true;
                }
        currentPlayer = prior;
        return false;
    }

    private List<Move> rawPassiveMoves(int board, int row, int col) {
        ArrayList<Move> moves = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dy == 0) continue;
            for (int distance = 1; distance <= 2; distance++) {
                int tr = row + dy * distance, tc = col + dx * distance;
                if (!inside(board, tr, tc)) break;
                boolean clear = true;
                for (int step = 1; step <= distance; step++)
                    if (pieces[board][row + dy * step][col + dx * step] != EMPTY) { clear = false; break; }
                if (clear) moves.add(new Move(board, row, col, tr, tc, dx, dy, distance));
            }
        }
        return moves;
    }

    private List<Move> rawAggressiveMoves(Move setup) {
        ArrayList<Move> moves = new ArrayList<>();
        if (setup == null) return moves;
        int requiredShade = 1 - shade(setup.board);
        for (int b = 0; b < BOARD_COUNT; b++) if (shade(b) == requiredShade)
            for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++)
                if (pieces[b][r][c] == currentPlayer) {
                    int tr = r + setup.dy * setup.distance, tc = c + setup.dx * setup.distance;
                    if (!inside(b, tr, tc)) continue;
                    Move candidate = new Move(b, r, c, tr, tc, setup.dx, setup.dy, setup.distance);
                    if (isLegalAggressive(candidate, currentPlayer)) moves.add(candidate);
                }
        return moves;
    }

    private boolean isLegalAggressive(Move move, int player) {
        int enemy = opponent(player), enemyStep = -1;
        for (int step = 1; step <= move.distance; step++) {
            int r = move.fromRow + move.dy * step, c = move.fromCol + move.dx * step;
            if (!inside(move.board, r, c)) return false;
            int occupant = pieces[move.board][r][c];
            if (occupant == player) return false;
            if (occupant == enemy) {
                if (enemyStep != -1) return false; // Shobu permits pushing at most one stone.
                enemyStep = step;
            }
        }
        if (enemyStep < 0) return true;
        // The pushed stone advances the full move distance. Any occupied square in its path blocks it.
        for (int step = 1; step <= move.distance; step++) {
            int r = move.fromRow + move.dy * (enemyStep + step);
            int c = move.fromCol + move.dx * (enemyStep + step);
            if (inside(move.board, r, c) && pieces[move.board][r][c] != EMPTY) return false;
        }
        return true;
    }

    private void executeAggressive(Move move, int player) {
        int enemy = opponent(player), enemyStep = -1;
        for (int step = 1; step <= move.distance; step++) {
            int r = move.fromRow + move.dy * step, c = move.fromCol + move.dx * step;
            if (pieces[move.board][r][c] == enemy) { enemyStep = step; break; }
        }
        pieces[move.board][move.fromRow][move.fromCol] = EMPTY;
        if (enemyStep >= 0) {
            int er = move.fromRow + move.dy * enemyStep, ec = move.fromCol + move.dx * enemyStep;
            int endRow = er + move.dy * move.distance, endCol = ec + move.dx * move.distance;
            pieces[move.board][er][ec] = EMPTY;
            if (inside(move.board, endRow, endCol)) pieces[move.board][endRow][endCol] = enemy;
        }
        pieces[move.board][move.toRow][move.toCol] = player;
    }

    private void moveStone(Move move, int player) {
        pieces[move.board][move.fromRow][move.fromCol] = EMPTY;
        pieces[move.board][move.toRow][move.toCol] = player;
    }

    private static Move reverse(Move move) {
        return new Move(move.board, move.toRow, move.toCol, move.fromRow, move.fromCol,
                -move.dx, -move.dy, move.distance);
    }

    private static boolean inside(int board, int row, int col) {
        return board >= 0 && board < BOARD_COUNT && row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }
}
