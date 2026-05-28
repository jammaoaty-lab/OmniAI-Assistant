package com.omniai.assistant.agent;

public class AgentTool {

    private String name;
    private String displayName;
    private String description;
    private String category;

    public AgentTool(String name, String displayName, String description, String category) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }

    public static AgentTool[] getAllTools() {
        return new AgentTool[]{
            new AgentTool("calculator", "计算器", "执行数学表达式计算", "基础"),
            new AgentTool("knowledge_search", "知识库搜索", "搜索本地向量知识库", "基础"),
            new AgentTool("web_search", "网络搜索", "搜索互联网信息", "网络"),
            new AgentTool("code_execute", "代码执行", "执行Python/JavaScript代码片段", "开发"),
            new AgentTool("file_read", "文件读取", "读取本地文件内容", "文件"),
            new AgentTool("file_write", "文件写入", "写入内容到本地文件", "文件"),
            new AgentTool("image_analyze", "图像分析", "使用视觉模型分析图片", "视觉"),
            new AgentTool("ocr", "OCR识别", "识别图片中的文字", "视觉"),
        };
    }

    public static AgentTool getToolByName(String name) {
        for (AgentTool tool : getAllTools()) {
            if (tool.name.equals(name)) return tool;
        }
        return null;
    }
}
