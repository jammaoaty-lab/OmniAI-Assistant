package com.omniai.assistant.agent;

import android.content.Context;

import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.knowledge.KnowledgeBaseManager;
import com.omniai.assistant.knowledge.KnowledgeBaseManager.SearchResult;
import com.omniai.assistant.model.KnowledgeBase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ToolExecutor {

    private final Context context;
    private final KnowledgeBaseManager knowledgeBaseManager;

    public ToolExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.knowledgeBaseManager = KnowledgeBaseManager.getInstance();
    }

    public String execute(String toolName, String toolInput) {
        switch (toolName) {
            case "calculator":
                return executeCalculator(toolInput);
            case "knowledge_search":
                return executeKnowledgeSearch(toolInput);
            case "web_search":
                return executeWebSearch(toolInput);
            case "code_execute":
                return executeCode(toolInput);
            case "file_read":
                return executeFileRead(toolInput);
            case "file_write":
                return executeFileWrite(toolInput);
            case "image_analyze":
                return executeImageAnalyze(toolInput);
            case "ocr":
                return executeOcr(toolInput);
            default:
                return "未知工具: " + toolName;
        }
    }

    private String executeCalculator(String expression) {
        try {
            String sanitized = expression.replaceAll("[^0-9+\\-*/.()\\s]", "").trim();
            if (sanitized.isEmpty()) {
                return "计算错误: 表达式为空";
            }

            double result = evaluateExpression(sanitized);
            if (Double.isInfinite(result) || Double.isNaN(result)) {
                return "计算错误: 结果无效（可能除以零）";
            }

            if (result == (long) result) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (ArithmeticException e) {
            return "计算错误: " + e.getMessage();
        } catch (Exception e) {
            return "计算错误: 无法解析表达式 '" + expression + "'";
        }
    }

    private double evaluateExpression(String expr) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("意外字符: " + (char)ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) {
                        double divisor = parseFactor();
                        if (divisor == 0) throw new ArithmeticException("除以零");
                        x /= divisor;
                    }
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("无法解析: " + (char)ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());

                return x;
            }
        }.parse();
    }

    private String executeKnowledgeSearch(String query) {
        try {
            List<KnowledgeBase> kbs = knowledgeBaseManager.listKnowledgeBases();
            if (kbs == null || kbs.isEmpty()) {
                return "知识库中未找到相关内容（无可用知识库）";
            }

            String kbId = kbs.get(0).getId();
            List<SearchResult> results = knowledgeBaseManager.search(kbId, query, 5);
            if (results == null || results.isEmpty()) {
                return "知识库中未找到相关内容";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(String.format("[%d] (相似度: %.2f) %s\n来源: %s\n\n",
                        i + 1, r.getScore(), r.getContent(), r.getSource()));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    private String executeWebSearch(String query) {
        return "网络搜索暂不可用（离线模式）。提示：可使用knowledge_search搜索本地知识库。";
    }

    private String executeCode(String code) {
        try {
            if (code == null || code.trim().isEmpty()) {
                return "代码为空，无法执行";
            }

            String trimmed = code.trim();
            if (trimmed.startsWith("print(") || trimmed.startsWith("len(") ||
                trimmed.contains("import ") || trimmed.contains("def ")) {
                return executePythonLike(code);
            }

            if (trimmed.startsWith("console.log") || trimmed.startsWith("function") ||
                trimmed.startsWith("const ") || trimmed.startsWith("let ") || trimmed.startsWith("var ")) {
                return executeJavaScriptLike(code);
            }

            return executePythonLike(code);
        } catch (Exception e) {
            return "代码执行错误: " + e.getMessage();
        }
    }

    private String executePythonLike(String code) {
        StringBuilder output = new StringBuilder();
        String[] lines = code.split("\n");
        Map<String, Object> vars = new HashMap<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("print(")) {
                String inner = line.substring(6, line.length() - 1);
                Object val = evalSimpleExpr(inner, vars);
                output.append(val != null ? val.toString() : "None").append("\n");
            } else if (line.contains("=") && !line.contains("==")) {
                String[] parts = line.split("=", 2);
                String varName = parts[0].trim();
                String valExpr = parts[1].trim();
                try {
                    Object val = evalSimpleExpr(valExpr, vars);
                    vars.put(varName, val);
                } catch (Exception e) {
                    output.append("错误: ").append(e.getMessage()).append("\n");
                }
            } else {
                try {
                    Object val = evalSimpleExpr(line, vars);
                    if (val != null) output.append(val.toString()).append("\n");
                } catch (Exception e) {
                    output.append("错误: ").append(e.getMessage()).append("\n");
                }
            }
        }

        return output.length() > 0 ? output.toString().trim() : "代码执行完成（无输出）";
    }

    private Object evalSimpleExpr(String expr, Map<String, Object> vars) {
        expr = expr.trim();
        if (expr.startsWith("\"") && expr.endsWith("\"")) {
            return expr.substring(1, expr.length() - 1);
        }
        if (expr.startsWith("'") && expr.endsWith("'")) {
            return expr.substring(1, expr.length() - 1);
        }
        if (vars.containsKey(expr)) {
            return vars.get(expr);
        }
        try {
            if (expr.contains(".")) return Double.parseDouble(expr);
            return Integer.parseInt(expr);
        } catch (NumberFormatException e) {
            return expr;
        }
    }

    private String executeJavaScriptLike(String code) {
        StringBuilder output = new StringBuilder();
        String[] lines = code.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("console.log(")) {
                String inner = line.substring(12, line.length() - 1);
                output.append(inner).append("\n");
            }
        }

        return output.length() > 0 ? output.toString().trim() : "代码执行完成（无输出）";
    }

    private String executeFileRead(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file = new File(context.getFilesDir(), filePath);
            }
            if (!file.exists()) {
                return "文件不存在: " + filePath;
            }
            if (file.length() > 1024 * 1024) {
                return "文件过大（超过1MB），仅支持读取1MB以内的文件";
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 500) {
                sb.append(line).append("\n");
                lineCount++;
            }
            reader.close();

            if (lineCount >= 500) {
                sb.append("... (文件过长，仅显示前500行)");
            }
            return sb.toString();
        } catch (IOException e) {
            return "文件读取失败: " + e.getMessage();
        }
    }

    private String executeFileWrite(String input) {
        try {
            String[] parts = input.split("\\|", 2);
            if (parts.length < 2) {
                return "格式错误，请使用: 文件路径|内容";
            }

            String filePath = parts[0].trim();
            String content = parts[1].trim();

            File file = new File(filePath);
            if (!file.isAbsolute()) {
                file = new File(context.getFilesDir(), filePath);
            }

            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();

            return "文件写入成功: " + file.getAbsolutePath() + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            return "文件写入失败: " + e.getMessage();
        }
    }

    private String executeImageAnalyze(String imagePath) {
        try {
            VisionInferenceEngine visionEngine = VisionInferenceEngine.getInstance();
            if (!visionEngine.isVisionModelLoaded()) {
                return "视觉模型未加载，无法分析图片";
            }

            File file = new File(imagePath);
            if (!file.exists()) {
                return "图片文件不存在: " + imagePath;
            }

            final String[] resultHolder = new String[1];
            final boolean[] errorHolder = new boolean[1];
            CountDownLatch latch = new CountDownLatch(1);

            visionEngine.visionChat(imagePath, "请详细描述这张图片的内容",
                    new VisionInferenceEngine.VisionCallback() {
                        @Override
                        public void onProgress(String partialText) {
                        }

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

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                return "图片分析超时";
            }
            if (errorHolder[0]) {
                return "图片分析失败: " + resultHolder[0];
            }
            return resultHolder[0] != null ? resultHolder[0] : "图片分析失败";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "图片分析被中断";
        } catch (Exception e) {
            return "图片分析失败: " + e.getMessage();
        }
    }

    private String executeOcr(String imagePath) {
        try {
            VisionInferenceEngine visionEngine = VisionInferenceEngine.getInstance();
            if (!visionEngine.isVisionModelLoaded()) {
                return "视觉模型未加载，无法执行OCR";
            }

            File file = new File(imagePath);
            if (!file.exists()) {
                return "图片文件不存在: " + imagePath;
            }

            final String[] resultHolder = new String[1];
            final boolean[] errorHolder = new boolean[1];
            CountDownLatch latch = new CountDownLatch(1);

            visionEngine.imageOcr(imagePath, new VisionInferenceEngine.OcrCallback() {
                @Override
                public void onProgress(int percent, String message) {
                }

                @Override
                public void onSuccess(String text) {
                    resultHolder[0] = text;
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    errorHolder[0] = true;
                    resultHolder[0] = error;
                    latch.countDown();
                }
            });

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                return "OCR识别超时";
            }
            if (errorHolder[0]) {
                return "OCR识别失败: " + resultHolder[0];
            }
            return resultHolder[0] != null ? resultHolder[0] : "OCR识别失败";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "OCR识别被中断";
        } catch (Exception e) {
            return "OCR识别失败: " + e.getMessage();
        }
    }
}
