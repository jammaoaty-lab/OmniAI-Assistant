package com.omniai.assistant.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Agent {

    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private List<String> enabledTools;
    private int maxSteps;
    private float temperature;
    private boolean autoExecute;
    private long createdAt;
    private long updatedAt;
    private String avatar;

    public Agent() {
        this.id = UUID.randomUUID().toString();
        this.name = "新Agent";
        this.description = "";
        this.systemPrompt = "你是一个智能助手，可以使用工具来帮助用户完成任务。请根据用户的需求，选择合适的工具进行操作。";
        this.enabledTools = new ArrayList<>();
        this.enabledTools.add("calculator");
        this.enabledTools.add("knowledge_search");
        this.maxSteps = 10;
        this.temperature = 0.7f;
        this.autoExecute = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.avatar = "🤖";
    }

    public static Agent createCodingAgent() {
        Agent agent = new Agent();
        agent.name = "代码助手";
        agent.description = "编写、调试和优化代码";
        agent.systemPrompt = "你是一个专业的编程助手。你可以编写代码、分析代码问题、提供优化建议。当需要执行代码时，使用code_execute工具。";
        agent.enabledTools.add("code_execute");
        agent.enabledTools.add("file_read");
        agent.enabledTools.add("file_write");
        agent.avatar = "💻";
        return agent;
    }

    public static Agent createResearchAgent() {
        Agent agent = new Agent();
        agent.name = "研究助手";
        agent.description = "搜索资料、总结知识、撰写报告";
        agent.systemPrompt = "你是一个研究助手。你可以搜索知识库、整理信息、撰写研究报告。使用knowledge_search搜索本地知识库，使用web_search搜索网络信息。";
        agent.enabledTools.add("knowledge_search");
        agent.enabledTools.add("web_search");
        agent.enabledTools.add("calculator");
        agent.avatar = "🔬";
        return agent;
    }

    public static Agent createWritingAgent() {
        Agent agent = new Agent();
        agent.name = "写作助手";
        agent.description = "撰写文章、翻译、润色文本";
        agent.systemPrompt = "你是一个专业的写作助手。你可以撰写文章、翻译文本、润色内容。使用file_write保存写作结果。";
        agent.enabledTools.add("file_write");
        agent.enabledTools.add("file_read");
        agent.avatar = "✍️";
        return agent;
    }

    public static Agent createDataAgent() {
        Agent agent = new Agent();
        agent.name = "数据分析";
        agent.description = "数据处理、计算、统计分析";
        agent.systemPrompt = "你是一个数据分析助手。你可以进行数学计算、处理数据、生成分析报告。使用calculator进行精确计算。";
        agent.enabledTools.add("calculator");
        agent.enabledTools.add("code_execute");
        agent.avatar = "📊";
        return agent;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public List<String> getEnabledTools() { return enabledTools; }
    public void setEnabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; }

    public int getMaxSteps() { return maxSteps; }
    public void setMaxSteps(int maxSteps) { this.maxSteps = maxSteps; }

    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }

    public boolean isAutoExecute() { return autoExecute; }
    public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
}
