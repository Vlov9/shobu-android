package com.stoneandstem.shobu;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private ShobuView gameView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.rgb(17, 28, 29));
        getWindow().setNavigationBarColor(Color.rgb(17, 28, 29));
        gameView = new ShobuView();
        setContentView(gameView);
        gameView.requestApplyInsets();
    }

    @Override protected void onDestroy() {
        if (gameView != null) gameView.dispose();
        super.onDestroy();
    }

    private final class ShobuView extends View {
        private final GameRules game = new GameRules();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF[] boardRects = {new RectF(), new RectF(), new RectF(), new RectF()};
        private final RectF undoRect = new RectF(), restartRect = new RectF(), rulesRect = new RectF();
        private final RectF topRestartRect = new RectF(), topRulesRect = new RectF(), themeRect = new RectF(), victoryPlayRect = new RectF();
        private final RectF storyContinueRect = new RectF();
        private final RectF[] aiChoiceRects = {new RectF(), new RectF(), new RectF(), new RectF()};
        private float cell, boardSide, boardGap, boardLeft, boardTop, layoutBottom;
        private int insetLeftPx, insetTopPx, insetRightPx, insetBottomPx;
        private int selectedBoard = -1, selectedRow = -1, selectedCol = -1;
        private List<GameRules.Move> options = Collections.emptyList();
        private boolean showRules = false;
        private boolean showAiChooser = false;
        private boolean showAiDifficultyChooser = false;
        private boolean storyMode = false;
        private boolean storyOpeningDiagonal = false;
        private boolean storyEpisode1Complete = false;
        private int storyScene = 0; // 0 = none, 1 = opening, 2 = post-duel
        private int storyPage = 0;
        private boolean aiThinking = false;
        private ShobuAi.Difficulty aiDifficulty;
        private int gameGeneration = 0;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
        private Future<?> aiSearch;
        private MoveAnimation moveAnimation;
        private ValueAnimator activeAnimator;
        private float animationProgress = 1f;

        private boolean darkMode = true;
        private int bg, panel, panelEdge, ink, muted, gold;
        private final int woodDark = Color.rgb(96, 78, 57);
        private final int woodLight = Color.rgb(221, 199, 157);

        ShobuView() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setOnApplyWindowInsetsListener((view, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    insetLeftPx = bars.left;
                    insetTopPx = bars.top;
                    insetRightPx = bars.right;
                    insetBottomPx = bars.bottom;
                } else {
                    insetLeftPx = insets.getSystemWindowInsetLeft();
                    insetTopPx = insets.getSystemWindowInsetTop();
                    insetRightPx = insets.getSystemWindowInsetRight();
                    insetBottomPx = insets.getSystemWindowInsetBottom();
                }
                invalidate();
                return insets;
            });
            darkMode = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", true);
            storyEpisode1Complete = getPreferences(MODE_PRIVATE).getBoolean("story_ep1_complete", false);
            String savedDifficulty = getPreferences(MODE_PRIVATE).getString("ai_difficulty", "LOCAL");
            if (!"LOCAL".equals(savedDifficulty)) {
                try { aiDifficulty = ShobuAi.Difficulty.valueOf(savedDifficulty); }
                catch (IllegalArgumentException ignored) { aiDifficulty = null; }
            }
            applyTheme();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            canvas.drawColor(bg);
            canvas.save();
            canvas.translate(insetLeftPx, insetTopPx);
            canvas.scale(density, density);
            float w = (getWidth() - insetLeftPx - insetRightPx) / density;
            float h = (getHeight() - insetTopPx - insetBottomPx) / density;
            canvas.drawColor(bg);
            drawHeader(canvas, w);
            drawStatus(canvas, w);
            layoutBoards(w, h);
            drawBoards(canvas);
            drawBoardLabels(canvas);
            drawGuide(canvas, w, h);
            drawActions(canvas, w, h);
            if (game.phase == GameRules.Phase.FINISHED && moveAnimation == null) drawVictory(canvas, w, h);
            if (showRules) drawRules(canvas, w, h);
            if (showAiChooser) drawAiChooser(canvas, w, h);
            if (storyScene != 0) drawStoryScene(canvas, w, h);
            canvas.restore();
            if (!options.isEmpty() && !showRules) postInvalidateDelayed(70);
        }

        private void drawHeader(Canvas c, float w) {
            drawButton(c, themeRect, 18, 23, 38, 38, "", false);
            drawThemeIcon(c);
            text(c, storyMode ? "THE FOUR BANNERS  /  EPISODE 1"
                            : "STONE & STEM  /  " + (aiDifficulty == null ? "LOCAL TABLE" : "AI OPPONENT"),
                    68, 25, 9, muted, Paint.Align.LEFT, true);
            text(c, "SHŌBU", 68, 59, 29, ink, Paint.Align.LEFT, true);
            text(c, "Quiet moves. Decisive pushes.", 70, 80, 12, muted, Paint.Align.LEFT, false);
            drawButton(c, topRulesRect, w - 105, 23, 38, 38, "?", false);
            drawButton(c, topRestartRect, w - 58, 23, 38, 38, "↻", false);
        }

        private void drawThemeIcon(Canvas c) {
            float x = themeRect.centerX(), y = themeRect.centerY();
            if (darkMode) {
                circle(c, x, y, 6, gold);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4.0;
                    line(c, x + (float) Math.cos(angle) * 9, y + (float) Math.sin(angle) * 9,
                            x + (float) Math.cos(angle) * 12, y + (float) Math.sin(angle) * 12, gold, 1.5f);
                }
            } else {
                circle(c, x, y, 9, gold);
                circle(c, x + 4, y - 4, 8, panel);
            }
        }

        private void applyTheme() {
            if (darkMode) {
                bg = Color.rgb(17, 28, 29);
                panel = Color.rgb(29, 44, 43);
                panelEdge = Color.rgb(54, 72, 66);
                ink = Color.rgb(239, 234, 220);
                muted = Color.rgb(159, 171, 157);
                gold = Color.rgb(220, 170, 99);
            } else {
                bg = Color.rgb(241, 238, 229);
                panel = Color.rgb(255, 253, 247);
                panelEdge = Color.rgb(207, 199, 177);
                ink = Color.rgb(37, 52, 46);
                muted = Color.rgb(103, 116, 105);
                gold = Color.rgb(164, 103, 38);
            }
            int systemUiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!darkMode) {
                systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26)
                    systemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }

        private void toggleTheme() {
            darkMode = !darkMode;
            applyTheme();
            getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", darkMode).apply();
            invalidate();
        }

        private void drawStatus(Canvas c, float w) {
            float left = 18, top = 99, right = w - 18, bottom = 164;
            rounded(c, left, top, right, bottom, 17, panel, panelEdge, 1);
            if (game.phase == GameRules.Phase.FINISHED) {
                int winner = game.winner;
                stone(c, left + 31, top + 32, 10, winner);
                text(c, (winner == GameRules.BLACK ? "BLACK" : "WHITE") + " WINS", left + 52, top + 29,
                        15, ink, Paint.Align.LEFT, true);
                text(c, "All opposing stones are off one board.", left + 52, top + 48, 10, muted, Paint.Align.LEFT, false);
                text(c, "GAME OVER", right - 14, top + 37, 9, gold, Paint.Align.RIGHT, true);
                return;
            }
            if (aiThinking) {
                stone(c, left + 31, top + 31, 10, GameRules.WHITE);
                text(c, storyMode ? "TAVI IS THINKING" : "AI THINKING", left + 52, top + 27, 15, ink, Paint.Align.LEFT, true);
                text(c, "SEARCHING", right - 15, top + 27, 10, gold, Paint.Align.RIGHT, true);
                text(c, storyMode ? "House Orme answers your move." : "You play Black. The AI plays White.",
                        left + 20, top + 49, 10, muted, Paint.Align.LEFT, false);
                return;
            }
            int player = game.currentPlayer;
            stone(c, left + 31, top + 31, 10, player);
            String color = player == GameRules.BLACK ? "BLACK" : "WHITE";
            String turnText = aiDifficulty != null && player == GameRules.BLACK ? "YOUR TURN · BLACK"
                    : color + " TO MOVE";
            text(c, turnText, left + 52, top + 27, 15, ink, Paint.Align.LEFT, true);
            String phase = game.phase == GameRules.Phase.SETUP ? "01  /  SET UP" : "02  /  PUSH";
            text(c, phase, right - 15, top + 27, 10, gold, Paint.Align.RIGHT, true);
            String hint = game.phase == GameRules.Phase.SETUP
                    ? "Move a stone on either of your homeboards."
                    : "Repeat that move on a board of the other shade.";
            text(c, hint, left + 20, top + 49, 10, muted, Paint.Align.LEFT, false);
        }

        private void layoutBoards(float w, float h) {
            cell = Math.min(46f, Math.min((w - 38f) / 8.48f, (h - 280f) / 8.48f));
            cell = Math.max(25f, cell);
            boardSide = cell * 4f;
            boardGap = cell * .46f;
            float total = boardSide * 2 + boardGap;
            boardLeft = (w - total) / 2f;
            boardTop = (h + 6f - total) / 2f;
            layoutBottom = boardTop + total;
            boardRects[0].set(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide);
            boardRects[1].set(boardLeft + boardSide + boardGap, boardTop,
                    boardLeft + boardSide * 2 + boardGap, boardTop + boardSide);
            boardRects[2].set(boardLeft, boardTop + boardSide + boardGap,
                    boardLeft + boardSide, boardTop + boardSide * 2 + boardGap);
            boardRects[3].set(boardLeft + boardSide + boardGap, boardTop + boardSide + boardGap,
                    boardLeft + boardSide * 2 + boardGap, boardTop + boardSide * 2 + boardGap);
        }

        private void drawBoards(Canvas c) {
            for (int b = 0; b < 4; b++) drawBoard(c, b, boardRects[b]);
            if (moveAnimation != null) drawMoveAnimation(c);
            // The fixed home line is a visual divider only; no board ever turns or changes sides.
            float y = boardTop + boardSide + boardGap / 2f;
            line(c, boardLeft + 4, y, boardLeft + boardSide * 2 + boardGap - 4, y,
                    darkMode ? Color.rgb(105, 111, 94) : Color.rgb(155, 148, 128), 1.2f);
            rounded(c, getWidth() / 2f - 35, y - 9, getWidth() / 2f + 35, y + 9, 9, bg, bg, 0);
            text(c, "HOME LINE", getWidth() / 2f, y + 3, 7, muted, Paint.Align.CENTER, true);
        }

        private void drawBoard(Canvas c, int board, RectF rect) {
            boolean dark = GameRules.shade(board) == 0;
            int base = dark ? woodDark : woodLight;
            int frame = dark ? Color.rgb(126, 103, 75) : Color.rgb(242, 224, 188);
            rounded(c, rect.left - 5, rect.top - 5, rect.right + 5, rect.bottom + 5, 12,
                    Color.rgb(35, 35, 30), Color.rgb(60, 65, 55), 1);
            rounded(c, rect.left - 2, rect.top - 2, rect.right + 2, rect.bottom + 2, 9, frame, frame, 0);
            for (int r = 0; r < 4; r++) for (int col = 0; col < 4; col++) {
                float l = rect.left + col * cell, t = rect.top + r * cell;
                int tile = base;
                if (((r + col) & 1) != 0) tile = dark ? Color.rgb(105, 87, 64) : Color.rgb(211, 188, 145);
                fill(c, l, t, l + cell, t + cell, tile);
                stroke(c, l, t, l + cell, t + cell, dark ? Color.rgb(76, 64, 49) : Color.rgb(194, 170, 129), .65f);
            }
            for (int r = 0; r < 4; r++) for (int col = 0; col < 4; col++) {
                int piece = game.pieces[board][r][col];
                if (piece != GameRules.EMPTY && !isAnimatedFinal(board, r, col)) {
                    float x = rect.left + (col + .5f) * cell;
                    float y = rect.top + (r + .5f) * cell;
                    stone(c, x, y, cell * .34f, piece);
                }
            }
            drawHighlights(c, board, rect);
        }

        private void drawHighlights(Canvas c, int board, RectF rect) {
            if (selectedBoard == board) {
                float x = rect.left + (selectedCol + .5f) * cell;
                float y = rect.top + (selectedRow + .5f) * cell;
                ring(c, x, y, cell * .43f, gold, 2.5f);
            }
            float pulse = (float) ((Math.sin(System.currentTimeMillis() / 340.0) + 1.0) / 2.0);
            int cr = (int) (216 - pulse * 110), cg = (int) (158 + pulse * 47), cb = (int) (81 + pulse * 85);
            int marker = Color.rgb(cr, cg, cb);
            for (GameRules.Move move : options) if (move.board == board) {
                float x = rect.left + (move.toCol + .5f) * cell;
                float y = rect.top + (move.toRow + .5f) * cell;
                ring(c, x, y, cell * (.34f + pulse * .06f), marker, 1.8f + pulse * .8f);
                if (game.pieces[board][move.toRow][move.toCol] == GameRules.EMPTY)
                    circle(c, x, y, cell * (.055f + pulse * .025f), marker);
            }
        }

        private boolean isAnimatedFinal(int board, int row, int col) {
            if (moveAnimation == null) return false;
            for (Flight f : moveAnimation.flights) {
                if (f.board == board && !f.ejected && f.toRow == row && f.toCol == col) return true;
            }
            return false;
        }

        private void drawMoveAnimation(Canvas c) {
            for (Flight f : moveAnimation.flights) {
                RectF rect = boardRects[f.board];
                float x = rect.left + (f.fromCol + .5f + (f.toCol - f.fromCol) * animationProgress) * cell;
                float y = rect.top + (f.fromRow + .5f + (f.toRow - f.fromRow) * animationProgress) * cell;
                float scale = 1f, alpha = 1f;
                if (f.ejected && animationProgress > .70f) {
                    float out = (animationProgress - .70f) / .30f;
                    scale = 1f - out * .38f;
                    alpha = 1f - out * .70f;
                }
                animatedStone(c, x, y, cell * .34f * scale, f.player, alpha);
            }
        }

        private void animatedStone(Canvas c, float x, float y, float radius, int player, float alpha) {
            int shadow = player == GameRules.BLACK ? Color.rgb(20, 22, 21) : Color.rgb(132, 111, 81);
            int face = player == GameRules.BLACK ? Color.rgb(42, 48, 46) : Color.rgb(242, 234, 211);
            circleAlpha(c, x + radius * .08f, y + radius * .13f, radius * 1.02f, shadow, alpha);
            circleAlpha(c, x, y, radius, face, alpha);
            circleAlpha(c, x - radius * .28f, y - radius * .32f, radius * .21f,
                    player == GameRules.BLACK ? Color.rgb(99, 110, 101) : Color.rgb(255, 250, 231), alpha);
        }

        private void startMoveAnimation(GameRules.Move move, int enemyStep, int movingPlayer,
                                        long durationMs, Runnable onFinished) {
            if (activeAnimator != null) activeAnimator.cancel();
            java.util.ArrayList<Flight> flights = new java.util.ArrayList<>();
            flights.add(new Flight(move.board, move.fromRow, move.fromCol, move.toRow, move.toCol,
                    movingPlayer, false));
            if (enemyStep > 0) {
                int er = move.fromRow + move.dy * enemyStep;
                int ec = move.fromCol + move.dx * enemyStep;
                int pushDistance = move.distance - enemyStep + 1;
                int endRow = er + move.dy * pushDistance;
                int endCol = ec + move.dx * pushDistance;
                boolean ejected = endRow < 0 || endRow >= 4 || endCol < 0 || endCol >= 4;
                flights.add(new Flight(move.board, er, ec, endRow, endCol,
                        GameRules.opponent(movingPlayer), ejected));
            }
            moveAnimation = new MoveAnimation(flights);
            animationProgress = 0f;
            activeAnimator = ValueAnimator.ofFloat(0f, 1f);
            activeAnimator.setDuration(durationMs);
            activeAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            activeAnimator.addUpdateListener(a -> {
                animationProgress = (Float) a.getAnimatedValue();
                invalidate();
            });
            activeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    moveAnimation = null;
                    if (activeAnimator == animation) activeAnimator = null;
                    animationProgress = 1f;
                    invalidate();
                    if (!cancelled && onFinished != null) onFinished.run();
                }
            });
            activeAnimator.start();
        }

        private final class MoveAnimation {
            final java.util.ArrayList<Flight> flights;
            MoveAnimation(java.util.ArrayList<Flight> flights) { this.flights = flights; }
        }

        private final class Flight {
            final int board, fromRow, fromCol, toRow, toCol, player;
            final boolean ejected;
            Flight(int board, int fromRow, int fromCol, int toRow, int toCol, int player, boolean ejected) {
                this.board = board; this.fromRow = fromRow; this.fromCol = fromCol;
                this.toRow = toRow; this.toCol = toCol; this.player = player; this.ejected = ejected;
            }
        }

        private void drawBoardLabels(Canvas c) {
            for (int b = 0; b < 4; b++) {
                RectF r = boardRects[b];
                String home = storyMode
                        ? (GameRules.isHomeBoard(b, GameRules.BLACK) ? "ASTER HOME" : "ORME HOME")
                        : (GameRules.isHomeBoard(b, GameRules.BLACK) ? "BLACK HOME" : "WHITE HOME");
                String shade = GameRules.shade(b) == 0 ? "DARK" : "LIGHT";
                String label = home + "  ·  " + shade;
                float baseline = b < 2 ? r.top - 8 : r.bottom + 14;
                text(c, label, r.centerX(), baseline, 8, muted, Paint.Align.CENTER, true);
            }
        }

        private void drawGuide(Canvas c, float w, float h) {
            String guide;
            if (aiThinking) guide = storyMode ? "Tavi is considering his next oath." : "The AI is considering its turn.";
            else if (game.phase == GameRules.Phase.SETUP) guide = "Choose a glowing destination  ·  1 or 2 spaces";
            else if (game.phase == GameRules.Phase.AGGRESSIVE) guide = "Match direction + distance  ·  push one stone";
            else guide = "Start a new game to play again";
            float y = Math.min(h - 103, layoutBottom + 36);
            text(c, guide, w / 2f, y, 10, muted, Paint.Align.CENTER, false);
        }

        private void drawActions(Canvas c, float w, float h) {
            float y = h - 65;
            drawButton(c, undoRect, 18, y, 91, 43, "UNDO", game.phase == GameRules.Phase.AGGRESSIVE);
            String opponent = storyMode ? "EP. 1 · TAVI" : aiDifficulty == null ? "PLAY MODE" : "AI: " + aiDifficulty.name();
            drawButton(c, restartRect, w / 2f - 71, y, 142, 43, opponent, false);
            drawButton(c, rulesRect, w - 101, y, 83, 43, "RULES", false);
        }

        private void drawAiChooser(Canvas c, float w, float h) {
            fill(c, 0, 0, w, h, darkMode ? Color.argb(215, 9, 15, 16) : Color.argb(200, 230, 227, 216));
            float left = 22, right = w - 22, top = h * .22f, bottom = h * .80f;
            rounded(c, left, top, right, bottom, 22, panel, panelEdge, 1.3f);
            text(c, showAiDifficultyChooser ? "CHOOSE AI DIFFICULTY" : "CHOOSE HOW TO PLAY",
                    left + 22, top + 35, 18, ink, Paint.Align.LEFT, true);
            text(c, showAiDifficultyChooser ? "You play Black. The AI plays White."
                            : "Play locally with a friend or challenge the AI.",
                    left + 22, top + 58, 10, muted, Paint.Align.LEFT, false);
            String[] names = showAiDifficultyChooser
                    ? new String[] {"EASY", "MEDIUM", "HARD", "BACK"}
                    : new String[] {"LOCAL TWO-PLAYER", "PLAY VS AI",
                            storyEpisode1Complete ? "STORY · EPISODE 1 ✓" : "STORY · THE FOUR BANNERS"};
            float buttonTop = top + 78, buttonH = 46, gap = 8;
            for (int i = 0; i < names.length; i++) {
                float y = buttonTop + i * (buttonH + gap);
                boolean selected = showAiDifficultyChooser
                        ? i < 3 && aiDifficulty == ShobuAi.Difficulty.values()[i]
                        : (i == 0 && aiDifficulty == null && !storyMode)
                        || (i == 1 && aiDifficulty != null && !storyMode) || (i == 2 && storyMode);
                drawButton(c, aiChoiceRects[i], left + 18, y, right - left - 36, buttonH,
                        names[i], selected);
            }
        }

        private void drawVictory(Canvas c, float w, float h) {
            int veil = darkMode ? Color.argb(183, 8, 14, 15) : Color.argb(175, 221, 218, 207);
            fill(c, 0, 0, w, h, veil);
            float cardW = Math.min(w - 44, 340);
            float left = (w - cardW) / 2f;
            float top = h * .37f, bottom = h * .64f;
            rounded(c, left, top, left + cardW, bottom, 24, panel, panelEdge, 1.4f);
            int winner = game.winner;
            if (storyMode) {
                text(c, "EPISODE 1 COMPLETE", w / 2f, top + 37, 11, gold, Paint.Align.CENTER, true);
                text(c, winner == GameRules.BLACK ? "NAYA EARNS ORME'S SUPPORT" : "TAVI SPONSORS NAYA",
                        w / 2f, top + 76, 17, ink, Paint.Align.CENTER, true);
                text(c, storyOpeningDiagonal ? "MASTERY SEAL EARNED · THE OPENING OATH"
                                : "The flood-night ledger awaits.",
                        w / 2f, top + 101, 9, muted, Paint.Align.CENTER, false);
                drawButton(c, victoryPlayRect, w / 2f - 84, bottom - 52, 168, 38,
                        "REPLAY EPISODE", true);
                return;
            }
            stone(c, w / 2f, top + 43, 18, winner);
            text(c, "VICTORY", w / 2f, top + 80, 10, gold, Paint.Align.CENTER, true);
            text(c, (winner == GameRules.BLACK ? "BLACK" : "WHITE") + " WINS", w / 2f, top + 113,
                    26, ink, Paint.Align.CENTER, true);
            text(c, "A board is clear of opposing stones.", w / 2f, top + 136, 10, muted, Paint.Align.CENTER, false);
            float buttonTop = bottom - 52;
            drawButton(c, victoryPlayRect, w / 2f - 74, buttonTop, 148, 38, "PLAY AGAIN", true);
        }

        private void drawStoryScene(Canvas c, float w, float h) {
            fill(c, 0, 0, w, h, darkMode ? Color.argb(224, 7, 12, 13) : Color.argb(218, 224, 219, 204));
            float cardW = Math.min(w - 32, 390);
            float left = (w - cardW) / 2f;
            float top = Math.max(68, h * .18f);
            float bottom = Math.min(h - 54, h * .82f);
            rounded(c, left, top, left + cardW, bottom, 22, panel, gold, 1.2f);
            String speaker = storySpeaker();
            int badge = "TAVI ORME".equals(speaker) ? Color.rgb(183, 112, 55)
                    : "SOL VEYR".equals(speaker) ? Color.rgb(149, 57, 59) : Color.rgb(95, 137, 149);
            circle(c, left + 40, top + 39, 18, badge);
            text(c, speaker.substring(0, 1), left + 40, top + 45, 17, bg, Paint.Align.CENTER, true);
            text(c, speaker, left + 68, top + 36, 11, gold, Paint.Align.LEFT, true);
            text(c, storyScene == 1 ? "THE BANNERLESS CHALLENGER" : "THE FLOOD-NIGHT LEDGER",
                    left + 22, top + 81, 16, ink, Paint.Align.LEFT, true);
            float yy = top + 117;
            for (String line : storyLines()) {
                text(c, line, left + 22, yy, 11, muted, Paint.Align.LEFT, false);
                yy += 22;
            }
            float buttonTop = bottom - 52;
            String button = storyPage < 2 ? "NEXT" : storyScene == 1 ? "BEGIN DUEL" : "CONTINUE";
            drawButton(c, storyContinueRect, w / 2f - 82, buttonTop, 164, 38, button, true);
        }

        private String storySpeaker() {
            if (storyScene == 1) {
                if (storyPage == 0) return "THE CONCLAVE";
                if (storyPage == 1) return "TAVI ORME";
                return "THE OATH";
            }
            if (storyPage == 0) return "TAVI ORME";
            if (storyPage == 1) return "NAYA ASTER";
            return "SOL VEYR";
        }

        private String[] storyLines() {
            if (storyScene == 1) {
                if (storyPage == 0) return new String[] {
                        "For fifty years, the four Houses have settled",
                        "their claims at the River Conclave.",
                        "Tonight, House Aster has no banner."
                };
                if (storyPage == 1) return new String[] {
                        "Tavi: I thought the last Aster would be taller.",
                        "Naya: I thought Orme's champion would be quieter.",
                        "Tavi: I'm not the champion. I'm the opening act."
                };
                return new String[] {
                        "SET UP: move 1 or 2 spaces on a homeboard.",
                        "PUSH: repeat the direction and distance on a",
                        "board of the opposite shade. You play Black."
                };
            }
            if (storyPage == 0) return game.winner == GameRules.BLACK
                    ? new String[] {
                            "The crowd calls it an upset.",
                            "Tavi calls it a reason to keep watching.",
                            "He slips you a bronze archive key."
                    }
                    : new String[] {
                            "Tavi blocks the hecklers' view.",
                            "You saw the board when they saw the banners.",
                            "He offers to sponsor your next match."
                    };
            if (storyPage == 1) return new String[] {
                    "The flood-night ledger records a two-space",
                    "setup followed by a one-space aggressive move.",
                    "That turn could not have happened."
            };
            return new String[] {
                    "My brother's seal is on that page, says Sol.",
                    "I don't know who put it there.",
                    "Meet me at the next banner."
            };
        }

        private void drawRules(Canvas c, float w, float h) {
            fill(c, 0, 0, w, h, darkMode ? Color.argb(212, 9, 15, 16) : Color.argb(190, 230, 227, 216));
            float l = 24, r = w - 24, t = Math.max(70, h * .20f), b = Math.min(h - 70, h * .81f);
            rounded(c, l, t, r, b, 22, panel, panelEdge, 1.3f);
            text(c, "HOW TO PLAY", l + 23, t + 39, 21, ink, Paint.Align.LEFT, true);
            text(c, "A TURN HAS TWO MATCHED MOVES", l + 23, t + 72, 9, gold, Paint.Align.LEFT, true);
            String[] lines = {
                    "1. SET UP — Move one of your stones on either",
                    "homeboard by 1 or 2 spaces in any direction.",
                    "The path and destination must be empty.",
                    "",
                    "2. PUSH — Make the same move on a board of",
                    "the opposite shade. You may push one opposing",
                    "stone the same distance. Never push your own,",
                    "jump a stone, or push two stones at once.",
                    "",
                    "Pushed stones leave the game at an edge. All",
                    "boards keep the same orientation throughout.",
                    "",
                    "WIN — Remove all opposing stones from any one",
                    "board. Black takes the first turn."
            };
            float yy = t + 105;
            for (String line : lines) {
                if (line.isEmpty()) { yy += 9; continue; }
                text(c, line, l + 23, yy, 11, muted, Paint.Align.LEFT, false);
                yy += 21;
            }
            rounded(c, w / 2f - 66, b - 52, w / 2f + 66, b - 14, 18, gold, gold, 0);
            text(c, "BACK TO GAME", w / 2f, b - 28, 10, darkMode ? bg : ink, Paint.Align.CENTER, true);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            if (activeAnimator != null) return true;
            float density = getResources().getDisplayMetrics().density;
            float x = (event.getX() - insetLeftPx) / density;
            float y = (event.getY() - insetTopPx) / density;
            if (storyScene != 0) {
                if (insideRect(x, y, storyContinueRect)) advanceStoryScene();
                return true;
            }
            if (showAiChooser) {
                boolean selected = false;
                int choiceCount = showAiDifficultyChooser ? 4 : 3;
                for (int i = 0; i < choiceCount; i++) {
                    if (insideRect(x, y, aiChoiceRects[i])) {
                        selectOpponent(i);
                        selected = true;
                        break;
                    }
                }
                if (!selected) {
                    showAiChooser = false;
                    showAiDifficultyChooser = false;
                    invalidate();
                    maybeStartAiTurn();
                }
                return true;
            }
            if (showRules) { showRules = false; invalidate(); return true; }
            // Top corner shortcuts and bottom controls have independent hit rectangles.
            if (insideRect(x, y, topRestartRect)) {
                if (storyMode) beginStoryEpisode(); else newGame();
                return true;
            }
            if (insideRect(x, y, restartRect)) {
                cancelAiSearch();
                showAiChooser = true;
                showAiDifficultyChooser = false;
                clearSelection();
                invalidate();
                return true;
            }
            if (insideRect(x, y, rulesRect) || insideRect(x, y, topRulesRect)) { showRules = true; clearSelection(); invalidate(); return true; }
            if (insideRect(x, y, themeRect)) { toggleTheme(); return true; }
            if (game.phase == GameRules.Phase.FINISHED) {
                if (insideRect(x, y, victoryPlayRect)) {
                    if (storyMode) beginStoryEpisode(); else newGame();
                }
                invalidate();
                return true;
            }
            if (aiThinking || (aiDifficulty != null && game.currentPlayer == GameRules.WHITE)) return true;
            if (insideRect(x, y, undoRect)) {
                if (game.undoPassive()) clearSelection();
                invalidate(); return true;
            }
            int board = boardAt(x, y);
            if (board < 0 || game.phase == GameRules.Phase.FINISHED) return true;
            RectF rect = boardRects[board];
            int col = (int) ((x - rect.left) / cell), row = (int) ((y - rect.top) / cell);
            if (row < 0 || row > 3 || col < 0 || col > 3) return true;
            if (game.phase == GameRules.Phase.SETUP) handlePassiveTap(board, row, col);
            else handleAggressiveTap(board, row, col);
            invalidate();
            return true;
        }

        private void handlePassiveTap(int board, int row, int col) {
            for (GameRules.Move move : options) if (move.board == board && move.toRow == row && move.toCol == col) {
                game.makePassive(move);
                clearSelection();
                return;
            }
            if (game.pieces[board][row][col] == game.currentPlayer && GameRules.isHomeBoard(board, game.currentPlayer)) {
                if (selectedBoard == board && selectedRow == row && selectedCol == col) { clearSelection(); return; }
                selectedBoard = board; selectedRow = row; selectedCol = col;
                options = game.passiveMovesFrom(board, row, col);
            } else if (selectedBoard != -1) clearSelection();
        }

        private void handleAggressiveTap(int board, int row, int col) {
            for (GameRules.Move move : options) if (move.board == board && move.toRow == row && move.toCol == col) {
                int enemyStep = 0;
                for (int step = 1; step <= move.distance; step++) {
                    int pr = move.fromRow + move.dy * step, pc = move.fromCol + move.dx * step;
                    if (game.pieces[move.board][pr][pc] == GameRules.opponent(game.currentPlayer)) { enemyStep = step; break; }
                }
                int movingPlayer = game.currentPlayer;
                if (game.makeAggressive(move)) {
                    if (storyMode && game.turnNumber == 2 && move.distance == 2 && move.dx != 0 && move.dy != 0)
                        storyOpeningDiagonal = true;
                    clearSelection();
                    startMoveAnimation(move, enemyStep, movingPlayer, 700, () -> {
                        invalidate();
                        if (storyMode && game.phase == GameRules.Phase.FINISHED) beginStoryOutro();
                        else maybeStartAiTurn();
                    });
                }
                return;
            }
            if (game.pieces[board][row][col] == game.currentPlayer) {
                selectedBoard = board; selectedRow = row; selectedCol = col;
                options = game.aggressiveMovesFrom(board, row, col);
            } else if (selectedBoard != -1) clearSelection();
        }

        private int boardAt(float x, float y) {
            for (int b = 0; b < 4; b++) if (boardRects[b].contains(x, y)) return b;
            return -1;
        }

        private boolean insideRect(float x, float y, RectF rect) {
            return rect.contains(x, y);
        }

        private void clearSelection() {
            selectedBoard = selectedRow = selectedCol = -1;
            options = Collections.emptyList();
        }

        private void selectOpponent(int choice) {
            if (!showAiDifficultyChooser) {
                if (choice == 1) {
                    showAiDifficultyChooser = true;
                    invalidate();
                    return;
                }
                if (choice == 2) {
                    aiDifficulty = ShobuAi.Difficulty.EASY;
                    storyMode = true;
                    beginStoryEpisode();
                    return;
                }
                aiDifficulty = null;
                storyMode = false;
            } else {
                if (choice == 3) {
                    showAiDifficultyChooser = false;
                    invalidate();
                    return;
                }
                aiDifficulty = ShobuAi.Difficulty.values()[choice];
                storyMode = false;
            }
            getPreferences(MODE_PRIVATE).edit()
                    .putString("ai_difficulty", aiDifficulty == null ? "LOCAL" : aiDifficulty.name())
                    .apply();
            showAiChooser = false;
            showAiDifficultyChooser = false;
            newGame();
        }

        private void beginStoryEpisode() {
            storyMode = true;
            aiDifficulty = ShobuAi.Difficulty.EASY;
            newGame();
            storyScene = 1;
            storyPage = 0;
            storyOpeningDiagonal = false;
            invalidate();
        }

        private void beginStoryOutro() {
            if (!storyMode || storyScene == 2) return;
            storyEpisode1Complete = true;
            getPreferences(MODE_PRIVATE).edit().putBoolean("story_ep1_complete", true).apply();
            storyScene = 2;
            storyPage = 0;
            invalidate();
        }

        private void advanceStoryScene() {
            int lastPage = 2;
            if (storyPage < lastPage) {
                storyPage++;
            } else {
                storyScene = 0;
                storyPage = 0;
            }
            invalidate();
        }

        private void maybeStartAiTurn() {
            if (aiDifficulty == null || aiThinking || game.phase != GameRules.Phase.SETUP
                    || game.currentPlayer != GameRules.WHITE) return;
            aiThinking = true;
            clearSelection();
            final int generation = gameGeneration;
            final GameRules searchPosition = game.copy();
            final ShobuAi.Difficulty level = aiDifficulty;
            invalidate();
            aiSearch = aiExecutor.submit(() -> {
                ShobuAi.Turn turn = ShobuAi.chooseMove(searchPosition, level);
                mainHandler.post(() -> {
                    if (generation != gameGeneration) return;
                    aiSearch = null;
                    if (turn != null && game.phase == GameRules.Phase.SETUP
                            && game.currentPlayer == GameRules.WHITE
                            && game.makePassive(turn.setupMove)) {
                        clearSelection();
                        startMoveAnimation(turn.setupMove, 0, GameRules.WHITE, 650, () -> mainHandler.postDelayed(() -> {
                            if (generation != gameGeneration || game.phase != GameRules.Phase.AGGRESSIVE
                                    || game.currentPlayer != GameRules.WHITE) return;
                            int enemyStep = 0;
                            for (int step = 1; step <= turn.pushMove.distance; step++) {
                                int row = turn.pushMove.fromRow + turn.pushMove.dy * step;
                                int col = turn.pushMove.fromCol + turn.pushMove.dx * step;
                                if (game.pieces[turn.pushMove.board][row][col]
                                        == GameRules.opponent(GameRules.WHITE)) { enemyStep = step; break; }
                            }
                            if (!game.makeAggressive(turn.pushMove)) {
                                game.undoPassive();
                                aiThinking = false;
                                invalidate();
                                return;
                            }
                            startMoveAnimation(turn.pushMove, enemyStep, GameRules.WHITE, 800, () -> {
                                aiThinking = false;
                                if (storyMode && game.phase == GameRules.Phase.FINISHED) beginStoryOutro();
                                invalidate();
                            });
                        }, 220));
                    } else {
                        aiThinking = false;
                    }
                    clearSelection();
                    invalidate();
                });
            });
        }

        private void cancelAiSearch() {
            gameGeneration++;
            if (aiSearch != null) aiSearch.cancel(true);
            aiSearch = null;
            aiThinking = false;
        }

        private void newGame() {
            cancelAiSearch();
            if (activeAnimator != null) activeAnimator.cancel();
            moveAnimation = null;
            storyScene = 0;
            storyPage = 0;
            game.reset();
            showRules = false;
            showAiChooser = false;
            clearSelection();
            invalidate();
        }

        void dispose() {
            cancelAiSearch();
            aiExecutor.shutdownNow();
        }

        private void drawButton(Canvas c, RectF rect, float x, float y, float w, float h,
                                String label, boolean enabled) {
            rect.set(x, y, x + w, y + h);
            int fill = enabled ? Color.rgb(67, 83, 68) : panel;
            int edge = enabled ? Color.rgb(120, 145, 107) : panelEdge;
            rounded(c, x, y, x + w, y + h, h / 2f, fill, edge, 1);
            text(c, label, x + w / 2f, y + h / 2f + 4, 10, enabled ? ink : muted, Paint.Align.CENTER, true);
        }

        private void stone(Canvas c, float x, float y, float radius, int player) {
            int shadow = player == GameRules.BLACK ? Color.rgb(20, 22, 21) : Color.rgb(132, 111, 81);
            int fill = player == GameRules.BLACK ? Color.rgb(42, 48, 46) : Color.rgb(242, 234, 211);
            circle(c, x + radius * .08f, y + radius * .13f, radius * 1.02f, shadow);
            circle(c, x, y, radius, fill);
            circle(c, x - radius * .28f, y - radius * .32f, radius * .21f,
                    player == GameRules.BLACK ? Color.rgb(99, 110, 101) : Color.rgb(255, 250, 231));
            ring(c, x, y, radius, player == GameRules.BLACK ? Color.rgb(116, 127, 113) : Color.rgb(219, 206, 176), .75f);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color,
                          Paint.Align align, boolean bold) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color);
            float density = getResources().getDisplayMetrics().density;
            paint.setTextSize(size * getResources().getDisplayMetrics().scaledDensity / density);
            paint.setTextAlign(align); paint.setTypeface(bold ? android.graphics.Typeface.create("sans-serif-medium", 0)
                    : android.graphics.Typeface.create("sans-serif", 0));
            c.drawText(value, x, y, paint);
        }

        private void fill(Canvas c, float l, float t, float r, float b, int color) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawRect(l, t, r, b, paint);
        }

        private void stroke(Canvas c, float l, float t, float r, float b, int color, float width) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); c.drawRect(l, t, r, b, paint);
        }

        private void rounded(Canvas c, float l, float t, float r, float b, float radius, int color, int edge, float width) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(l, t, r, b, radius, radius, paint);
            if (width > 0) { paint.setColor(edge); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); c.drawRoundRect(l, t, r, b, radius, radius, paint); }
        }

        private void circle(Canvas c, float x, float y, float radius, int color) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.FILL); c.drawCircle(x, y, radius, paint);
        }

        private void circleAlpha(Canvas c, float x, float y, float radius, int color, float alpha) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color);
            paint.setAlpha((int) (255 * Math.max(0f, Math.min(1f, alpha))));
            paint.setStyle(Paint.Style.FILL); c.drawCircle(x, y, radius, paint);
        }

        private void ring(Canvas c, float x, float y, float radius, int color, float width) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); c.drawCircle(x, y, radius, paint);
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
            paint.reset(); paint.setAntiAlias(true); paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); c.drawLine(x1, y1, x2, y2, paint);
        }
    }
}
