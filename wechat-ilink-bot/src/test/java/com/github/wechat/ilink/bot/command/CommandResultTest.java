package com.github.wechat.ilink.bot.command;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandResultTest {

    @Test
    void success_noData() {
        CommandResult result = CommandResult.success("ok");
        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void success_withData() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("count", 5);
        CommandResult result = CommandResult.success("done", data);
        assertTrue(result.isSuccess());
        assertEquals(5, result.getData().get("count"));
    }

    @Test
    void error_message() {
        CommandResult result = CommandResult.error("出错了");
        assertFalse(result.isSuccess());
        assertEquals("出错了", result.getMessage());
    }

    @Test
    void parsedCommand_getters() {
        ParsedCommand cmd = new ParsedCommand("TEST", new String[]{"a", "b"});
        assertEquals("TEST", cmd.getName());
        assertArrayEquals(new String[]{"a", "b"}, cmd.getArgs());
    }

    @Test
    void parsedCommand_nullArgs_becomesEmpty() {
        ParsedCommand cmd = new ParsedCommand("TEST", null);
        assertEquals(0, cmd.getArgs().length);
    }
}
