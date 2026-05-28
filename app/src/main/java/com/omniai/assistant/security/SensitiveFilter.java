package com.omniai.assistant.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SensitiveFilter {

    private List<String> sensitiveWords;
    private List<String> injectionPatterns;

    public SensitiveFilter() {
        this.sensitiveWords = new ArrayList<>();
        this.injectionPatterns = new ArrayList<>(Arrays.asList(
                "ignore previous",
                "disregard instructions",
                "system prompt",
                "you are now",
                "jailbreak"
        ));
    }

    public void loadSensitiveWords() {
        sensitiveWords.clear();
        sensitiveWords.add("password");
        sensitiveWords.add("secret");
        sensitiveWords.add("api key");
        sensitiveWords.add("token");
        sensitiveWords.add("credential");
        sensitiveWords.add("private key");
        sensitiveWords.add("ssn");
        sensitiveWords.add("social security");
        sensitiveWords.add("credit card");
        sensitiveWords.add("bank account");
    }

    public void addSensitiveWord(String word) {
        if (word != null && !word.trim().isEmpty() && !sensitiveWords.contains(word.toLowerCase())) {
            sensitiveWords.add(word.toLowerCase());
        }
    }

    public void removeSensitiveWord(String word) {
        if (word != null) {
            sensitiveWords.remove(word.toLowerCase());
        }
    }

    public boolean containsSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase();
        for (String word : sensitiveWords) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public String filterText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String filtered = text;
        for (String word : sensitiveWords) {
            filtered = filtered.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
        }
        return filtered;
    }

    public boolean detectInjection(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return false;
        }
        String lowerPrompt = prompt.toLowerCase();
        for (String pattern : injectionPatterns) {
            if (lowerPrompt.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getInjectionPatterns() {
        return new ArrayList<>(injectionPatterns);
    }
}
