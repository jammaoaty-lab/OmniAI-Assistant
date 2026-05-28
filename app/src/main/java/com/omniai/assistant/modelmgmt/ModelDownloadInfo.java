package com.omniai.assistant.modelmgmt;

public class ModelDownloadInfo {

    public static final String MODEL_ID_TEXT = "qwen2.5-0.5b-instruct";
    public static final String MODEL_ID_VISION = "qwen3-vl-2b";

    private String modelId;
    private String modelName;
    private String downloadUrl;
    private String expectedMd5;
    private long fileSize;
    private String fileName;
    private String quantType;
    private String modelType;
    private String description;

    public static ModelDownloadInfo createTextModel() {
        ModelDownloadInfo info = new ModelDownloadInfo();
        info.modelId = MODEL_ID_TEXT;
        info.modelName = "Qwen2.5-0.5B-Instruct";
        info.downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf";
        info.expectedMd5 = "";
        info.fileSize = 394_572_864L;
        info.fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf";
        info.quantType = "Q4_K_M";
        info.modelType = "TEXT";
        info.description = "通义千问文本模型，支持对话、写作、代码生成等";
        return info;
    }

    public static ModelDownloadInfo createVisionModel() {
        ModelDownloadInfo info = new ModelDownloadInfo();
        info.modelId = MODEL_ID_VISION;
        info.modelName = "Qwen3-VL-2B";
        info.downloadUrl = "https://huggingface.co/Qwen/Qwen3-VL-2B-GGUF/resolve/main/qwen3-vl-2b-q4_k_m.gguf";
        info.expectedMd5 = "";
        info.fileSize = 1_509_949_440L;
        info.fileName = "Qwen3-VL-2B-Q4_K_M.gguf";
        info.quantType = "Q4_K_M";
        info.modelType = "VISION";
        info.description = "通义千问视觉模型，支持图像理解、OCR、图文问答";
        return info;
    }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getExpectedMd5() { return expectedMd5; }
    public void setExpectedMd5(String expectedMd5) { this.expectedMd5 = expectedMd5; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getQuantType() { return quantType; }
    public void setQuantType(String quantType) { this.quantType = quantType; }

    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDisplaySize() {
        if (fileSize <= 0) return "未知";
        double gb = fileSize / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format("%.1f GB", gb);
        double mb = fileSize / (1024.0 * 1024.0);
        return String.format("%.0f MB", mb);
    }
}
