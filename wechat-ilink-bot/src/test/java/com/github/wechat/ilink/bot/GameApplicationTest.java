package com.github.wechat.ilink.bot;

import com.github.wechat.ilink.sdk.core.exception.NotLoginException;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GameApplicationTest {

    @Test
    void isPermanentFailure_notLogin_returnsTrue() {
        GameApplication app = new GameApplication();
        assertTrue(app.isPermanentFailure(new NotLoginException("not logged in")));
    }

    @Test
    void isPermanentFailure_sessionExpired_returnsTrue() {
        GameApplication app = new GameApplication();
        assertTrue(app.isPermanentFailure(new SessionExpiredException("session expired")));
    }

    @Test
    void isPermanentFailure_ioException_returnsFalse() {
        GameApplication app = new GameApplication();
        assertFalse(app.isPermanentFailure(new IOException("network error")));
    }

    @Test
    void isPermanentFailure_runtimeException_returnsFalse() {
        GameApplication app = new GameApplication();
        assertFalse(app.isPermanentFailure(new RuntimeException("unexpected")));
    }
}
