package com.omniai.assistant.model;

public class AIModel {

    private String id;
    private String name;
    private String filePath;
    private long fileSize;
    private String quantType;
    private boolean isLoaded;
    private boolean isRunning;
    private boolean gpuAccelerated;
    private boolean hasLora;
    private String loraPath;
    private float loraScale;
    private String category;
    private boolean encrypted;
    private String modelType;
    private String visionCapability;
    private boolean isPreinstalled;
    private String downloadUrl;
    private String expectedHash;
    private long downloadSize;

    public AIModel() {
        this.isLoaded = false;
        this.isRunning = false;
        this.gpuAccelerated = false;
        this.hasLora = false;
        this.loraScale = 1.0f;
        this.encrypted = false;
    }

    public AIModel(String id, String name, String filePath, long fileSize, String quantType, boolean isLoaded, boolean isRunning, boolean gpuAccelerated, boolean hasLora, String loraPath, float loraScale) {
        this.id = id;
        this.name = name;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.quantType = quantType;
        this.isLoaded = isLoaded;
        this.isRunning = isRunning;
        this.gpuAccelerated = gpuAccelerated;
        this.hasLora = hasLora;
        this.loraPath = loraPath;
        this.loraScale = loraScale;
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

    public String getQuantType() {
        return quantType;
    }

    public void setQuantType(String quantType) {
        this.quantType = quantType;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setLoaded(boolean loaded) {
        isLoaded = loaded;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public boolean isGpuAccelerated() {
        return gpuAccelerated;
    }

    public void setGpuAccelerated(boolean gpuAccelerated) {
        this.gpuAccelerated = gpuAccelerated;
    }

    public boolean hasLora() {
        return hasLora;
    }

    public void setHasLora(boolean hasLora) {
        this.hasLora = hasLora;
    }

    public String getLoraPath() {
        return loraPath;
    }

    public void setLoraPath(String loraPath) {
        this.loraPath = loraPath;
    }

    public float getLoraScale() {
        return loraScale;
    }

    public void setLoraScale(float loraScale) {
        this.loraScale = loraScale;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getVisionCapability() {
        return visionCapability;
    }

    public void setVisionCapability(String visionCapability) {
        this.visionCapability = visionCapability;
    }

    public boolean isPreinstalled() {
        return isPreinstalled;
    }

    public void setPreinstalled(boolean preinstalled) {
        isPreinstalled = preinstalled;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getExpectedHash() {
        return expectedHash;
    }

    public void setExpectedHash(String expectedHash) {
        this.expectedHash = expectedHash;
    }

    public long getDownloadSize() {
        return downloadSize;
    }

    public void setDownloadSize(long downloadSize) {
        this.downloadSize = downloadSize;
    }
}
