package com.omniai.assistant.chat;

import com.omniai.assistant.model.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContextManager {

    private int maxContextLength;
    private int currentTokenCount;
    private List<ChatMessage> contextWindow;
    private boolean kvCacheEnabled;

    public ContextManager() {
        this.maxContextLength = 4096;
        this.currentTokenCount = 0;
        this.contextWindow = new ArrayList<>();
        this.kvCacheEnabled = false;
    }

    public void addToContext(ChatMessage message) {
        contextWindow.add(message);
        currentTokenCount += estimateTokenCount(message.getContent());
        if (isContextFull()) {
            trimContext();
        }
    }

    public void removeFromContext(long messageId) {
        for (int i = 0; i < contextWindow.size(); i++) {
            if (contextWindow.get(i).getId() == messageId) {
                ChatMessage removed = contextWindow.remove(i);
                currentTokenCount -= estimateTokenCount(removed.getContent());
                if (currentTokenCount < 0) {
                    currentTokenCount = 0;
                }
                break;
            }
        }
    }

    public void clearContext() {
        contextWindow.clear();
        currentTokenCount = 0;
    }

    public List<ChatMessage> getContextWindow() {
        return Collections.unmodifiableList(contextWindow);
    }

    public int getCurrentTokenCount() {
        return currentTokenCount;
    }

    public boolean isContextFull() {
        return currentTokenCount >= maxContextLength;
    }

    public void trimContext() {
        while (currentTokenCount > (int)(maxContextLength * 0.8) && contextWindow.size() > 1) {
            ChatMessage oldest = contextWindow.get(0);
            if (!oldest.isUser() || contextWindow.size() <= 2) {
                if (contextWindow.size() > 1) {
                    oldest = contextWindow.remove(0);
                    currentTokenCount -= estimateTokenCount(oldest.getContent());
                } else {
                    break;
                }
            } else {
                oldest = contextWindow.remove(0);
                currentTokenCount -= estimateTokenCount(oldest.getContent());
            }
        }
        if (currentTokenCount < 0) {
            currentTokenCount = 0;
        }
    }

    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
        if (currentTokenCount > maxContextLength) {
            trimContext();
        }
    }

    public String buildPromptFromContext(String systemPrompt) {
        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append("[System]\n").append(systemPrompt).append("\n\n");
        }
        for (ChatMessage message : contextWindow) {
            if (message.isUser()) {
                sb.append("[User]\n").append(message.getContent()).append("\n\n");
            } else {
                sb.append("[Assistant]\n").append(message.getContent()).append("\n\n");
            }
        }
        sb.append("[Assistant]\n");
        return sb.toString();
    }

    public float getContextUsagePercent() {
        if (maxContextLength <= 0) return 0f;
        return (float) currentTokenCount / maxContextLength * 100f;
    }

    public boolean isKvCacheEnabled() {
        return kvCacheEnabled;
    }

    public void setKvCacheEnabled(boolean kvCacheEnabled) {
        this.kvCacheEnabled = kvCacheEnabled;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return chineseCount + otherCount / 4 + 1;
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
