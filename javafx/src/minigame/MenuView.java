package minigame;

import java.util.function.IntConsumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Six round cards. Unlock rules come from Config and Progress. */
public class MenuView extends VBox {
    public MenuView(Progress progress, IntConsumer openRound, Runnable onReset) {
        setSpacing(14);
        setPadding(new Insets(24));
        setStyle("-fx-background-color: linear-gradient(to bottom, #b9e6ff, #fff6df);");

        Label title = new Label("Mini Games");
        title.setStyle("-fx-font-size: 40px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        Label hint = new Label("Pick a round. Locked rounds will tell you how to open them.");
        hint.setWrapText(true);
        hint.setStyle("-fx-font-size: 18px; -fx-text-fill: #1e2430;");

        Label message = new Label(" ");
        message.setWrapText(true);
        message.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        int index = 0;
        for (Config.Round round : Config.ROUNDS) {
            Button card = card(round, progress, openRound, message);
            grid.add(card, index % 2, index / 2);
            GridPane.setHgrow(card, Priority.ALWAYS);
            index += 1;
        }

        Button reset = new Button("Reset all progress");
        reset.setStyle(buttonStyle("#ffffff"));
        reset.setOnAction(event -> onReset.run());
        Label testing = new Label("Developer testing only");
        testing.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #6a4b2a;");
        VBox testPanel = new VBox(8, testing, new Label("Clears saved progress on this device."), reset);
        testPanel.setPadding(new Insets(14));
        testPanel.setStyle("-fx-background-color: rgba(255,255,255,0.82); -fx-background-radius: 20;");

        getChildren().addAll(title, hint, grid, message, testPanel);
    }

    private static Button card(Config.Round round, Progress progress, IntConsumer openRound, Label message) {
        boolean unlocked = progress.isUnlocked(round);
        boolean star = progress.starEarned(round.id);
        Color base = Color.web(round.color);
        Color fill = unlocked ? base : base.interpolate(Color.WHITE, 0.6);

        Button button = new Button();
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(120);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: " + toHex(fill) + ";"
                + "-fx-background-radius: 24;"
                + "-fx-padding: 14 16;"
                + "-fx-cursor: hand;");

        Label number = new Label("Round " + round.id);
        number.setStyle("-fx-font-size: 16px; -fx-text-fill: #1e2430;");
        Label name = new Label(round.name);
        name.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");

        HBox state = new HBox(8);
        state.setAlignment(Pos.CENTER_LEFT);
        if (!unlocked) {
            state.getChildren().add(lockIcon());
        }
        Label stateText = new Label(unlocked ? "Unlocked" : "Locked");
        stateText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        state.getChildren().add(stateText);

        VBox text = new VBox(2, number, name, state);
        HBox.setHgrow(text, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label starLabel = new Label(star ? "★" : "☆");
        starLabel.setStyle("-fx-font-size: 34px; -fx-text-fill: " + (star ? "#c47c00" : "#1e2430") + ";");
        button.setGraphic(new HBox(10, text, spacer, starLabel));

        button.setOnAction(event -> {
            if (!progress.isUnlocked(round)) {
                message.setText(progress.unlockMessage(round));
                return;
            }
            openRound.accept(round.id);
        });
        return button;
    }

    private static SVGPath lockIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent("M8 11 V8 a4 4 0 0 1 8 0 v3 M6 11 h12 v9 h-12 z");
        icon.setFill(Color.web("#1e2430"));
        icon.setScaleX(0.9);
        icon.setScaleY(0.9);
        return icon;
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    static String buttonStyle(String color) {
        return "-fx-background-color: " + color + ";"
                + "-fx-text-fill: #1e2430;"
                + "-fx-font-size: 18px;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 999;"
                + "-fx-padding: 12 22;"
                + "-fx-cursor: hand;";
    }
}
