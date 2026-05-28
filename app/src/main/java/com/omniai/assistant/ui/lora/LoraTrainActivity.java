package com.omniai.assistant.ui.lora;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsFeatureGate;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.lora.DataSetProcessor;
import com.omniai.assistant.lora.LoraTrainManager;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.nativebridge.LlamaBridge;

import java.util.ArrayList;
import java.util.List;

public class LoraTrainActivity extends AppCompatActivity {

    private static final int PICK_DATASET_FILE = 4001;
    private static final int PICK_IMAGE_DATASET_FILE = 4002;
    private static final long HW_MONITOR_INTERVAL_MS = 3000L;
    private static final float TEMP_THRESHOLD = 45.0f;
    private static final int MEMORY_THRESHOLD_MB = 500;

    private SeekBar rankSeek;
    private SeekBar alphaSeek;
    private SeekBar epochsSeek;
    private SeekBar batchSizeSeek;
    private SeekBar dropoutSeek;
    private EditText lrInput;
    private EditText ctxInput;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView logOutput;
    private Button startBtn;
    private Button pauseBtn;
    private Button resumeBtn;
    private Button stopBtn;
    private Button exportBtn;

    private Spinner trainTargetSpinner;
    private Spinner visionModelSpinner;
    private LinearLayout visionModelSection;

    private LoraTrainManager trainManager;
    private DataSetProcessor dataSetProcessor;
    private Handler uiHandler;
    private Handler hwMonitorHandler;
    private Runnable hwMonitorRunnable;

    private LoraTrainManager.TrainState currentState = LoraTrainManager.TrainState.IDLE;

    private boolean isVisionMode = false;
    private List<AIModel> availableVisionModels = new ArrayList<>();
    private AIModel selectedVisionModel = null;

    private LlamaBridge llamaBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lora_train);

        trainManager = LoraTrainManager.getInstance(this);
        dataSetProcessor = new DataSetProcessor();
        uiHandler = new Handler(Looper.getMainLooper());
        hwMonitorHandler = new Handler(Looper.getMainLooper());
        llamaBridge = LlamaBridge.getInstance();

        rankSeek = findViewById(R.id.seek_rank);
        alphaSeek = findViewById(R.id.seek_alpha);
        epochsSeek = findViewById(R.id.seek_epochs);
        batchSizeSeek = findViewById(R.id.seek_batch_size);
        dropoutSeek = findViewById(R.id.seek_dropout);
        lrInput = findViewById(R.id.input_lr);
        ctxInput = findViewById(R.id.input_ctx);
        progressBar = findViewById(R.id.progress_bar);
        progressText = findViewById(R.id.tv_progress);
        logOutput = findViewById(R.id.tv_log);
        startBtn = findViewById(R.id.btn_start);
        pauseBtn = findViewById(R.id.btn_pause);
        resumeBtn = findViewById(R.id.btn_resume);
        stopBtn = findViewById(R.id.btn_stop);
        exportBtn = findViewById(R.id.btn_export);

        trainTargetSpinner = findViewById(R.id.spinner_train_target);
        visionModelSpinner = findViewById(R.id.spinner_vision_model);
        visionModelSection = findViewById(R.id.layout_vision_model_section);

        setupTrainTargetSpinner();
        setupVisionModelSpinner();
        setupSeekBarListeners();
        setupButtons();
        updateButtonStates();
    }

    private void setupTrainTargetSpinner() {
        String[] targets = {"文本模型", "视觉模型"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, targets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        trainTargetSpinner.setAdapter(adapter);

        trainTargetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                isVisionMode = (position == 1);
                visionModelSection.setVisibility(isVisionMode ? View.VISIBLE : View.GONE);
                if (isVisionMode && availableVisionModels.isEmpty()) {
                    loadVisionModels();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupVisionModelSpinner() {
        loadVisionModels();
    }

    private void loadVisionModels() {
        availableVisionModels = VisionInferenceEngine.getInstance().getAvailableVisionModels();
        List<String> modelNames = new ArrayList<>();
        for (AIModel model : availableVisionModels) {
            modelNames.add(model.getName() + " (" + model.getQuantType() + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modelNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        visionModelSpinner.setAdapter(adapter);

        visionModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableVisionModels.size()) {
                    selectedVisionModel = availableVisionModels.get(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedVisionModel = null;
            }
        });

        if (!availableVisionModels.isEmpty()) {
            selectedVisionModel = availableVisionModels.get(0);
        }
    }

    private void setupSeekBarListeners() {
        TextView rankLabel = findViewById(R.id.tv_rank_value);
        rankSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rankLabel.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView alphaLabel = findViewById(R.id.tv_alpha_value);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaLabel.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView epochsLabel = findViewById(R.id.tv_epochs_value);
        epochsSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                epochsLabel.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView batchLabel = findViewById(R.id.tv_batch_value);
        batchSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                batchLabel.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView dropoutLabel = findViewById(R.id.tv_dropout_value);
        dropoutSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100.0f;
                dropoutLabel.setText(String.format("%.2f", value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupButtons() {
        startBtn.setOnClickListener(v -> startTraining());
        pauseBtn.setOnClickListener(v -> pauseTraining());
        resumeBtn.setOnClickListener(v -> resumeTraining());
        stopBtn.setOnClickListener(v -> stopTraining());
        exportBtn.setOnClickListener(v -> exportModel());

        findViewById(R.id.btn_import_dataset).setOnClickListener(v -> {
            if (isVisionMode) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                String[] mimeTypes = {
                        "application/json",
                        "text/plain",
                        "application/zip",
                        "image/png",
                        "image/jpeg",
                        "image/webp"
                };
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(intent, PICK_IMAGE_DATASET_FILE);
            } else {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                String[] mimeTypes = {"application/json", "text/plain", "application/zip"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(intent, PICK_DATASET_FILE);
            }
        });
    }

    private void startTraining() {
        if (!CreditsFeatureGate.getInstance().canTrainLora()) {
            CreditsFeatureGate.getInstance().showInsufficientCreditsDialog(this);
            return;
        }

        int rank = rankSeek.getProgress() + 1;
        int alpha = alphaSeek.getProgress() + 1;
        int epochs = epochsSeek.getProgress() + 1;
        int batchSize = batchSizeSeek.getProgress() + 1;
        float dropout = dropoutSeek.getProgress() / 100.0f;

        String lrStr = lrInput.getText().toString().trim();
        String ctxStr = ctxInput.getText().toString().trim();

        if (lrStr.isEmpty()) {
            Toast.makeText(this, R.string.error_lr_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ctxStr.isEmpty()) {
            Toast.makeText(this, R.string.error_ctx_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        float learningRate;
        int contextLength;
        try {
            learningRate = Float.parseFloat(lrStr);
            contextLength = Integer.parseInt(ctxStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_number, Toast.LENGTH_SHORT).show();
            return;
        }

        if (isVisionMode && selectedVisionModel == null) {
            Toast.makeText(this, "请选择视觉模型", Toast.LENGTH_SHORT).show();
            return;
        }

        LoraTrainManager.TrainConfig config = new LoraTrainManager.TrainConfig(
                rank, alpha, epochs, batchSize, dropout, learningRate, contextLength
        );

        if (isVisionMode) {
            config.setTargetModel(selectedVisionModel);
        }

        currentState = LoraTrainManager.TrainState.TRAINING;
        updateButtonStates();

        LoraTrainManager.TrainCallback callback = new LoraTrainManager.TrainCallback() {
            @Override
            public void onProgress(int current, int total, float loss) {
                uiHandler.post(() -> {
                    int percent = (int) ((current / (float) total) * 100);
                    progressBar.setProgress(percent);
                    progressText.setText(getString(R.string.train_progress_format, current, total, loss));
                });
            }

            @Override
            public void onLog(String message) {
                uiHandler.post(() -> logOutput.append(message + "\n"));
            }

            @Override
            public void onComplete() {
                uiHandler.post(() -> {
                    stopHwMonitoring();
                    currentState = LoraTrainManager.TrainState.COMPLETED;
                    updateButtonStates();
                    progressBar.setProgress(100);
                    progressText.setText(R.string.train_completed);
                    Toast.makeText(LoraTrainActivity.this, R.string.train_completed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> {
                    stopHwMonitoring();
                    currentState = LoraTrainManager.TrainState.IDLE;
                    updateButtonStates();
                    Toast.makeText(LoraTrainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (isVisionMode) {
            trainManager.startVisionTraining(config, selectedVisionModel, callback);
        } else {
            trainManager.startTraining(config, callback);
        }

        startHwMonitoring();
    }

    private void pauseTraining() {
        trainManager.pauseTraining();
        stopHwMonitoring();
        currentState = LoraTrainManager.TrainState.PAUSED;
        updateButtonStates();
    }

    private void resumeTraining() {
        trainManager.resumeTraining();
        currentState = LoraTrainManager.TrainState.TRAINING;
        updateButtonStates();
        startHwMonitoring();
    }

    private void stopTraining() {
        trainManager.stopTraining();
        stopHwMonitoring();
        currentState = LoraTrainManager.TrainState.IDLE;
        updateButtonStates();
        progressBar.setProgress(0);
        progressText.setText("");
    }

    private void exportModel() {
        trainManager.exportModel(new LoraTrainManager.ExportCallback() {
            @Override
            public void onSuccess(String path) {
                uiHandler.post(() -> Toast.makeText(LoraTrainActivity.this,
                        getString(R.string.export_success, path), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> Toast.makeText(LoraTrainActivity.this,
                        message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startHwMonitoring() {
        stopHwMonitoring();
        hwMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState != LoraTrainManager.TrainState.TRAINING) {
                    return;
                }
                checkHardwareStatus();
                hwMonitorHandler.postDelayed(this, HW_MONITOR_INTERVAL_MS);
            }
        };
        hwMonitorHandler.postDelayed(hwMonitorRunnable, HW_MONITOR_INTERVAL_MS);
    }

    private void stopHwMonitoring() {
        if (hwMonitorRunnable != null) {
            hwMonitorHandler.removeCallbacks(hwMonitorRunnable);
            hwMonitorRunnable = null;
        }
    }

    private void checkHardwareStatus() {
        if (llamaBridge == null) {
            return;
        }

        float temperature = llamaBridge.getDeviceTemperature();
        int availableMemoryMb = llamaBridge.getDeviceMemory();

        if (temperature > TEMP_THRESHOLD) {
            pauseTraining();
            logOutput.append("[警告] " + getString(R.string.train_device_overheated) + " (" + String.format("%.1f", temperature)
                    + "°C)\n");
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.train_device_overheated))
                    .setMessage("当前设备温度 " + String.format("%.1f", temperature)
                            + "°C 已超过安全阈值 " + (int) TEMP_THRESHOLD
                            + "°C，训练已自动暂停。请等待设备冷却后继续训练。")
                    .setPositiveButton(R.string.ok, null)
                    .setCancelable(false)
                    .show();
            return;
        }

        if (availableMemoryMb > 0 && availableMemoryMb < MEMORY_THRESHOLD_MB) {
            pauseTraining();
            logOutput.append("[警告] " + getString(R.string.train_memory_low_message) + " (" + availableMemoryMb
                    + "MB)\n");
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.train_memory_low_message))
                    .setMessage("当前可用内存 " + availableMemoryMb
                            + "MB 低于安全阈值 " + MEMORY_THRESHOLD_MB
                            + "MB，训练已自动暂停。请关闭其他应用释放内存后继续训练。")
                    .setPositiveButton(R.string.ok, null)
                    .setCancelable(false)
                    .show();
        }
    }

    private void updateButtonStates() {
        startBtn.setVisibility(currentState == LoraTrainManager.TrainState.IDLE ? View.VISIBLE : View.GONE);
        pauseBtn.setVisibility(currentState == LoraTrainManager.TrainState.TRAINING ? View.VISIBLE : View.GONE);
        stopBtn.setVisibility(currentState == LoraTrainManager.TrainState.TRAINING || currentState == LoraTrainManager.TrainState.PAUSED ? View.VISIBLE : View.GONE);
        resumeBtn.setVisibility(currentState == LoraTrainManager.TrainState.PAUSED ? View.VISIBLE : View.GONE);
        exportBtn.setVisibility(currentState == LoraTrainManager.TrainState.COMPLETED ? View.VISIBLE : View.GONE);

        boolean canEdit = currentState == LoraTrainManager.TrainState.IDLE;
        rankSeek.setEnabled(canEdit);
        alphaSeek.setEnabled(canEdit);
        epochsSeek.setEnabled(canEdit);
        batchSizeSeek.setEnabled(canEdit);
        dropoutSeek.setEnabled(canEdit);
        lrInput.setEnabled(canEdit);
        ctxInput.setEnabled(canEdit);
        trainTargetSpinner.setEnabled(canEdit);
        visionModelSpinner.setEnabled(canEdit);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == PICK_DATASET_FILE) {
            dataSetProcessor.process(uri, new DataSetProcessor.ProcessCallback() {
                @Override
                public void onSuccess(String datasetId) {
                    uiHandler.post(() -> {
                        trainManager.setDataset(datasetId);
                        Toast.makeText(LoraTrainActivity.this,
                                R.string.dataset_imported, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String message) {
                    uiHandler.post(() -> Toast.makeText(LoraTrainActivity.this,
                            message, Toast.LENGTH_SHORT).show());
                }
            });
        } else if (requestCode == PICK_IMAGE_DATASET_FILE) {
            String mimeType = getContentResolver().getType(uri);
            boolean isImage = mimeType != null && mimeType.startsWith("image/");

            if (isImage) {
                dataSetProcessor.processImageTextDataset(uri, new DataSetProcessor.ProcessCallback() {
                    @Override
                    public void onSuccess(String datasetId) {
                        uiHandler.post(() -> {
                            trainManager.setDataset(datasetId);
                            Toast.makeText(LoraTrainActivity.this,
                                    "图像数据集已导入", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        uiHandler.post(() -> Toast.makeText(LoraTrainActivity.this,
                                message, Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                dataSetProcessor.process(uri, new DataSetProcessor.ProcessCallback() {
                    @Override
                    public void onSuccess(String datasetId) {
                        uiHandler.post(() -> {
                            trainManager.setDataset(datasetId);
                            Toast.makeText(LoraTrainActivity.this,
                                    R.string.dataset_imported, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        uiHandler.post(() -> Toast.makeText(LoraTrainActivity.this,
                                message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopHwMonitoring();
    }
}
