package com.omniai.assistant.lora;

import com.omniai.assistant.knowledge.DocumentParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DataSetProcessor {

    private DocumentParser parser;

    public DataSetProcessor() {
        this.parser = new DocumentParser();
    }

    public List<String> importFile(String filePath, String format) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + filePath);
        }
        List<String> entries = new ArrayList<>();
        String content;
        switch (format.toLowerCase()) {
            case "txt":
                content = parser.parseTxt(filePath);
                break;
            case "md":
            case "markdown":
                content = parser.parseMarkdown(filePath);
                break;
            case "pdf":
                content = parser.parsePdf(filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        if (content != null && !content.trim().isEmpty()) {
            String[] lines = content.split("\n");
            StringBuilder current = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    if (current.length() > 0) {
                        entries.add(current.toString().trim());
                        current = new StringBuilder();
                    }
                } else {
                    if (current.length() > 0) {
                        current.append(" ");
                    }
                    current.append(trimmed);
                }
            }
            if (current.length() > 0) {
                entries.add(current.toString().trim());
            }
        }
        return entries;
    }

    public List<String> importDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new RuntimeException("Directory not found: " + dirPath);
        }
        List<String> allEntries = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) {
            return allEntries;
        }
        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                String format = null;
                if (name.endsWith(".txt")) {
                    format = "txt";
                } else if (name.endsWith(".md") || name.endsWith(".markdown")) {
                    format = "markdown";
                } else if (name.endsWith(".pdf")) {
                    format = "pdf";
                }
                if (format != null) {
                    try {
                        List<String> entries = importFile(file.getAbsolutePath(), format);
                        allEntries.addAll(entries);
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
        }
        return allEntries;
    }

    public List<String> cleanData(List<String> raw) {
        List<String> cleaned = new ArrayList<>();
        for (String entry : raw) {
            String result = entry;
            result = result.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
            result = result.replaceAll("\\s+", " ");
            result = normalizePunctuation(result);
            result = result.trim();
            if (!result.isEmpty()) {
                cleaned.add(result);
            }
        }
        return cleaned;
    }

    private String normalizePunctuation(String text) {
        return text
                .replace("\u2018", "'").replace("\u2019", "'")
                .replace("\u201C", "\"").replace("\u201D", "\"")
                .replace("\u3001", ",")
                .replace("\u3002", ".")
                .replace("\uff0c", ",")
                .replace("\uff0e", ".")
                .replace("\uff1f", "?")
                .replace("\uff01", "!")
                .replace("\uff1b", ";")
                .replace("\uff1a", ":")
                .replace("\u2026", "...")
                .replace("\u2014", "-")
                .replace("\u2013", "-");
    }

    public List<String> deduplicate(List<String> data) {
        Set<String> seen = new HashSet<>();
        List<String> unique = new ArrayList<>();
        for (String entry : data) {
            String normalized = entry.trim();
            if (!seen.contains(normalized)) {
                seen.add(normalized);
                unique.add(entry);
            }
        }
        return unique;
    }

    public List<String> filterInvalid(List<String> data) {
        List<String> valid = new ArrayList<>();
        for (String entry : data) {
            if (entry == null) {
                continue;
            }
            int len = entry.length();
            if (len >= 10 && len <= 10000) {
                valid.add(entry);
            }
        }
        return valid;
    }

    public List<int[]> tokenize(List<String> data) {
        com.omniai.assistant.nativebridge.LlamaBridge bridge =
                com.omniai.assistant.nativebridge.LlamaBridge.getInstance();
        List<int[]> tokenized = new ArrayList<>();
        for (String text : data) {
            int[] tokens = bridge.tokenize(0, text, true);
            tokenized.add(tokens);
        }
        return tokenized;
    }

    public boolean buildCorpus(List<String> data, String outputPath) {
        try {
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            java.io.BufferedWriter writer = new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(new java.io.FileOutputStream(outputFile), StandardCharsets.UTF_8));
            for (String entry : data) {
                writer.write(entry.replace("\n", " "));
                writer.newLine();
            }
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
