package com.omniai.assistant.agent;

import android.content.Context;

import com.omniai.assistant.inference.InferenceEngine;
import com.omniai.assistant.model.Agent;
import com.omniai.assistant.scheduler.InferenceParams;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AgentCore {

    private static final String TOOL_CALL_PATTERN = "\\[TOOL_CALL:\\s*(\\w+)\\s*\\(\\s*\"([^\"]*)\"\\s*\\)\\]";
    private static final String FINAL_ANSWER_PREFIX = "[FINAL_ANSWER:";
    private static final int DEFAULT_MAX_STEPS = 120;

    private final Context context;
    private final InferenceEngine inferenceEngine;
    private final ToolExecutor toolExecutor;
    private final List<AgentStep> steps;
    private Agent currentAgent;
    private boolean isRunning;
    private boolean isAborted;
    private AgentCallback callback;

    private StringBuilder conversationHistory;
    private int currentStep;
    private int maxSteps;
    private String pendingToolName;
    private String pendingToolInput;
    private boolean waitingForApproval;

    public interface AgentCallback {
        void onStep(AgentStep step);
        void onThinking(String thought);
        void onToolCall(String toolName, String toolInput);
        void onToolResult(String toolName, String result);
        void onFinalAnswer(String answer);
        void onError(String message);
        void onComplete(List<AgentStep> steps);
    }

    public AgentCore(Context context) {
        this.context = context.getApplicationContext();
        this.inferenceEngine = InferenceEngine.getInstance();
        this.toolExecutor = new ToolExecutor(context);
        this.steps = new ArrayList<>();
        this.isRunning = false;
        this.isAborted = false;
        this.waitingForApproval = false;
    }

    public void setCallback(AgentCallback callback) {
        this.callback = callback;
    }

    public void execute(Agent agent, String userInput) {
        if (isRunning) {
            notifyError("Agent正在执行中，请等待完成");
            return;
        }

        this.currentAgent = agent;
        this.steps.clear();
        this.isRunning = true;
        this.isAborted = false;
        this.waitingForApproval = false;
        this.pendingToolName = null;
        this.pendingToolInput = null;
        this.conversationHistory = new StringBuilder();
        this.currentStep = 0;
        this.maxSteps = agent != null ? agent.getMaxSteps() : DEFAULT_MAX_STEPS;

        conversationHistory.append(buildSystemPrompt());
        conversationHistory.append("\n\n用户: ").append(userInput).append("\n");

        new Thread(() -> continueAgentLoop()).start();
    }

    private void continueAgentLoop() {
        try {
            for (; currentStep < maxSteps; currentStep++) {
                if (isAborted) {
                    AgentStep abortStep = AgentStep.error("Agent执行已被中止");
                    steps.add(abortStep);
                    notifyStep(abortStep);
                    break;
                }

                String modelResponse = callModel(conversationHistory.toString());
                if (modelResponse == null || modelResponse.isEmpty()) {
                    AgentStep errStep = AgentStep.error("模型未返回有效结果");
                    steps.add(errStep);
                    notifyStep(errStep);
                    break;
                }

                conversationHistory.append("助手: ").append(modelResponse).append("\n");

                if (modelResponse.contains(FINAL_ANSWER_PREFIX)) {
                    String answer = extractFinalAnswer(modelResponse);
                    AgentStep finalStep = AgentStep.finalAnswer(answer);
                    steps.add(finalStep);
                    notifyStep(finalStep);
                    notifyFinalAnswer(answer);
                    break;
                }

                Matcher matcher = Pattern.compile(TOOL_CALL_PATTERN).matcher(modelResponse);
                if (matcher.find()) {
                    String toolName = matcher.group(1);
                    String toolInput = matcher.group(2);

                    AgentStep callStep = AgentStep.toolCall(toolName, toolInput);
                    steps.add(callStep);
                    notifyStep(callStep);
                    notifyToolCall(toolName, toolInput);

                    if (currentAgent != null && !currentAgent.getEnabledTools().contains(toolName)) {
                        String errResult = "工具 " + toolName + " 未被当前Agent启用";
                        AgentStep errToolStep = AgentStep.toolResult(toolName, errResult);
                        steps.add(errToolStep);
                        notifyStep(errToolStep);
                        notifyToolResult(toolName, errResult);
                        conversationHistory.append("工具结果: ").append(errResult).append("\n");
                        continue;
                    }

                    if (currentAgent != null && !currentAgent.isAutoExecute()) {
                        pendingToolName = toolName;
                        pendingToolInput = toolInput;
                        waitingForApproval = true;
                        conversationHistory.append("工具结果: [等待用户确认执行]\n");
                        return;
                    }

                    String toolResult = toolExecutor.execute(toolName, toolInput);
                    AgentStep resultStep = AgentStep.toolResult(toolName, toolResult);
                    steps.add(resultStep);
                    notifyStep(resultStep);
                    notifyToolResult(toolName, toolResult);
                    conversationHistory.append("工具结果: ").append(toolResult).append("\n");
                } else {
                    String thinking = modelResponse.trim();
                    AgentStep thinkStep = AgentStep.thinking(thinking);
                    steps.add(thinkStep);
                    notifyStep(thinkStep);
                    notifyThinking(thinking);
                    conversationHistory.append("思考: ").append(thinking).append("\n");

                    if (currentStep == maxSteps - 1) {
                        AgentStep finalStep = AgentStep.finalAnswer(thinking);
                        steps.add(finalStep);
                        notifyStep(finalStep);
                        notifyFinalAnswer(thinking);
                    }
                }
            }

        } catch (Exception e) {
            AgentStep errStep = AgentStep.error("Agent执行异常: " + e.getMessage());
            steps.add(errStep);
            notifyStep(errStep);
            notifyError("Agent执行异常: " + e.getMessage());
        } finally {
            isRunning = false;
            waitingForApproval = false;
            notifyComplete(steps);
        }
    }

    public void approveToolExecution() {
        if (!waitingForApproval || pendingToolName == null) return;

        final String toolName = pendingToolName;
        final String toolInput = pendingToolInput;
        waitingForApproval = false;
        pendingToolName = null;
        pendingToolInput = null;

        new Thread(() -> {
            String result = toolExecutor.execute(toolName, toolInput);
            AgentStep resultStep = AgentStep.toolResult(toolName, result);
            steps.add(resultStep);
            notifyStep(resultStep);
            notifyToolResult(toolName, result);

            int lastToolResultIdx = conversationHistory.lastIndexOf("[等待用户确认执行]");
            if (lastToolResultIdx != -1) {
                conversationHistory.replace(lastToolResultIdx,
                        lastToolResultIdx + "[等待用户确认执行]".length(), result);
            } else {
                conversationHistory.append("工具结果: ").append(result).append("\n");
            }

            currentStep++;
            continueAgentLoop();
        }).start();
    }

    public void rejectToolExecution() {
        if (!waitingForApproval) return;

        final String toolName = pendingToolName;
        waitingForApproval = false;
        pendingToolName = null;
        pendingToolInput = null;

        new Thread(() -> {
            String rejectMsg = "用户拒绝了工具执行";
            AgentStep rejectStep = AgentStep.toolResult(toolName != null ? toolName : "", rejectMsg);
            steps.add(rejectStep);
            notifyStep(rejectStep);
            notifyToolResult(toolName != null ? toolName : "", rejectMsg);

            int lastToolResultIdx = conversationHistory.lastIndexOf("[等待用户确认执行]");
            if (lastToolResultIdx != -1) {
                conversationHistory.replace(lastToolResultIdx,
                        lastToolResultIdx + "[等待用户确认执行]".length(), rejectMsg);
            }

            currentStep++;
            continueAgentLoop();
        }).start();
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("系统提示: ");

        if (currentAgent != null && currentAgent.getSystemPrompt() != null) {
            sb.append(currentAgent.getSystemPrompt());
        } else {
            sb.append("你是一个智能助手，可以使用工具来帮助用户完成任务。");
        }

        sb.append("\n\n可用工具:\n");
        List<String> tools = currentAgent != null ? currentAgent.getEnabledTools() : new ArrayList<>();
        for (String toolName : tools) {
            AgentTool tool = AgentTool.getToolByName(toolName);
            if (tool != null) {
                sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            }
        }

        sb.append("\n使用工具格式: [TOOL_CALL: 工具名(\"参数\")]\n");
        sb.append("返回最终答案格式: [FINAL_ANSWER: 你的答案]\n");
        sb.append("请先思考，然后决定是否需要使用工具，最后给出最终答案。\n");

        return sb.toString();
    }

    private String callModel(String prompt) {
        if (!inferenceEngine.isModelLoaded()) {
            return "[FINAL_ANSWER: 模型未加载，请先加载模型]";
        }

        try {
            float temp = currentAgent != null ? currentAgent.getTemperature() : 0.7f;
            InferenceParams params = new InferenceParams.Builder()
                    .nPredict(1024)
                    .temperature(temp)
                    .topP(0.9f)
                    .topK(40)
                    .repeatPenalty(1.1f)
                    .build();

            final String[] resultHolder = new String[1];
            final boolean[] errorHolder = new boolean[1];
            CountDownLatch latch = new CountDownLatch(1);

            inferenceEngine.complete(prompt, params, new InferenceEngine.InferenceCallback() {
                @Override
                public void onSuccess(String result) {
                    resultHolder[0] = result;
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    errorHolder[0] = true;
                    resultHolder[0] = error;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                return null;
            }
            if (errorHolder[0]) {
                return null;
            }
            return resultHolder[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFinalAnswer(String response) {
        int start = response.indexOf(FINAL_ANSWER_PREFIX);
        if (start == -1) return response;
        start += FINAL_ANSWER_PREFIX.length();
        int end = response.indexOf("]", start);
        if (end == -1) return response.substring(start).trim();
        return response.substring(start, end).trim();
    }

    public void abort() {
        isAborted = true;
        if (waitingForApproval) {
            waitingForApproval = false;
            pendingToolName = null;
            pendingToolInput = null;
            isRunning = false;
            notifyComplete(steps);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isWaitingForApproval() {
        return waitingForApproval;
    }

    public List<AgentStep> getSteps() {
        return new ArrayList<>(steps);
    }

    private void notifyStep(AgentStep step) {
        if (callback != null) {
            callback.onStep(step);
        }
    }

    private void notifyThinking(String thought) {
        if (callback != null) callback.onThinking(thought);
    }

    private void notifyToolCall(String name, String input) {
        if (callback != null) callback.onToolCall(name, input);
    }

    private void notifyToolResult(String name, String result) {
        if (callback != null) callback.onToolResult(name, result);
    }

    private void notifyFinalAnswer(String answer) {
        if (callback != null) callback.onFinalAnswer(answer);
    }

    private void notifyError(String message) {
        if (callback != null) callback.onError(message);
    }

    private void notifyComplete(List<AgentStep> steps) {
        if (callback != null) callback.onComplete(steps);
    }
}
