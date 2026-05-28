package com.omniai.assistant.ui.terminal;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.omniai.assistant.R;
import com.omniai.assistant.common.Constants;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.modelmgmt.ModelDownloadInfo;
import com.omniai.assistant.modelmgmt.ModelDownloadManager;
import com.omniai.assistant.service.LlamaCppService;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalActivity extends AppCompatActivity {

    private static final String TAG = "TerminalActivity";
    private static final String ASSETS_BIN_DIR = "llama-bin";
    private static final String SCRIPT_COPY_FAILED = "Failed to copy %s to app dir\n";
    private static final String PROMPT = "llama> ";
    private static final int MAX_LINES = 1000;

    private TextView tvOutput;
    private EditText etInput;
    private ScrollView scrollView;
    private ImageButton btnSend;
    private LinearLayout downloadBar;
    private ProgressBar downloadProgress;
    private TextView downloadStatus;
    private ImageButton btnDownload;

    private StringBuilder outputBuffer = new StringBuilder();
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;

    private File appFilesDir;
    private File appBinDir;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ModelDownloadManager downloadManager;

    public static void start(Context context) {
        context.startActivity(new Intent(context, TerminalActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        initViews();
        initPaths();
        setupToolbar();
        setupListeners();
        setupDownloadManager();
        
        appendWelcomeMessage();
        executorService.execute(this::initializeBinaries);
        checkModelStatus();
    }

    private void initViews() {
        tvOutput = findViewById(R.id.tvOutput);
        etInput = findViewById(R.id.etInput);
        scrollView = findViewById(R.id.scrollView);
        btnSend = findViewById(R.id.btnSend);
        downloadBar = findViewById(R.id.layout_download_bar);
        downloadProgress = findViewById(R.id.progress_download);
        downloadStatus = findViewById(R.id.tv_download_status);
        btnDownload = findViewById(R.id.btn_download_model);
    }

    private void initPaths() {
        appFilesDir = getFilesDir();
        appBinDir = new File(appFilesDir, "bin");
        if (!appBinDir.exists()) {
            appBinDir.mkdirs();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Senta AI Terminal");
        toolbar.setTitleTextColor(0xFF00FF00);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> executeCurrentInput());
        
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCurrentInput();
                return true;
            }
            return false;
        });

        etInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && historyIndex > 0) {
                    historyIndex--;
                    etInput.setText(commandHistory.get(historyIndex));
                    etInput.setSelection(etInput.getText().length());
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (historyIndex < commandHistory.size() - 1) {
                        historyIndex++;
                        etInput.setText(commandHistory.get(historyIndex));
                    } else {
                        historyIndex = commandHistory.size();
                        etInput.setText("");
                    }
                    etInput.setSelection(etInput.getText().length());
                    return true;
                }
            }
            return false;
        });

        btnDownload.setOnClickListener(v -> showDownloadMenu());
    }

    private void setupDownloadManager() {
        downloadManager = ModelDownloadManager.getInstance(this);
        downloadManager.setCallback(new ModelDownloadManager.DownloadCallback() {
            @Override
            public void onProgress(String modelId, int percent, long downloadedBytes, long totalBytes, float speedMBps) {
                runOnUiThread(() -> {
                    if (percent == -2) {
                        downloadStatus.setText("校验文件中...");
                        return;
                    }
                    downloadBar.setVisibility(View.VISIBLE);
                    if (percent >= 0) {
                        downloadProgress.setProgress(percent);
                        downloadProgress.setVisibility(View.VISIBLE);
                    }
                    String speed = speedMBps > 0 ? String.format(" · %.1f MB/s", speedMBps) : "";
                    String downloaded = formatSize(downloadedBytes);
                    String total = formatSize(totalBytes);
                    downloadStatus.setText(downloaded + " / " + total + speed);
                });
            }

            @Override
            public void onPaused(String modelId, long downloadedBytes) {
                runOnUiThread(() -> {
                    downloadStatus.setText("已暂停 · " + formatSize(downloadedBytes) + " 已下载");
                    btnDownload.setImageResource(android.R.drawable.ic_media_play);
                });
            }

            @Override
            public void onCompleted(String modelId, String filePath) {
                runOnUiThread(() -> {
                    downloadProgress.setProgress(100);
                    downloadStatus.setText("下载完成: " + new File(filePath).getName());
                    btnDownload.setImageResource(android.R.drawable.stat_sys_download_done);
                    appendOutput("[Download] 模型下载完成: " + filePath + "\n");
                    checkModelStatus();
                });
            }

            @Override
            public void onMd5Verified(String modelId, boolean passed) {
                runOnUiThread(() -> {
                    if (passed) {
                        downloadStatus.setText("MD5校验通过");
                    } else {
                        downloadStatus.setText("MD5校验失败，文件可能已损坏");
                    }
                });
            }

            @Override
            public void onFailed(String modelId, String error) {
                runOnUiThread(() -> {
                    downloadStatus.setText("下载失败: " + error);
                    btnDownload.setImageResource(android.R.drawable.stat_sys_download);
                    appendOutput("[Download] 下载失败: " + error + "\n");
                });
            }
        });
    }

    private void showDownloadMenu() {
        String[] options = {
            "下载 Qwen2.5-0.5B 文本模型 (~0.4 GB)",
            "下载 Qwen3-VL-2B 视觉模型 (~1.5 GB)",
            "查看下载状态",
            "取消"
        };

        new android.app.AlertDialog.Builder(this)
            .setTitle("模型下载")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        startModelDownload(ModelDownloadInfo.createTextModel());
                        break;
                    case 1:
                        startModelDownload(ModelDownloadInfo.createVisionModel());
                        break;
                    case 2:
                        showDownloadStatus();
                        break;
                }
            })
            .show();
    }

    private void startModelDownload(ModelDownloadInfo modelInfo) {
        if (downloadManager.isModelDownloaded(modelInfo.getFileName())) {
            appendOutput("[Download] " + modelInfo.getModelName() + " 已存在，无需下载\n");
            return;
        }

        if (downloadManager.isDownloading(modelInfo.getModelId())) {
            downloadManager.pauseDownload(modelInfo.getModelId());
            appendOutput("[Download] 暂停下载 " + modelInfo.getModelName() + "\n");
            return;
        }

        if (downloadManager.hasPartialDownload(modelInfo.getModelId(), modelInfo.getFileName())) {
            long partial = downloadManager.getPartialDownloadSize(modelInfo.getModelId(), modelInfo.getFileName());
            appendOutput("[Download] 检测到未完成下载，断点续传 " + modelInfo.getModelName()
                + " (已下载 " + formatSize(partial) + ")\n");
        } else {
            appendOutput("[Download] 开始下载 " + modelInfo.getModelName()
                + " (" + modelInfo.getDisplaySize() + ")\n");
        }

        downloadBar.setVisibility(View.VISIBLE);
        downloadProgress.setProgress(0);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadStatus.setText("正在下载 " + modelInfo.getModelName() + "...");
        btnDownload.setImageResource(android.R.drawable.ic_media_pause);

        downloadManager.startDownload(modelInfo);
    }

    private void showDownloadStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 模型下载状态 ===\n");

        ModelDownloadInfo textInfo = ModelDownloadInfo.createTextModel();
        ModelDownloadInfo visionInfo = ModelDownloadInfo.createVisionModel();

        sb.append("📄 ").append(textInfo.getModelName()).append("\n");
        if (downloadManager.isModelDownloaded(textInfo.getFileName())) {
            sb.append("   状态: ✅ 已下载\n");
        } else if (downloadManager.isDownloading(textInfo.getModelId())) {
            sb.append("   状态: ⬇️ 下载中\n");
        } else if (downloadManager.hasPartialDownload(textInfo.getModelId(), textInfo.getFileName())) {
            long partial = downloadManager.getPartialDownloadSize(textInfo.getModelId(), textInfo.getFileName());
            sb.append("   状态: ⏸️ 已暂停 (").append(formatSize(partial)).append(")\n");
        } else {
            sb.append("   状态: 📥 未下载\n");
        }

        sb.append("👁️ ").append(visionInfo.getModelName()).append("\n");
        if (downloadManager.isModelDownloaded(visionInfo.getFileName())) {
            sb.append("   状态: ✅ 已下载\n");
        } else if (downloadManager.isDownloading(visionInfo.getModelId())) {
            sb.append("   状态: ⬇️ 下载中\n");
        } else if (downloadManager.hasPartialDownload(visionInfo.getModelId(), visionInfo.getFileName())) {
            long partial = downloadManager.getPartialDownloadSize(visionInfo.getModelId(), visionInfo.getFileName());
            sb.append("   状态: ⏸️ 已暂停 (").append(formatSize(partial)).append(")\n");
        } else {
            sb.append("   状态: 📥 未下载\n");
        }

        sb.append("==================\n");
        appendOutput(sb.toString());
    }

    private void checkModelStatus() {
        executorService.execute(() -> {
            File modelsDir = new File(getFilesDir(), Constants.MODEL_DIR);
            if (!modelsDir.exists()) {
                mainHandler.post(() -> {
                    downloadBar.setVisibility(View.VISIBLE);
                    downloadStatus.setText("模型未下载，点击右侧按钮下载");
                    downloadProgress.setVisibility(View.GONE);
                });
                return;
            }

            boolean hasText = new File(modelsDir, "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf").exists();
            boolean hasVision = new File(modelsDir, "Qwen3-VL-2B-Q4_K_M.gguf").exists();

            mainHandler.post(() -> {
                if (hasText && hasVision) {
                    downloadBar.setVisibility(View.GONE);
                } else {
                    downloadBar.setVisibility(View.VISIBLE);
                    StringBuilder sb = new StringBuilder();
                    if (!hasText) sb.append("文本模型 ");
                    if (!hasVision) sb.append("视觉模型 ");
                    sb.append("未下载");
                    downloadStatus.setText(sb.toString());
                    downloadProgress.setVisibility(View.GONE);
                }
            });
        });
    }

    private void appendWelcomeMessage() {
        String welcome = "Senta AI Terminal v1.0\n" +
                        "======================\n" +
                        "llama.cpp command shell ready\n" +
                        "Type 'help' for available commands\n" +
                        "Type 'download' to download models\n\n";
        appendOutput(welcome);
    }

    private void initializeBinaries() {
        appendOutput("[Initializing llama.cpp binaries...]\n");
        
        try {
            String[] assets = getAssets().list(ASSETS_BIN_DIR);
            if (assets != null) {
                for (String asset : assets) {
                    copyAssetToBin(asset);
                }
            }
            
            checkAndSetPermissions();
            
            appendOutput("[Binaries ready]\n");
            appendOutput(String.format("Working directory: %s\n", appBinDir.getAbsolutePath()));
            
        } catch (IOException e) {
            appendOutput(String.format(SCRIPT_COPY_FAILED, e.getMessage()));
        }
    }

    private void copyAssetToBin(String filename) {
        File destFile = new File(appBinDir, filename);
        
        if (destFile.exists()) {
            appendOutput(String.format("- %s exists, skipping\n", filename));
            return;
        }

        appendOutput(String.format("- Copying %s...", filename));
        
        try (InputStream is = getAssets().open(ASSETS_BIN_DIR + "/" + filename);
             FileOutputStream os = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            
            appendOutput("done\n");
            
        } catch (IOException e) {
            appendOutput(String.format("failed: %s\n", e.getMessage()));
        }
    }

    private void checkAndSetPermissions() {
        File[] files = appBinDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.canExecute()) {
                    boolean success = file.setExecutable(true);
                    appendOutput(String.format("- chmod +x %s: %s\n",
                        file.getName(), success ? "OK" : "failed"));
                }
            }
        }
    }

    private void executeCurrentInput() {
        String command = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(command)) return;
        
        commandHistory.add(command);
        historyIndex = commandHistory.size();
        etInput.setText("");
        
        appendOutput(PROMPT + command + "\n");
        executeCommand(command);
    }

    private void executeCommand(String command) {
        executorService.execute(() -> {
            try {
                String[] args = command.split("\\s+");
                if (args.length == 0) return;
                
                switch (args[0].toLowerCase()) {
                    case "help":
                        showHelp();
                        break;
                    case "clear":
                        clearOutput();
                        break;
                    case "ls":
                        listFiles(args);
                        break;
                    case "cd":
                        changeDirectory(args);
                        break;
                    case "pwd":
                        showWorkingDir();
                        break;
                    case "download":
                        handleDownloadCommand(args);
                        break;
                    case "models":
                        showDownloadStatus();
                        break;
                    case "start-service":
                        startLlamaService();
                        break;
                    case "stop-service":
                        stopLlamaService();
                        break;
                    case "demo":
                        runDemo();
                        break;
                    case "main":
                    case "server":
                    case "llama-cli":
                    case "llama-server":
                        executeLlamaCommand(command);
                        break;
                    case "exit":
                    case "quit":
                        finish();
                        break;
                    default:
                        executeShellCommand(command);
                        break;
                }
            } catch (Exception e) {
                appendOutput(String.format("Error: %s\n", e.getMessage()));
            }
        });
    }

    private void showHelp() {
        String help = "Available commands:\n" +
                     "  help           - Show this help message\n" +
                     "  clear          - Clear terminal screen\n" +
                     "  ls [path]      - List directory contents\n" +
                     "  pwd            - Show current directory\n" +
                     "  download text  - Download Qwen2.5-0.5B text model\n" +
                     "  download vision- Download Qwen3-VL-2B vision model\n" +
                     "  download pause <id>  - Pause download\n" +
                     "  download resume <id> - Resume download\n" +
                     "  download cancel <id> - Cancel & delete download\n" +
                     "  models         - Show model download status\n" +
                     "  start-service  - Start background service\n" +
                     "  stop-service   - Stop background service\n" +
                     "  main ...       - Run llama.cpp main\n" +
                     "  server ...     - Run llama.cpp server\n" +
                     "  llama-cli ...  - Run llama-cli\n" +
                     "  llama-server...- Run llama-server\n" +
                     "  demo           - Run demo command\n" +
                     "  exit/quit      - Exit terminal\n";
        appendOutput(help);
    }

    private void handleDownloadCommand(String[] args) {
        if (args.length < 2) {
            appendOutput("Usage: download <text|vision|pause|resume|cancel> [model_id]\n");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "text":
                startModelDownload(ModelDownloadInfo.createTextModel());
                break;
            case "vision":
                startModelDownload(ModelDownloadInfo.createVisionModel());
                break;
            case "pause":
                if (args.length >= 3) {
                    downloadManager.pauseDownload(args[2]);
                    appendOutput("[Download] 已暂停下载: " + args[2] + "\n");
                } else {
                    downloadManager.pauseDownload(ModelDownloadInfo.MODEL_ID_TEXT);
                    downloadManager.pauseDownload(ModelDownloadInfo.MODEL_ID_VISION);
                    appendOutput("[Download] 已暂停所有下载\n");
                }
                break;
            case "resume":
                if (args.length >= 3) {
                    ModelDownloadInfo info = args[2].equals(ModelDownloadInfo.MODEL_ID_TEXT)
                            ? ModelDownloadInfo.createTextModel()
                            : ModelDownloadInfo.createVisionModel();
                    startModelDownload(info);
                } else {
                    appendOutput("Usage: download resume <text|vision>\n");
                }
                break;
            case "cancel":
                if (args.length >= 3) {
                    ModelDownloadInfo info = args[2].equals(ModelDownloadInfo.MODEL_ID_TEXT)
                            ? ModelDownloadInfo.createTextModel()
                            : ModelDownloadInfo.createVisionModel();
                    downloadManager.cancelDownload(info.getModelId(), info.getFileName());
                    appendOutput("[Download] 已取消并删除下载: " + info.getModelName() + "\n");
                } else {
                    appendOutput("Usage: download cancel <text|vision>\n");
                }
                break;
            default:
                appendOutput("Unknown download target: " + args[1] + "\n");
                appendOutput("Available: text, vision, pause, resume, cancel\n");
                break;
        }
    }

    private void clearOutput() {
        mainHandler.post(() -> {
            outputBuffer.setLength(0);
            tvOutput.setText("");
        });
    }

    private void listFiles(String[] args) {
        File dir = appBinDir;
        if (args.length > 1) {
            dir = new File(args[1]);
            if (!dir.isAbsolute()) {
                dir = new File(appBinDir, args[1]);
            }
        }
        
        if (!dir.exists() || !dir.isDirectory()) {
            appendOutput("ls: No such directory\n");
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) {
            appendOutput("ls: Empty directory\n");
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (File file : files) {
            sb.append(String.format("%s%s%s\n",
                file.isDirectory() ? "[DIR] " : "      ",
                file.getName(),
                file.canExecute() ? " *" : ""));
        }
        appendOutput(sb.toString());
    }

    private void showWorkingDir() {
        appendOutput(appBinDir.getAbsolutePath() + "\n");
    }

    private void changeDirectory(String[] args) {
        appendOutput("cd: Working directory fixed to " + appBinDir.getAbsolutePath() + "\n");
    }

    private void executeLlamaCommand(String command) {
        executeShellCommand(command);
    }

    private void executeShellCommand(String command) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(appBinDir);
            pb.command("sh", "-c", command);
            pb.redirectErrorStream(true);
            
            process = pb.start();
            
            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                appendOutput(line + "\n");
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                appendOutput(String.format("[Exit code: %d]\n", exitCode));
            }
            
        } catch (Exception e) {
            appendOutput(String.format("Command failed: %s\n", e.getMessage()));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void appendOutput(String text) {
        mainHandler.post(() -> {
            outputBuffer.append(text);
            
            String fullText = outputBuffer.toString();
            int lineCount = 0;
            int index = fullText.length();
            while (index >= 0 && lineCount < MAX_LINES) {
                index = fullText.lastIndexOf('\n', index - 1);
                lineCount++;
            }
            
            if (lineCount >= MAX_LINES && index >= 0) {
                fullText = fullText.substring(index + 1);
                outputBuffer = new StringBuilder(fullText);
            }
            
            tvOutput.setText(fullText);
            
            scrollView.post(() -> {
                scrollView.fullScroll(View.FOCUS_DOWN);
            });
        });
    }

    private void startLlamaService() {
        appendOutput("[Service] Starting LlamaCpp background service...\n");
        try {
            LlamaCppService.start(this);
            appendOutput("[Service] Service started successfully\n");
        } catch (Exception e) {
            appendOutput(String.format("[Service] Error starting service: %s\n", e.getMessage()));
        }
    }

    private void stopLlamaService() {
        appendOutput("[Service] Stopping LlamaCpp background service...\n");
        try {
            LlamaCppService.stop(this);
            appendOutput("[Service] Service stopped\n");
        } catch (Exception e) {
            appendOutput(String.format("[Service] Error stopping service: %s\n", e.getMessage()));
        }
    }

    private void runDemo() {
        appendOutput("=========================================\n");
        appendOutput("Senta AI Terminal Demo\n");
        appendOutput("=========================================\n\n");
        
        appendOutput("[1] Listing available files...\n");
        listFiles(new String[]{"ls"});
        
        appendOutput("\n[2] Running demo inference...\n");
        executeShellCommand("./main -p \"Hello from Senta AI!\" -n 128 --temp 0.7");
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format("%.2f GB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) return String.format("%.1f MB", mb);
        double kb = bytes / 1024.0;
        return String.format("%.0f KB", kb);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
