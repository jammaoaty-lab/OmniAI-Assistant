package com.omniai.assistant.model;

import java.util.UUID;

public class KnowledgeBase {

    private String id;
    private String name;
    private String description;
    private int documentCount;
    private long totalSize;
    private long createdAt;
    private String status;

    public KnowledgeBase() {
        this.id = UUID.randomUUID().toString();
        this.documentCount = 0;
        this.totalSize = 0;
        this.createdAt = System.currentTimeMillis();
        this.status = "idle";
    }

    public KnowledgeBase(String id, String name, String description, int documentCount, long totalSize, long createdAt, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.documentCount = documentCount;
        this.totalSize = totalSize;
        this.createdAt = createdAt;
        this.status = status;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isIdle() {
        return "idle".equals(status);
    }

    public boolean isIndexing() {
        return "indexing".equals(status);
    }

    public boolean isError() {
        return "error".equals(status);
    }
}
