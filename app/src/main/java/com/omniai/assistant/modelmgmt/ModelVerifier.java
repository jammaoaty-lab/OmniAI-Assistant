package com.omniai.assistant.modelmgmt;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelVerifier {

    private static final String TAG = "ModelVerifier";

    private static final long GGUF_MAGIC = 0x46475547L;
    private static final int MIN_MODEL_SIZE = 1024;
    private static final int GGUF_VERSION = 3;

    public static class ModelInfo {
        private String name;
        private String quantType;
        private long fileSize;
        private int contextLength;
        private int layerCount;
        private String hash;
        private String visionCapability;
        private String architecture;
        private int embeddingLength;
        private int headCount;
        private String description;
        private Map<String, Object> metadata;

        public ModelInfo() {
            this.metadata = new HashMap<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getQuantType() { return quantType; }
        public void setQuantType(String quantType) { this.quantType = quantType; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public int getContextLength() { return contextLength; }
        public void setContextLength(int contextLength) { this.contextLength = contextLength; }
        public int getLayerCount() { return layerCount; }
        public void setLayerCount(int layerCount) { this.layerCount = layerCount; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        public String getVisionCapability() { return visionCapability; }
        public void setVisionCapability(String visionCapability) { this.visionCapability = visionCapability; }
        public String getArchitecture() { return architecture; }
        public void setArchitecture(String architecture) { this.architecture = architecture; }
        public int getEmbeddingLength() { return embeddingLength; }
        public void setEmbeddingLength(int embeddingLength) { this.embeddingLength = embeddingLength; }
        public int getHeadCount() { return headCount; }
        public void setHeadCount(int headCount) { this.headCount = headCount; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public boolean verifyGguf(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || file.length() < MIN_MODEL_SIZE) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magicBytes = new byte[4];
            int read = fis.read(magicBytes);
            if (read != 4) return false;
            int magic = ByteBuffer.wrap(magicBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            return magic == GGUF_MAGIC;
        } catch (IOException e) {
            return false;
        }
    }

    public String calculateHash(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "";
        }
    }

    public boolean verifyHash(String filePath, String expectedHash) {
        String actualHash = calculateHash(filePath);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    public boolean verifyModelHash(String filePath, String expectedHash) {
        String actualHash = calculateHash(filePath);
        return actualHash.equalsIgnoreCase(expectedHash);
    }

    public ModelInfo getModelInfo(String filePath) {
        File file = new File(filePath);
        ModelInfo info = new ModelInfo();
        info.setFileSize(file.length());
        info.setHash(calculateHash(filePath));
        info.setName(file.getName());
        
        if (verifyGguf(filePath)) {
            try {
                Map<String, Object> metadata = parseGgufMetadata(filePath);
                info.setMetadata(metadata);
                
                if (metadata.containsKey("general.architecture")) {
                    info.setArchitecture((String) metadata.get("general.architecture"));
                }
                if (metadata.containsKey("llama.context_length")) {
                    info.setContextLength(((Number) metadata.get("llama.context_length")).intValue());
                }
                if (metadata.containsKey("llama.block_count")) {
                    info.setLayerCount(((Number) metadata.get("llama.block_count")).intValue());
                }
                if (metadata.containsKey("llama.embedding_length")) {
                    info.setEmbeddingLength(((Number) metadata.get("llama.embedding_length")).intValue());
                }
                if (metadata.containsKey("llama.attention.head_count")) {
                    info.setHeadCount(((Number) metadata.get("llama.attention.head_count")).intValue());
                }
                if (metadata.containsKey("general.description")) {
                    info.setDescription((String) metadata.get("general.description"));
                }
                
                info.setQuantType(detectQuantType(filePath, metadata));
            } catch (Exception e) {
                info.setContextLength(4096);
                info.setLayerCount(32);
                info.setQuantType(detectQuantType(filePath));
            }
        }
        return info;
    }

    public Map<String, Object> parseGgufMetadata(String filePath) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        File file = new File(filePath);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[64];
            int read = fis.read(header);
            if (read < 24) {
                return metadata;
            }
            
            ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int magic = buf.getInt();
            if (magic != GGUF_MAGIC) {
                return metadata;
            }
            
            int version = buf.getInt();
            long tensorCount = buf.getLong();
            long kvCount = buf.getLong();
            
            for (long i = 0; i < kvCount; i++) {
                try {
                    String key = readGgufString(fis);
                    int valueType = readGgufInt(fis);
                    Object value = readGgufValue(fis, valueType);
                    if (key != null && value != null) {
                        metadata.put(key, value);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        
        return metadata;
    }

    private String readGgufString(FileInputStream fis) throws IOException {
        long len = readGgufLong(fis);
        if (len <= 0 || len > 1024 * 1024) {
            return "";
        }
        byte[] bytes = new byte[(int) len];
        int read = fis.read(bytes);
        if (read != len) {
            return "";
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readGgufInt(FileInputStream fis) throws IOException {
        byte[] bytes = new byte[4];
        fis.read(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private long readGgufLong(FileInputStream fis) throws IOException {
        byte[] bytes = new byte[8];
        fis.read(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private Object readGgufValue(FileInputStream fis, int type) throws IOException {
        switch (type) {
            case 0:
                byte[] b = new byte[1];
                fis.read(b);
                return b[0];
            case 1:
                byte[] bool = new byte[1];
                fis.read(bool);
                return bool[0] != 0;
            case 2:
                return readGgufInt(fis);
            case 3:
                return readGgufLong(fis);
            case 4:
                byte[] f32 = new byte[4];
                fis.read(f32);
                return ByteBuffer.wrap(f32).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            case 5:
                byte[] f64 = new byte[8];
                fis.read(f64);
                return ByteBuffer.wrap(f64).order(ByteOrder.LITTLE_ENDIAN).getDouble();
            case 6:
                return readGgufString(fis);
            case 7:
                long arrayLen = readGgufLong(fis);
                int arrayType = readGgufInt(fis);
                List<Object> list = new ArrayList<>();
                for (long i = 0; i < arrayLen; i++) {
                    Object val = readGgufValue(fis, arrayType);
                    list.add(val);
                }
                return list;
            default:
                return null;
        }
    }

    public ModelInfo getVisionModelInfo(String filePath) {
        ModelInfo info = getModelInfo(filePath);
        if (isVisionModel(filePath)) {
            info.setVisionCapability(detectVisionCapability(filePath));
        } else {
            info.setVisionCapability("NONE");
        }
        return info;
    }

    public boolean isVisionModel(String filePath) {
        if (!verifyGguf(filePath)) return false;
        String name = new File(filePath).getName().toLowerCase();
        if (name.contains("vl") || name.contains("vision") || name.contains("qwen3-vl")
                || name.contains("qwen2.5-vl") || name.contains("smolvlm")) {
            return true;
        }
        try {
            Map<String, Object> metadata = parseGgufMetadata(filePath);
            for (String key : metadata.keySet()) {
                if (key.toLowerCase().contains("vision") || key.toLowerCase().contains("vl") 
                        || key.toLowerCase().contains("clip")) {
                    return true;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to parse GGUF metadata for vision check", e);
        }
        return false;
    }

    private String detectVisionCapability(String filePath) {
        String name = new File(filePath).getName().toLowerCase();
        if (name.contains("smolvlm")) return "IMAGE_UNDERSTANDING";
        return "FULL";
    }

    public boolean isModelCompatible(String filePath) {
        if (!verifyGguf(filePath)) return false;
        File file = new File(filePath);
        return file.length() >= MIN_MODEL_SIZE;
    }

    private String detectQuantType(String filePath) {
        return detectQuantType(filePath, new HashMap<>());
    }

    private String detectQuantType(String filePath, Map<String, Object> metadata) {
        if (metadata.containsKey("general.quantization_version")) {
            Object version = metadata.get("general.quantization_version");
            if (version instanceof Number) {
                int qVer = ((Number) version).intValue();
            }
        }
        
        String name = new File(filePath).getName().toLowerCase();
        if (name.contains("q4_0")) return "Q4_0";
        if (name.contains("q4_1")) return "Q4_1";
        if (name.contains("q5_0")) return "Q5_0";
        if (name.contains("q5_1")) return "Q5_1";
        if (name.contains("q8_0")) return "Q8_0";
        if (name.contains("q2_k")) return "Q2_K";
        if (name.contains("q3_k")) return "Q3_K";
        if (name.contains("q4_k")) return "Q4_K";
        if (name.contains("q5_k")) return "Q5_K";
        if (name.contains("q6_k")) return "Q6_K";
        if (name.contains("q8_k")) return "Q8_K";
        if (name.contains("iq1_s")) return "IQ1_S";
        if (name.contains("iq2_s")) return "IQ2_S";
        if (name.contains("iq3_s")) return "IQ3_S";
        if (name.contains("iq4_s")) return "IQ4_S";
        if (name.contains("f16")) return "F16";
        if (name.contains("f32")) return "F32";
        return "Q4_K";
    }
}
