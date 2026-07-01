package com.github.wechat.ilink.bot.persistence;

import com.github.wechat.ilink.bot.farm.model.FarmPlot;
import com.github.wechat.ilink.bot.farm.model.Inventory;
import com.github.wechat.ilink.bot.mode.BotModeType;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);
    private final DatabaseManager dbManager;

    public PlayerRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public PlayerSession findById(String userId) {
        String sql = "SELECT * FROM player WHERE user_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerSession session = new PlayerSession(userId);
                session.setGold(rs.getInt("gold"));
                session.setExp(rs.getInt("exp"));
                session.setLevel(rs.getInt("level"));
                session.setMaxPlots(rs.getInt("max_plots"));
                session.setCoupon(rs.getInt("coupon"));
                session.setLastCheckin(rs.getString("last_checkin"));
                session.setCheckinStreak(rs.getInt("checkin_streak"));
                BotModeType mode = readBotMode(rs);
                session.setCurrentMode(mode);
                session.clearDirty();
                return session;
            }
            return null;
        } catch (SQLException e) {
            log.error("查询玩家失败: userId={}", userId, e);
            return null;
        }
    }

    public void insert(PlayerSession session) {
        String sql = "INSERT INTO player (user_id, gold, exp, level, max_plots, coupon, bot_mode, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            String now = now();
            ps.setString(1, session.getUserId());
            ps.setInt(2, session.getGold());
            ps.setInt(3, session.getExp());
            ps.setInt(4, session.getLevel());
            ps.setInt(5, session.getMaxPlots());
            ps.setInt(6, session.getCoupon());
            ps.setString(7, session.getCurrentMode().name());
            ps.setString(8, now);
            ps.setString(9, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("插入玩家失败: userId={}", session.getUserId(), e);
        }
    }

    public void update(PlayerSession session) {
        String sql = "UPDATE player SET gold=?, exp=?, level=?, max_plots=?, coupon=?, last_checkin=?, checkin_streak=?, bot_mode=?, updated_at=? WHERE user_id=?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, session.getGold());
            ps.setInt(2, session.getExp());
            ps.setInt(3, session.getLevel());
            ps.setInt(4, session.getMaxPlots());
            ps.setInt(5, session.getCoupon());
            ps.setString(6, session.getLastCheckin());
            ps.setInt(7, session.getCheckinStreak());
            ps.setString(8, session.getCurrentMode().name());
            ps.setString(9, now());
            ps.setString(10, session.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("更新玩家失败: userId={}", session.getUserId(), e);
        }
    }

    private static BotModeType readBotMode(ResultSet rs) throws SQLException {
        String raw = rs.getString("bot_mode");
        if (raw == null || raw.isEmpty()) {
            return BotModeType.defaultMode();
        }
        BotModeType parsed = BotModeType.fromName(raw);
        return parsed == null ? BotModeType.defaultMode() : parsed;
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }
}
