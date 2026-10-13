package minigame;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.beans.value.ChangeListener;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/**
 * Falling Balls, ported from js/rounds/round1.js.
 * Speeds, sizes, and the clear/star rules are read from Config only.
 */
public class FallingBallsView extends VBox {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final Color[] BALL_COLORS = {
            Color.web("#ff8d6b"),
            Color.web("#ffd84d"),
            Color.web("#5fd0b0"),
            Color.web("#ff9ec8"),
            Color.web("#8eb6ff")
    };

    private final Scene scene;
    private final Progress progress;
    private final Runnable backToMenu;

    private final StackPane stage = new StackPane();
    private final VBox intro = new VBox(12);
    private final VBox hudPanel = new VBox(8);
    private final Pane field = new Pane();
    private final VBox result = new VBox(12);
    private final Rectangle platform = new Rectangle();
    private final Label progressLabel = new Label();
    private final Label scoreLabel = new Label();
    private final Label chancesLabel = new Label();
    private final Label speedLabel = new Label();
    private final HBox lives = new HBox(6);
    private final Label pausedLabel = new Label("Paused");
    private final Label resultMessage = new Label();
    private final Label resultScore = new Label();
    private final Label resultChances = new Label();
    private final Button pauseButton = new Button("Pause");

    private final List<Ball> balls = new ArrayList<>();
    private final List<SVGPath> lifeIcons = new ArrayList<>();

    private enum Mode { START, PLAYING, PAUSED, RESULT, LEFT }

    private Mode mode = Mode.START;
    private int score;
    private int chances;
    private int spawned;
    private int settled;
    private double spawnTimer;
    private double platformX;
    private boolean heldLeft;
    private boolean heldRight;
    private boolean dragging;
    private boolean loopOn;
    private long lastTime;
    private AnimationTimer timer;

    public FallingBallsView(Scene scene, Progress progress, Runnable backToMenu) {
        this.scene = scene;
        this.progress = progress;
        this.backToMenu = backToMenu;

        setSpacing(12);
        setPadding(new Insets(18));
        setStyle("-fx-background-color: white; -fx-background-radius: 28;");
        VBox.setVgrow(stage, Priority.ALWAYS);

        buildChrome();
        buildIntro();
        buildHud();
        buildField();
        buildResult();
        showOnly(intro);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
    }

    private final javafx.event.EventHandler<KeyEvent> keyPressed = this::onKeyPressed;
    private final javafx.event.EventHandler<KeyEvent> keyReleased = this::onKeyReleased;

    private void buildChrome() {
        Button back = new Button("Back");
        back.setStyle(MenuView.buttonStyle("#ffffff"));
        back.setOnAction(event -> leave());

        pauseButton.setStyle(MenuView.buttonStyle("#ffffff"));
        pauseButton.setVisible(false);
        pauseButton.setManaged(false);
        pauseButton.setOnAction(event -> togglePause());

        Label title = new Label("Falling Balls");
        title.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");

        HBox toolbar = new HBox(12, back, pauseButton);
        getChildren().addAll(toolbar, title, hudPanel, stage);
    }

    private void buildIntro() {
        intro.setAlignment(Pos.CENTER_LEFT);
        Label help = new Label("Catch the falling balls with the platform. Use the arrow keys, A and D, or drag.");
        Label rules = new Label("There are " + Config.TOTAL_BALLS + " balls. You start with "
                + Config.STARTING_CHANCES + " chances. Each miss costs one chance."
                + " The last miss ends the run.");
        String clearWord = Config.CLEAR_MIN_CHANCES_REMAINING == 1 ? "chance" : "chances";
        String starWord = Config.STAR_REQUIRES_CHANCES_REMAINING == 1 ? "chance" : "chances";
        Label goals = new Label("Finish every ball with at least " + Config.CLEAR_MIN_CHANCES_REMAINING
                + " " + clearWord + " left to clear the round. Finish with "
                + Config.STAR_REQUIRES_CHANCES_REMAINING + " " + starWord + " left to earn the star.");
        Label speedNote = new Label("Balls start at Speed 1 and get faster every "
                + Config.SPEED_INCREASE_EVERY_BALLS + " balls.");
        for (Label label : List.of(help, rules, goals, speedNote)) {
            label.setWrapText(true);
            label.setStyle("-fx-font-size: 20px; -fx-text-fill: #1e2430;");
        }
        Button start = new Button("Start");
        start.setStyle(MenuView.buttonStyle("#ff8d6b"));
        start.setOnAction(event -> beginPlay());
        intro.getChildren().addAll(help, rules, goals, speedNote, start);
    }

    private void buildHud() {
        String style = "-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #1e2430;";
        scoreLabel.setStyle(style);
        progressLabel.setStyle(style);
        speedLabel.setStyle(style);
        chancesLabel.setStyle(style);

        javafx.scene.layout.Region gapBeforeBall = new javafx.scene.layout.Region();
        javafx.scene.layout.Region gapBeforeSpeed = new javafx.scene.layout.Region();
        HBox.setHgrow(gapBeforeBall, Priority.ALWAYS);
        HBox.setHgrow(gapBeforeSpeed, Priority.ALWAYS);
        HBox top = new HBox(16, scoreLabel, gapBeforeBall, progressLabel, gapBeforeSpeed, speedLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        lives.setAlignment(Pos.CENTER_LEFT);
        HBox chancesRow = new HBox(12, lives, chancesLabel);
        chancesRow.setAlignment(Pos.CENTER_LEFT);

        hudPanel.setPadding(new Insets(10, 14, 10, 14));
        hudPanel.setStyle("-fx-background-color: #fff6df; -fx-background-radius: 20;");
        hudPanel.getChildren().addAll(top, chancesRow);
        hideHud();
    }

    private void buildField() {
        field.setMinHeight(320);
        field.setPrefHeight(520);
        field.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        field.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#e7f6ff")),
                        new Stop(1, Color.web("#fff6df"))),
                new CornerRadii(28),
                Insets.EMPTY)));
        platform.setArcWidth(999);
        platform.setArcHeight(999);
        platform.setFill(Color.web("#ffd84d"));
        platform.setHeight(Config.PLATFORM_HEIGHT);
        pausedLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-background-color: white;"
                + "-fx-background-radius: 999; -fx-padding: 8 18;");
        pausedLabel.setVisible(false);
        StackPane.setAlignment(pausedLabel, Pos.CENTER);
        field.getChildren().add(platform);
        field.setOnMousePressed(this::onMousePressed);
        field.setOnMouseDragged(this::onMouseDragged);
        field.setOnMouseReleased(event -> dragging = false);
        field.setOnTouchPressed(this::onTouchPressed);
        field.setOnTouchMoved(this::onTouchMoved);
        field.setOnTouchReleased(event -> dragging = false);
        pausedLabel.layoutXProperty().bind(
                field.widthProperty().subtract(pausedLabel.widthProperty()).divide(2));
        pausedLabel.layoutYProperty().bind(
                field.heightProperty().subtract(pausedLabel.heightProperty()).divide(2));
    }

    private void buildResult() {
        result.setAlignment(Pos.CENTER_LEFT);
        resultMessage.setWrapText(true);
        resultMessage.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        resultScore.setStyle(readoutStyle());
        resultChances.setStyle(readoutStyle());
        Button replay = new Button("Replay");
        replay.setStyle(MenuView.buttonStyle("#ff8d6b"));
        replay.setOnAction(event -> beginPlay());
        Button menu = new Button("Back to menu");
        menu.setStyle(MenuView.buttonStyle("#ffffff"));
        menu.setOnAction(event -> leave());
        result.getChildren().addAll(resultMessage, resultScore, resultChances, new HBox(12, replay, menu));
    }

    private void beginPlay() {
        mode = Mode.START;
        pauseButton.setText("Pause");
        pauseButton.setVisible(true);
        pauseButton.setManaged(true);
        pausedLabel.setVisible(false);
        showHud();
        showOnly(field);
        score = 0;
        chances = Config.STARTING_CHANCES;
        spawned = 0;
        buildLifeIcons();
        paintHud();
        // Wait until the field has a real width, then drop the first ball.
        if (field.getWidth() > 1) {
            startRun();
        } else {
            ChangeListener<Number> waitForWidth = new ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> observable,
                        Number oldWidth, Number newWidth) {
                    if (newWidth.doubleValue() > 1) {
                        field.widthProperty().removeListener(this);
                        startRun();
                    }
                }
            };
            field.widthProperty().addListener(waitForWidth);
        }
    }

    private void startRun() {
        if (mode == Mode.LEFT) {
            return;
        }
        resetRun();
        spawnBall();
        paintHud();
        mode = Mode.PLAYING;
        startLoop();
    }

    private void resetRun() {
        clearBalls();
        score = 0;
        chances = Config.STARTING_CHANCES;
        spawned = 0;
        settled = 0;
        spawnTimer = 0;
        double width = platformPixelWidth(field.getWidth());
        platformX = Math.max(0, (field.getWidth() - width) / 2);
        placePlatform();
        buildLifeIcons();
    }

    private void togglePause() {
        if (mode == Mode.PLAYING) {
            mode = Mode.PAUSED;
            stopLoop();
            pauseButton.setText("Resume");
            pausedLabel.setVisible(true);
            pausedLabel.toFront();
            return;
        }
        if (mode == Mode.PAUSED) {
            mode = Mode.PLAYING;
            pauseButton.setText("Pause");
            pausedLabel.setVisible(false);
            startLoop();
        }
    }

    private void startLoop() {
        if (loopOn) {
            return;
        }
        loopOn = true;
        lastTime = 0;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!loopOn) {
                    return;
                }
                if (lastTime == 0) {
                    lastTime = now;
                    return;
                }
                double dt = (now - lastTime) / NANOS_PER_SECOND;
                lastTime = now;
                if (dt > Config.MAX_FRAME_SECONDS) {
                    dt = Config.MAX_FRAME_SECONDS;
                }
                if (mode != Mode.PLAYING) {
                    return;
                }
                update(dt);
            }
        };
        timer.start();
    }

    private void stopLoop() {
        loopOn = false;
        if (timer != null) {
            timer.stop();
        }
    }

    private void update(double dt) {
        double fieldWidth = field.getWidth();
        double fieldHeight = field.getHeight();
        if (fieldWidth < 1 || fieldHeight < 1) {
            return;
        }
        movePlatform(dt, fieldWidth);
        stepBalls(dt, fieldWidth, fieldHeight);
        if (mode != Mode.PLAYING) {
            return;
        }
        spawnTimer += dt;
        if (spawned < Config.TOTAL_BALLS && spawnTimer >= Config.BALL_SPAWN_INTERVAL) {
            spawnTimer -= Config.BALL_SPAWN_INTERVAL;
            spawnBall();
            paintHud();
        }
        if (spawned == Config.TOTAL_BALLS && balls.isEmpty()) {
            finish();
        }
    }

    private void movePlatform(double dt, double fieldWidth) {
        double width = platformPixelWidth(fieldWidth);
        if (!dragging) {
            int direction = 0;
            if (heldLeft) {
                direction -= 1;
            }
            if (heldRight) {
                direction += 1;
            }
            platformX += direction * Config.PLATFORM_SPEED * dt;
        }
        platformX = clamp(platformX, 0, Math.max(0, fieldWidth - width));
        placePlatform();
    }

    private void stepBalls(double dt, double fieldWidth, double fieldHeight) {
        double radius = Config.BALL_SIZE / 2;
        double platformTop = fieldHeight - Config.PLATFORM_HEIGHT;
        double width = platformPixelWidth(fieldWidth);
        List<Ball> kept = new ArrayList<>();
        // Copy first so finish() can clear the live list without breaking this loop.
        List<Ball> snapshot = new ArrayList<>(balls);

        for (Ball ball : snapshot) {
            if (mode != Mode.PLAYING) {
                break;
            }
            double previousY = ball.y;
            // Pixels per second from Config, times seconds since the last frame.
            ball.y += Config.fallSpeedForBall(Math.max(1, spawned)) * dt;

            boolean overPlatform = ball.x + radius >= platformX && ball.x - radius <= platformX + width;
            boolean reachedPlatform = ball.y + radius >= platformTop && previousY - radius <= fieldHeight;
            boolean reachedBottom = ball.y + radius >= fieldHeight;

            if (overPlatform && reachedPlatform) {
                score += Config.POINTS_PER_CATCH;
                settled += 1;
                field.getChildren().remove(ball.circle);
                paintHud();
            } else if (reachedBottom) {
                settled += 1;
                chances -= Config.CHANCES_PER_MISS;
                field.getChildren().remove(ball.circle);
                paintHud();
                if (chances <= 0) {
                    chances = 0;
                    paintHud();
                    finish();
                    break;
                }
            } else {
                placeBall(ball);
                kept.add(ball);
            }
        }

        if (mode == Mode.PLAYING) {
            balls.clear();
            balls.addAll(kept);
        }
    }

    private void spawnBall() {
        double radius = Config.BALL_SIZE / 2;
        double span = Math.max(0, field.getWidth() - radius * 2);
        Ball ball = new Ball();
        ball.x = radius + Math.random() * span;
        ball.y = radius;
        ball.circle = new Circle(radius);
        ball.circle.setFill(BALL_COLORS[spawned % BALL_COLORS.length]);
        field.getChildren().add(ball.circle);
        placeBall(ball);
        balls.add(ball);
        spawned += 1;
    }

    private void finish() {
        if (mode == Mode.RESULT || mode == Mode.LEFT) {
            return;
        }
        mode = Mode.RESULT;
        stopLoop();
        clearBalls();
        dragging = false;

        Config.Result outcome = Config.judge(chances, settled);
        int roundId = Config.ROUNDS.getFirst().id;
        if (outcome.cleared()) {
            progress.markCleared(roundId);
        }
        if (outcome.star()) {
            progress.awardStar(roundId);
        }
        pauseButton.setVisible(false);
        pauseButton.setManaged(false);
        hideHud();
        showOnly(result);
        resultMessage.setText(outcome.message());
        resultScore.setText("Caught " + score + " of " + Config.TOTAL_BALLS);
        resultChances.setText(Config.chanceSentence(chances));
    }

    private void paintHud() {
        progressLabel.setText("Ball " + spawned + " of " + Config.TOTAL_BALLS);
        scoreLabel.setText("Score: " + score);
        speedLabel.setText(Config.speedLabel(Math.max(1, spawned)));
        chancesLabel.setText(Config.chancesStatus(chances));
        for (int i = 0; i < lifeIcons.size(); i++) {
            boolean lost = i >= chances;
            SVGPath icon = lifeIcons.get(i);
            icon.setFill(lost ? Color.TRANSPARENT : Color.web("#ff6b8a"));
            icon.setStroke(lost ? Color.web("#7d8799") : Color.web("#c43b5c"));
            icon.setOpacity(lost ? 0.45 : 1);
        }
    }

    private void buildLifeIcons() {
        lives.getChildren().clear();
        lifeIcons.clear();
        for (int i = 0; i < Config.STARTING_CHANCES; i++) {
            SVGPath heart = new SVGPath();
            heart.setContent("M12 20s-7-4.4-7-9a4 4 0 0 1 7-2 4 4 0 0 1 7 2c0 4.6-7 9-7 9z");
            heart.setScaleX(1.7);
            heart.setScaleY(1.7);
            StackPane holder = new StackPane(heart);
            holder.setMinSize(40, 36);
            holder.setPrefSize(40, 36);
            lifeIcons.add(heart);
            lives.getChildren().add(holder);
        }
    }

    private void placeBall(Ball ball) {
        ball.circle.setCenterX(ball.x);
        ball.circle.setCenterY(ball.y);
    }

    private void placePlatform() {
        platform.setWidth(platformPixelWidth(field.getWidth()));
        platform.setHeight(Config.PLATFORM_HEIGHT);
        platform.setX(platformX);
        platform.setY(field.getHeight() - Config.PLATFORM_HEIGHT);
    }

    private double platformPixelWidth(double fieldWidth) {
        return Math.min(Config.PLATFORM_WIDTH, Math.max(0, fieldWidth));
    }

    private void clearBalls() {
        for (Ball ball : balls) {
            field.getChildren().remove(ball.circle);
        }
        balls.clear();
    }

    private void leave() {
        if (mode == Mode.LEFT) {
            return;
        }
        mode = Mode.LEFT;
        stopLoop();
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
        backToMenu.run();
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.A) {
            heldLeft = true;
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.D) {
            heldRight = true;
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            togglePause();
            event.consume();
        }
    }

    private void onKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.A) {
            heldLeft = false;
        } else if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.D) {
            heldRight = false;
        }
    }

    private void onMousePressed(MouseEvent event) {
        if (event.isSynthesized() || mode != Mode.PLAYING) {
            return;
        }
        dragging = true;
        movePlatformTo(event.getX());
    }

    private void onMouseDragged(MouseEvent event) {
        if (event.isSynthesized() || !dragging || mode != Mode.PLAYING) {
            return;
        }
        movePlatformTo(event.getX());
    }

    private void onTouchPressed(TouchEvent event) {
        if (mode != Mode.PLAYING) {
            return;
        }
        dragging = true;
        movePlatformTo(event.getTouchPoint().getX());
        event.consume();
    }

    private void onTouchMoved(TouchEvent event) {
        if (!dragging || mode != Mode.PLAYING) {
            return;
        }
        movePlatformTo(event.getTouchPoint().getX());
        event.consume();
    }

    private void movePlatformTo(double localX) {
        double width = platformPixelWidth(field.getWidth());
        platformX = clamp(localX - width / 2, 0, Math.max(0, field.getWidth() - width));
        placePlatform();
    }

    private void showHud() {
        hudPanel.setVisible(true);
        hudPanel.setManaged(true);
    }

    private void hideHud() {
        hudPanel.setVisible(false);
        hudPanel.setManaged(false);
    }

    private void showOnly(javafx.scene.Node screen) {
        stage.getChildren().setAll(screen);
        if (screen == field) {
            field.getChildren().remove(pausedLabel);
            field.getChildren().add(pausedLabel);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    private static String readoutStyle() {
        return "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e2430;";
    }

    private static final class Ball {
        private double x;
        private double y;
        private Circle circle;
    }
}
