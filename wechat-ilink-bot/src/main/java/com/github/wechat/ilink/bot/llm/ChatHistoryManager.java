package com.github.wechat.ilink.bot.llm;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatHistoryManager {

    private final int maxHistory;
    private final ConcurrentHashMap<String, LinkedList<ChatMessage>> histories = new ConcurrentHashMap<String, LinkedList<ChatMessage>>();

    public ChatHistoryManager(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void addMessage(String userId, String role, String content) {
        histories.computeIfAbsent(userId, new java.util.function.Function<String, LinkedList<ChatMessage>>() {
            @Override
            public LinkedList<ChatMessage> apply(String k) {
                return new LinkedList<ChatMessage>();
            }
        }).addLast(new ChatMessage(role, content));

        LinkedList<ChatMessage> history = histories.get(userId);
        while (history.size() > maxHistory) {
            history.removeFirst();
        }
    }

    public List<ChatMessage> getHistory(String userId) {
        LinkedList<ChatMessage> history = histories.get(userId);
        if (history == null) {
            return new ArrayList<ChatMessage>();
        }
        return new ArrayList<ChatMessage>(history);
    }

    public void clear(String userId) {
        histories.remove(userId);
    }
}
