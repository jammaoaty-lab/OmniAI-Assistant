package com.omniai.assistant.lora;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.LoraWeight;
import com.omniai.assistant.nativebridge.LlamaBridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LoraWeightManager {

    private List<LoraWeight> weights;
    private SharedPreferences prefs;
    private Gson gson;
    private LlamaBridge bridge;

    private static final String PREFS_NAME = "omniai_lora_weights";
    private static final String KEY_WEIGHTS = "lora_weights";

    public LoraWeightManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.bridge = LlamaBridge.getInstance();
        this.weights = loadWeightsFromPrefs();
    }

    public void importLora(String filePath, String name) {
        File srcFile = new File(filePath);
        if (!srcFile.exists()) {
            throw new RuntimeException("LoRA file not found: " + filePath);
        }
        String loraId = UUID.randomUUID().toString();
        File loraDir = new File(Constants.LORA_DIR, loraId);
        if (!loraDir.exists()) {
            loraDir.mkdirs();
        }
        File dstFile = new File(loraDir, srcFile.getName());
        copyFile(srcFile, dstFile);

        LoraWeight weight = new LoraWeight();
        weight.setId(loraId);
        weight.setName(name);
        weight.setFilePath(loraDir.getAbsolutePath());
        weight.setFileSize(calculateSize(loraDir));
        weight.setEnabled(false);
        weight.setCreatedAt(System.currentTimeMillis());
        weights.add(weight);
        saveWeightsToPrefs();
    }

    public void importVisionLora(String filePath, String name, String parentVisionModelId) {
        File srcFile = new File(filePath);
        if (!srcFile.exists()) {
            throw new RuntimeException("Vision LoRA file not found: " + filePath);
        }
        String loraId = UUID.randomUUID().toString();
        File loraDir = new File(Constants.LORA_DIR, loraId);
        if (!loraDir.exists()) {
            loraDir.mkdirs();
        }
        File dstFile = new File(loraDir, srcFile.getName());
        copyFile(srcFile, dstFile);

        LoraWeight weight = new LoraWeight();
        weight.setId(loraId);
        weight.setName(name);
        weight.setFilePath(loraDir.getAbsolutePath());
        weight.setFileSize(calculateSize(loraDir));
        weight.setEnabled(false);
        weight.setParentModelId(parentVisionModelId);
        weight.setCreatedAt(System.currentTimeMillis());
        weights.add(weight);
        saveWeightsToPrefs();
    }

    public List<LoraWeight> getVisionLoraWeights(String visionModelId) {
        List<LoraWeight> visionWeights = new ArrayList<>();
        for (LoraWeight w : weights) {
            if (visionModelId != null && visionModelId.equals(w.getParentModelId())) {
                visionWeights.add(w);
            }
        }
        return visionWeights;
    }

    public boolean enableVisionLora(String loraId, long visionModelHandle) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        boolean result = bridge.applyLora(visionModelHandle, target.getFilePath(), 1.0f);
        if (result) {
            target.setEnabled(true);
            saveWeightsToPrefs();
        }
        return result;
    }

    public boolean disableVisionLora(String loraId) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        boolean result = bridge.removeLora(0);
        if (result) {
            target.setEnabled(false);
            saveWeightsToPrefs();
        }
        return result;
    }

    public boolean deleteLora(String loraId) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        File loraDir = new File(target.getFilePath());
        if (loraDir.exists()) {
            deleteRecursive(loraDir);
        }
        weights.remove(target);
        saveWeightsToPrefs();
        return true;
    }

    public boolean renameLora(String loraId, String newName) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        target.setName(newName);
        saveWeightsToPrefs();
        return true;
    }

    public boolean enableLora(String loraId) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        target.setEnabled(true);
        saveWeightsToPrefs();
        return true;
    }

    public boolean disableLora(String loraId) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        target.setEnabled(false);
        saveWeightsToPrefs();
        return true;
    }

    public List<LoraWeight> getLoraWeights() {
        return Collections.unmodifiableList(weights);
    }

    public List<LoraWeight> getEnabledWeights() {
        List<LoraWeight> enabled = new ArrayList<>();
        for (LoraWeight w : weights) {
            if (w.isEnabled()) {
                enabled.add(w);
            }
        }
        return enabled;
    }

    public boolean stackLoraWeights(List<String> loraIds, String outputPath) {
        if (loraIds == null || loraIds.size() < 2) {
            return false;
        }
        List<LoraWeight> toStack = new ArrayList<>();
        for (String id : loraIds) {
            LoraWeight w = getLoraById(id);
            if (w == null) {
                return false;
            }
            toStack.add(w);
        }
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        for (LoraWeight w : toStack) {
            File srcDir = new File(w.getFilePath());
            File[] files = srcDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    copyFile(f, new File(outputDir, w.getId() + "_" + f.getName()));
                }
            }
        }
        return true;
    }

    public boolean exportLora(String loraId, String exportPath) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return false;
        }
        File srcDir = new File(target.getFilePath());
        File exportDir = new File(exportPath);
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File[] files = srcDir.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            copyFile(f, new File(exportDir, f.getName()));
        }
        return true;
    }

    public String backupLora(String loraId) {
        LoraWeight target = getLoraById(loraId);
        if (target == null) {
            return null;
        }
        String backupPath = Constants.LORA_DIR + "/backups/" + loraId + "_" + System.currentTimeMillis();
        File backupDir = new File(backupPath);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        File srcDir = new File(target.getFilePath());
        File[] files = srcDir.listFiles();
        if (files != null) {
            for (File f : files) {
                copyFile(f, new File(backupDir, f.getName()));
            }
        }
        return backupPath;
    }

    public LoraWeight getLoraById(String id) {
        if (id == null) {
            return null;
        }
        for (LoraWeight w : weights) {
            if (id.equals(w.getId())) {
                return w;
            }
        }
        return null;
    }

    private void saveWeightsToPrefs() {
        String json = gson.toJson(weights);
        prefs.edit().putString(KEY_WEIGHTS, json).apply();
    }

    private List<LoraWeight> loadWeightsFromPrefs() {
        String json = prefs.getString(KEY_WEIGHTS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<ArrayList<LoraWeight>>() {}.getType();
            List<LoraWeight> loaded = gson.fromJson(json, type);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void copyFile(File src, File dst) {
        try {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file: " + src.getPath(), e);
        }
    }

    private long calculateSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                } else {
                    size += calculateSize(f);
                }
            }
        }
        return size;
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }
}
