package com.omniai.assistant.agent;

public class AgentStep {

    public enum StepType {
        THINKING,
        TOOL_CALL,
        TOOL_RESULT,
        FINAL_ANSWER,
        ERROR
    }

    private StepType type;
    private String content;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private long timestamp;

    public AgentStep(StepType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public static AgentStep thinking(String thought) {
        return new AgentStep(StepType.THINKING, thought);
    }

    public static AgentStep toolCall(String toolName, String toolInput) {
        AgentStep step = new AgentStep(StepType.TOOL_CALL, "调用工具: " + toolName);
        step.toolName = toolName;
        step.toolInput = toolInput;
        return step;
    }

    public static AgentStep toolResult(String toolName, String output) {
        AgentStep step = new AgentStep(StepType.TOOL_RESULT, "工具结果: " + toolName);
        step.toolName = toolName;
        step.toolOutput = output;
        return step;
    }

    public static AgentStep finalAnswer(String answer) {
        return new AgentStep(StepType.FINAL_ANSWER, answer);
    }

    public static AgentStep error(String message) {
        return new AgentStep(StepType.ERROR, message);
    }

    public StepType getType() { return type; }
    public String getContent() { return content; }
    public String getToolName() { return toolName; }
    public String getToolInput() { return toolInput; }
    public String getToolOutput() { return toolOutput; }
    public long getTimestamp() { return timestamp; }
}
