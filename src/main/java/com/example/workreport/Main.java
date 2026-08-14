package com.example.workreport;

import com.example.workreport.service.GitService;
import com.example.workreport.ui.MainView;
import com.example.workreport.util.AppConfig;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main extends Application {

    private final AppConfig config = new AppConfig();
    private final GitService gitService = new GitService();
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Path project = loadProject();
        show(project);
        stage.show();
    }

    private Path loadProject() {
        String stored = config.getProjectPath();
        if (stored != null && !stored.isBlank()) {
            Path p = Path.of(stored);
            if (Files.isDirectory(p) && gitService.isRepository(p)) {
                return p;
            }
        }
        return chooseProject();
    }

    private Path chooseProject() {
        while (true) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select a Git project directory");
            Path dir = chooser.showDialog(stage).toPath();
            if (dir == null) {
                return null;
            }
            if (!gitService.isRepository(dir)) {
                new Alert(Alert.AlertType.ERROR,
                        "Selected directory is not a Git repository.").showAndWait();
                continue;
            }
            config.setProjectPath(dir);
            return dir;
        }
    }

    private void show(Path project) {
        if (project == null) {
            showNoProject();
            return;
        }
        MainView view = new MainView(project);
        view.setOnChangeProject(this::changeProject);
        Scene scene = new Scene(view.build(), 900, 600);
        stage.setTitle("Boquila Work Reports — " + project.getFileName());
        stage.setScene(scene);
    }

    private void showNoProject() {
        stage.setTitle("Boquila — No Project");
        stage.setScene(new Scene(new javafx.scene.layout.VBox(
                new javafx.scene.control.Label(
                        "No project selected. Change the project inside the app.")),
                400, 200));
    }

    private void changeProject() {
        Path dir = chooseProject();
        if (dir != null) {
            show(dir);
        }
    }
}