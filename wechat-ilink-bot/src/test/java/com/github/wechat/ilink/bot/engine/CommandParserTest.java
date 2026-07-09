package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.CommandRegistry;
import com.github.wechat.ilink.bot.command.ParsedCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserTest {

    private CommandParser parser;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        registry.registerAlias("签到", "CHECKIN");
        registry.registerAlias("我的信息", "USER_INFO");
        registry.registerAlias("施肥", "FERTILIZE");
        registry.registerAlias("种植", "PLANT_ALL");
        registry.registerAlias("购买", "BUY_SEED");
        parser = new CommandParser(registry);
    }

    @Test
    void parse_exactAlias_returnsCommand() {
        ParsedCommand result = parser.parse("签到");
        assertEquals("CHECKIN", result.getName());
        assertEquals(0, result.getArgs().length);
    }

    @Test
    void parse_chineseAlias_returnsCommand() {
        ParsedCommand result = parser.parse("我的信息");
        assertEquals("USER_INFO", result.getName());
    }

    @Test
    void parse_spaceArg_extractsArgument() {
        ParsedCommand result = parser.parse("种植 小麦");
        assertEquals("PLANT_ALL", result.getName());
        assertEquals(1, result.getArgs().length);
        assertEquals("小麦", result.getArgs()[0]);
    }

    @Test
    void parse_buyWithArg() {
        ParsedCommand result = parser.parse("购买 小麦");
        assertEquals("BUY_SEED", result.getName());
        assertEquals("小麦", result.getArgs()[0]);
    }

    @Test
    void parse_unknownInput_returnsUnknown() {
        ParsedCommand result = parser.parse("乱七八糟");
        assertEquals("UNKNOWN", result.getName());
    }

    @Test
    void parse_nullInput_returnsUnknown() {
        ParsedCommand result = parser.parse(null);
        assertEquals("UNKNOWN", result.getName());
    }

    @Test
    void parse_emptyInput_returnsUnknown() {
        ParsedCommand result = parser.parse("");
        assertEquals("UNKNOWN", result.getName());
    }

    @Test
    void parse_whitespace_trimmed() {
        ParsedCommand result = parser.parse("  签到  ");
        assertEquals("CHECKIN", result.getName());
    }

    @Test
    void parse_trailingPunctuation_stripped() {
        ParsedCommand result = parser.parse("签到。");
        assertEquals("CHECKIN", result.getName());
    }

    @Test
    void parse_fullWidthSpace_normalized() {
        ParsedCommand result = parser.parse("种植　小麦");
        assertEquals("PLANT_ALL", result.getName());
        assertEquals("小麦", result.getArgs()[0]);
    }

    @Test
    void parse_trailingPunctuationInArg_stripped() {
        ParsedCommand result = parser.parse("购买 小麦。");
        assertEquals("BUY_SEED", result.getName());
        assertEquals("小麦", result.getArgs()[0]);
    }
}
