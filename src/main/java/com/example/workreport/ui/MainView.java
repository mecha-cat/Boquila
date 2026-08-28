package com.example.workreport.ui;

import com.example.workreport.model.WorkReport;
import com.example.workreport.service.GitService;
import com.example.workreport.service.ReportService;
import com.example.workreport.util.DeveloperStore;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MainView {

    private final Path projectDir;
    private final ReportService reportService;
    private final GitService gitService;
    private final DeveloperStore developerStore = new DeveloperStore();

    private final TableView<WorkReport> table = new TableView<>();
    private final ObservableList<WorkReport> reports = FXCollections.observableArrayList();
    private final TextField searchField = new TextField();
    private final DatePicker dateFilter = new DatePicker(LocalDate.now());
    private final ComboBox<String> developerFilter = new ComboBox<>();
    private final ComboBox<String> tagFilter = new ComboBox<>();
    private final Label gitStatus = new Label("Git: -");
    private final Label unreadableLabel = new Label();
    private final TextArea detail = new TextArea();
    private final Button editButton = new Button("Edit");
    private final Button deleteButton = new Button("Delete");
    private Runnable onChangeProject;

    public MainView(Path projectDir) {
        this.projectDir = projectDir;
        this.gitService = new GitService(projectDir);
        this.reportService = new ReportService(gitService);
        this.developerStore.rememberAll(reportService.list().stream()
                .map(WorkReport::getDeveloper).toList());
    }

    public void setOnChangeProject(Runnable onChangeProject) {
        this.onChangeProject = onChangeProject;
    }

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(buildTable());
        root.setBottom(detailArea());
        refresh();
        autoSync();
        return root;
    }

    private void autoSync() {
        if (!gitService.isAvailable() || !gitService.isRepository()) {
            return;
        }
        gitService.pull();
        refresh();
    }

    private HBox buildHeader() {
        Label title = new Label("Work Reports");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        title.setPadding(new Insets(0, 0, 0, 10));

        Button pull = new Button("Pull");
        pull.setOnAction(e -> pull());
        Button newReport = new Button("New Report");
        newReport.setOnAction(e -> newReport());
        Button commit = new Button("Commit & Push");
        commit.setOnAction(e -> commitAndPush());
        Button changeProject = new Button("Change Project");
        changeProject.setOnAction(e -> changeProject());

        HBox top = new HBox(8, title, pull, newReport, commit, changeProject);
        top.setPadding(new Insets(10));
        top.setStyle("-fx-background-color: #f0f0f0;");
        return top;
    }

    private VBox buildTable() {
        searchField.setPromptText("Search...");
        searchField.textProperty().addListener((obs, o, n) -> refreshTable());
        searchField.setPrefWidth(280);

        gitStatus.setPadding(new Insets(0, 10, 0, 0));
        unreadableLabel.setStyle("-fx-text-fill: #c0392b;");

        TableColumn<WorkReport, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(110);
        TableColumn<WorkReport, String> devCol = new TableColumn<>("Developer");
        devCol.setCellValueFactory(new PropertyValueFactory<>("developer"));
        devCol.setPrefWidth(120);
        TableColumn<WorkReport, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("timeRange"));
        timeCol.setPrefWidth(130);
        TableColumn<WorkReport, String> sumCol = new TableColumn<>("Summary");
        sumCol.setCellValueFactory(new PropertyValueFactory<>("summary"));
        sumCol.setPrefWidth(400);

        table.getColumns().add(dateCol);
        table.getColumns().add(devCol);
        table.getColumns().add(timeCol);
        table.getColumns().add(sumCol);
        table.setItems(reports);
        table.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> showDetail(n));
        editButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2
                    && table.getSelectionModel().getSelectedItem() != null) {
                editSelected();
            }
        });

        HBox filterBar = buildFilterBar();
        HBox bar = new HBox(8, searchField, gitStatus, unreadableLabel);
        bar.setPadding(new Insets(8, 10, 4, 10));

        VBox box = new VBox(filterBar, bar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private HBox buildFilterBar() {
        Label dateLabel = new Label("Date:");
        dateLabel.setPadding(new Insets(5, 0, 0, 0));
        dateFilter.setPromptText("Date");
        dateFilter.valueProperty().addListener((obs, o, n) -> refreshTable());

        Label devLabel = new Label("Developer:");
        devLabel.setPadding(new Insets(5, 0, 0, 0));
        developerFilter.setPrefWidth(180);
        developerFilter.setPromptText("All");
        developerFilter.valueProperty().addListener((obs, o, n) -> refreshTable());

        Label tagLabel = new Label("Tag:");
        tagLabel.setPadding(new Insets(5, 0, 0, 0));
        tagFilter.setPrefWidth(160);
        tagFilter.setPromptText("All");
        tagFilter.valueProperty().addListener((obs, o, n) -> refreshTable());

        Button resetFilters = new Button("Reset Filters");
        resetFilters.setOnAction(e -> resetFilters());

        HBox bar = new HBox(8,
                dateLabel, dateFilter, devLabel, developerFilter, tagLabel, tagFilter, resetFilters);
        bar.setPadding(new Insets(8, 10, 0, 10));
        return bar;
    }

    private void resetFilters() {
        dateFilter.setValue(LocalDate.now());
        developerFilter.setValue("All");
    }

    private VBox detailArea() {
        detail.setEditable(false);
        detail.setPrefHeight(220);
        detail.setWrapText(true);

        Button edit = editButton;
        edit.setOnAction(e -> editSelected());
        Button delete = deleteButton;
        delete.setOnAction(e -> deleteSelected());
        Button exportMd = new Button("Export .md");
        Button exportHtml = new Button("Export HTML");
        exportMd.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        exportHtml.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        exportMd.setOnAction(e -> exportMd(table.getSelectionModel().getSelectedItem()));
        exportHtml.setOnAction(e -> exportHtml(table.getSelectionModel().getSelectedItem()));

        HBox buttons = new HBox(8, edit, delete, exportMd, exportHtml);
        buttons.setPadding(new Insets(6, 10, 10, 10));

        return new VBox(detail, buttons);
    }

    private void exportMd(WorkReport r) {
        if (r == null) {
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Markdown");
        fc.setInitialFileName(ReportService.fileNameFor(r));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), ReportService.toMarkdown(r));
            } catch (IOException ex) {
                alert("Export failed:\n" + ex.getMessage());
            }
        }
    }

    private void exportHtml(WorkReport r) {
        if (r == null) {
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export HTML");
        fc.setInitialFileName(ReportService.fileNameFor(r).replace(".md", ".html"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML", "*.html"));
        java.io.File file = fc.showSaveDialog(null);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), toHtml(r));
            } catch (IOException ex) {
                alert("Export failed:\n" + ex.getMessage());
            }
        }
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String toHtml(WorkReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<title>Work Report</title></head><body>");
        sb.append("<h1>Work Report</h1>");
        sb.append("<p>");
        sb.append("<strong>Date:</strong> ")
                .append(escapeHtml(r.getDate() == null ? "" : r.getDate().toString()))
                .append("<br>");
        sb.append("<strong>Developer:</strong> ").append(escapeHtml(r.getDeveloper())).append("<br>");
        sb.append("<strong>Start Time:</strong> ")
                .append(escapeHtml(r.getStartTime() == null ? "" : r.getStartTime().toString()))
                .append("<br>");
        sb.append("<strong>End Time:</strong> ")
                .append(escapeHtml(r.getEndTime() == null ? "" : r.getEndTime().toString()))
                .append("<br>");
        if (!r.getTags().isEmpty()) {
            sb.append("<strong>Tags:</strong> ");
            sb.append(r.getTags().stream().map(t -> "#" + escapeHtml(t))
                    .reduce((a, b) -> a + " " + b).orElse(""));
        }
        sb.append("</p>");
        sb.append("<h2>Summary</h2><p>")
                .append(escapeHtml(r.getSummary()).replace("\n", "<br>")).append("</p>");
        sb.append("<h2>Tasks</h2><ul>");
        for (String t : r.getTasks()) {
            sb.append("<li>").append(escapeHtml(t)).append("</li>");
        }
        sb.append("</ul>");
        sb.append("<h2>Notes</h2><p>")
                .append(escapeHtml(r.getNotes()).replace("\n", "<br>")).append("</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private void refresh() {
        updateGitStatus();
        refreshTable();
    }

    private void refreshTable() {
        populateDevelopers();
        populateTags();
        LocalDate date = dateFilter.getValue();
        String dev = developerFilter.getValue();
        String tag = tagFilter.getValue();
        List<WorkReport> all = reportService.search(searchField.getText());
        reports.setAll(all.stream()
                .filter(r -> date == null || date.equals(r.getDate()))
                .filter(r -> dev == null || dev.isBlank() || "All".equals(dev)
                        || dev.equals(r.getDeveloper()))
                .filter(r -> tag == null || tag.isBlank() || "All".equals(tag)
                        || r.getTags().contains(tag))
                .toList());
        long unreadable = reportService.countUnreadable();
        unreadableLabel.setText(unreadable > 0
                ? unreadable + " report file(s) could not be parsed."
                : "");
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void populateDevelopers() {
        String selected = developerFilter.getValue();
        List<String> names = reportService.list().stream()
                .map(WorkReport::getDeveloper)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted()
                .toList();
        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All");
        items.addAll(names);
        developerFilter.setItems(items);
        if (selected != null && items.contains(selected)) {
            developerFilter.setValue(selected);
        } else {
            developerFilter.setValue("All");
        }
    }

    private void populateTags() {
        String selected = tagFilter.getValue();
        List<String> tags = reportService.list().stream()
                .flatMap(r -> r.getTags().stream())
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .toList();
        ObservableList<String> items = FXCollections.observableArrayList();
        items.add("All");
        items.addAll(tags);
        tagFilter.setItems(items);
        if (selected != null && items.contains(selected)) {
            tagFilter.setValue(selected);
        } else {
            tagFilter.setValue("All");
        }
    }

    private void showDetail(WorkReport r) {
        if (r == null) {
            detail.clear();
            return;
        }
        detail.setText(ReportService.toMarkdown(r));
    }

    private void updateGitStatus() {
        if (!gitService.isAvailable()) {
            gitStatus.setText("Git: not installed");
            return;
        }
        GitService.Result s = gitService.status();
        if (!s.success()) {
            gitStatus.setText("Git: error");
            return;
        }
        boolean hasChanges = s.output().lines().anyMatch(l ->
                !l.startsWith("##") && !l.isBlank());
        gitStatus.setText("Git: " + (hasChanges ? "Changes" : "Clean"));
    }

    private void pull() {
        if (!gitService.isAvailable()) {
            alert("Git is not installed.");
            return;
        }
        GitService.Result r = gitService.pull();
        if (!r.success()) {
            alert("Pull failed:\n" + r.output()
                    + "\n\nIf this is a conflict, resolve it in Git first.");
        }
        refresh();
    }

    private void commitAndPush() {
        if (!gitService.isAvailable()) {
            alert("Git is not installed.");
            return;
        }
        if (!gitService.isRepository()) {
            alert("This directory is not a Git repository.");
            return;
        }
        GitService.Result before = gitService.status();
        boolean hasChanges = before.success() && before.output().lines()
                .anyMatch(l -> !l.startsWith("##") && !l.isBlank());
        if (!hasChanges) {
            new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Nothing to commit — no changes.")
                    .showAndWait();
            return;
        }
        GitService.Result add = gitService.add("work-reports");
        if (!add.success()) {
            alert("git add failed:\n" + add.output());
            return;
        }
        String message = "Work report update " + LocalDate.now();
        GitService.Result commit = gitService.commit(message);
        if (!commit.success()) {
            alert("git commit failed:\n" + commit.output());
            return;
        }
        GitService.Result push = gitService.push();
        if (!push.success()) {
            alert("git push failed:\n" + push.output());
            return;
        }
        refresh();
    }

    private void newReport() {
        WorkReport r = new WorkReport();
        r.setDate(LocalDate.now());
        editor(r).ifPresent(saved -> {
            try {
                reportService.create(saved);
                developerStore.remember(saved.getDeveloper());
                refresh();
                selectReport(saved);
            } catch (IOException ex) {
                alert("Could not save report:\n" + ex.getMessage());
            }
        });
    }

    private void selectReport(WorkReport saved) {
        String name = saved.getFileName();
        reports.stream().filter(r -> name.equals(r.getFileName())).findFirst()
                .ifPresent(reports -> table.getSelectionModel().select(reports));
    }

    private void editSelected() {
        WorkReport selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        editor(selected).ifPresent(saved -> {
            try {
                reportService.save(saved);
                developerStore.remember(saved.getDeveloper());
                refresh();
            } catch (IOException ex) {
                alert("Could not save report:\n" + ex.getMessage());
            }
        });
    }

    private void deleteSelected() {
        WorkReport selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        var confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Delete report for " + selected.getDeveloper()
                        + " on " + selected.getDate() + "?");
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isPresent()) {
            try {
                reportService.delete(selected);
                refresh();
            } catch (IOException ex) {
                alert("Could not delete report:\n" + ex.getMessage());
            }
        }
    }

    private void changeProject() {
        if (onChangeProject != null) {
            onChangeProject.run();
        }
    }

    private Optional<WorkReport> editor(WorkReport original) {
        Dialog<WorkReport> dialog = new Dialog<>();
        dialog.setTitle("Work Report");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField date = new TextField(original.getDate() == null
                ? LocalDate.now().toString() : original.getDate().toString());
        TextField dev = developerField(initialDeveloper(original));
        TextField start = new TextField(original.getStartTime() == null
                ? LocalTime.now().toString() : original.getStartTime().toString());
        TextField end = new TextField(original.getEndTime() == null
                ? LocalTime.now().toString() : original.getEndTime().toString());
        TextArea summary = new TextArea(original.getSummary());
        summary.setPromptText("Summary");
        summary.setPrefRowCount(3);
        TextArea tasks = new TextArea(String.join("\n", original.getTasks()));
        tasks.setPromptText("Tasks (one per line)");
        tasks.setPrefRowCount(3);
        TextArea notes = new TextArea(original.getNotes());
        notes.setPromptText("Notes");
        notes.setPrefRowCount(2);
        TextField tags = new TextField(String.join(" ", original.getTags()));
        tags.setPromptText("Tags (space-separated, # optional)");

        Label autoSave = new Label();
        autoSave.setStyle("-fx-text-fill: #777;");

        Label elapsed = new Label();
        Button startBtn = new Button("Start Work");
        Button pauseBtn = new Button("Pause");
        Button stopBtn = new Button("Stop");
        final boolean[] running = {false};
        final boolean[] paused = {false};
        final long[] startedAt = {0};
        final long[] accumulated = {0};
        final LocalTime[] startT = {null};

        startBtn.setOnAction(e -> {
            if (running[0] && !paused[0]) {
                return;
            }
            if (!running[0]) {
                startT[0] = LocalTime.now();
                accumulated[0] = 0;
                start.setText(startT[0].toString());
            }
            startedAt[0] = System.currentTimeMillis();
            running[0] = true;
            paused[0] = false;
        });
        pauseBtn.setOnAction(e -> {
            if (running[0] && !paused[0]) {
                accumulated[0] += System.currentTimeMillis() - startedAt[0];
                paused[0] = true;
            }
        });
        stopBtn.setOnAction(e -> {
            if (running[0]) {
                accumulated[0] += System.currentTimeMillis() - startedAt[0];
                running[0] = false;
                paused[0] = false;
                if (startT[0] != null) {
                    start.setText(startT[0].toString());
                    end.setText(startT[0]
                            .plus(java.time.Duration.ofMillis(accumulated[0])).toString());
                }
                elapsed.setText("Total: " + formatDuration(accumulated[0]));
            }
        });

        HBox timeBox = new HBox(8, startBtn, pauseBtn, stopBtn, elapsed);

        VBox form = new VBox(6,
                labeled("Date", date), labeled("Developer", dev),
                labeled("Start Time (HH:MM)", start), labeled("End Time (HH:MM)", end),
                timeBox,
                labeled("Summary", summary), labeled("Tasks", tasks),
                labeled("Tags", tags), labeled("Notes", notes), autoSave);
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);

        long[] lastSave = {-1};
        Timeline saver = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            try {
                WorkReport draft = buildReport(date, dev, start, end,
                        summary, tasks, tags, notes, original);
                reportService.save(draft);
                original.setFileName(draft.getFileName());
                lastSave[0] = System.currentTimeMillis() / 1000;
                autoSave.setText("Auto-saved just now");
            } catch (IOException ex) {
                autoSave.setText("Auto-save failed: " + ex.getMessage());
            }
        }));
        saver.setCycleCount(Timeline.INDEFINITE);
        Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (lastSave[0] >= 0) {
                long ago = System.currentTimeMillis() / 1000 - lastSave[0];
                autoSave.setText("Auto-saved " + ago + "s ago");
            }
            if (running[0]) {
                long ms = accumulated[0]
                        + (paused[0] ? 0 : System.currentTimeMillis() - startedAt[0]);
                elapsed.setText(paused[0] ? "Paused — " + formatDuration(ms)
                        : "Total: " + formatDuration(ms));
            }
        }));
        ticker.setCycleCount(Timeline.INDEFINITE);
        saver.play();
        ticker.play();
        dialog.setOnHidden(ev -> {
            saver.stop();
            ticker.stop();
        });

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
            return buildReport(date, dev, start, end, summary, tasks, tags, notes, original);
        });
        return dialog.showAndWait();
    }

    private WorkReport buildReport(TextField date, TextField dev, TextField start, TextField end,
            TextArea summary, TextArea tasks, TextField tags, TextArea notes, WorkReport original) {
        WorkReport r = new WorkReport();
        r.setDate(parseDate(date.getText()));
        r.setDeveloper(dev.getText().trim());
        r.setStartTime(parseTime(start.getText()));
        r.setEndTime(parseTime(end.getText()));
        r.setSummary(summary.getText());
        r.getTasks().addAll(tasks.getText().lines().map(String::trim)
                .filter(l -> !l.isEmpty()).toList());
        r.setTags(tags.getText().lines().map(String::trim)
                .filter(l -> !l.isEmpty())
                .map(t -> t.startsWith("#") ? t.substring(1) : t)
                .toList());
        r.setNotes(notes.getText());
        r.setFileName(original.getFileName());
        return r;
    }

    private String initialDeveloper(WorkReport original) {
        if (original.getDeveloper() != null && !original.getDeveloper().isBlank()) {
            return original.getDeveloper();
        }
        List<String> known = developerStore.all();
        if (known.size() == 1) {
            return known.get(0);
        }
        return null;
    }

    private TextField developerField(String initial) {
        TextField field = new TextField();
        field.setPromptText("Developer");
        field.setPrefWidth(220);

        ListView<String> suggestions = new ListView<>();
        suggestions.setPrefWidth(220);
        suggestions.setPrefHeight(140);

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(suggestions);

        List<String> allNames = developerStore.all();

        field.textProperty().addListener((obs, o, n) -> {
            String q = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String name : allNames) {
                if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(name);
                }
            }
            suggestions.setItems(filtered);
            if (filtered.isEmpty()) {
                popup.hide();
            } else if (field.isFocused() && !popup.isShowing()) {
                Bounds b = field.localToScreen(field.getBoundsInLocal());
                popup.show(field, b.getMinX(), b.getMaxY());
            }
        });

        suggestions.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> {
                    if (n != null) {
                        field.setText(n);
                        popup.hide();
                        field.positionCaret(field.getText().length());
                    }
                });

        if (initial != null && !initial.isBlank()) {
            field.setText(initial);
        }
        return field;
    }

    private VBox labeled(String text, javafx.scene.Node node) {
        Label label = new Label(text);
        VBox box = new VBox(3, label, node);
        return box;
    }

    private static java.time.LocalDate parseDate(String s) {
        try {
            return java.time.LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static java.time.LocalTime parseTime(String s) {
        try {
            return java.time.LocalTime.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatDuration(long millis) {
        long totalMin = millis / 60_000;
        long h = totalMin / 60;
        long m = totalMin % 60;
        return h + "h " + m + "m";
    }

    private void alert(String message) {
        new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                message).showAndWait();
    }
}