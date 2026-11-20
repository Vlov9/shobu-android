package com.stoneandstem.shobu;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(17, 28, 29));
        getWindow().setNavigationBarColor(Color.rgb(17, 28, 29));
        ShobuView gameView = new ShobuView();
        setContentView(gameView);
    }

    private final class ShobuView extends View {
        private final GameRules game = new GameRules();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF[] boardRects = {new RectF(), new RectF(), new RectF(), new RectF()};
        private final RectF undoRect = new RectF(), restartRect = new RectF(), rulesRect = new RectF();
        private final RectF topRestartRect = new RectF(), topRulesRect = new RectF(), themeRect = new RectF(), victoryPlayRect = new RectF();
        private float cell, boardSide, boardGap, boardLeft, boardTop, layoutBottom;
        private int selectedBoard = -1, selectedRow = -1, selectedCol = -1;
        private List<GameRules.Move> options = Collections.emptyList();
        private boolean showRules = false;
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
            darkMode = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", true);
            applyTheme();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            canvas.save();
            canvas.scale(density, density);
            float w = getWidth() / density, h = getHeight() / density;
            canvas.drawColor(bg);
            drawHeader(canvas, w);
            drawStatus(canvas, w);
            layoutBoards(w, h);
            drawBoards(canvas);
            drawBoardLabels(canvas);
            drawGuide(canvas, w, h);
            drawActions(canvas, w, h);
            if (game.phase == GameRules.Phase.FINISHED) drawVictory(canvas, w, h);
            if (showRules) drawRules(canvas, w, h);
            canvas.restore();
            if (!options.isEmpty() && !showRules) postInvalidateDelayed(70);
        }

        private void drawHeader(Canvas c, float w) {
            drawButton(c, themeRect, 18, 23, 38, 38, "", false);
            drawThemeIcon(c);
            text(c, "STONE & STEM  /  LOCAL TABLE", 68, 25, 9, muted, Paint.Align.LEFT, true);
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
            getWindow().getDecorView().setSystemUiVisibility(darkMode ? 0
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
            int player = game.currentPlayer;
            stone(c, left + 31, top + 31, 10, player);
            String color = player == GameRules.BLACK ? "BLACK" : "WHITE";
            text(c, color + " TO MOVE", left + 52, top + 27, 15, ink, Paint.Align.LEFT, true);
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

        private void startMoveAnimation(GameRules.Move move, int enemyStep) {
            if (activeAnimator != null) activeAnimator.cancel();
            java.util.ArrayList<Flight> flights = new java.util.ArrayList<>();
            flights.add(new Flight(move.board, move.fromRow, move.fromCol, move.toRow, move.toCol,
                    game.currentPlayer, false));
            if (enemyStep > 0) {
                int er = move.fromRow + move.dy * enemyStep;
                int ec = move.fromCol + move.dx * enemyStep;
                int endRow = er + move.dy * move.distance;
                int endCol = ec + move.dx * move.distance;
                boolean ejected = endRow < 0 || endRow >= 4 || endCol < 0 || endCol >= 4;
                flights.add(new Flight(move.board, er, ec, endRow, endCol,
                        GameRules.opponent(game.currentPlayer), ejected));
            }
            moveAnimation = new MoveAnimation(flights);
            animationProgress = 0f;
            activeAnimator = ValueAnimator.ofFloat(0f, 1f);
            activeAnimator.setDuration(420);
            activeAnimator.addUpdateListener(a -> {
                animationProgress = (Float) a.getAnimatedValue();
                invalidate();
            });
            activeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    moveAnimation = null;
                    activeAnimator = null;
                    animationProgress = 1f;
                    invalidate();
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
                String home = GameRules.isHomeBoard(b, GameRules.BLACK) ? "BLACK HOME" : "WHITE HOME";
                String shade = GameRules.shade(b) == 0 ? "DARK" : "LIGHT";
                String label = home + "  ·  " + shade;
                float baseline = b < 2 ? r.top - 8 : r.bottom + 14;
                text(c, label, r.centerX(), baseline, 8, muted, Paint.Align.CENTER, true);
            }
        }

        private void drawGuide(Canvas c, float w, float h) {
            String guide;
            if (game.phase == GameRules.Phase.SETUP) guide = "Choose a glowing destination  ·  1 or 2 spaces";
            else if (game.phase == GameRules.Phase.AGGRESSIVE) guide = "Match direction + distance  ·  push one stone";
            else guide = "Start a new game to play again";
            float y = Math.min(h - 103, layoutBottom + 36);
            text(c, guide, w / 2f, y, 10, muted, Paint.Align.CENTER, false);
        }

        private void drawActions(Canvas c, float w, float h) {
            float y = h - 65;
            drawButton(c, undoRect, 18, y, 91, 43, "UNDO", game.phase == GameRules.Phase.AGGRESSIVE);
            drawButton(c, restartRect, w / 2f - 71, y, 142, 43, "NEW GAME", false);
            drawButton(c, rulesRect, w - 101, y, 83, 43, "RULES", false);
        }

        private void drawVictory(Canvas c, float w, float h) {
            int veil = darkMode ? Color.argb(183, 8, 14, 15) : Color.argb(175, 221, 218, 207);
            fill(c, 0, 0, w, h, veil);
            float cardW = Math.min(w - 44, 340);
            float left = (w - cardW) / 2f;
            float top = h * .37f, bottom = h * .64f;
            rounded(c, left, top, left + cardW, bottom, 24, panel, panelEdge, 1.4f);
            int winner = game.winner;
            stone(c, w / 2f, top + 43, 18, winner);
            text(c, "VICTORY", w / 2f, top + 80, 10, gold, Paint.Align.CENTER, true);
            text(c, (winner == GameRules.BLACK ? "BLACK" : "WHITE") + " WINS", w / 2f, top + 113,
                    26, ink, Paint.Align.CENTER, true);
            text(c, "A board is clear of opposing stones.", w / 2f, top + 136, 10, muted, Paint.Align.CENTER, false);
            float buttonTop = bottom - 52;
            drawButton(c, victoryPlayRect, w / 2f - 74, buttonTop, 148, 38, "PLAY AGAIN", true);
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
            float density = getResources().getDisplayMetrics().density;
            float x = event.getX() / density, y = event.getY() / density;
            if (showRules) { showRules = false; invalidate(); return true; }
            if (game.phase == GameRules.Phase.FINISHED) {
                if (insideRect(x, y, victoryPlayRect)) newGame();
                invalidate();
                return true;
            }
            // Top corner shortcuts and bottom controls have independent hit rectangles.
            if (insideRect(x, y, restartRect) || insideRect(x, y, topRestartRect)) { newGame(); return true; }
            if (insideRect(x, y, rulesRect) || insideRect(x, y, topRulesRect)) { showRules = true; clearSelection(); invalidate(); return true; }
            if (insideRect(x, y, themeRect)) { toggleTheme(); return true; }
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
                startMoveAnimation(move, enemyStep);
                game.makeAggressive(move);
                clearSelection();
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

        private void newGame() {
            if (activeAnimator != null) activeAnimator.cancel();
            moveAnimation = null;
            game.reset();
            showRules = false;
            clearSelection();
            invalidate();
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
