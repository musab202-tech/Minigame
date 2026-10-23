package minigame;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
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
import javafx.util.Duration;

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
    private final Label readyTitle = new Label("Get ready!");
    private final Label readyCount = new Label();
    private final VBox readyBox = new VBox(8, readyTitle, readyCount);
    private TranslateTransition titleBob;
    private final Label resultMessage = new Label();
    private final Label resultScore = new Label();
    private final Label resultChances = new Label();
    private final Button pauseButton = new Button("Pause");

    private final List<Ball> balls = new ArrayList<>();
    private final List<SVGPath> lifeIcons = new ArrayList<>();

    /** READY shows Start. Balls exist only in PLAYING. */
    private enum State { READY, COUNTDOWN, PLAYING, PAUSED, FINISHED }

    private State state = State.READY;
    private int score;
    private int chances;
    private int spawned;
    private int settled;
    private double spawnTimer;
    private double readyRemaining;
    private State resumeState = State.COUNTDOWN;
    private boolean left;
    /** True only after the player presses the Start button. The opening click does not count. */
    private boolean startArmed;
    /** Ignore Space or Enter that is still down from opening the round. */
    private boolean blockOpeningKey = true;
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
        showReady();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
        Platform.runLater(() -> {
            if (!blockOpeningKey) {
                return;
            }
            blockOpeningKey = false;
        });
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
        // Decorative only. The countdown itself always takes the Config wait.
        if (!Motion.reduce()) {
            titleBob = new TranslateTransition(Duration.millis(1400), title);
            titleBob.setFromY(0);
            titleBob.setToY(6);
            titleBob.setAutoReverse(true);
            titleBob.setCycleCount(Animation.INDEFINITE);
            titleBob.setInterpolator(Interpolator.EASE_BOTH);
            titleBob.play();
        }

        HBox toolbar = new HBox(12, back, pauseButton);
        getChildren().addAll(toolbar, title, hudPanel, stage);
    }

    private void buildIntro() {
        intro.setAlignment(Pos.CENTER_LEFT);
        Label help = new Label("Move the platform with the arrow keys, A/D or the mouse to catch the balls.");
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
        start.setStyle(MenuView.buttonStyle("#ff8d6b") + "-fx-font-size: 28px; -fx-padding: 16 40;");
        start.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> startArmed = true);
        start.addEventFilter(TouchEvent.TOUCH_PRESSED, event -> startArmed = true);
        start.setOnAction(event -> {
            // A click that merely opened this screen has no press on this button.
            if (!startArmed || state != State.READY) {
                return;
            }
            startArmed = false;
            beginCountdown();
        });
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
        readyTitle.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        readyCount.setStyle("-fx-font-size: 64px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        readyBox.setAlignment(Pos.CENTER);
        readyBox.setStyle("-fx-background-color: white; -fx-background-radius: 24; -fx-padding: 12 28;");
        readyBox.setVisible(false);
        readyBox.layoutXProperty().bind(
                field.widthProperty().subtract(readyBox.widthProperty()).divide(2));
        readyBox.layoutYProperty().bind(field.heightProperty().multiply(0.18));
        field.getChildren().addAll(platform, readyBox);
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
        replay.setOnAction(event -> showReady());
        Button menu = new Button("Back to menu");
        menu.setStyle(MenuView.buttonStyle("#ffffff"));
        menu.setOnAction(event -> leave());
        result.getChildren().addAll(resultMessage, resultScore, resultChances, new HBox(12, replay, menu));
    }

    /** Back to the Start screen. No loop, no balls. */
    private void showReady() {
        stopLoop();
        clearBalls();
        heldLeft = false;
        heldRight = false;
        dragging = false;
        score = 0;
        chances = Config.STARTING_CHANCES;
        spawned = 0;
        settled = 0;
        spawnTimer = 0;
        readyRemaining = Config.FIRST_BALL_DELAY_SECONDS;
        readyBox.setVisible(false);
        hideHud();
        pauseButton.setVisible(false);
        pauseButton.setManaged(false);
        pausedLabel.setVisible(false);
        state = State.READY;
        showOnly(intro);
        if (titleBob != null) {
            titleBob.play();
        }
    }

    private void beginCountdown() {
        if (left || state != State.READY) {
            return;
        }
        if (titleBob != null) {
            titleBob.stop();
        }
        pauseButton.setText("Pause");
        pauseButton.setVisible(true);
        pauseButton.setManaged(true);
        pausedLabel.setVisible(false);
        showHud();
        showOnly(field);
        resetRun();
        paintHud();
        // Platform can move now. The first ball waits.
        readyRemaining = Config.FIRST_BALL_DELAY_SECONDS;
        readyBox.setVisible(true);
        readyBox.toFront();
        paintReady();
        state = State.COUNTDOWN;
        if (field.getWidth() > 1) {
            startLoop();
        } else {
            ChangeListener<Number> waitForWidth = new ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> observable,
                        Number oldWidth, Number newWidth) {
                    if (newWidth.doubleValue() > 1 && state == State.COUNTDOWN) {
                        field.widthProperty().removeListener(this);
                        startLoop();
                    }
                }
            };
            field.widthProperty().addListener(waitForWidth);
        }
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
        if (state == State.COUNTDOWN || state == State.PLAYING) {
            resumeState = state;
            state = State.PAUSED;
            stopLoop();
            pauseButton.setText("Resume");
            pausedLabel.setVisible(true);
            pausedLabel.toFront();
            return;
        }
        if (state == State.PAUSED) {
            state = resumeState;
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
                double realDt = (now - lastTime) / NANOS_PER_SECOND;
                lastTime = now;
                if (state != State.PLAYING && state != State.COUNTDOWN) {
                    return;
                }
                double moveDt = realDt;
                if (moveDt > Config.MAX_FRAME_SECONDS) {
                    moveDt = Config.MAX_FRAME_SECONDS;
                }
                update(moveDt, realDt);
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

    private void update(double moveDt, double realDt) {
        double fieldWidth = field.getWidth();
        double fieldHeight = field.getHeight();
        if (fieldWidth < 1 || fieldHeight < 1) {
            return;
        }
        movePlatform(moveDt, fieldWidth);
        if (state == State.COUNTDOWN) {
            readyRemaining -= realDt;
            paintReady();
            if (readyRemaining <= 0) {
                beginFalling();
            }
            return;
        }
        stepBalls(moveDt, fieldWidth, fieldHeight);
        if (state != State.PLAYING) {
            return;
        }
        spawnTimer += moveDt;
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
            if (state != State.PLAYING) {
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

        if (state == State.PLAYING) {
            balls.clear();
            balls.addAll(kept);
        }
    }

    /** The countdown is over. Drop the first ball, then use the normal spawn interval. */
    private void beginFalling() {
        readyBox.setVisible(false);
        spawnBall();
        paintHud();
        state = State.PLAYING;
    }

    private void paintReady() {
        double left = Math.max(readyRemaining, 0);
        int number = (int) Math.ceil(left);
        if (number < 1) {
            number = 1;
        }
        readyCount.setText(String.valueOf(number));
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
        if (left || state == State.FINISHED) {
            return;
        }
        state = State.FINISHED;
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
        readyBox.setVisible(false);
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
        if (left) {
            return;
        }
        left = true;
        state = State.FINISHED;
        stopLoop();
        clearBalls();
        if (titleBob != null) {
            titleBob.stop();
        }
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
        backToMenu.run();
    }

    private boolean canSteer() {
        return state == State.COUNTDOWN || state == State.PLAYING;
    }

    private void onKeyPressed(KeyEvent event) {
        if (state == State.READY && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
            event.consume();
            if (blockOpeningKey) {
                return;
            }
            beginCountdown();
            return;
        }
        if (!canSteer()) {
            return;
        }
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
        if (blockOpeningKey && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
            blockOpeningKey = false;
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.A) {
            heldLeft = false;
        } else if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.D) {
            heldRight = false;
        }
    }

    private void onMousePressed(MouseEvent event) {
        if (event.isSynthesized() || !canSteer()) {
            return;
        }
        dragging = true;
        movePlatformTo(event.getX());
    }

    private void onMouseDragged(MouseEvent event) {
        if (event.isSynthesized() || !dragging || !canSteer()) {
            return;
        }
        movePlatformTo(event.getX());
    }

    private void onTouchPressed(TouchEvent event) {
        if (!canSteer()) {
            return;
        }
        dragging = true;
        movePlatformTo(event.getTouchPoint().getX());
        event.consume();
    }

    private void onTouchMoved(TouchEvent event) {
        if (!dragging || !canSteer()) {
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
