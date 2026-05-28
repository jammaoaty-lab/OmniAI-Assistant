package com.omniai.assistant.simulation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.AIModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SimulatedInferenceEngine {

    private static final String TAG = "SimulatedInferenceEngine";
    private static SimulatedInferenceEngine instance;

    private final Context appContext;
    private final Random random = new Random();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isModelLoaded = false;
    private AIModel currentModel;
    private int availableMemoryMb = 2048;
    private float currentTemp = 36.5f;

    private Map<String, String> quickResponses = new HashMap<>();

    private SimulatedInferenceEngine(Context context) {
        this.appContext = context.getApplicationContext();
        initQuickResponses();
    }

    public static synchronized SimulatedInferenceEngine getInstance(Context context) {
        if (instance == null) {
            instance = new SimulatedInferenceEngine(context);
        }
        return instance;
    }

    private void initQuickResponses() {
        quickResponses.put("你好", "你好！我是 Senta AI，很高兴为你服务。我可以帮你回答问题、创作内容、编写代码。");
        quickResponses.put("你是谁", "我是 Senta AI，一款本地运行的 AI 助手，基于 llama.cpp 和 Qwen 模型。我的对话完全在本地处理，保护你的隐私。");
        quickResponses.put("天气", "很抱歉，我无法获取实时天气信息。不过你可以检查系统天气应用，或者打开浏览器搜索。");
        quickResponses.put("代码", "当然！我可以帮你编写各种编程语言的代码。请告诉我你需要什么功能，比如 \"帮我写一个 Android Activity\"。");
        quickResponses.put("翻译", "翻译功能已就绪！请输入需要翻译的文字，我可以在中文、英文、日文、韩文等多种语言间互译。");
        quickResponses.put("写文章", "写作功能已启用！请告诉我你想写什么主题，比如科技文章、产品描述、邮件内容，我会为你生成高质量内容。");
        quickResponses.put("积分", "积分系统：你可以通过邀请好友或充值获得积分，使用高级模型、LoRA 训练、云端 GPU 等功能需要消耗积分。");
        quickResponses.put("模型", "当前可用模型：Qwen2.5-0.5B（预装）、Qwen2.5-7B（高级）、Qwen3-VL-2B（视觉）、SmolVLM2-256M（轻量视觉）。");
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }

    public AIModel getCurrentModel() {
        return currentModel;
    }

    public void loadModel(AIModel model, final LoadCallback callback) {
        if (isModelLoaded && currentModel != null && currentModel.id.equals(model.id)) {
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess());
            }
            return;
        }

        isModelLoaded = false;
        currentModel = null;

        final int totalSteps = 100;
        new Thread(() -> {
            for (int i = 0; i <= totalSteps; i++) {
                final int progress = i;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onProgress(progress, totalSteps);
                    }
                });
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            isModelLoaded = true;
            currentModel = model;
            availableMemoryMb = 1024 + random.nextInt(1536);

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onSuccess();
                }
            });
        }).start();
    }

    public void unloadModel() {
        isModelLoaded = false;
        currentModel = null;
    }

    public void complete(String prompt, int maxTokens, float temperature, 
                         final CompletionCallback callback) {
        if (!isModelLoaded) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Model not loaded"));
            }
            return;
        }

        final String finalPrompt = prompt.toLowerCase();
        final String baseResponse = getBaseResponse(finalPrompt);

        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            final int charsPerStep = Math.max(1, baseResponse.length() / 50);
            int idx = 0;

            while (idx < baseResponse.length()) {
                final int endIdx = Math.min(idx + charsPerStep, baseResponse.length());
                final String chunk = baseResponse.substring(idx, endIdx);
                sb.append(chunk);
                idx = endIdx;

                final int currentIdx = idx;
                final String currentText = sb.toString();

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPartialResult(currentText);
                    }
                });

                try {
                    Thread.sleep(50 + random.nextInt(100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            final String fullResponse = sb.toString();
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onComplete(fullResponse);
                }
            });
        }).start();
    }

    private String getBaseResponse(String prompt) {
        for (Map.Entry<String, String> entry : quickResponses.entrySet()) {
            if (prompt.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        if (prompt.contains("代码") || prompt.contains("编程") || prompt.contains("写")) {
            return getCodeResponse(prompt);
        } else if (prompt.contains("翻译")) {
            return getTranslateResponse(prompt);
        } else if (prompt.contains("文章") || prompt.contains("写")) {
            return getArticleResponse();
        } else if (prompt.contains("？") || prompt.contains("?") || prompt.contains("什么")) {
            return getQuestionResponse(prompt);
        } else {
            return getGeneralResponse(prompt);
        }
    }

    private String getCodeResponse(String prompt) {
        if (prompt.contains("Android") || prompt.contains("Activity")) {
            return "```java\npublic class MainActivity extends AppCompatActivity {\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n        // TODO: 初始化 UI 和逻辑\n    }\n}\n```\n这是一个标准的 Android Activity 模板。请告诉我你需要添加什么功能。";
        } else if (prompt.contains("Python")) {
            return "```python\ndef main():\n    print(\"Hello, Senta AI!\")\n    # 你的 Python 代码\n\nif __name__ == \"__main__\":\n    main()\n```\n这是一个简单的 Python 程序模板。请告诉我具体需求。";
        } else {
            return "代码生成功能已准备就绪！我支持多种编程语言：\n- Java/Kotlin (Android)\n- Python\n- JavaScript/TypeScript\n- C/C++\n- Go\n\n请告诉我你需要什么功能。";
        }
    }

    private String getTranslateResponse(String prompt) {
        return "翻译演示：原文 → 译文\n\n（这是模拟的翻译响应，真实环境下将使用 Qwen 模型的强大翻译能力。）";
    }

    private String getArticleResponse() {
        return "## 标题\n\n这是一篇模拟生成的文章，展示 Senta AI 的创作能力。在真实环境中，你可以创作：\n\n- 产品描述\n- 技术文章\n- 邮件内容\n- 营销文案\n- 教学材料\n\n请告诉我你想写什么主题。";
    }

    private String getQuestionResponse(String prompt) {
        return "关于 \"" + prompt.replace("？", "").replace("?", "").trim() + "\" 的回答：\n\n这是一个模拟的回答。真实环境下，我会根据 Qwen 模型的知识库提供准确的回答。";
    }

    private String getGeneralResponse(String prompt) {
        return "收到！我理解你的输入。\n\n（这是模拟的推理响应，真实环境下会使用本地 llama.cpp 和 Qwen 模型进行真实推理。）";
    }

    public float[] getEmbedding(String text) {
        int dim = 384;
        float[] emb = new float[dim];
        for (int i = 0; i < dim; i++) {
            emb[i] = (random.nextFloat() - 0.5f) * 2;
        }
        float norm = 0;
        for (float v : emb) norm += v * v;
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < dim; i++) emb[i] /= norm;
        return emb;
    }

    public int getAvailableMemoryMb() {
        updateHardware();
        return availableMemoryMb;
    }

    public float getDeviceTemperature() {
        updateHardware();
        return currentTemp;
    }

    private void updateHardware() {
        currentTemp = 35f + random.nextFloat() * 10f;
        availableMemoryMb = 1024 + random.nextInt(1536);
    }

    public List<String> tokenize(String text) {
        String[] words = text.split("\\s+|(?<=\\p{Punct})|(?=\\p{Punct})");
        return new ArrayList<>(Arrays.asList(words));
    }

    public interface LoadCallback {
        void onProgress(int current, int total);
        void onSuccess();
        void onError(String error);
    }

    public interface CompletionCallback {
        void onPartialResult(String text);
        void onComplete(String text);
        void onError(String error);
    }
}
