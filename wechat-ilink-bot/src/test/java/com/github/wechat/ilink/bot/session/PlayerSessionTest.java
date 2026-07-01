package com.github.wechat.ilink.bot.session;

import com.github.wechat.ilink.bot.farm.model.CropStage;
import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionTest {

    @Test
    void constructor_initializesDefaults() {
        PlayerSession session = new PlayerSession("user1");
        assertEquals("user1", session.getUserId());
        assertEquals(500, session.getGold());
        assertEquals(0, session.getExp());
        assertEquals(1, session.getLevel());
        assertEquals(4, session.getMaxPlots());
        assertEquals(36, session.getPlots().size());
        assertTrue(session.isDirty());
    }

    @Test
    void spendGold_sufficient_subtracts() {
        PlayerSession session = new PlayerSession("u");
        assertTrue(session.spendGold(100));
        assertEquals(400, session.getGold());
    }

    @Test
    void spendGold_insufficient_returnsFalse() {
        PlayerSession session = new PlayerSession("u");
        assertFalse(session.spendGold(9999));
        assertEquals(500, session.getGold());
    }

    @Test
    void addGold_increasesBalance() {
        PlayerSession session = new PlayerSession("u");
        session.addGold(100);
        assertEquals(600, session.getGold());
    }

    @Test
    void addExp_levelsUp() {
        PlayerSession session = new PlayerSession("u");
        session.addExp(100);
        assertEquals(2, session.getLevel());
        assertEquals(0, session.getExp());
    }

    @Test
    void addExp_levelUpUnlocksPlots() {
        PlayerSession session = new PlayerSession("u");
        session.setExp(200);
        session.addExp(100);
        assertEquals(3, session.getLevel());
        assertTrue(session.getMaxPlots() > 4);
    }

    @Test
    void getActivePlots_returnsMaxPlotsCount() {
        PlayerSession session = new PlayerSession("u");
        assertEquals(4, session.getActivePlots().size());
    }

    @Test
    void getPlot_validIndex_returnsPlot() {
        PlayerSession session = new PlayerSession("u");
        assertNotNull(session.getPlot(0));
        assertEquals(0, session.getPlot(0).getIndex());
    }

    @Test
    void getPlot_invalidIndex_returnsNull() {
        PlayerSession session = new PlayerSession("u");
        assertNull(session.getPlot(-1));
        assertNull(session.getPlot(99));
    }

    @Test
    void clearDirty_resetsFlag() {
        PlayerSession session = new PlayerSession("u");
        session.clearDirty();
        assertFalse(session.isDirty());
    }

    @Test
    void touchActivity_updatesTimestamp() {
        PlayerSession session = new PlayerSession("u");
        long before = session.getLastActivity();
        session.touchActivity();
        assertTrue(session.getLastActivity() >= before);
    }

    @Test
    void claudePrivileged_defaultsFalse_andSettable() {
        PlayerSession session = new PlayerSession("u");
        assertFalse(session.isClaudePrivileged(), "默认受限（未提权）");

        session.setClaudePrivileged(true);
        assertTrue(session.isClaudePrivileged());

        session.setClaudePrivileged(false);
        assertFalse(session.isClaudePrivileged());
    }

    @Test
    void claudePlanMode_defaultsFalse_andSettable() {
        PlayerSession session = new PlayerSession("u");
        assertFalse(session.isClaudePlanMode());

        session.setClaudePlanMode(true);
        assertTrue(session.isClaudePlanMode());

        session.setClaudePlanMode(false);
        assertFalse(session.isClaudePlanMode());
    }

    @Test
    void claudeApprovedExec_defaultsFalse_andSettable() {
        PlayerSession session = new PlayerSession("u");
        assertFalse(session.isClaudeApprovedExec());

        session.setClaudeApprovedExec(true);
        assertTrue(session.isClaudeApprovedExec());

        session.setClaudeApprovedExec(false);
        assertFalse(session.isClaudeApprovedExec());
    }

    @Test
    void setActiveClaudeSessionId_changedId_clearsApprovedExec() {
        PlayerSession session = new PlayerSession("u");
        session.setActiveClaudeSessionId("sid-a");
        session.setClaudeApprovedExec(true);

        session.setActiveClaudeSessionId("sid-b");
        assertFalse(session.isClaudeApprovedExec(), "切会话应清除 approved 标志");
    }

    @Test
    void setActiveClaudeSessionId_sameId_keepsApprovedExec() {
        PlayerSession session = new PlayerSession("u");
        session.setActiveClaudeSessionId("sid-a");
        session.setClaudeApprovedExec(true);

        session.setActiveClaudeSessionId("sid-a");
        assertTrue(session.isClaudeApprovedExec(), "同 id 续传不应清 approved");
    }

    @Test
    void claudeTurnCount_defaultsZero_incrementAndReset() {
        PlayerSession session = new PlayerSession("u");
        assertEquals(0, session.getClaudeTurnCount());

        assertEquals(1, session.incrementClaudeTurn());
        assertEquals(2, session.incrementClaudeTurn());
        assertEquals(2, session.getClaudeTurnCount());

        session.resetClaudeTurnCount();
        assertEquals(0, session.getClaudeTurnCount());
    }

    @Test
    void setActiveClaudeSessionId_changedId_resetsTurnCount() {
        PlayerSession session = new PlayerSession("u");
        session.setActiveClaudeSessionId("sid-a");
        session.incrementClaudeTurn();
        session.incrementClaudeTurn();
        assertEquals(2, session.getClaudeTurnCount());

        session.setActiveClaudeSessionId("sid-b");
        assertEquals(0, session.getClaudeTurnCount(), "切换到不同会话应归零");
    }

    @Test
    void setActiveClaudeSessionId_sameId_keepsTurnCount() {
        PlayerSession session = new PlayerSession("u");
        session.setActiveClaudeSessionId("sid-a");
        session.incrementClaudeTurn();
        session.incrementClaudeTurn();

        session.setActiveClaudeSessionId("sid-a");
        assertEquals(2, session.getClaudeTurnCount(), "同 id 续传不应归零");
    }

    @Test
    void setActiveClaudeSessionId_clearToNull_resetsTurnCount() {
        PlayerSession session = new PlayerSession("u");
        session.setActiveClaudeSessionId("sid-a");
        session.incrementClaudeTurn();

        session.setActiveClaudeSessionId(null);
        assertEquals(0, session.getClaudeTurnCount(), "/new 清空会话应归零");
    }
}
