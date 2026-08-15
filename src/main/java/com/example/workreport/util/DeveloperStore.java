package com.example.workreport.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DeveloperStore {

    private static final Path STORE_DIR = Path.of(
            System.getProperty("user.home"), ".boquila");
    private static final Path STORE_FILE = STORE_DIR.resolve("developers.txt");

    private final Set<String> developers = new LinkedHashSet<>();

    public DeveloperStore() {
        load();
    }

    private void load() {
        if (!Files.exists(STORE_FILE)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(STORE_FILE)) {
                String name = line.trim();
                if (!name.isEmpty()) {
                    developers.add(name);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read developers: " + e.getMessage());
        }
    }

    public List<String> all() {
        return new ArrayList<>(developers);
    }

    public void remember(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (developers.add(name.trim())) {
            save();
        }
    }

    public void rememberAll(Iterable<String> names) {
        boolean changed = false;
        for (String name : names) {
            if (name != null && !name.isBlank() && developers.add(name.trim())) {
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void save() {
        try {
            Files.createDirectories(STORE_DIR);
            Files.write(STORE_FILE, developers);
        } catch (IOException e) {
            System.err.println("Could not save developers: " + e.getMessage());
        }
    }
}