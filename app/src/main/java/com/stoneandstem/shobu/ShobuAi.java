package com.stoneandstem.shobu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Local Shobu opponent using UCT Monte-Carlo tree search.
 *
 * A search action is a complete Shobu turn (one setup move plus its matching push), so the
 * search tree never stops in the middle of a turn. Progressive widening lets it prioritize
 * tactical moves without expanding every legal turn at once.
 */
public final class ShobuAi {
    public enum Difficulty {
        EASY(0, 0),
        MEDIUM(300, 8),
        HARD(900, 12);

        final int simulations;
        final int rolloutTurns;
        Difficulty(int simulations, int rolloutTurns) {
            this.simulations = simulations;
            this.rolloutTurns = rolloutTurns;
        }
    }

    public static final class Turn {
        public final GameRules.Move setupMove;
        public final GameRules.Move pushMove;

        Turn(GameRules.Move setupMove, GameRules.Move pushMove) {
            this.setupMove = setupMove;
            this.pushMove = pushMove;
        }

        /** Applies both moves to a copy and returns the resulting position. */
        public GameRules apply(GameRules position) {
            GameRules next = position.copy();
            if (!next.makePassive(setupMove) || !next.makeAggressive(pushMove))
                throw new IllegalArgumentException("Turn is not legal in this position");
            return next;
        }
    }

    private static final double UCT_EXPLORATION = 1.41421356237;
    private static final double WIDENING = 1.8;

    private ShobuAi() {}

    public static Turn chooseMove(GameRules position, Difficulty difficulty) {
        return chooseMove(position, difficulty, System.nanoTime());
    }

    /** The seed overload makes search results reproducible in tests. */
    public static Turn chooseMove(GameRules position, Difficulty difficulty, long seed) {
        if (position == null || difficulty == null) throw new IllegalArgumentException("Position and difficulty are required");
        if (Thread.currentThread().isInterrupted()) return null;
        if (position.phase != GameRules.Phase.SETUP) return null;

        final int aiPlayer = position.currentPlayer;
        final Random random = new Random(seed);
        long[] rootActions = generateActions(position);
        if (Thread.currentThread().isInterrupted()) return null;
        if (rootActions.length == 0) return null;

        // Never overlook a one-turn win, even at the easiest setting.
        for (long encoded : rootActions) {
            Turn turn = decodeTurn(encoded);
            if (winsImmediatelyByEjection(position, turn.pushMove)) return turn;
        }

        if (difficulty == Difficulty.EASY) {
            int choicePool = Math.min(rootActions.length, Math.max(1, Math.min(16, (rootActions.length + 3) / 4)));
            return decodeTurn(rootActions[random.nextInt(choicePool)]);
        }

        Node root = new Node(position.copy(), aiPlayer);
        root.actions = rootActions;
        for (int i = 0; i < difficulty.simulations; i++) {
            if (Thread.currentThread().isInterrupted()) return null;
            runSimulation(root, aiPlayer, difficulty.rolloutTurns, random);
        }
        if (root.children.isEmpty()) return decodeTurn(rootActions[0]);

        Node best = root.children.get(0);
        for (int i = 1; i < root.children.size(); i++) {
            Node candidate = root.children.get(i);
            if (candidate.visits > best.visits
                    || (candidate.visits == best.visits && candidate.meanValue() > best.meanValue()))
                best = candidate;
        }
        return decodeTurn(best.action);
    }

    private static final class Node {
        final GameRules state;
        final int rootPlayer;
        final long action;
        long[] actions;
        final ArrayList<Node> children = new ArrayList<>();
        int visits;
        double valueSum;

        Node(GameRules state, int rootPlayer) { this(state, rootPlayer, 0L); }
        Node(GameRules state, int rootPlayer, long action) {
            this.state = state;
            this.rootPlayer = rootPlayer;
            this.action = action;
        }
        double meanValue() { return visits == 0 ? 0.0 : valueSum / visits; }
    }

    private static void runSimulation(Node root, int rootPlayer, int rolloutLimit, Random random) {
        ArrayList<Node> path = new ArrayList<>();
        Node node = root;
        path.add(node);
        double value;

        while (true) {
            if (Thread.currentThread().isInterrupted()) return;
            if (node.state.phase == GameRules.Phase.FINISHED) {
                value = node.state.winner == rootPlayer ? 1.0 : -1.0;
                break;
            }
            if (node.actions == null) node.actions = generateActions(node.state);
            if (node.actions.length == 0) {
                value = node.state.currentPlayer == rootPlayer ? -1.0 : 1.0;
                break;
            }

            int allowedChildren = Math.min(node.actions.length,
                    1 + (int) (WIDENING * Math.sqrt(node.visits + 1.0)));
            if (node.children.size() < allowedChildren) {
                long action = node.actions[node.children.size()];
                GameRules childState = apply(node.state, action);
                Node child = new Node(childState, rootPlayer, action);
                node.children.add(child);
                node = child;
                path.add(node);
                value = rollout(childState, rootPlayer, rolloutLimit, random);
                break;
            }

            node = selectChild(node, rootPlayer);
            path.add(node);
        }

        for (Node visited : path) {
            visited.visits++;
            visited.valueSum += value;
        }
    }

    private static Node selectChild(Node parent, int rootPlayer) {
        double logParent = Math.log(parent.visits + 1.0);
        boolean maximizing = parent.state.currentPlayer == rootPlayer;
        Node best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Node child : parent.children) {
            double exploitation = child.meanValue() * (maximizing ? 1.0 : -1.0);
            double exploration = UCT_EXPLORATION * Math.sqrt(logParent / Math.max(1, child.visits));
            double score = exploitation + exploration;
            if (score > bestScore) { bestScore = score; best = child; }
        }
        return best;
    }

    private static double rollout(GameRules starting, int rootPlayer, int turnLimit, Random random) {
        GameRules state = starting.copy();
        for (int turn = 0; turn < turnLimit && state.phase != GameRules.Phase.FINISHED; turn++) {
            if (Thread.currentThread().isInterrupted()) return evaluate(state, rootPlayer);
            Turn action = randomTurn(state, random);
            if (action == null) return state.currentPlayer == rootPlayer ? -1.0 : 1.0;
            state = action.apply(state);
        }
        if (state.phase == GameRules.Phase.FINISHED) return state.winner == rootPlayer ? 1.0 : -1.0;
        return evaluate(state, rootPlayer);
    }

    private static Turn randomTurn(GameRules state, Random random) {
        ArrayList<GameRules.Move> setupMoves = new ArrayList<>();
        int player = state.currentPlayer;
        for (int board = 0; board < GameRules.BOARD_COUNT; board++) {
            if (!GameRules.isHomeBoard(board, player)) continue;
            for (int row = 0; row < GameRules.SIZE; row++)
                for (int col = 0; col < GameRules.SIZE; col++)
                    if (state.pieces[board][row][col] == player)
                        setupMoves.addAll(state.passiveMovesFrom(board, row, col));
        }
        if (setupMoves.isEmpty()) return null;
        GameRules.Move setup = setupMoves.get(random.nextInt(setupMoves.size()));
        GameRules afterSetup = state.copy();
        if (!afterSetup.makePassive(setup)) return null;
        List<GameRules.Move> pushes = afterSetup.allAggressiveMoves();
        if (pushes.isEmpty()) return null;
        return new Turn(setup, pushes.get(random.nextInt(pushes.size())));
    }

    private static double evaluate(GameRules state, int player) {
        int other = GameRules.opponent(player);
        double material = (state.countPieces(player) - state.countPieces(other)) / 32.0;
        double bestOwnClear = 0.0, bestEnemyClear = 0.0;
        for (int b = 0; b < GameRules.BOARD_COUNT; b++) {
            int enemies = countOnBoard(state, b, other);
            int own = countOnBoard(state, b, player);
            bestOwnClear = Math.max(bestOwnClear, (4.0 - Math.min(4, enemies)) / 4.0);
            bestEnemyClear = Math.max(bestEnemyClear, (4.0 - Math.min(4, own)) / 4.0);
        }
        double pressure = bestOwnClear - bestEnemyClear;
        return clamp(0.72 * material + 0.28 * pressure, -1.0, 1.0);
    }

    private static int countOnBoard(GameRules state, int board, int player) {
        int count = 0;
        for (int r = 0; r < GameRules.SIZE; r++)
            for (int c = 0; c < GameRules.SIZE; c++)
                if (state.pieces[board][r][c] == player) count++;
        return count;
    }

    private static long[] generateActions(GameRules state) {
        if (state.phase != GameRules.Phase.SETUP) return new long[0];
        ArrayList<Long> actions = new ArrayList<>();
        int player = state.currentPlayer;
        for (int board = 0; board < GameRules.BOARD_COUNT; board++) {
            if (!GameRules.isHomeBoard(board, player)) continue;
            for (int row = 0; row < GameRules.SIZE; row++) {
                for (int col = 0; col < GameRules.SIZE; col++) {
                    if (state.pieces[board][row][col] != player) continue;
                    List<GameRules.Move> setupMoves = state.passiveMovesFrom(board, row, col);
                    for (GameRules.Move setup : setupMoves) {
                        GameRules afterSetup = state.copy();
                        if (!afterSetup.makePassive(setup)) continue;
                        for (GameRules.Move push : afterSetup.allAggressiveMoves())
                            actions.add(encodeTurn(setup, push));
                    }
                }
            }
        }

        Collections.sort(actions, new Comparator<Long>() {
            @Override public int compare(Long left, Long right) {
                return Double.compare(actionScore(state, right), actionScore(state, left));
            }
        });
        long[] packed = new long[actions.size()];
        for (int i = 0; i < actions.size(); i++) packed[i] = actions.get(i);
        return packed;
    }

    private static double actionScore(GameRules state, long encoded) {
        GameRules.Move setup = decodeMove((int) ((encoded >>> 16) & 0xffff));
        GameRules.Move push = decodeMove((int) (encoded & 0xffff));
        int player = state.currentPlayer;
        double score = 0.0;

        int enemyStep = -1;
        int enemy = GameRules.opponent(player);
        for (int step = 1; step <= push.distance; step++) {
            int row = push.fromRow + push.dy * step;
            int col = push.fromCol + push.dx * step;
            if (state.pieces[push.board][row][col] == enemy) { enemyStep = step; break; }
        }
        if (enemyStep >= 0) {
            int ejectedRow = push.fromRow + push.dy * (enemyStep + push.distance);
            int ejectedCol = push.fromCol + push.dx * (enemyStep + push.distance);
            if (ejectedRow < 0 || ejectedRow >= GameRules.SIZE || ejectedCol < 0 || ejectedCol >= GameRules.SIZE)
                score += 40.0;
            else score += 8.0;
        }

        score += advancement(setup, player) * 0.45 + advancement(push, player) * 0.35;
        score -= centerDistance(push.toRow, push.toCol) * 0.12;
        return score;
    }

    private static int advancement(GameRules.Move move, int player) {
        return player == GameRules.BLACK ? -move.dy : move.dy;
    }

    private static double centerDistance(int row, int col) {
        return Math.abs(1.5 - row) + Math.abs(1.5 - col);
    }

    private static boolean winsImmediatelyByEjection(GameRules state, GameRules.Move push) {
        int enemyStep = -1;
        int enemy = GameRules.opponent(state.currentPlayer);
        for (int step = 1; step <= push.distance; step++) {
            int row = push.fromRow + push.dy * step;
            int col = push.fromCol + push.dx * step;
            if (state.pieces[push.board][row][col] == enemy) { enemyStep = step; break; }
        }
        if (enemyStep < 0 || countOnBoard(state, push.board, enemy) != 1) return false;
        int finalRow = push.fromRow + push.dy * (enemyStep + push.distance);
        int finalCol = push.fromCol + push.dx * (enemyStep + push.distance);
        return finalRow < 0 || finalRow >= GameRules.SIZE || finalCol < 0 || finalCol >= GameRules.SIZE;
    }

    private static GameRules apply(GameRules state, long encoded) {
        Turn turn = decodeTurn(encoded);
        GameRules next = state.copy();
        if (!next.makePassive(turn.setupMove) || !next.makeAggressive(turn.pushMove))
            throw new IllegalStateException("Search generated an illegal turn");
        return next;
    }

    private static long encodeTurn(GameRules.Move setup, GameRules.Move push) {
        return (((long) encodeMove(setup)) << 16) | (encodeMove(push) & 0xffffL);
    }

    private static int encodeMove(GameRules.Move move) {
        return move.board
                | (move.fromRow << 2) | (move.fromCol << 4)
                | (move.toRow << 6) | (move.toCol << 8)
                | ((move.dx + 1) << 10) | ((move.dy + 1) << 12)
                | ((move.distance - 1) << 14);
    }

    private static GameRules.Move decodeMove(int value) {
        int board = value & 3;
        int fromRow = (value >>> 2) & 3;
        int fromCol = (value >>> 4) & 3;
        int toRow = (value >>> 6) & 3;
        int toCol = (value >>> 8) & 3;
        int dx = ((value >>> 10) & 3) - 1;
        int dy = ((value >>> 12) & 3) - 1;
        int distance = ((value >>> 14) & 1) + 1;
        return new GameRules.Move(board, fromRow, fromCol, toRow, toCol, dx, dy, distance);
    }

    private static Turn decodeTurn(long value) {
        return new Turn(decodeMove((int) ((value >>> 16) & 0xffff)), decodeMove((int) (value & 0xffff)));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

