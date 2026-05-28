package com.omniai.assistant.model;

import java.util.UUID;

public class LoraWeight {

    private String id;
    private String name;
    private String filePath;
    private long fileSize;
    private int rank;
    private float alpha;
    private boolean isEnabled;
    private String parentModelId;
    private long createdAt;

    public LoraWeight() {
        this.id = UUID.randomUUID().toString();
        this.isEnabled = true;
        this.createdAt = System.currentTimeMillis();
    }

    public LoraWeight(String id, String name, String filePath, long fileSize, int rank, float alpha, boolean isEnabled, String parentModelId, long createdAt) {
        this.id = id;
        this.name = name;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.rank = rank;
        this.alpha = alpha;
        this.isEnabled = isEnabled;
        this.parentModelId = parentModelId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public String getParentModelId() {
        return parentModelId;
    }

    public void setParentModelId(String parentModelId) {
        this.parentModelId = parentModelId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
