package com.example.workreport.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WorkReport {

    private LocalDate date;
    private String developer;
    private LocalTime startTime;
    private LocalTime endTime;
    private String summary;
    private final List<String> tasks = new ArrayList<>();
    private String notes;
    private String fileName;

    public WorkReport() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDeveloper() {
        return developer;
    }

    public void setDeveloper(String developer) {
        this.developer = developer;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTimeRange() {
        if (startTime == null && endTime == null) {
            return "";
        }
        String start = startTime == null ? "?" : startTime.toString();
        String end = endTime == null ? "?" : endTime.toString();
        return start + "-" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkReport that)) {
            return false;
        }
        return Objects.equals(date, that.date)
                && Objects.equals(developer, that.developer)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(summary, that.summary)
                && Objects.equals(tasks, that.tasks)
                && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, developer, startTime, endTime, summary, tasks, notes);
    }
}