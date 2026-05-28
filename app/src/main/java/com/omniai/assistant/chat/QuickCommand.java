package com.omniai.assistant.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuickCommand {

    private String id;
    private String name;
    private String icon;
    private String prompt;
    private String category;

    public QuickCommand() {
        this.id = UUID.randomUUID().toString();
    }

    public QuickCommand(String id, String name, String icon, String prompt, String category) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.prompt = prompt;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public static List<QuickCommand> getDefaultCommands() {
        List<QuickCommand> commands = new ArrayList<>();
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "AI写作", "edit", "请帮我撰写以下内容：", "创作"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "代码生成", "code", "请帮我生成以下代码：", "开发"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "翻译", "translate", "请将以下内容翻译：", "工具"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "OCR识图", "image", "请识别图片中的文字：", "工具"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "语音助手", "mic", "请处理以下语音内容：", "助手"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "AI搜索", "search", "请搜索以下问题：", "工具"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "知识库", "book", "请从知识库中查询：", "工具"));
        commands.add(new QuickCommand(UUID.randomUUID().toString(), "Agent", "robot", "请作为智能代理执行：", "助手"));
        return commands;
    }

    public void execute(String userInput, QuickCommandCallback callback) {
        if (callback == null) return;
        String combinedPrompt = this.prompt + userInput;
        try {
            callback.onSuccess(combinedPrompt);
        } catch (Exception e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : "Quick command execution failed");
        }
    }

    public interface QuickCommandCallback {
        void onSuccess(String result);
        void onError(String error);
    }
}
