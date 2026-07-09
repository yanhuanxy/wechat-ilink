package com.github.wechat.ilink.bot.command;

import com.github.wechat.ilink.bot.session.PlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    void register_andFind_returnsCommand() {
        Command cmd = new StubCommand("TEST");
        registry.register(cmd);
        assertEquals(cmd, registry.find("TEST"));
    }

    @Test
    void find_unknown_returnsNull() {
        assertNull(registry.find("NONEXISTENT"));
    }

    @Test
    void resolveAlias_exactMatch_returnsName() {
        Command cmd = new StubCommand("CHECKIN");
        registry.register(cmd);
        assertEquals("CHECKIN", registry.resolveAlias("CHECKIN"));
    }

    @Test
    void resolveAlias_alias_returnsMappedName() {
        registry.register(new StubCommand("CHECKIN"));
        registry.registerAlias("签到", "CHECKIN");
        assertEquals("CHECKIN", registry.resolveAlias("签到"));
    }

    @Test
    void resolveAlias_unknown_returnsNull() {
        assertNull(registry.resolveAlias("不存在"));
    }

    @Test
    void allCommands_returnsAllRegistered() {
        registry.register(new StubCommand("A"));
        registry.register(new StubCommand("B"));
        assertEquals(2, registry.allCommands().size());
    }

    @Test
    void findSimilar_typo_returnsClosestAlias() {
        registry.register(new StubCommand("CHECKIN"));
        registry.registerAlias("签到", "CHECKIN");
        List<String> similar = registry.findSimilar("签诛");
        assertFalse(similar.isEmpty());
        assertTrue(similar.contains("签到"));
    }

    @Test
    void findSimilar_noMatch_returnsEmpty() {
        registry.register(new StubCommand("CHECKIN"));
        registry.registerAlias("签到", "CHECKIN");
        assertTrue(registry.findSimilar("完全无关的xyz").isEmpty());
    }

    @Test
    void findSimilar_emptyInput_returnsEmpty() {
        assertTrue(registry.findSimilar("").isEmpty());
        assertTrue(registry.findSimilar(null).isEmpty());
    }

    private static class StubCommand implements Command {
        private final String name;
        StubCommand(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return name; }
        @Override public CommandResult execute(PlayerSession session, String[] args) {
            return CommandResult.success(name);
        }
    }
}
