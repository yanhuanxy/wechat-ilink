package com.github.wechat.ilink.bot.util;

public class TextFormatter {

    public static String box(String title, String content) {
        StringBuilder sb = new StringBuilder();
        String topBorder = border(title.length() + 8);
        sb.append(topBorder).append("\n");
        sb.append("| ").append(title).append(" |").append("\n");
        sb.append(topBorder).append("\n");
        sb.append(content);
        return sb.toString();
    }

    public static String menu(String title, java.util.List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("╭┈┈ ").append(title).append(" ┈┈╮\n");
        for (String item : items) {
            sb.append("🌴✨").append(item).append("\n");
        }
        sb.append("╰ ┈┈┈┈┈┈┈┈┈┈┈┈┈ ╯");
        return sb.toString();
    }

    public static String grid(String title, java.util.List<String> cells, int cols) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            sb.append(String.format("[%02d]%-16s", i + 1, cell));
            if ((i + 1) % cols == 0 || i == cells.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String info(java.util.Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : fields.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String border(int width) {
        StringBuilder sb = new StringBuilder();
        sb.append("+");
        for (int i = 0; i < width; i++) {
            sb.append("-");
        }
        sb.append("+");
        return sb.toString();
    }
}
