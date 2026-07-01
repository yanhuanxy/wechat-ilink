package com.github.wechat.ilink.bot.persistence;

import com.github.wechat.ilink.bot.mode.claude.ClaudeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClaudeSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(ClaudeSessionRepository.class);

    private final DatabaseManager dbManager;

    public ClaudeSessionRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void insert(ClaudeSession session) {
        String sql = "INSERT OR REPLACE INTO claude_sessions " +
                "(session_id, user_id, cwd, model, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getUserId());
            ps.setString(3, session.getCwd());
            ps.setString(4, session.getModel());
            ps.setString(5, session.getTitle());
            ps.setLong(6, session.getCreatedAt());
            ps.setLong(7, session.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("插入 Claude 会话失败: sessionId={}", session.getSessionId(), e);
        }
    }

    public void touchUpdatedAt(String sessionId, long timestamp) {
        String sql = "UPDATE claude_sessions SET updated_at = ? WHERE session_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setLong(1, timestamp);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("更新 Claude 会话时间失败: sessionId={}", sessionId, e);
        }
    }

    public ClaudeSession findById(String sessionId) {
        String sql = "SELECT * FROM claude_sessions WHERE session_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            log.error("查询 Claude 会话失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    public List<ClaudeSession> findByUserIdOrderByUpdatedDesc(String userId, int limit) {
        String sql = "SELECT * FROM claude_sessions WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?";
        List<ClaudeSession> result = new ArrayList<ClaudeSession>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            log.error("查询用户 Claude 会话列表失败: userId={}", userId, e);
        }
        return result;
    }

    public void deleteByUserIdAndSessionId(String userId, String sessionId) {
        String sql = "DELETE FROM claude_sessions WHERE user_id = ? AND session_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("删除 Claude 会话失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    private static ClaudeSession map(ResultSet rs) throws SQLException {
        return new ClaudeSession(
                rs.getString("session_id"),
                rs.getString("user_id"),
                rs.getString("cwd"),
                rs.getString("model"),
                rs.getString("title"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }
}
