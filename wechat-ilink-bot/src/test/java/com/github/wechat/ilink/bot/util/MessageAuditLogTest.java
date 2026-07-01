package com.github.wechat.ilink.bot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageAuditLogTest {

    @Test
    void sanitize_keepsAlnumAndDashUnderscore() {
        assertEquals("abc-123_XY", MessageAuditLog.sanitize("abc-123_XY"));
    }

    @Test
    void sanitize_replacesPathBreakingChars() {
        assertEquals("a_b_c", MessageAuditLog.sanitize("a/b:c"));
        assertEquals("u__id", MessageAuditLog.sanitize("u\\.id"));
    }

    @Test
    void sanitize_nullOrEmpty_returnsUnknown() {
        assertEquals("unknown", MessageAuditLog.sanitize(null));
        assertEquals("unknown", MessageAuditLog.sanitize(""));
    }

    @Test
    void inboundAndOutbound_doNotThrow() {
        assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                MessageAuditLog.inbound("user1", "你好");
                MessageAuditLog.outbound("user1", "text", "回复");
                MessageAuditLog.inbound(null, null);
            }
        });
    }
}
