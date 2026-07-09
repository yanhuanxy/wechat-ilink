package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandRegistry;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.persistence.DatabaseManager;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

    private GameEngine engine;
    private CommandRegistry registry;
    private SessionManager sessionManager;
    private DatabaseManager dbManager;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();

        sessionManager = new SessionManager(dbManager);
        registry = new CommandRegistry();

        registry.register(new Command() {
            @Override
            public String name() { return "TEST_CMD"; }
            @Override
            public String description() { return "test"; }
            @Override
            public CommandResult execute(PlayerSession session, String[] args) {
                return CommandResult.success("ok");
            }
        });
        registry.registerAlias("测试", "TEST_CMD");

        CommandParser parser = new CommandParser(registry);
        engine = new GameEngine(parser, sessionManager, registry);
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    void dispatch_validCommand_returnsSuccess() {
        CommandResult result = engine.dispatch("user1", "测试");
        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
    }

    @Test
    void dispatch_unknownCommand_returnsError() {
        CommandResult result = engine.dispatch("user1", "不存在的命令");
        assertFalse(result.isSuccess());
    }

    @Test
    void dispatch_createsSessionForNewUser() {
        engine.dispatch("newUser", "测试");
        PlayerSession session = sessionManager.getOrCreate("newUser");
        assertNotNull(session);
        assertEquals("newUser", session.getUserId());
    }

    @Test
    void dispatch_sameUserSequential_threadSafe() {
        CommandResult r1 = engine.dispatch("user1", "测试");
        CommandResult r2 = engine.dispatch("user1", "测试");
        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
    }

    @Test
    void dispatch_differentUsers_parallelProcessing() throws InterruptedException {
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                engine.dispatch("userA", "测试");
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                engine.dispatch("userB", "测试");
            }
        });
        t1.start();
        t2.start();
        t1.join(2000);
        t2.join(2000);

        assertNotNull(sessionManager.getOrCreate("userA"));
        assertNotNull(sessionManager.getOrCreate("userB"));
    }

    @Test
    void dispatch_unknownCloseTypo_returnsSuggestion() {
        CommandResult result = engine.dispatch("user1", "测试x");
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("你是不是想输入"));
        assertTrue(result.getMessage().contains("测试"));
    }
}
