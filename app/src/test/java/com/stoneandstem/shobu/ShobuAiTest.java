package com.stoneandstem.shobu;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ShobuAiTest {
    @Test public void chosenEasyTurnIsLegalAndDoesNotMutateInput() {
        GameRules position = new GameRules();
        int initialBlack = position.countPieces(GameRules.BLACK);
        int initialWhite = position.countPieces(GameRules.WHITE);

        ShobuAi.Turn turn = ShobuAi.chooseMove(position, ShobuAi.Difficulty.EASY, 1234L);

        assertNotNull(turn);
        assertEquals(GameRules.Phase.SETUP, position.phase);
        assertEquals(initialBlack, position.countPieces(GameRules.BLACK));
        assertEquals(initialWhite, position.countPieces(GameRules.WHITE));
        GameRules next = turn.apply(position);
        assertTrue(next.phase == GameRules.Phase.SETUP || next.phase == GameRules.Phase.FINISHED);
        assertEquals(GameRules.BLACK, position.currentPlayer);
    }

    @Test public void allDifficultyLevelsTakeAnImmediateBoardClearWin() {
        for (ShobuAi.Difficulty difficulty : ShobuAi.Difficulty.values()) {
            GameRules position = immediateWinPosition();
            ShobuAi.Turn turn = ShobuAi.chooseMove(position, difficulty, 99L);

            assertNotNull("No turn at " + difficulty, turn);
            GameRules result = turn.apply(position);
            assertEquals("AI should clear the board at " + difficulty, GameRules.WHITE, result.winner);
            assertEquals(GameRules.Phase.FINISHED, result.phase);
            assertEquals(GameRules.Phase.SETUP, position.phase);
            assertEquals(GameRules.WHITE, position.currentPlayer);
        }
    }

    @Test public void copyKeepsAnUnfinishedTurnIndependent() {
        GameRules position = new GameRules();
        GameRules.Move setup = position.passiveMovesFrom(2, 3, 0).get(0);
        assertTrue(position.makePassive(setup));

        GameRules copy = position.copy();
        assertEquals(GameRules.Phase.AGGRESSIVE, copy.phase);
        assertEquals(position.currentPlayer, copy.currentPlayer);
        assertFalse(copy.allAggressiveMoves().isEmpty());

        copy.pieces[2][3][3] = GameRules.EMPTY;
        assertEquals(GameRules.BLACK, position.pieces[2][3][3]);
    }

    @Test public void mediumSearchFindsAValidTurnFromTheOpening() {
        GameRules position = new GameRules();
        ShobuAi.Turn turn = ShobuAi.chooseMove(position, ShobuAi.Difficulty.MEDIUM, 2026L);
        assertNotNull(turn);
        GameRules result = turn.apply(position);
        assertEquals(GameRules.Phase.SETUP, result.phase);
        assertEquals(GameRules.WHITE, result.currentPlayer);
        assertEquals(GameRules.BLACK, position.currentPlayer);
    }

    private static GameRules immediateWinPosition() {
        GameRules position = new GameRules();
        for (int b = 0; b < GameRules.BOARD_COUNT; b++)
            for (int r = 0; r < GameRules.SIZE; r++)
                for (int c = 0; c < GameRules.SIZE; c++)
                    position.setStone(b, r, c, GameRules.EMPTY);

        position.currentPlayer = GameRules.WHITE;
        // Keep black stones on three boards; the only black stone on board 2 can be pushed off.
        position.setStone(0, 3, 3, GameRules.BLACK);
        position.setStone(1, 3, 3, GameRules.BLACK);
        position.setStone(2, 3, 0, GameRules.BLACK);
        position.setStone(3, 3, 3, GameRules.BLACK);
        position.setStone(0, 0, 0, GameRules.WHITE);
        position.setStone(2, 2, 0, GameRules.WHITE);
        return position;
    }
}

