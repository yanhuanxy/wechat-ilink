package com.github.wechat.ilink.bot.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryManagerTest {

    private ChatHistoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new ChatHistoryManager(5);
    }

    @Test
    void addMessage_singleUser_storesMessage() {
        manager.addMessage("user1", "user", "你好");
        List<ChatMessage> history = manager.getHistory("user1");
        assertEquals(1, history.size());
        assertEquals("你好", history.get(0).getContent());
        assertEquals("user", history.get(0).getRole());
    }

    @Test
    void addMessage_multipleUsers_separatesHistories() {
        manager.addMessage("user1", "user", "A");
        manager.addMessage("user2", "user", "B");

        assertEquals(1, manager.getHistory("user1").size());
        assertEquals(1, manager.getHistory("user2").size());
        assertEquals("A", manager.getHistory("user1").get(0).getContent());
        assertEquals("B", manager.getHistory("user2").get(0).getContent());
    }

    @Test
    void addMessage_exceedsMaxHistory_trimsOldest() {
        manager.addMessage("user1", "user", "msg1");
        manager.addMessage("user1", "assistant", "reply1");
        manager.addMessage("user1", "user", "msg2");
        manager.addMessage("user1", "assistant", "reply2");
        manager.addMessage("user1", "user", "msg3");
        manager.addMessage("user1", "assistant", "reply3");

        List<ChatMessage> history = manager.getHistory("user1");
        assertEquals(5, history.size());
        assertEquals("reply1", history.get(0).getContent());
        assertEquals("reply3", history.get(4).getContent());
    }

    @Test
    void getHistory_unknownUser_returnsEmptyList() {
        List<ChatMessage> history = manager.getHistory("unknown");
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void clear_removesUserHistory() {
        manager.addMessage("user1", "user", "hello");
        manager.clear("user1");
        assertTrue(manager.getHistory("user1").isEmpty());
    }
}
