package com.github.wechat.ilink.bot.session;

import com.github.wechat.ilink.bot.persistence.DatabaseManager;
import com.github.wechat.ilink.bot.persistence.FarmPlotRepository;
import com.github.wechat.ilink.bot.persistence.InventoryRepository;
import com.github.wechat.ilink.bot.persistence.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<String, PlayerSession> sessions = new ConcurrentHashMap<String, PlayerSession>();
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<String, Object>();
    private final PlayerRepository playerRepo;
    private final FarmPlotRepository plotRepo;
    private final InventoryRepository inventoryRepo;
    private final FlushGate flushGate;

    public SessionManager(DatabaseManager dbManager) {
        this(dbManager, 0L, 0L);
    }

    /** 全量构造器：启用 FlushGate（同步立即刷 + 周期兜底）。delay<=0 为同步，>0 为 debounce 合并。 */
    public SessionManager(DatabaseManager dbManager, long flushDelayMs, long flushIntervalMs) {
        this.playerRepo = new PlayerRepository(dbManager);
        this.plotRepo = new FarmPlotRepository(dbManager);
        this.inventoryRepo = new InventoryRepository(dbManager);
        if (flushDelayMs > 0 || flushIntervalMs > 0) {
            this.flushGate = new FlushGate(this, flushDelayMs, flushIntervalMs);
            this.flushGate.start();
        } else {
            this.flushGate = null;
        }
    }

    public PlayerSession getOrCreate(String userId) {
        PlayerSession cached = sessions.get(userId);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.getLastActivity() > SESSION_TIMEOUT_MS) {
                sessions.remove(userId);
            } else {
                return cached;
            }
        }

        return sessions.computeIfAbsent(userId, this::loadFromDb);
    }

    private PlayerSession loadFromDb(String userId) {
        PlayerSession session = playerRepo.findById(userId);
        if (session == null) {
            session = new PlayerSession(userId);
            playerRepo.insert(session);
            log.info("创建新玩家: userId={}", userId);
        } else {
            List<com.github.wechat.ilink.bot.farm.model.FarmPlot> plots = plotRepo.findByUserId(userId);
            if (!plots.isEmpty()) {
                session.setPlots(plots);
            }
            com.github.wechat.ilink.bot.farm.model.Inventory inventory = inventoryRepo.findByUserId(userId);
            session.setInventory(inventory);
            session.clearDirty();
            log.debug("从数据库加载玩家: userId={}", userId);
        }
        return session;
    }

    public void saveSession(PlayerSession session) {
        if (!session.isDirty()) {
            return;
        }
        playerRepo.update(session);
        plotRepo.replaceByUserId(session.getUserId(), session.getPlots());
        inventoryRepo.replaceByUserId(session.getUserId(), session.getInventory());
        session.clearDirty();
    }

    public void flushAll() {
        for (PlayerSession session : sessions.values()) {
            saveSession(session);
        }
        log.info("所有会话已刷盘, count={}", sessions.size());
    }

    /** 每用户锁对象（GameEngine 命令执行与 FlushGate 异步 flush 共用，保证一致快照）。 */
    public Object lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, new java.util.function.Function<String, Object>() {
            @Override
            public Object apply(String k) {
                return new Object();
            }
        });
    }

    public void withLock(String userId, Runnable task) {
        synchronized (lockFor(userId)) {
            task.run();
        }
    }

    /** 在用户锁内立即刷盘该用户（仅 dirty 写）。 */
    public void flushNow(final String userId) {
        withLock(userId, new Runnable() {
            @Override
            public void run() {
                saveSession(getOrCreate(userId));
            }
        });
    }

    /** 在各自用户锁内刷盘所有 dirty 会话（周期兜底 / shutdown）。 */
    public void flushAllDirty() {
        for (String userId : sessions.keySet()) {
            flushNow(userId);
        }
    }

    /** 命令/模式变更后经 FlushGate 刷盘（gate 为 null 时同步立即刷）。 */
    public void scheduleFlush(String userId) {
        if (flushGate != null) {
            flushGate.scheduleFlush(userId);
        } else {
            flushNow(userId);
        }
    }

    /** 关闭：先全量刷盘，再停 FlushGate 调度线程。 */
    public void shutdown() {
        if (flushGate != null) {
            flushGate.flushAllNow();
            flushGate.shutdown();
        } else {
            flushAll();
        }
    }

    public void remove(String userId) {
        sessions.remove(userId);
    }
}
