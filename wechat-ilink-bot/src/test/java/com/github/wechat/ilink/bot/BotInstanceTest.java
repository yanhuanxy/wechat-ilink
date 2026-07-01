package com.github.wechat.ilink.bot;

import com.github.wechat.ilink.bot.command.QrCodeProvider;
import com.github.wechat.ilink.bot.llm.LlmRequestQueue;
import com.github.wechat.ilink.bot.persistence.BotSessionRecord;
import com.github.wechat.ilink.bot.persistence.DatabaseManager;
import com.github.wechat.ilink.bot.session.SessionManager;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class BotInstanceTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private SessionManager sessionManager;
    private QrCodeProvider stubProvider;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
        sessionManager = new SessionManager(dbManager);
        stubProvider = new QrCodeProvider() {
            @Override
            public String getQrCodeUrl() {
                return "https://example.com/qr";
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    void create_buildsAllComponents() {
        BotConfig config = new BotConfig("test-bot", "test-tag");

        BotInstance instance = BotInstance.create(config, dbManager, sessionManager,
                null, null, new LlmRequestQueue(3, 50), false, 5000, stubProvider, null, null);

        assertNotNull(instance);
        assertEquals("test-bot", instance.getName());
    }

    @Test
    void create_nullRouteTag_buildsSuccessfully() {
        BotConfig config = new BotConfig("test-bot", null);

        BotInstance instance = BotInstance.create(config, dbManager, sessionManager,
                null, null, new LlmRequestQueue(3, 50), false, 5000, stubProvider, null, null);

        assertNotNull(instance);
    }

    @Test
    void shutdown_closesClient() {
        BotConfig config = new BotConfig("test-bot", null);
        BotInstance instance = BotInstance.create(config, dbManager, sessionManager,
                null, null, new LlmRequestQueue(3, 50), false, 5000, stubProvider, null, null);

        instance.shutdown();
    }

    @Test
    void isAlive_beforeLogin_returnsFalse() {
        BotConfig config = new BotConfig("test-bot", null);
        BotInstance instance = BotInstance.create(config, dbManager, sessionManager,
                null, null, new LlmRequestQueue(3, 50), false, 5000, stubProvider, null, null);

        assertFalse(instance.isAlive());
    }

    @Test
    void toRecord_copiesLoginContextFields() {
        LoginContext ctx = new LoginContext("tok", "u-1", "b-1", "https://base");

        BotSessionRecord rec = BotInstance.toRecord("bot1", ctx, "cursor-3");

        assertEquals("bot1", rec.getName());
        assertEquals("tok", rec.getBotToken());
        assertEquals("u-1", rec.getUserId());
        assertEquals("b-1", rec.getBotId());
        assertEquals("https://base", rec.getBaseUrl());
        assertEquals("cursor-3", rec.getUpdatesCursor());
    }

    @Test
    void toResumeContext_rebuildsLoginContextAndCursor() {
        BotSessionRecord rec = new BotSessionRecord("bot1", "tok", "u-1", "b-1", "https://base", "cursor-3");

        ResumeContext rc = BotInstance.toResumeContext(rec);

        assertNotNull(rc.getLoginContext());
        assertEquals("tok", rc.getLoginContext().getBotToken());
        assertEquals("u-1", rc.getLoginContext().getUserId());
        assertEquals("b-1", rc.getLoginContext().getBotId());
        assertEquals("cursor-3", rc.getUpdatesCursor());
    }

    @Test
    void dynamicBotResult_holdsInstanceAndUrl() {
        BotInstance instance = new BotInstance("test", null, null, null, null, null);
        BotInstance.DynamicBotResult result = new BotInstance.DynamicBotResult(instance, "https://qr.example.com");

        assertEquals(instance, result.getInstance());
        assertEquals("https://qr.example.com", result.getQrCodeUrl());
    }
}
