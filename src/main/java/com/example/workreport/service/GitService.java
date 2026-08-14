package com.example.workreport.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GitService {

    public record Result(boolean success, String output) {
    }

    private static final long TIMEOUT_SECONDS = 60;

    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("git", "--version")
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

    public boolean isRepository(Path projectDir) {
        return run(projectDir, "rev-parse", "--is-inside-work-tree").success();
    }

    public Result status(Path projectDir) {
        return run(projectDir, "status", "--short", "--branch");
    }

    public Result pull(Path projectDir) {
        return run(projectDir, "pull");
    }

    public Result add(Path projectDir, String... paths) {
        List<String> args = new ArrayList<>(List.of("add"));
        args.addAll(List.of(paths));
        return run(projectDir, args.toArray(String[]::new));
    }

    public Result commit(Path projectDir, String message) {
        return run(projectDir, "commit", "-m", message);
    }

    public Result push(Path projectDir) {
        return run(projectDir, "push");
    }

    private Result run(Path workDir, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workDir != null) {
                pb.directory(workDir.toFile());
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