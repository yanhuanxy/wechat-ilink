package com.github.wechat.ilink.bot.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {

    @Test
    void menu_formatsCorrectly() {
        List<String> items = Arrays.asList("我的信息", "查看农场");
        String result = TextFormatter.menu("测试菜单", items);
        assertTrue(result.contains("测试菜单"));
        assertTrue(result.contains("我的信息"));
        assertTrue(result.contains("查看农场"));
    }

    @Test
    void grid_formatsCorrectly() {
        List<String> cells = Arrays.asList("小麦", "玉米", "空地", "空地", "番茄", "空地");
        String result = TextFormatter.grid("农场", cells, 3);
        assertTrue(result.contains("农场"));
        assertTrue(result.contains("小麦"));
        assertTrue(result.contains("番茄"));
    }

    @Test
    void info_formatsMap() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("金币", "500");
        fields.put("等级", "1");
        String result = TextFormatter.info(fields);
        assertTrue(result.contains("金币: 500"));
        assertTrue(result.contains("等级: 1"));
    }

    @Test
    void box_formatsWithTitle() {
        String result = TextFormatter.box("标题", "内容");
        assertTrue(result.contains("标题"));
        assertTrue(result.contains("内容"));
    }
}
