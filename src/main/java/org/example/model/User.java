package org.example.model;

import java.util.LinkedList;

public class User {
    private final long chatId;
    private final LinkedList<Task> tasks;

    public User(long chatId) {
        this.chatId = chatId;
        tasks = new LinkedList<>();
    }

    public long getChatId() {
        return chatId;
    }

    public LinkedList<Task> getTasks() {
        return tasks;
    }

    public boolean isNotTask() {
        return this.tasks.stream().allMatch(Task::isCompleted);
    }
}
