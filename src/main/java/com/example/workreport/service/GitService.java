package com.example.workreport.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class GitService {

    public static final String REPORTS_BRANCH = "work-reports";
    public static final String REPORTS_DIR = "work-reports";

    public record Result(boolean success, String output) {
    }

    private static final long TIMEOUT_SECONDS = 60;

    private final Path projectDir;

    public GitService(Path projectDir) {
        this.projectDir = projectDir;
    }

    private String gitExecutable() {
        String appDir = System.getProperty("app.dir");
        if (appDir != null) {
            for (String layout : new String[]{"app", "."}) {
                Path bundled = Path.of(appDir, layout, "git", "cmd", "git.exe");
                if (Files.exists(bundled)) {
                    return bundled.toString();
                }
            }
        }
        return "git";
    }

    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder(gitExecutable(), "--version")
                    .redirectErrorStream(true).start();
            boolean ok = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean isRepository() {
        return run(projectDir, "rev-parse", "--is-inside-work-tree").success();
    }

    public Result status() {
        try {
            return run(branchWorktree(), "status", "--short", "--branch");
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        }
    }

    public Result pull() {
        try {
            return run(branchWorktree(), "pull");
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        }
    }

    public Result add(String... paths) {
        try {
            List<String> args = new ArrayList<>(List.of("add"));
            args.addAll(List.of(paths));
            return run(branchWorktree(), args.toArray(String[]::new));
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        }
    }

    public Result commit(String message) {
        try {
            return run(branchWorktree(), "commit", "-m", message);
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        }
    }

    public Result push() {
        try {
            return run(branchWorktree(), "push");
        } catch (IOException e) {
            return new Result(false, e.getMessage());
        }
    }

    private String currentBranch() {
        Result r = run(projectDir, "rev-parse", "--abbrev-ref", "HEAD");
        return r.success() ? r.output().trim() : "";
    }

    private void ensureBranch() throws IOException {
        Result exists = run(projectDir, "show-ref", "--verify", "--quiet",
                "refs/heads/" + REPORTS_BRANCH);
        if (exists.success()) {
            return;
        }
        Result created = run(projectDir, "branch", REPORTS_BRANCH);
        if (!created.success()) {
            throw new IOException("Could not create branch " + REPORTS_BRANCH
                    + ": " + created.output());
        }
    }

    private Path worktreePath() {
        return projectDir.resolveSibling(
                projectDir.getFileName() + ".boquila-worktree");
    }

    private void ensureWorktree(Path wt) throws IOException {
        if (Files.exists(wt.resolve(".git"))) {
            return;
        }
        if (Files.exists(wt)) {
            try (Stream<Path> s = Files.list(wt)) {
                if (s.findAny().isEmpty()) {
                    Files.delete(wt);
                } else {
                    throw new IOException("Worktree path exists and is not a git worktree: " + wt);
                }
            }
        }
        Result added = run(projectDir, "worktree", "add", wt.toString(), REPORTS_BRANCH);
        if (!added.success()) {
            throw new IOException("Could not create worktree for " + REPORTS_BRANCH
                    + ": " + added.output());
        }
    }

    private Path branchWorktree() throws IOException {
        ensureBranch();
        if (REPORTS_BRANCH.equals(currentBranch())) {
            return projectDir;
        }
        Path wt = worktreePath();
        ensureWorktree(wt);
        return wt;
    }

    public Path reportRoot() throws IOException {
        Path wt = branchWorktree();
        LocalDate now = LocalDate.now();
        Path root = wt.resolve(REPORTS_DIR)
                .resolve(String.format("%04d", now.getYear()))
                .resolve(String.format("%02d", now.getMonthValue()));
        Files.createDirectories(root);
        return root;
    }

    private Result run(Path workDir, String... args) {
        List<String> cmd = new ArrayList<>();
        String git = gitExecutable();
        cmd.add(git);
        cmd.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }
            if (!"git".equals(git)) {
                Path gitRoot = Path.of(git).getParent().getParent();
                String prefix = String.join(File.pathSeparator,
                        gitRoot.resolve("cmd").toString(),
                        gitRoot.resolve("mingw64").resolve("bin").toString(),
                        gitRoot.resolve("usr").resolve("bin").toString());
                String oldPath = pb.environment().getOrDefault("PATH", "");
                pb.environment().put("PATH", prefix + File.pathSeparator + oldPath);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(false, "Command timed out.");
            }
            String output = new String(p.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            return new Result(p.exitValue() == 0, output);
        } catch (IOException e) {
            return new Result(false, "Could not run git: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Interrupted.");
        }
    }
}
