package com.omniai.assistant.chat;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omniai.assistant.model.ChatMessage;
import com.omniai.assistant.model.Conversation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatRepository {

    private static final String STORAGE_KEY = "omniai_conversations";
    private static final String MESSAGES_KEY_PREFIX = "omniai_messages_";

    private final SharedPreferences prefs;
    private final Gson gson;

    public ChatRepository(Context context) {
        this.prefs = context.getSharedPreferences("omniai_chat", Context.MODE_PRIVATE);
        this.gson = new Gson();
    }

    public void saveConversations(List<Conversation> conversations) {
        String json = gson.toJson(conversations);
        prefs.edit().putString(STORAGE_KEY, json).apply();
    }

    public List<Conversation> loadConversations() {
        String json = prefs.getString(STORAGE_KEY, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<Conversation>>() {}.getType();
            List<Conversation> result = gson.fromJson(json, type);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveMessages(String conversationId, List<ChatMessage> messages) {
        String key = MESSAGES_KEY_PREFIX + conversationId;
        String json = gson.toJson(messages);
        prefs.edit().putString(key, json).apply();
    }

    public List<ChatMessage> loadMessages(String conversationId) {
        String key = MESSAGES_KEY_PREFIX + conversationId;
        String json = prefs.getString(key, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type type = new TypeToken<List<ChatMessage>>() {}.getType();
            List<ChatMessage> result = gson.fromJson(json, type);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void deleteConversation(String id) {
        String key = MESSAGES_KEY_PREFIX + id;
        prefs.edit().remove(key).apply();
    }

    public void deleteMessage(long messageId) {
        List<Conversation> conversations = loadConversations();
        for (Conversation conversation : conversations) {
            List<ChatMessage> messages = loadMessages(conversation.getId());
            boolean found = false;
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).getId() == messageId) {
                    messages.remove(i);
                    found = true;
                    break;
                }
            }
            if (found) {
                saveMessages(conversation.getId(), messages);
                return;
            }
        }
    }

    public List<ChatMessage> searchMessages(String query) {
        List<ChatMessage> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        List<Conversation> conversations = loadConversations();
        for (Conversation conversation : conversations) {
            List<ChatMessage> messages = loadMessages(conversation.getId());
            for (ChatMessage message : messages) {
                if (message.getContent() != null && message.getContent().toLowerCase().contains(lowerQuery)) {
                    results.add(message);
                }
            }
        }
        return results;
    }
}
