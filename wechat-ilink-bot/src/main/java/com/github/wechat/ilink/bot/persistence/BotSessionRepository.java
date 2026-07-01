package com.github.wechat.ilink.bot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * bot 登录会话持久化仓库。服务重启后据此免扫码恢复连接。
 *
 * <p>纯 JDBC 实现，不依赖任何 SDK 类型。</p>
 */
public class BotSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(BotSessionRepository.class);

    private final DatabaseManager dbManager;

    public BotSessionRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /** 保存（upsert）一条会话记录。botToken 仅入库，绝不写日志。 */
    public void save(BotSessionRecord rec) {
        String sql = "INSERT OR REPLACE INTO bot_session " +
                "(name, bot_token, user_id, bot_id, base_url, updates_cursor, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, rec.getName());
            ps.setString(2, rec.getBotToken());
            ps.setString(3, rec.getUserId());
            ps.setString(4, rec.getBotId());
            ps.setString(5, rec.getBaseUrl());
            ps.setString(6, rec.getUpdatesCursor());
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存 bot 会话失败: name={}", rec.getName(), e);
        }
    }

    /** 按 bot 名加载会话记录，无则返回 null。 */
    public BotSessionRecord load(String name) {
        String sql = "SELECT name, bot_token, user_id, bot_id, base_url, updates_cursor " +
                "FROM bot_session WHERE name = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            log.error("加载 bot 会话失败: name={}", name, e);
            return null;
        }
    }

    /** 删除指定 bot 的会话记录（token 失效或登出时调用）。 */
    public void clear(String name) {
        String sql = "DELETE FROM bot_session WHERE name = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("清除 bot 会话失败: name={}", name, e);
        }
    }

    private static BotSessionRecord map(ResultSet rs) throws SQLException {
        return new BotSessionRecord(
                rs.getString("name"),
                rs.getString("bot_token"),
                rs.getString("user_id"),
                rs.getString("bot_id"),
                rs.getString("base_url"),
                rs.getString("updates_cursor"));
    }
}
