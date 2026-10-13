package minigame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * The whole six-round collection.
 * Run with: mvn javafx:run
 */
public class Main extends Application {
    private final StackPane root = new StackPane();
    private final Progress progress = Progress.load();
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(root, 980, 820);
        scene.setFill(Color.web("#d7f3ff"));
        showMenu();
        stage.setTitle("Mini Games");
        stage.setScene(scene);
        stage.show();
    }

    private void showMenu() {
        root.getChildren().setAll(new MenuView(progress, this::openRound, () -> {
            progress.reset();
            showMenu();
        }));
    }

    private void openRound(int roundId) {
        Config.Round round = Config.round(roundId);
        if (round == null || !progress.isUnlocked(round)) {
            showMenu();
            return;
        }
        if (roundId == Config.ROUNDS.get(0).id) {
            root.getChildren().setAll(new FallingBallsView(scene, progress, this::showMenu));
            return;
        }
        if (roundId == Config.ROUNDS.get(1).id) {
            root.getChildren().setAll(new StopOnRedView(scene, progress, this::showMenu));
            return;
        }
        root.getChildren().setAll(new PlaceholderView(round, progress, this::showMenu));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
