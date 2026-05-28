package com.omniai.assistant.common;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {

    private static volatile EventBus defaultInstance;

    private final Handler handler;
    private final Map<Class<?>, List<Consumer<?>>> subscribers;

    public EventBus() {
        this.handler = new Handler(Looper.getMainLooper());
        this.subscribers = new HashMap<>();
    }

    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    public <T> void subscribe(Class<T> eventType, Consumer<T> consumer) {
        synchronized (subscribers) {
            List<Consumer<?>> list = subscribers.get(eventType);
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
                subscribers.put(eventType, list);
            }
            list.add(consumer);
        }
    }

    public <T> void unsubscribe(Class<T> eventType, Consumer<T> consumer) {
        synchronized (subscribers) {
            List<Consumer<?>> list = subscribers.get(eventType);
            if (list != null) {
                list.remove(consumer);
                if (list.isEmpty()) {
                    subscribers.remove(eventType);
                }
            }
        }
    }

    public <T> void post(T event) {
        Class<?> eventType = event.getClass();
        List<Consumer<?>> list;
        synchronized (subscribers) {
            list = subscribers.get(eventType);
            if (list == null || list.isEmpty()) {
                return;
            }
            list = new ArrayList<>(list);
        }
        for (Consumer<?> consumer : list) {
            @SuppressWarnings("unchecked")
            Consumer<T> typed = (Consumer<T>) consumer;
            handler.post(() -> typed.accept(event));
        }
    }

    public <T> void postSticky(T event) {
        post(event);
    }

    public void clear() {
        synchronized (subscribers) {
            subscribers.clear();
        }
    }

    public boolean hasSubscribers(Class<?> eventType) {
        synchronized (subscribers) {
            List<Consumer<?>> list = subscribers.get(eventType);
            return list != null && !list.isEmpty();
        }
    }
}
