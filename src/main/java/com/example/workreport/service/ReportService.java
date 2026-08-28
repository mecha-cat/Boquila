package com.example.workreport.service;

import com.example.workreport.model.WorkReport;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ReportService {

    private static final DateTimeFormatter TIME_STAMP =
            DateTimeFormatter.ofPattern("HHmmssSSS");

    private final GitService gitService;

    public ReportService(GitService gitService) {
        this.gitService = gitService;
    }

    private Path baseDir() throws IOException {
        Path base = gitService.reportsBase();
        Files.createDirectories(base);
        return base;
    }

    private Path dirFor(WorkReport report) throws IOException {
        Path base = gitService.reportsBase();
        LocalDate d = report.getDate();
        if (d == null) {
            return gitService.reportRoot();
        }
        return base.resolve(String.format("%04d", d.getYear()))
                .resolve(String.format("%02d", d.getMonthValue()));
    }

    private Path findExisting(String name) throws IOException {
        Path base = gitService.reportsBase();
        if (!Files.isDirectory(base)) {
            return null;
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name))
                    .findFirst().orElse(null);
        }
    }

    public List<WorkReport> list() {
        List<WorkReport> result = new ArrayList<>();
        try {
            Path base = gitService.reportsBase();
            if (!Files.isDirectory(base)) {
                return result;
            }
            try (Stream<Path> stream = Files.walk(base)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .forEach(p -> parseFile(p).ifPresent(result::add));
            }
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
        Path dir = dirFor(report);
        Files.createDirectories(dir);
        String name = writeUnique(report, dir);
        report.setFileName(name);
    }

    public void save(WorkReport report) throws IOException {
        Path dir = dirFor(report);
        Files.createDirectories(dir);
        String current = report.getFileName();
        String desired = fileNameFor(report);
        if (current == null || current.isBlank()) {
            current = desired;
        }
        Path existing = findExisting(current);
        if (!desired.equals(current)) {
            Files.writeString(dir.resolve(desired), toMarkdown(report),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            if (existing != null) {
                Files.deleteIfExists(existing);
            } else {
                Files.deleteIfExists(dir.resolve(current));
            }
            report.setFileName(desired);
        } else {
            Path target = existing != null ? existing : dir.resolve(current);
            Files.writeString(target, toMarkdown(report),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    public void delete(WorkReport report) throws IOException {
        String name = report.getFileName() != null ? report.getFileName() : fileNameFor(report);
        Path found = findExisting(name);
        if (found != null) {
            Files.deleteIfExists(found);
            return;
        }
        Files.deleteIfExists(dirFor(report).resolve(name));
    }

    public List<WorkReport> search(String query) {
        if (query == null || query.isBlank()) {
            return list();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return list().stream().filter(r -> matches(r, q)).toList();
    }

    public long countUnreadable() {
        try {
            Path base = gitService.reportsBase();
            if (!Files.isDirectory(base)) {
                return 0;
            }
        } catch (IOException e) {
            return 0;
        }
        long count = 0;
        try (Stream<Path> stream = Files.walk(gitService.reportsBase())) {
            for (Path p : stream.filter(Files::isRegularFile)
                    .filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .toList()) {
                try {
                    WorkReport r = fromMarkdown(Files.readString(p));
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
                || r.getTags().stream().anyMatch(t -> contains(t, q))
                || contains(r.getTimeRange(), q);
    }

    private boolean contains(Object value, String q) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private String writeUnique(WorkReport report, Path dir) throws IOException {
        String base = fileNameFor(report);
        String stamp = LocalDateTime.now().format(TIME_STAMP);
        int attempt = 0;
        while (true) {
            String candidate = candidateName(base, stamp, attempt);
            try {
                Files.writeString(dir.resolve(candidate), toMarkdown(report),
                        StandardOpenOption.CREATE_NEW);
                return candidate;
            } catch (FileAlreadyExistsException e) {
                attempt++;
                if (attempt > 100_000) {
                    throw new IOException("Could not generate a unique report filename.");
                }
            }
        }
    }

    private static String candidateName(String base, String stamp, int attempt) {
        if (attempt == 0) {
            return base;
        }
        String core = base.substring(0, base.length() - ".md".length());
        String suffix = attempt == 1 ? stamp : stamp + "-" + attempt;
        return core + "-" + suffix + ".md";
    }

    private java.util.Optional<WorkReport> parseFile(Path file) {
        try {
            String text = Files.readString(file);
            WorkReport report = fromMarkdown(text);
            if (report.getDate() == null || report.getDeveloper() == null
                    || report.getDeveloper().isBlank()) {
                return java.util.Optional.empty();
            }
            report.setFileName(file.getFileName().toString());
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
        return date + "-" + dev + ".md";
    }

    public static String toMarkdown(WorkReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Work Report\n\n");
        append(sb, "Date", r.getDate() == null ? "" : r.getDate().toString());
        append(sb, "Developer", nz(r.getDeveloper()));
        append(sb, "Start Time", r.getStartTime() == null ? "" : r.getStartTime().toString());
        append(sb, "End Time", r.getEndTime() == null ? "" : r.getEndTime().toString());
        append(sb, "Tags", r.getTags().stream()
                .map(t -> "#" + t).reduce((a, b) -> a + " " + b).orElse(""));
        sb.append('\n');
        sb.append("## Summary\n\n").append(nz(r.getSummary())).append('\n');
        sb.append("\n## Tasks\n\n");
        if (r.getTasks().isEmpty()) {
            sb.append("-");
        } else {
            for (String t : r.getTasks()) {
                sb.append("- ").append(t).append('\n');
            }
        }
        sb.append("\n## Notes\n\n").append(nz(r.getNotes())).append('\n');
        return sb.toString();
    }

    public static WorkReport fromMarkdown(String text) {
        WorkReport r = new WorkReport();
        String[] lines = text.split("\\R", -1);
        StringBuilder summary = new StringBuilder();
        StringBuilder notes = new StringBuilder();
        List<String> tasks = new ArrayList<>();
        Section current = Section.NONE;

        for (String line : lines) {
            if (line.startsWith("## Summary")) {
                current = Section.SUMMARY;
                continue;
            }
            if (line.startsWith("## Tasks")) {
                current = Section.TASKS;
                continue;
            }
            if (line.startsWith("## Notes")) {
                current = Section.NOTES;
                continue;
            }
            switch (current) {
                case NONE -> {
                    if (line.startsWith("**")) {
                        applyHeader(r, line);
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

    private static void applyHeader(WorkReport r, String line) {
        int end = line.indexOf("**", 2);
        if (end < 0) {
            return;
        }
        String key = line.substring(2, end).trim().replace(":", "").trim();
        String value = line.substring(end + 2).replaceFirst("^\\s*:\\s*", "").trim();
        switch (key) {
            case "Date" -> r.setDate(parseDate(value));
            case "Developer" -> r.setDeveloper(value);
            case "Start Time" -> r.setStartTime(parseTime(value));
            case "End Time" -> r.setEndTime(parseTime(value));
            case "Tags" -> r.setTags(parseTags(value));
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

    private static List<String> parseTags(String value) {
        List<String> tags = new ArrayList<>();
        if (value.isEmpty()) {
            return tags;
        }
        for (String token : value.split("\\s+")) {
            if (!token.isEmpty()) {
                tags.add(token.startsWith("#") ? token.substring(1) : token);
            }
        }
        return tags;
    }

    private static void append(StringBuilder sb, String key, String value) {
        sb.append("**").append(key).append(":** ").append(value).append('\n');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
