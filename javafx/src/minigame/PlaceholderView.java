package minigame;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Screen for a round whose real game is not built yet.
 * Round 3 keeps the temporary test buttons. Rounds 4-6 do not.
 */
public class PlaceholderView extends VBox {
    public PlaceholderView(Config.Round round, Progress progress, Runnable onBack) {
        setSpacing(14);
        setPadding(new Insets(22));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: rgba(255,255,255,0.9); -fx-background-radius: 28;");

        Button back = new Button("Back");
        back.setStyle(MenuView.buttonStyle("#ffffff"));
        back.setOnAction(event -> onBack.run());

        Label eyebrow = new Label("Round " + round.id);
        eyebrow.setStyle("-fx-font-size: 18px; -fx-text-fill: #1e2430;");
        Label title = new Label(round.name);
        title.setStyle("-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");
        Label coming = new Label("Gameplay coming soon");
        coming.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");

        getChildren().addAll(back, eyebrow, title, coming);

        if (round.developerTestControls) {
            getChildren().add(testPanel(round, progress));
        }
    }

    private static VBox testPanel(Config.Round round, Progress progress) {
        Label heading = new Label("Developer testing only");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #6a4b2a;");
        Label help = new Label("These buttons save test progress on this device only.");
        help.setWrapText(true);
        Label status = new Label();
        status.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e2430;");

        Button clear = new Button("Mark cleared");
        Button star = new Button("Award star");
        clear.setStyle(MenuView.buttonStyle("#ffffff"));
        star.setStyle(MenuView.buttonStyle("#ffffff"));

        Runnable paint = () -> {
            status.setText("Cleared: " + (progress.isCleared(round.id) ? "Yes" : "No")
                    + ". Star: " + (progress.starEarned(round.id) ? "Yes" : "No") + ".");
            clear.setDisable(progress.isCleared(round.id));
            star.setDisable(progress.starEarned(round.id));
        };
        clear.setOnAction(event -> {
            progress.markCleared(round.id);
            paint.run();
        });
        star.setOnAction(event -> {
            progress.awardStar(round.id);
            paint.run();
        });
        paint.run();

        VBox panel = new VBox(10, heading, help, status, new HBox(12, clear, star));
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 20;");
        return panel;
    }
}
