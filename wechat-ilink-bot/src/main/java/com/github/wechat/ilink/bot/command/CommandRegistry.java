package com.github.wechat.ilink.bot.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public void register(Command command) {
        commands.put(command.name(), command);
    }

    public void registerAlias(String alias, String commandName) {
        aliases.put(alias, commandName);
    }

    public Command find(String name) {
        return commands.get(name);
    }

    public String resolveAlias(String text) {
        if (commands.containsKey(text)) {
            return text;
        }
        return aliases.get(text);
    }

    public Set<Command> allCommands() {
        return Collections.unmodifiableSet(new HashSet<>(commands.values()));
    }

    /**
     * 找出与输入最接近的命令名/别名（编辑距离），用于未知命令时给"你是不是想输入 X"提示。
     * 返回最多 2 个；阈值 max(1, 输入长度/3)，避免给毫无关联的建议。
     */
    public List<String> findSimilar(String input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = input.toLowerCase();
        int threshold = Math.max(1, input.length() / 3);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        String second = null;
        int secondDist = Integer.MAX_VALUE;
        for (String candidate : allCandidateTexts()) {
            int d = levenshtein(normalized, candidate.toLowerCase());
            if (d > threshold) {
                continue;
            }
            if (d < bestDist) {
                second = best;
                secondDist = bestDist;
                best = candidate;
                bestDist = d;
            } else if (d < secondDist) {
                second = candidate;
                secondDist = d;
            }
        }
        List<String> result = new ArrayList<String>();
        if (best != null) {
            result.add(best);
        }
        if (second != null) {
            result.add(second);
        }
        return result;
    }

    private List<String> allCandidateTexts() {
        List<String> all = new ArrayList<String>();
        all.addAll(aliases.keySet());
        all.addAll(commands.keySet());
        return all;
    }

    /** 标准编辑距离（Levenshtein），两行 DP，无外部依赖。 */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
