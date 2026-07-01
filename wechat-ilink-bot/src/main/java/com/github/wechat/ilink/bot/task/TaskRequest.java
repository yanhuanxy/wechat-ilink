package com.github.wechat.ilink.bot.task;

import java.util.UUID;

public class TaskRequest {

    private final String taskId;
    private final String userId;
    private final byte[] videoBytes;
    private final String videoFileName;
    private final String userPrompt;
    private final String rubricId;

    public TaskRequest(String userId, byte[] videoBytes, String videoFileName,
                       String userPrompt, String rubricId) {
        this.taskId = UUID.randomUUID().toString();
        this.userId = userId;
        this.videoBytes = videoBytes;
        this.videoFileName = videoFileName;
        this.userPrompt = userPrompt;
        this.rubricId = rubricId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUserId() {
        return userId;
    }

    public byte[] getVideoBytes() {
        return videoBytes;
    }

    public String getVideoFileName() {
        return videoFileName;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getRubricId() {
        return rubricId;
    }
}
