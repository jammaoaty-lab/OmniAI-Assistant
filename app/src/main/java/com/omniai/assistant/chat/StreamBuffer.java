package com.omniai.assistant.chat;

import java.util.ArrayList;
import java.util.List;

public class StreamBuffer {

    private final StringBuilder buffer;
    private volatile boolean isStreaming;
    private final List<StreamListener> listeners;

    public StreamBuffer() {
        this.buffer = new StringBuilder();
        this.isStreaming = false;
        this.listeners = new ArrayList<>();
    }

    public synchronized void append(String token) {
        if (!isStreaming) return;
        buffer.append(token);
        List<StreamListener> snapshot = new ArrayList<>(listeners);
        for (StreamListener listener : snapshot) {
            listener.onToken(token);
        }
    }

    public synchronized String getContent() {
        return buffer.toString();
    }

    public synchronized void clear() {
        buffer.setLength(0);
    }

    public void startStream() {
        isStreaming = true;
        clear();
    }

    public void endStream() {
        isStreaming = false;
        String content = getContent();
        List<StreamListener> snapshot = new ArrayList<>(listeners);
        for (StreamListener listener : snapshot) {
            listener.onStreamEnd(content);
        }
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public void addListener(StreamListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(StreamListener listener) {
        listeners.remove(listener);
    }

    public interface StreamListener {
        void onToken(String token);
        void onStreamEnd(String fullContent);
    }
}
