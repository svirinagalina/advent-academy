package ru.adventacademy.training1.repository;

public class Lesson {
    private long id;

    public Lesson(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
