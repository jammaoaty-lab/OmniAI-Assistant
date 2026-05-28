package com.omniai.assistant.model;

import java.util.UUID;

public class Conversation {

    private String id;
    private String title;
    private long createdAt;
    private long updatedAt;
    private String modelId;
    private boolean isPinned;
    private String category;

    public Conversation() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.isPinned = false;
        this.category = "default";
    }

    public Conversation(String id, String title, long createdAt, long updatedAt, String modelId, boolean isPinned, String category) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.modelId = modelId;
        this.isPinned = isPinned;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}
