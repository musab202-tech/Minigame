package minigame;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Stop on Red.
 * Hold the button and the marker travels up and down. Let go to stop it.
 * Landing on the red spot is a hit. Numbers come from Config.
 */
public class StopOnRedView extends VBox {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final String HIT_ICON = "M3 12 L9 18 L21 6";
    private static final String MISS_ICON = "M6 6 L18 18 M18 6 L6 18";

    private final Scene scene;
    private final Progress progress;
    private final Runnable backToMenu;

    private final StackPane stage = new StackPane();
    private final VBox intro = new VBox(12);
    private final VBox play = new VBox(12);
    private final VBox result = new VBox(12);
    private final Label stageLabel = new Label();
    private final Label paceLabel = new Label();
    private final Label hitsLabel = new Label();
    private final Label feedbackWord = new Label();
    private final SVGPath feedbackIcon = new SVGPath();
    private final HBox feedback = new HBox(8);
    private final Button back = new Button("Back");
    private final Button hold = new Button("Hold");
    private final Pane track = new Pane();
    private final Rectangle bar = new Rectangle();
    private final Rectangle redSpot = new Rectangle();
    private final Rectangle marker = new Rectangle();
    private final Label redWord = new Label("Red");
    private final Label resultMessage = new Label();
    private final Label resultHits = new Label();
    private final Label title = new Label("Stop on Red");

    private enum Mode { START, PLAYING, FEEDBACK, RESULT, LEFT }

    private Mode mode = Mode.START;
    private int stageNumber = 1;
    private int hits;
    private double markerCenter;
    private long holdStartedNanos;
    private boolean clockOn;
    private boolean spaceDown;
    private boolean pointerDown;
    private boolean heldThisStage;
    private double feedbackTimer;
    private boolean loopOn;
    private long lastTime;
    private AnimationTimer timer;
    private TranslateTransition titleBob;

    private final javafx.event.EventHandler<KeyEvent> keyPressed = this::onKeyPressed;
    private final javafx.event.EventHandler<KeyEvent> keyReleased = this::onKeyReleased;
    private final javafx.event.EventHandler<MouseEvent> mouseReleased = this::onMouseReleased;
    private final javafx.event.EventHandler<TouchEvent> touchReleased = this::onTouchReleased;
    private final ChangeListener<Boolean> focusListener = (obs, wasFocused, focused) -> {
        if (!focused) {
            // Letting go of the window is not a stop. Do not score the attempt.
            cancelHold();
        }
    };
    private final ChangeListener<Window> windowListener = (obs, oldWindow, newWindow) -> {
        if (oldWindow != null) {
            oldWindow.focusedProperty().removeListener(focusListener);
        }
        if (newWindow != null) {
            newWindow.focusedProperty().addListener(focusListener);
        }
    };

    public StopOnRedView(Scene scene, Progress progress, Runnable backToMenu) {
        this.scene = scene;
        this.progress = progress;
        this.backToMenu = backToMenu;

        setSpacing(12);
        setPadding(new Insets(18));
        setFocusTraversable(true);
        setStyle("-fx-background-color: white; -fx-background-radius: 28;");

        buildChrome();
        buildIntro();
        buildPlay();
        buildResult();
        showOnly(intro);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleased);
        scene.addEventFilter(TouchEvent.TOUCH_RELEASED, touchReleased);
        scene.windowProperty().addListener(windowListener);
        if (scene.getWindow() != null) {
            scene.getWindow().focusedProperty().addListener(focusListener);
        }
    }

    private void buildChrome() {
        back.setFocusTraversable(false);
        back.setStyle(MenuView.buttonStyle("#ffffff"));
        back.setOnAction(event -> leave());

        title.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        // A tiny bob on the title. The marker does not use this.
        if (!Motion.reduce()) {
            titleBob = new TranslateTransition(Duration.millis(1400), title);
            titleBob.setFromY(0);
            titleBob.setToY(6);
            titleBob.setAutoReverse(true);
            titleBob.setCycleCount(Animation.INDEFINITE);
            titleBob.setInterpolator(Interpolator.EASE_BOTH);
            titleBob.play();
        }
        getChildren().addAll(new HBox(12, back), title, stage);
    }

    private void buildIntro() {
        intro.setAlignment(Pos.CENTER_LEFT);
        Label help = new Label("Hold SPACE, release to stop on red.");
        Label rules = new Label("There are " + Config.ROUND2_TOTAL_STAGES
                + " stages. The hold button stays the same size.");
        String spot = Config.ROUND2_TARGET_SHRINKS
                ? "The red spot gets shorter each stage. The last stage is the fastest."
                : "The red spot stays the same size each stage.";
        Label shrink = new Label(spot);
        Label goals = new Label(Config.ROUND2_HITS_TO_CLEAR + " hits clear the round. "
                + Config.ROUND2_HITS_FOR_STAR + " hits earn the star.");
        for (Label label : java.util.List.of(help, rules, shrink, goals)) {
            label.setWrapText(true);
            label.setStyle("-fx-font-size: 20px; -fx-text-fill: #1e2430;");
        }
        Button start = new Button("Start");
        start.setFocusTraversable(false);
        start.setStyle(MenuView.buttonStyle("#ffd84d") + "-fx-font-size: 28px; -fx-padding: 16 36;");
        start.setOnAction(event -> beginPlay());
        intro.getChildren().addAll(help, rules, shrink, goals, start);
    }

    private void buildPlay() {
        play.setAlignment(Pos.CENTER_LEFT);
        stageLabel.setStyle(readoutStyle());
        paceLabel.setStyle(readoutStyle());
        hitsLabel.setStyle(readoutStyle());

        bar.setWidth(Config.ROUND2_TRACK_WIDTH);
        bar.setHeight(Config.ROUND2_TRACK_HEIGHT);
        bar.setArcWidth(28);
        bar.setArcHeight(28);
        bar.setFill(Color.web("#e7f6ff"));

        redSpot.setArcWidth(0);
        redSpot.setArcHeight(0);
        redSpot.setFill(Color.web("#ff5a5a"));

        // A rounded bar, not a ball. The outline keeps it visible on the red band.
        marker.setArcWidth(Config.ROUND2_MARKER_SIZE);
        marker.setArcHeight(Config.ROUND2_MARKER_SIZE);
        marker.setFill(Color.web("#fffdf6"));
        marker.setStroke(Color.web("#1e2430"));
        marker.setStrokeWidth(4);

        redWord.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        track.setPrefSize(Config.ROUND2_TRACK_WIDTH + 72, Config.ROUND2_TRACK_HEIGHT);
        track.getChildren().addAll(bar, redSpot, marker, redWord);

        feedbackIcon.setFill(Color.TRANSPARENT);
        feedbackIcon.setStroke(Color.web("#1e2430"));
        feedbackIcon.setStrokeWidth(3);
        feedbackWord.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        feedback.setAlignment(Pos.CENTER_LEFT);
        feedback.getChildren().addAll(feedbackIcon, feedbackWord);
        feedback.setVisible(false);
        feedback.setManaged(false);

        hold.setFocusTraversable(false);
        hold.setDefaultButton(false);
        styleHoldButton();
        hold.setOnMousePressed(this::onHoldPressed);
        hold.setOnTouchPressed(this::onHoldTouched);
        Label hint = new Label("Hold SPACE, release to stop on red.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 18px; -fx-text-fill: #1e2430;");

        VBox side = new VBox(14, feedback, hold, hint);
        side.setAlignment(Pos.CENTER_LEFT);
        play.getChildren().addAll(stageLabel, paceLabel, hitsLabel, new HBox(28, track, side));
    }

    private void buildResult() {
        result.setAlignment(Pos.CENTER_LEFT);
        resultMessage.setWrapText(true);
        resultMessage.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        resultHits.setStyle(readoutStyle());
        Button replay = new Button("Replay");
        replay.setFocusTraversable(false);
        replay.setStyle(MenuView.buttonStyle("#ffd84d"));
        replay.setOnAction(event -> beginPlay());
        Button menu = new Button("Back to menu");
        menu.setFocusTraversable(false);
        menu.setStyle(MenuView.buttonStyle("#ffffff"));
        menu.setOnAction(event -> leave());
        result.getChildren().addAll(resultMessage, resultHits, new HBox(12, replay, menu));
    }

    private void beginPlay() {
        if (mode == Mode.LEFT) {
            return;
        }
        hits = 0;
        stageNumber = 1;
        showOnly(play);
        beginStage();
        startLoop();
        // Keep Space from landing on a button. Mouse can still press Back and Hold.
        requestFocus();
    }

    /** A fresh stage. The marker waits at the top until the player holds. */
    private void beginStage() {
        mode = Mode.PLAYING;
        spaceDown = false;
        pointerDown = false;
        heldThisStage = false;
        clockOn = false;
        markerCenter = Config.ROUND2_MARKER_SIZE / 2.0;
        feedback.setVisible(false);
        feedback.setManaged(false);
        paintStage();
        placePieces();
    }

    private void paintStage() {
        stageLabel.setText("Stage " + stageNumber + " of " + Config.ROUND2_TOTAL_STAGES);
        paceLabel.setText(Config.round2PaceLabel(stageNumber));
        hitsLabel.setText("Hits: " + hits);
    }

    /** One size for every stage. The number lives in Config. */
    private void styleHoldButton() {
        double size = Config.ROUND2_BUTTON_SIZE;
        hold.setText("Hold");
        hold.setMinSize(size, size);
        hold.setPrefSize(size, size);
        hold.setMaxSize(size, size);
        hold.setStyle("-fx-background-color: #ffd84d;"
                + "-fx-text-fill: #1e2430;"
                + "-fx-font-weight: bold;"
                + "-fx-font-size: 22px;"
                + "-fx-background-radius: 999;"
                + "-fx-padding: 0;"
                + "-fx-cursor: hand;");
    }

    private void placePieces() {
        double redTop = Config.round2RedZoneTop(stageNumber);
        double redHeight = Config.round2RedZoneHeight(stageNumber);
        redSpot.setX(0);
        redSpot.setWidth(Config.ROUND2_TRACK_WIDTH);
        redSpot.setY(redTop);
        redSpot.setHeight(redHeight);
        redWord.setLayoutX(Config.ROUND2_TRACK_WIDTH + 10);
        redWord.setLayoutY(redTop + redHeight / 2 - 12);

        double thickness = Config.ROUND2_MARKER_SIZE;
        double width = Config.ROUND2_TRACK_WIDTH + thickness;
        marker.setWidth(width);
        marker.setHeight(thickness);
        marker.setX((Config.ROUND2_TRACK_WIDTH - width) / 2.0);
        marker.setY(markerCenter - thickness / 2.0);
    }

    /** Centre of the bar after this many seconds of holding. Same on every computer. */
    private double centerAt(long nowNanos) {
        double elapsed = (nowNanos - holdStartedNanos) / NANOS_PER_SECOND;
        if (elapsed < 0) {
            elapsed = 0;
        }
        double speed = Config.round2MarkerSpeed(stageNumber);
        double span = Config.ROUND2_TRACK_HEIGHT - Config.ROUND2_MARKER_SIZE;
        if (span < 1) {
            span = 1;
        }
        double cycle = span * 2;
        double along = (elapsed * speed) % cycle;
        if (along > span) {
            along = cycle - along;
        }
        return Config.ROUND2_MARKER_SIZE / 2.0 + along;
    }

    /** Remember when this hold began. A second button does not restart the clock. */
    private void armHold() {
        if (!spaceDown && !pointerDown) {
            holdStartedNanos = System.nanoTime();
            clockOn = true;
        }
        heldThisStage = true;
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
        if (mode == Mode.PLAYING && clockOn && (spaceDown || pointerDown)) {
            markerCenter = centerAt(System.nanoTime());
            placePieces();
            return;
        }
        if (mode == Mode.FEEDBACK) {
            feedbackTimer += dt;
            if (feedbackTimer >= Config.ROUND2_FEEDBACK_SECONDS) {
                advance();
            }
        }
    }

    private void onHoldPressed(MouseEvent event) {
        pressPointer();
        event.consume();
    }

    private void onHoldTouched(TouchEvent event) {
        pressPointer();
        event.consume();
    }

    private void pressPointer() {
        if (mode != Mode.PLAYING || pointerDown) {
            return;
        }
        armHold();
        pointerDown = true;
    }

    private void onMouseReleased(MouseEvent event) {
        if (!pointerDown) {
            return;
        }
        pointerDown = false;
        releaseHold();
    }

    private void onTouchReleased(TouchEvent event) {
        if (!pointerDown) {
            return;
        }
        pointerDown = false;
        releaseHold();
        event.consume();
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() != KeyCode.SPACE) {
            return;
        }
        // Swallow Space so it cannot click a focused button or scroll the scene.
        event.consume();
        if (mode != Mode.PLAYING || spaceDown) {
            return;
        }
        armHold();
        spaceDown = true;
    }

    private void onKeyReleased(KeyEvent event) {
        if (event.getCode() != KeyCode.SPACE) {
            return;
        }
        event.consume();
        if (!spaceDown) {
            return;
        }
        Window window = scene.getWindow();
        if (window != null && !window.isFocused()) {
            cancelHold();
            return;
        }
        spaceDown = false;
        releaseHold();
    }

    /** Drop a held Space or pointer without counting a hit or a miss. */
    private void cancelHold() {
        spaceDown = false;
        pointerDown = false;
        heldThisStage = false;
        clockOn = false;
    }

    /** Stop the marker when every hold (mouse, touch, or Space) has been let go. */
    private void releaseHold() {
        if (mode != Mode.PLAYING || spaceDown || pointerDown || !heldThisStage) {
            return;
        }
        heldThisStage = false;
        if (clockOn) {
            markerCenter = centerAt(System.nanoTime());
            clockOn = false;
            placePieces();
        }
        boolean hit = Config.round2StoppedOnRed(markerCenter, stageNumber);
        if (hit) {
            hits += 1;
        }
        hitsLabel.setText("Hits: " + hits);
        feedbackWord.setText(hit ? "Hit" : "Miss");
        feedbackIcon.setContent(hit ? HIT_ICON : MISS_ICON);
        feedback.setVisible(true);
        feedback.setManaged(true);
        feedbackTimer = 0;
        mode = Mode.FEEDBACK;
    }

    private void advance() {
        if (stageNumber >= Config.ROUND2_TOTAL_STAGES) {
            finish();
            return;
        }
        stageNumber += 1;
        beginStage();
    }

    private void finish() {
        if (mode == Mode.RESULT || mode == Mode.LEFT) {
            return;
        }
        mode = Mode.RESULT;
        stopLoop();
        Config.Result outcome = Config.judgeRound2(hits);
        int roundId = Config.ROUNDS.get(1).id;
        if (outcome.cleared()) {
            progress.markCleared(roundId);
        }
        if (outcome.star()) {
            progress.awardStar(roundId);
        }
        showOnly(result);
        resultMessage.setText(outcome.message());
        resultHits.setText(Config.hitsSentence(hits));
    }

    private void leave() {
        if (mode == Mode.LEFT) {
            return;
        }
        mode = Mode.LEFT;
        cancelHold();
        stopLoop();
        if (titleBob != null) {
            titleBob.stop();
        }
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyPressed);
        scene.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleased);
        scene.removeEventFilter(MouseEvent.MOUSE_RELEASED, mouseReleased);
        scene.removeEventFilter(TouchEvent.TOUCH_RELEASED, touchReleased);
        scene.windowProperty().removeListener(windowListener);
        if (scene.getWindow() != null) {
            scene.getWindow().focusedProperty().removeListener(focusListener);
        }
        backToMenu.run();
    }

    private void showOnly(javafx.scene.Node screen) {
        stage.getChildren().setAll(screen);
    }

    private static String readoutStyle() {
        return "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e2430;";
    }
}
