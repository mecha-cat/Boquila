package com.example.workreport.ui;

import com.example.workreport.model.WorkReport;
import com.example.workreport.service.GitService;
import com.example.workreport.service.ReportService;
import com.example.workreport.util.DeveloperStore;

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
import javafx.stage.Popup;

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
        return root;
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

        HBox buttons = new HBox(8, edit, delete);
        buttons.setPadding(new Insets(6, 10, 10, 10));

        return new VBox(detail, buttons);
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
                ? "" : original.getStartTime().toString());
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

        VBox form = new VBox(6,
                labeled("Date", date), labeled("Developer", dev),
                labeled("Start Time (HH:MM)", start), labeled("End Time (HH:MM)", end),
                labeled("Summary", summary), labeled("Tasks", tasks),
                labeled("Tags", tags), labeled("Notes", notes));
        form.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(form);

        dialog.setResultConverter(bt -> {
            if (bt != ButtonType.OK) {
                return null;
            }
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
        });
        return dialog.showAndWait();
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

    private void alert(String message) {
        new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                message).showAndWait();
    }
}