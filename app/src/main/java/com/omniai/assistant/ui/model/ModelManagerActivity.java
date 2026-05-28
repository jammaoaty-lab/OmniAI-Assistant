package com.omniai.assistant.ui.model;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsFeatureGate;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.manager.UserManager;
import com.omniai.assistant.ui.login.LoginActivity;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.modelmgmt.ModelManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModelManagerActivity extends AppCompatActivity {

    private static final int PICK_MODEL_FILE = 3001;
    private static final int TAB_LOCAL = 0;
    private static final int TAB_DOWNLOAD = 1;
    private static final int TAB_VISION = 2;

    private TabLayout tabLayout;
    private RecyclerView modelList;
    private View fabImport;
    private ModelCardAdapter adapter;
    private ModelManager modelManager;
    private VisionInferenceEngine visionEngine;
    private CreditsFeatureGate creditsFeatureGate;

    private List<AIModel> localModels = new ArrayList<>();
    private List<AIModel> downloadModels = new ArrayList<>();
    private List<AIModel> visionModels = new ArrayList<>();
    private int currentTab = TAB_LOCAL;

    private AlertDialog downloadDialog;
    private ProgressBar downloadProgressBar;
    private TextView downloadProgressText;
    private TextView downloadSizeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_manager);

        modelManager = ModelManager.getInstance(this);
        visionEngine = VisionInferenceEngine.getInstance();
        creditsFeatureGate = CreditsFeatureGate.getInstance();

        tabLayout = findViewById(R.id.tab_layout);
        modelList = findViewById(R.id.rv_models);
        fabImport = findViewById(R.id.fab_import);

        adapter = new ModelCardAdapter(new ArrayList<>(), new ModelCardAdapter.OnModelActionListener() {
            @Override
            public void onEnable(AIModel model) {
                if (currentTab == TAB_VISION) {
                    switchVisionModel(model);
                } else {
                    toggleModelEnable(model);
                }
            }

            @Override
            public void onDelete(AIModel model) {
                confirmDeleteModel(model);
            }

            @Override
            public void onClick(AIModel model) {
                handleModelClick(model);
            }
        });
        modelList.setLayoutManager(new LinearLayoutManager(this));
        modelList.setAdapter(adapter);

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.local_models)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.download_models)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.vision_models)));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateModelList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                loadModels();
            }
        });

        fabImport.setOnClickListener(v -> {
            if (currentTab == TAB_VISION) {
                restoreDefaultVisionModel();
            } else {
                showImportDialog();
            }
        });

        loadModels();
    }

    private void loadModels() {
        localModels = modelManager.getLocalModels();
        downloadModels = modelManager.getDownloadModels();
        loadVisionModels();
        updateModelList();
    }

    private void loadVisionModels() {
        visionModels.clear();

        List<AIModel> availableVisionModels = visionEngine.getAvailableVisionModels();
        List<AIModel> localVisionModels = modelManager.getVisionModels();
        List<AIModel> preinstalledModels = modelManager.getPreinstalledModels();

        for (AIModel available : availableVisionModels) {
            AIModel displayModel = findMatchById(available, localVisionModels);
            if (displayModel != null) {
                visionModels.add(displayModel);
            } else {
                displayModel = findMatchById(available, preinstalledModels);
                if (displayModel != null) {
                    visionModels.add(displayModel);
                } else {
                    visionModels.add(available);
                }
            }
        }

        for (AIModel local : localVisionModels) {
            if (!containsById(visionModels, local.getId())) {
                visionModels.add(local);
            }
        }

        List<AIModel> downloadableModels = modelManager.getDownloadableModels();
        for (AIModel downloadable : downloadableModels) {
            if ("VISION".equals(downloadable.getModelType()) && !containsById(visionModels, downloadable.getId())) {
                visionModels.add(downloadable);
            }
        }

        AIModel currentVision = visionEngine.getCurrentVisionModel();
        if (currentVision != null) {
            for (AIModel vm : visionModels) {
                if (vm.getId().equals(currentVision.getId())) {
                    vm.setEnabled(true);
                    vm.setLoaded(true);
                    break;
                }
            }
        }
    }

    private AIModel findMatchById(AIModel source, List<AIModel> list) {
        for (AIModel item : list) {
            if (item.getId().equals(source.getId())) {
                return item;
            }
        }
        return null;
    }

    private boolean containsById(List<AIModel> list, String id) {
        for (AIModel item : list) {
            if (item.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void updateModelList() {
        switch (currentTab) {
            case TAB_LOCAL:
                adapter.updateData(localModels);
                fabImport.setVisibility(View.VISIBLE);
                break;
            case TAB_DOWNLOAD:
                adapter.updateData(downloadModels);
                fabImport.setVisibility(View.GONE);
                break;
            case TAB_VISION:
                adapter.updateData(visionModels);
                fabImport.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void toggleModelEnable(AIModel model) {
        if (isModelOperating) {
            Toast.makeText(this, getString(R.string.operation_in_progress), Toast.LENGTH_SHORT).show();
            return;
        }

        isModelOperating = true;
        if (model.isEnabled()) {
            modelManager.unloadModel(model, new ModelManager.ModelCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isModelOperating = false;
                        model.setEnabled(false);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        isModelOperating = false;
                        Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            modelManager.loadModel(model, new ModelManager.ModelCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isModelOperating = false;
                        model.setEnabled(true);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        isModelOperating = false;
                        Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void switchVisionModel(AIModel model) {
        if (isModelOperating) {
            Toast.makeText(this, getString(R.string.operation_in_progress), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!visionEngine.checkHardwareCompatibility(model)) {
            new AlertDialog.Builder(this)
                    .setTitle("硬件不兼容")
                    .setMessage("当前设备硬件不满足 " + model.getName() + " 的运行要求，无法切换。")
                    .setPositiveButton("确定", null)
                    .show();
            return;
        }

        if (model.isEnabled() && model.isLoaded()) {
            Toast.makeText(this, model.getName() + " 已是当前视觉模型", Toast.LENGTH_SHORT).show();
            return;
        }

        isModelOperating = true;
        AlertDialog switchingDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.switching_vision_model))
                .setMessage(getString(R.string.switching_vision_model_message, model.getName()))
                .setCancelable(false)
                .create();
        switchingDialog.show();

        visionEngine.switchVisionModel(model, new VisionInferenceEngine.LoadCallback() {
            @Override
            public void onLoaded(AIModel loadedModel) {
                runOnUiThread(() -> {
                    isModelOperating = false;
                    switchingDialog.dismiss();
                    for (AIModel vm : visionModels) {
                        vm.setEnabled(false);
                        vm.setLoaded(false);
                    }
                    model.setEnabled(true);
                    model.setLoaded(true);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.switched_vision_model, model.getName()), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isModelOperating = false;
                    switchingDialog.dismiss();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.switch_vision_model_failed, error), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void restoreDefaultVisionModel() {
        AIModel defaultModel = visionEngine.getDefaultVisionModel();
        if (defaultModel == null) {
            Toast.makeText(this, getString(R.string.default_vision_model_not_found), Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog restoreDialog = new AlertDialog.Builder(this)
                .setTitle("恢复默认视觉模型")
                .setMessage("正在恢复默认视觉模型 " + defaultModel.getName() + "…")
                .setCancelable(false)
                .create();
        restoreDialog.show();

        modelManager.restoreDefaultVisionModel(new ModelManager.ModelCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    restoreDialog.dismiss();
                    for (AIModel vm : visionModels) {
                        vm.setEnabled(false);
                        vm.setLoaded(false);
                        if (vm.getId().equals(defaultModel.getId())) {
                            vm.setEnabled(true);
                            vm.setLoaded(true);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    Toast.makeText(ModelManagerActivity.this, "已恢复默认视觉模型: " + defaultModel.getName(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    restoreDialog.dismiss();
                    Toast.makeText(ModelManagerActivity.this, "恢复默认视觉模型失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void confirmDeleteModel(AIModel model) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.model_delete_confirm, model.getName()))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    modelManager.deleteModel(model, new ModelManager.ModelCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> loadModels());
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private boolean isModelOperating = false;

    private void handleModelClick(AIModel model) {
        if (isModelOperating) {
            Toast.makeText(this, getString(R.string.operation_in_progress), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentTab == TAB_DOWNLOAD) {
            downloadModel(model);
            return;
        }

        if (currentTab == TAB_VISION) {
            handleVisionModelClick(model);
            return;
        }

        if (model.isEnabled()) {
            modelManager.unloadModel(model, new ModelManager.ModelCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        model.setEnabled(false);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            modelManager.loadModel(model, new ModelManager.ModelCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        model.setEnabled(true);
                        adapter.notifyDataSetChanged();
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void handleVisionModelClick(AIModel model) {
        boolean isDownloaded = model.getFilePath() != null && !model.getFilePath().isEmpty()
                && new File(model.getFilePath()).exists();
        boolean isPreinstalled = model.isPreinstalled();

        if (isPreinstalled || isDownloaded) {
            switchVisionModel(model);
        } else {
            downloadVisionModel(model);
        }
    }

    private void downloadModel(AIModel model) {
        UserManager userManager = UserManager.getInstance(this);
        if (!userManager.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.login_required))
                    .setMessage(getString(R.string.login_required_download))
                    .setPositiveButton(getString(R.string.login), (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }

        long requiredSize = model.getDownloadSize() > 0 ? model.getDownloadSize() : model.getFileSize();
        if (!checkStorageSpace(requiredSize)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.storage_insufficient))
                    .setMessage(getString(R.string.storage_insufficient_message, model.getName(), formatFileSize(requiredSize)))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        showDownloadProgressDialog(model);

        modelManager.downloadModel(model, new ModelManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> updateDownloadProgress(progress));
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    dismissDownloadDialog();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.download_complete, model.getName()), Toast.LENGTH_SHORT).show();
                    loadModels();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    dismissDownloadDialog();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.download_failed, message), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void downloadVisionModel(AIModel model) {
        if (isAdvancedVisionModel(model)) {
            if (!creditsFeatureGate.canUseAdvancedVisionModel()) {
                creditsFeatureGate.showInsufficientCreditsDialog(this);
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.advanced_vision_model))
                    .setMessage(getString(R.string.advanced_vision_model_message, model.getName(), CreditsManager.CreditsFeature.ADVANCED_VISION_MODEL.getCost()))
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (creditsFeatureGate.deductIfNeeded(CreditsManager.CreditsFeature.ADVANCED_VISION_MODEL)) {
                            startVisionModelDownload(model);
                        } else {
                            creditsFeatureGate.showInsufficientCreditsDialog(this);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            startVisionModelDownload(model);
        }
    }

    private boolean isAdvancedVisionModel(AIModel model) {
        return model.getId() != null && model.getId().contains("7b");
    }

    private void startVisionModelDownload(AIModel model) {
        UserManager userManager = UserManager.getInstance(this);
        if (!userManager.isLoggedIn()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.login_required))
                    .setMessage(getString(R.string.login_required_download))
                    .setPositiveButton(getString(R.string.login), (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }

        long requiredSize = model.getDownloadSize() > 0 ? model.getDownloadSize() : model.getFileSize();
        if (!checkStorageSpace(requiredSize)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.storage_insufficient))
                    .setMessage(getString(R.string.storage_insufficient_message, model.getName(), formatFileSize(requiredSize)))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        showDownloadProgressDialog(model);

        modelManager.downloadModel(model, new ModelManager.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> updateDownloadProgress(progress));
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    dismissDownloadDialog();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.download_complete, model.getName()), Toast.LENGTH_SHORT).show();
                    loadModels();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    dismissDownloadDialog();
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.download_failed, message), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private boolean checkStorageSpace(long requiredSize) {
        File downloadDir = getExternalFilesDir(null);
        if (downloadDir == null) {
            downloadDir = getFilesDir();
        }
        if (!downloadDir.exists()) {
            downloadDir = new File("/data/local/tmp");
        }
        try {
            StatFs stat = new StatFs(downloadDir.getPath());
            long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return availableBytes > requiredSize * 2;
        } catch (Exception e) {
            return true;
        }
    }

    private void showDownloadProgressDialog(AIModel model) {
        long totalSize = model.getDownloadSize() > 0 ? model.getDownloadSize() : model.getFileSize();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(8));

        downloadProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadProgressBar.setMax(100);
        downloadProgressBar.setProgress(0);
        container.addView(downloadProgressBar);

        downloadProgressText = new TextView(this);
        downloadProgressText.setText("0%");
        downloadProgressText.setPadding(0, dpToPx(8), 0, 0);
        container.addView(downloadProgressText);

        downloadSizeText = new TextView(this);
        downloadSizeText.setText("0 B / " + formatFileSize(totalSize));
        downloadSizeText.setPadding(0, dpToPx(4), 0, 0);
        container.addView(downloadSizeText);

        downloadDialog = new AlertDialog.Builder(this)
                .setTitle("正在下载 " + model.getName())
                .setView(container)
                .setNegativeButton("取消", (dialog, which) -> {
                    modelManager.cancelDownload(model.getId());
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.download_cancelled), Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .create();
        downloadDialog.show();
    }

    private void updateDownloadProgress(int progress) {
        if (downloadProgressBar != null) {
            downloadProgressBar.setProgress(progress);
        }
        if (downloadProgressText != null) {
            downloadProgressText.setText(progress + "%");
        }
    }

    private void dismissDownloadDialog() {
        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.dismiss();
            downloadDialog = null;
            downloadProgressBar = null;
            downloadProgressText = null;
            downloadSizeText = null;
        }
    }

    private void showImportDialog() {
        String[] options = {getString(R.string.import_from_file), getString(R.string.import_from_url)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_model))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickModelFile();
                    } else {
                        showUrlImportDialog();
                    }
                })
                .show();
    }

    private void pickModelFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/octet-stream", "application/zip", "application/x-gzip"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_MODEL_FILE);
    }

    private void showUrlImportDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import_url, null);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_from_url))
                .setView(dialogView)
                .setPositiveButton("导入", (dialog, which) -> {
                    android.widget.EditText urlInput = dialogView.findViewById(R.id.input_url);
                    String url = urlInput.getText().toString().trim();
                    if (!url.isEmpty()) {
                        importModelFromUrl(url);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importModelFromUrl(String url) {
        modelManager.importModel(url, new ModelManager.ModelCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ModelManagerActivity.this, getString(R.string.import_success), Toast.LENGTH_SHORT).show();
                    loadModels();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MODEL_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                modelManager.importModel(uri, new ModelManager.ModelCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(ModelManagerActivity.this, getString(R.string.import_success), Toast.LENGTH_SHORT).show();
                            loadModels();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> Toast.makeText(ModelManagerActivity.this, message, Toast.LENGTH_SHORT).show());
                    }
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadModels();
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
