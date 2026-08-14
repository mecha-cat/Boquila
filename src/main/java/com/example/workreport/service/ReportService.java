package com.example.workreport.service;

import com.example.workreport.model.WorkReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ReportService {

    public static final String REPORTS_DIR_NAME = "reports";

    private final Path reportsDir;

    public ReportService(Path projectDir) {
        this.reportsDir = projectDir.resolve(REPORTS_DIR_NAME);
    }

    public Path getReportsDir() {
        return reportsDir;
    }

    public void ensureReportsDir() throws IOException {
        Files.createDirectories(reportsDir);
    }

    public List<WorkReport> list() {
        List<WorkReport> result = new ArrayList<>();
        if (!Files.isDirectory(reportsDir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(reportsDir)) {
            stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .forEach(p -> parseFile(p).ifPresent(result::add));
        } catch (IOException e) {
            System.err.println("Could not list reports: " + e.getMessage());
        }
        result.sort(Comparator
                .comparing(WorkReport::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkReport::getDeveloper,
                        Comparator.nullsLast(String::compareTo)));
        return result;
    }

    public void create(WorkReport report) throws IOException {
        ensureReportsDir();
        write(report);
    }

    public void save(WorkReport report) throws IOException {
        write(report);
    }

    public void delete(WorkReport report) throws IOException {
        Files.deleteIfExists(reportsDir.resolve(fileNameFor(report)));
    }

    public List<WorkReport> search(String query) {
        if (query == null || query.isBlank()) {
            return list();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return list().stream().filter(r -> matches(r, q)).toList();
    }

    public long countUnreadable() {
        if (!Files.isDirectory(reportsDir)) {
            return 0;
        }
        long count = 0;
        try (Stream<Path> stream = Files.list(reportsDir)) {
            for (Path p : stream
                    .filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .toList()) {
                try {
                    WorkReport r = fromText(Files.readString(p));
                    if (r.getDate() == null || r.getDeveloper() == null
                            || r.getDeveloper().isBlank()) {
                        count++;
                    }
                } catch (IOException e) {
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("Could not scan reports: " + e.getMessage());
        }
        return count;
    }

    private boolean matches(WorkReport r, String q) {
        return contains(r.getDate(), q)
                || contains(r.getDeveloper(), q)
                || contains(r.getSummary(), q)
                || contains(r.getNotes(), q)
                || r.getTasks().stream().anyMatch(t -> contains(t, q))
                || contains(r.getTimeRange(), q);
    }

    private boolean contains(Object value, String q) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void write(WorkReport report) throws IOException {
        ensureReportsDir();
        Path file = reportsDir.resolve(fileNameFor(report));
        Files.writeString(file, toText(report));
    }

    private java.util.Optional<WorkReport> parseFile(Path file) {
        try {
            String text = Files.readString(file);
            WorkReport report = fromText(text);
            if (report.getDate() == null || report.getDeveloper() == null
                    || report.getDeveloper().isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(report);
        } catch (IOException e) {
            System.err.println("Could not read report " + file + ": " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public static String fileNameFor(WorkReport report) {
        String date = report.getDate() == null ? "unknown" : report.getDate().toString();
        String dev = report.getDeveloper() == null ? "unknown"
                : report.getDeveloper().trim().replaceAll("\\s+", "-");
        return date + "-" + dev + ".txt";
    }

    public static String toText(WorkReport r) {
        StringBuilder sb = new StringBuilder();
        append(sb, "Date", r.getDate() == null ? "" : r.getDate().toString());
        append(sb, "Developer", nz(r.getDeveloper()));
        append(sb, "Start Time", r.getStartTime() == null ? "" : r.getStartTime().toString());
        append(sb, "End Time", r.getEndTime() == null ? "" : r.getEndTime().toString());

        sb.append("\nSummary:\n").append(nz(r.getSummary()));

        sb.append("\nTasks:\n");
        if (r.getTasks().isEmpty()) {
            sb.append("-");
        } else {
            for (String t : r.getTasks()) {
                sb.append("- ").append(t).append('\n');
            }
        }

        sb.append("\nNotes:\n").append(nz(r.getNotes()));
        return sb.toString();
    }

    public static WorkReport fromText(String text) {
        WorkReport r = new WorkReport();
        String[] lines = text.split("\\R", -1);
        StringBuilder summary = new StringBuilder();
        StringBuilder notes = new StringBuilder();
        List<String> tasks = new ArrayList<>();
        Section current = Section.NONE;

        for (String line : lines) {
            if (line.startsWith("Summary:")) {
                current = Section.SUMMARY;
                continue;
            }
            if (line.startsWith("Tasks:")) {
                current = Section.TASKS;
                continue;
            }
            if (line.startsWith("Notes:")) {
                current = Section.NOTES;
                continue;
            }
            switch (current) {
                case NONE -> {
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        applyKey(r, key, value);
                    }
                }
                case SUMMARY -> appendLine(summary, line);
                case NOTES -> appendLine(notes, line);
                case TASKS -> {
                    String t = line.trim();
                    if (t.startsWith("-")) {
                        t = t.substring(1).trim();
                    }
                    if (!t.isEmpty()) {
                        tasks.add(t);
                    }
                }
            }
        }
        r.setSummary(summary.toString().strip());
        r.setNotes(notes.toString().strip());
        r.getTasks().addAll(tasks);
        return r;
    }

    private enum Section { NONE, SUMMARY, TASKS, NOTES }

    private static void applyKey(WorkReport r, String key, String value) {
        switch (key) {
            case "Date" -> r.setDate(parseDate(value));
            case "Developer" -> r.setDeveloper(value);
            case "Start Time" -> r.setStartTime(parseTime(value));
            case "End Time" -> r.setEndTime(parseTime(value));
            default -> { }
        }
    }

    private static LocalDate parseDate(String v) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String v) {
        if (v.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(line);
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append(key).append(": ").append(value).append('\n');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}