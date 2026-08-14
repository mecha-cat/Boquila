package com.example.workreport.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {

    private static final Path CONFIG_DIR = Path.of(
            System.getProperty("user.home"), ".boquila");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");
    private static final String KEY_PROJECT = "project.path";

    private final Properties props = new Properties();

    public AppConfig() {
        load();
    }

    private void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (var in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Could not read config: " + e.getMessage());
            }
        }
    }

    public String getProjectPath() {
        return props.getProperty(KEY_PROJECT);
    }

    public void setProjectPath(Path path) {
        props.setProperty(KEY_PROJECT, path.toString());
        save();
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (var out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "Boquila config");
            }
        } catch (IOException e) {
            System.err.println("Could not save config: " + e.getMessage());
        }
    }
}