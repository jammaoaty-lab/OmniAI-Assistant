package com.omniai.assistant.model;

public class ChatMessage {

    private long id;
    private String conversationId;
    private String content;
    private boolean isUser;
    private long timestamp;
    private String messageType;
    private String attachmentPath;
    private int tokenCount;

    public ChatMessage() {
        this.messageType = "text";
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(long id, String conversationId, String content, boolean isUser, long timestamp, String messageType, String attachmentPath, int tokenCount) {
        this.id = id;
        this.conversationId = conversationId;
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.attachmentPath = attachmentPath;
        this.tokenCount = tokenCount;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public boolean isText() {
        return "text".equals(messageType);
    }

    public boolean isImage() {
        return "image".equals(messageType);
    }

    public boolean isVoice() {
        return "voice".equals(messageType);
    }

    public boolean isDocument() {
        return "document".equals(messageType);
    }
}
