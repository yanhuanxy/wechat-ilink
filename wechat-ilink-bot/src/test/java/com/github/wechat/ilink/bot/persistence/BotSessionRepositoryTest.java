package com.github.wechat.ilink.bot.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class BotSessionRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseManager dbManager;
    private BotSessionRepository repo;

    @BeforeEach
    void setUp() {
        String dbPath = new File(tempDir, "bot.db").getAbsolutePath();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
        repo = new BotSessionRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void saveAndLoad_roundTrips() {
        repo.save(new BotSessionRecord("bot1", "tok-abc", "u-1", "b-1", "https://base", "cursor-9"));

        BotSessionRecord found = repo.load("bot1");

        assertNotNull(found);
        assertEquals("bot1", found.getName());
        assertEquals("tok-abc", found.getBotToken());
        assertEquals("u-1", found.getUserId());
        assertEquals("b-1", found.getBotId());
        assertEquals("https://base", found.getBaseUrl());
        assertEquals("cursor-9", found.getUpdatesCursor());
    }

    @Test
    void load_unknown_returnsNull() {
        assertNull(repo.load("missing"));
    }

    @Test
    void save_sameName_replacesExisting() {
        repo.save(new BotSessionRecord("bot1", "tok-1", "u-1", "b-1", "base", "c-1"));
        repo.save(new BotSessionRecord("bot1", "tok-2", "u-2", "b-2", "base2", "c-2"));

        BotSessionRecord found = repo.load("bot1");
        assertEquals("tok-2", found.getBotToken());
        assertEquals("c-2", found.getUpdatesCursor());
    }

    @Test
    void save_nullCursor_loadsAsNull() {
        repo.save(new BotSessionRecord("bot1", "tok", "u", "b", "base", null));

        assertNull(repo.load("bot1").getUpdatesCursor());
    }

    @Test
    void clear_removesRow() {
        repo.save(new BotSessionRecord("bot1", "tok", "u", "b", "base", "c"));

        repo.clear("bot1");

        assertNull(repo.load("bot1"));
    }

    @Test
    void clear_onlyAffectsNamedBot() {
        repo.save(new BotSessionRecord("bot1", "tok", "u", "b", "base", "c"));
        repo.save(new BotSessionRecord("bot2", "tok", "u", "b", "base", "c"));

        repo.clear("bot1");

        assertNull(repo.load("bot1"));
        assertNotNull(repo.load("bot2"));
    }
}
