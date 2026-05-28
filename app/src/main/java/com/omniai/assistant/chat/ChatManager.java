package com.omniai.assistant.chat;

import android.content.Context;

import com.omniai.assistant.model.ChatMessage;
import com.omniai.assistant.model.Conversation;
import com.omniai.assistant.scheduler.AIScheduler;
import com.omniai.assistant.scheduler.InferenceParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatManager {

    private static volatile ChatManager instance;

    private List<Conversation> conversations;
    private Conversation currentConversation;
    private ChatRepository repository;
    private AIScheduler scheduler;
    private Context context;

    private ChatManager(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new ChatRepository(this.context);
        this.scheduler = AIScheduler.getInstance();
        this.conversations = new ArrayList<>();
        loadConversations();
    }

    public static ChatManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ChatManager.class) {
                if (instance == null) {
                    instance = new ChatManager(context);
                }
            }
        }
        return instance;
    }

    private void loadConversations() {
        List<Conversation> saved = repository.loadConversations();
        if (saved != null) {
            conversations = saved;
        }
    }

    public Conversation createConversation(String title, String modelId) {
        Conversation conversation = new Conversation();
        conversation.setTitle(title);
        conversation.setModelId(modelId);
        conversations.add(0, conversation);
        repository.saveConversations(conversations);
        return conversation;
    }

    public void deleteConversation(String id) {
        Conversation target = findConversation(id);
        if (target != null) {
            conversations.remove(target);
            repository.deleteConversation(id);
            repository.saveConversations(conversations);
            if (currentConversation != null && currentConversation.getId().equals(id)) {
                currentConversation = null;
            }
        }
    }

    public void renameConversation(String id, String newTitle) {
        Conversation target = findConversation(id);
        if (target != null) {
            target.setTitle(newTitle);
            target.touch();
            repository.saveConversations(conversations);
        }
    }

    public List<Conversation> getConversations() {
        List<Conversation> sorted = new ArrayList<>(conversations);
        Collections.sort(sorted, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                if (a.isPinned() != b.isPinned()) {
                    return a.isPinned() ? -1 : 1;
                }
                return Long.compare(b.getUpdatedAt(), a.getUpdatedAt());
            }
        });
        return sorted;
    }

    public Conversation getConversation(String id) {
        return findConversation(id);
    }

    public void setCurrentConversation(String id) {
        currentConversation = findConversation(id);
    }

    public Conversation getCurrentConversation() {
        return currentConversation;
    }

    public void sendMessage(String content, String messageType, SendMessageCallback callback) {
        if (currentConversation == null) {
            if (callback != null) {
                callback.onError("No active conversation");
            }
            return;
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setConversationId(currentConversation.getId());
        userMessage.setContent(content);
        userMessage.setUser(true);
        userMessage.setMessageType(messageType);
        userMessage.setTimestamp(System.currentTimeMillis());

        List<ChatMessage> messages = repository.loadMessages(currentConversation.getId());
        messages.add(userMessage);
        repository.saveMessages(currentConversation.getId(), messages);

        currentConversation.touch();
        repository.saveConversations(conversations);

        InferenceParams params = new InferenceParams.Builder().build();

        scheduler.dispatch(content, params, new AIScheduler.InferenceCallback() {
            @Override
            public void onToken(String token) {
                if (callback != null) {
                    callback.onToken(token);
                }
            }

            @Override
            public void onComplete(String result) {
                ChatMessage aiMessage = new ChatMessage();
                aiMessage.setConversationId(currentConversation.getId());
                aiMessage.setContent(result);
                aiMessage.setUser(false);
                aiMessage.setMessageType("text");
                aiMessage.setTimestamp(System.currentTimeMillis());

                List<ChatMessage> updatedMessages = repository.loadMessages(currentConversation.getId());
                updatedMessages.add(aiMessage);
                repository.saveMessages(currentConversation.getId(), updatedMessages);

                if (callback != null) {
                    callback.onComplete(aiMessage);
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }

            @Override
            public void onModeSwitched(AIScheduler.InferenceMode mode) {
            }
        });
    }

    public List<Conversation> searchConversations(String query) {
        List<Conversation> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (Conversation conversation : conversations) {
            if (conversation.getTitle().toLowerCase().contains(lowerQuery)) {
                results.add(conversation);
                continue;
            }
            List<ChatMessage> messages = repository.loadMessages(conversation.getId());
            for (ChatMessage message : messages) {
                if (message.getContent().toLowerCase().contains(lowerQuery)) {
                    results.add(conversation);
                    break;
                }
            }
        }
        return results;
    }

    public String exportConversation(String id, String format) {
        Conversation conversation = findConversation(id);
        if (conversation == null) {
            return null;
        }
        List<ChatMessage> messages = repository.loadMessages(id);
        StringBuilder sb = new StringBuilder();

        if ("json".equalsIgnoreCase(format)) {
            sb.append("{\n");
            sb.append("  \"title\": \"").append(escapeJson(conversation.getTitle())).append("\",\n");
            sb.append("  \"modelId\": \"").append(escapeJson(conversation.getModelId())).append("\",\n");
            sb.append("  \"createdAt\": ").append(conversation.getCreatedAt()).append(",\n");
            sb.append("  \"messages\": [\n");
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                sb.append("    {\n");
                sb.append("      \"role\": \"").append(msg.isUser() ? "user" : "assistant").append("\",\n");
                sb.append("      \"content\": \"").append(escapeJson(msg.getContent())).append("\",\n");
                sb.append("      \"timestamp\": ").append(msg.getTimestamp()).append("\n");
                sb.append("    }");
                if (i < messages.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}");
        } else if ("markdown".equalsIgnoreCase(format)) {
            sb.append("# ").append(conversation.getTitle()).append("\n\n");
            for (ChatMessage msg : messages) {
                sb.append("**").append(msg.isUser() ? "User" : "AI").append("**: ");
                sb.append(msg.getContent()).append("\n\n");
            }
        } else {
            sb.append(conversation.getTitle()).append("\n\n");
            for (ChatMessage msg : messages) {
                sb.append(msg.isUser() ? "[User]" : "[AI]").append(" ");
                sb.append(msg.getContent()).append("\n\n");
            }
        }

        return sb.toString();
    }

    public void batchDeleteConversations(List<String> ids) {
        for (String id : ids) {
            Conversation target = findConversation(id);
            if (target != null) {
                conversations.remove(target);
                repository.deleteConversation(id);
                if (currentConversation != null && currentConversation.getId().equals(id)) {
                    currentConversation = null;
                }
            }
        }
        repository.saveConversations(conversations);
    }

    public void categorizeConversation(String id, String category) {
        Conversation target = findConversation(id);
        if (target != null) {
            target.setCategory(category);
            target.touch();
            repository.saveConversations(conversations);
        }
    }

    public void pinConversation(String id, boolean pinned) {
        Conversation target = findConversation(id);
        if (target != null) {
            target.setPinned(pinned);
            target.touch();
            repository.saveConversations(conversations);
        }
    }

    private Conversation findConversation(String id) {
        for (Conversation conversation : conversations) {
            if (conversation.getId().equals(id)) {
                return conversation;
            }
        }
        return null;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public interface SendMessageCallback {
        void onToken(String token);
        void onComplete(ChatMessage message);
        void onError(String error);
    }
}
