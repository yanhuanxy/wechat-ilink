package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseRendererTest {

    private final ResponseRenderer renderer = new ResponseRenderer();

    @Test
    void render_success_returnsMessage() {
        CommandResult result = CommandResult.success("hello");
        assertEquals("hello", renderer.render(result));
    }

    @Test
    void render_error_prefixWithErrorEmoji() {
        CommandResult result = CommandResult.error("出错了");
        assertEquals("❌ 出错了", renderer.render(result));
    }

    @Test
    void render_null_returnsEmpty() {
        assertEquals("", renderer.render(null));
    }
}
