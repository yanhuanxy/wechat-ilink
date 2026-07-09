package com.github.wechat.ilink.bot.engine;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandRegistry;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.command.ParsedCommand;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GameEngine {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    private final CommandParser commandParser;
    private final SessionManager sessionManager;
    private final CommandRegistry registry;

    public GameEngine(CommandParser commandParser, SessionManager sessionManager, CommandRegistry registry) {
        this.commandParser = commandParser;
        this.sessionManager = sessionManager;
        this.registry = registry;
    }

    public CommandResult dispatch(String userId, String rawText) {
        // 用 SessionManager 的每用户锁，与 FlushGate 异步 flush 共用，保证落盘读到一致快照
        synchronized (sessionManager.lockFor(userId)) {
            return doProcess(userId, rawText);
        }
    }

    private CommandResult doProcess(String userId, String rawText) {
        long start = System.currentTimeMillis();
        try {
            ParsedCommand parsed = commandParser.parse(rawText);
            PlayerSession session = sessionManager.getOrCreate(userId);
            session.touchActivity();

            Command command = registry.find(parsed.getName());
            if (command == null) {
                return unknownCommandResult(rawText);
            }

            CommandResult result = command.execute(session, parsed.getArgs());

            if (result.isSuccess()) {
                sessionManager.scheduleFlush(userId);
            }

            return result;
        } catch (Exception e) {
            log.error("命令处理出错, userId={}", userId, e);
            return CommandResult.error("出了点问题，请稍后再试");
        } finally {
            log.debug("dispatch completed, userId={}, text={}, cost={}ms",
                    userId, rawText, System.currentTimeMillis() - start);
        }
    }

    /** 未知命令时，基于首个词做近似匹配，给"你是不是想输入 X"提示；无近似则回通用文案。 */
    private CommandResult unknownCommandResult(String rawText) {
        String firstToken = rawText.trim();
        int sp = firstToken.indexOf(' ');
        if (sp > 0) {
            firstToken = firstToken.substring(0, sp);
        }
        List<String> suggestions = registry.findSimilar(firstToken);
        if (suggestions.isEmpty()) {
            return CommandResult.error("未知命令，输入'帮助'查看可用命令");
        }
        return CommandResult.error("未知命令，你是不是想输入：" + String.join(" / ", suggestions) + "？输入'帮助'查看全部");
    }
}
