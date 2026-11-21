package com.stoneandstem.shobu;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.List;

public class GameRulesTest {
    private GameRules emptyGame() {
        GameRules g = new GameRules();
        for (int b = 0; b < 4; b++) for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++)
            g.setStone(b, r, c, GameRules.EMPTY);
        // Keep valid opposing material on the untouched boards in these reduced test positions.
        g.setStone(1, 1, 1, GameRules.WHITE);
        g.setStone(2, 0, 3, GameRules.WHITE);
        g.setStone(3, 1, 3, GameRules.WHITE);
        return g;
    }

    private GameRules.Move find(List<GameRules.Move> moves, int board, int fr, int fc, int tr, int tc) {
        for (GameRules.Move m : moves)
            if (m.board == board && m.fromRow == fr && m.fromCol == fc && m.toRow == tr && m.toCol == tc) return m;
        return null;
    }

    @Test public void startingPositionHasSixteenStonesPerPlayerAndFixedHomeSides() {
        GameRules g = new GameRules();
        assertEquals(16, g.countPieces(GameRules.BLACK));
        assertEquals(16, g.countPieces(GameRules.WHITE));
        assertTrue(GameRules.isHomeBoard(2, GameRules.BLACK));
        assertTrue(GameRules.isHomeBoard(3, GameRules.BLACK));
        assertTrue(GameRules.isHomeBoard(0, GameRules.WHITE));
        assertTrue(GameRules.isHomeBoard(1, GameRules.WHITE));
        assertNotEquals(GameRules.shade(0), GameRules.shade(1));
        assertEquals(GameRules.shade(0), GameRules.shade(3));
    }

    @Test public void passiveMoveIsOneOrTwoClearSpacesAndMustEnableMatchingAttack() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(2, 2, 1, GameRules.WHITE); // Blocks the two-space diagonal.
        g.setStone(0, 1, 0, GameRules.BLACK); // Supports an up-one push on the other shade.
        g.setStone(0, 0, 0, GameRules.WHITE);
        List<GameRules.Move> moves = g.passiveMovesFrom(2, 3, 0);
        assertNotNull(find(moves, 2, 3, 0, 2, 0));
        assertNull(find(moves, 2, 3, 0, 1, 2));
        assertNull(find(moves, 2, 3, 0, 2, 1));
        assertTrue(g.passiveMovesFrom(0, 3, 0).isEmpty()); // Not a Black homeboard.
        assertTrue(g.passiveMovesFrom(2, 2, 1).isEmpty()); // Cannot set up with an opponent stone.
    }

    @Test public void undoSetupRestoresStoneAndTurn() {
        GameRules g = new GameRules();
        GameRules.Move move = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 2, 0);
        assertNotNull(move);
        assertTrue(g.makePassive(move));
        assertEquals(GameRules.Phase.AGGRESSIVE, g.phase);
        assertTrue(g.undoPassive());
        assertEquals(GameRules.BLACK, g.currentPlayer);
        assertEquals(GameRules.Phase.SETUP, g.phase);
        assertEquals(GameRules.BLACK, g.pieces[2][3][0]);
        assertEquals(GameRules.EMPTY, g.pieces[2][2][0]);
    }

    @Test public void aggressiveMoveMustMatchDirectionDistanceAndOppositeShade() {
        GameRules g = new GameRules();
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 1, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        assertNotNull(find(g.aggressiveMovesFrom(0, 3, 0), 0, 3, 0, 1, 0));
        assertNull(find(g.aggressiveMovesFrom(1, 3, 0), 1, 3, 0, 1, 0)); // Same shade as setup.
        assertNull(find(g.aggressiveMovesFrom(0, 3, 0), 0, 3, 0, 2, 0)); // Wrong distance.
    }

    @Test public void oneOpponentCanBePushedButTwoCannot() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 2, 1, GameRules.BLACK);
        g.setStone(0, 1, 1, GameRules.WHITE);
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 2, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        assertNotNull(find(g.aggressiveMovesFrom(0, 2, 1), 0, 2, 1, 1, 1));
        g.setStone(0, 0, 1, GameRules.WHITE);
        assertNull(find(g.aggressiveMovesFrom(0, 2, 1), 0, 2, 1, 1, 1));
    }

    @Test public void cannotPushOwnStoneOrJumpAStone() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 2, 1, GameRules.BLACK);
        g.setStone(0, 1, 1, GameRules.BLACK);
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 2, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        assertNull(find(g.aggressiveMovesFrom(0, 2, 1), 0, 2, 1, 1, 1));
        assertNull(find(g.aggressiveMovesFrom(0, 2, 1), 0, 2, 1, 0, 1));
    }

    @Test public void cannotPushAnOpponentIntoAnOccupiedSpace() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 2, 0, GameRules.BLACK);
        g.setStone(0, 1, 0, GameRules.WHITE);
        g.setStone(0, 0, 0, GameRules.BLACK);
        g.setStone(3, 2, 2, GameRules.BLACK);
        g.setStone(3, 1, 2, GameRules.WHITE); // A separate legal support move keeps the setup valid.
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 2, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        assertNull(find(g.aggressiveMovesFrom(0, 2, 0), 0, 2, 0, 1, 0));
    }

    @Test public void neitherMoveMayCarryThePlayersStoneOffTheBoard() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 1, 2, GameRules.BLACK);
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 3, 1);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        assertTrue(g.aggressiveMovesFrom(0, 1, 3).isEmpty()); // The matching rightward move exits.
    }

    @Test public void pushedStoneLeavesBoardAndCountsAsCaptured() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 1, 0, GameRules.BLACK);
        g.setStone(0, 0, 0, GameRules.WHITE);
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 2, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        GameRules.Move push = find(g.aggressiveMovesFrom(0, 1, 0), 0, 1, 0, 0, 0);
        assertNotNull(push);
        assertTrue(g.makeAggressive(push));
        assertEquals(GameRules.BLACK, g.pieces[0][0][0]);
        assertEquals(3, g.countPieces(GameRules.WHITE));
        assertEquals(GameRules.BLACK, g.winner);
        assertEquals(GameRules.Phase.FINISHED, g.phase);
    }

    @Test public void twoSpacePushMovesOnlyOneEnemyAndWinNeedsAnEmptyOpponentBoard() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 3, 0, GameRules.BLACK);
        g.setStone(0, 2, 0, GameRules.WHITE);
        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 1, 0);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        GameRules.Move push = find(g.aggressiveMovesFrom(0, 3, 0), 0, 3, 0, 1, 0);
        assertNotNull(push);
        assertTrue(g.makeAggressive(push));
        assertEquals(GameRules.WHITE, g.pieces[0][0][0]);
        assertEquals(GameRules.BLACK, g.pieces[0][1][0]);
        assertNotEquals(GameRules.BLACK, g.winner); // Another white stone remains on this board.
    }

    @Test public void twoSpaceDiagonalPushFromFinalSquareMovesEnemyOnlyOneSpace() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 3, 0, GameRules.BLACK);
        g.setStone(0, 1, 2, GameRules.WHITE);

        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 1, 2);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        GameRules.Move push = find(g.aggressiveMovesFrom(0, 3, 0), 0, 3, 0, 1, 2);
        assertNotNull(push);
        assertTrue(g.makeAggressive(push));

        assertEquals(GameRules.BLACK, g.pieces[0][1][2]);
        assertEquals(GameRules.WHITE, g.pieces[0][0][3]);
        assertEquals(4, g.countPieces(GameRules.WHITE));
        assertNotEquals(GameRules.BLACK, g.winner);
    }

    @Test public void twoSpaceDiagonalPushStillEjectsEnemyWhenNextCellIsOffBoard() {
        GameRules g = emptyGame();
        g.setStone(2, 3, 0, GameRules.BLACK);
        g.setStone(0, 2, 1, GameRules.BLACK);
        g.setStone(0, 0, 3, GameRules.WHITE);

        GameRules.Move setup = find(g.passiveMovesFrom(2, 3, 0), 2, 3, 0, 1, 2);
        assertNotNull(setup);
        assertTrue(g.makePassive(setup));
        GameRules.Move push = find(g.aggressiveMovesFrom(0, 2, 1), 0, 2, 1, 0, 3);
        assertNotNull(push);
        assertTrue(g.makeAggressive(push));

        assertEquals(GameRules.BLACK, g.pieces[0][0][3]);
        assertEquals(3, g.countPieces(GameRules.WHITE));
    }
}
