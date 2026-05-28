package com.omniai.assistant.ui.settings;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsFeatureGate;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.settings.SettingsManager;
import com.omniai.assistant.ui.credits.CreditsCenterActivity;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final int DEV_MODE_TAP_COUNT = 7;

    private RecyclerView settingsList;
    private SettingsAdapter adapter;
    private SettingsManager settingsManager;
    private CreditsManager creditsManager;
    private CreditsFeatureGate creditsFeatureGate;

    private int versionTapCount = 0;
    private Handler tapResetHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsManager = SettingsManager.getInstance(this);
        creditsManager = CreditsManager.getInstance();
        creditsFeatureGate = CreditsFeatureGate.getInstance();
        tapResetHandler = new Handler(Looper.getMainLooper());

        settingsList = findViewById(R.id.rv_settings);
        adapter = new SettingsAdapter(buildSettingItems(), new SettingsAdapter.OnSettingChangeListener() {
            @Override
            public void onSwitchChanged(String key, boolean value) {
                if (isPremiumSwitch(key) && value) {
                    if (!checkCreditsForPremiumFeature(key)) {
                        creditsFeatureGate.showInsufficientCreditsDialog(SettingsActivity.this);
                        adapter.updateData(buildSettingItems());
                        return;
                    }
                }
                settingsManager.putBoolean(key, value);
                handleSwitchChange(key, value);
            }

            @Override
            public void onClicked(String key) {
                handleSettingClick(key);
            }
        });

        settingsList.setLayoutManager(new LinearLayoutManager(this));
        settingsList.setAdapter(adapter);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
    }

    private boolean isPremiumSwitch(String key) {
        return "云端GPU加速".equals(key) || "超长上下文".equals(key);
    }

    private boolean checkCreditsForPremiumFeature(String key) {
        if ("云端GPU加速".equals(key)) {
            return creditsFeatureGate.canUseCloudGpu();
        }
        if ("超长上下文".equals(key)) {
            return creditsFeatureGate.canUseLongContext();
        }
        return false;
    }

    private List<SettingsAdapter.SettingItem> buildSettingItems() {
        List<SettingsAdapter.SettingItem> items = new ArrayList<>();

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_group_general), null, 0, null, false, false, false, "general"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_language),
                settingsManager.getLanguage(),
                R.drawable.ic_language,
                null, false, false, true, "general"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_theme),
                settingsManager.getTheme(),
                R.drawable.ic_theme,
                null, false, false, true, "general"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_notifications),
                null,
                R.drawable.ic_notifications,
                null, true, settingsManager.isNotificationsEnabled(), false, "general"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_group_ai), null, 0, null, false, false, false, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_default_model),
                settingsManager.getDefaultModel(),
                R.drawable.ic_model,
                null, false, false, true, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_inference_mode),
                settingsManager.getInferenceMode(),
                R.drawable.ic_inference,
                null, false, false, true, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_stream_output),
                null,
                R.drawable.ic_stream,
                null, true, settingsManager.isStreamOutput(), false, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_temperature),
                String.valueOf(settingsManager.getTemperature()),
                R.drawable.ic_temperature,
                null, false, false, true, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "云端GPU加速",
                null,
                R.drawable.ic_gpu,
                null, true, settingsManager.isCloudGpuEnabled(), false, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "超长上下文",
                null,
                R.drawable.ic_context,
                null, true, settingsManager.isLongContextEnabled(), false, "ai"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "视觉", null, 0, null, false, false, false, "vision"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "视觉GPU加速",
                null,
                R.drawable.ic_gpu,
                null, true, settingsManager.isVisionGpuEnabled(), false, "vision"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "图像自动OCR",
                null,
                R.drawable.ic_ocr,
                null, true, settingsManager.isAutoOcrEnabled(), false, "vision"
        ));

        String visionMode = settingsManager.getVisionInferenceMode();
        if (visionMode == null || visionMode.isEmpty()) {
            visionMode = "均衡";
        }
        items.add(new SettingsAdapter.SettingItem(
                "视觉推理模式",
                visionMode,
                R.drawable.ic_inference,
                null, false, false, true, "vision"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "图片缓存自动清理",
                null,
                R.drawable.ic_cache,
                null, true, settingsManager.isImageCacheAutoClean(), false, "vision"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "积分", null, 0, null, false, false, false, "credits"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "积分余额",
                creditsManager.getCredits() + "积分",
                R.drawable.ic_credits,
                null, false, false, true, "credits"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "积分消耗提醒",
                null,
                R.drawable.ic_warning,
                null, true, settingsManager.isCreditsConsumeWarning(), false, "credits"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_group_privacy), null, 0, null, false, false, false, "privacy"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_analytics),
                null,
                R.drawable.ic_analytics,
                null, true, settingsManager.isAnalyticsEnabled(), false, "privacy"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_crash_report),
                null,
                R.drawable.ic_crash,
                null, true, settingsManager.isCrashReportEnabled(), false, "privacy"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_clear_cache),
                settingsManager.getCacheSize(),
                R.drawable.ic_cache,
                null, false, false, true, "privacy"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_group_about), null, 0, null, false, false, false, "about"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_version),
                settingsManager.getVersionName(),
                R.drawable.ic_version,
                null, false, false, false, "about"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_check_update),
                null,
                R.drawable.ic_update,
                null, false, false, true, "about"
        ));

        items.add(new SettingsAdapter.SettingItem(
                getString(R.string.settings_licenses),
                null,
                R.drawable.ic_licenses,
                null, false, false, true, "about"
        ));

        items.add(new SettingsAdapter.SettingItem(
                "关于我们",
                null,
                R.drawable.ic_info,
                null, false, false, true, "about"
        ));

        if (settingsManager.isDeveloperMode()) {
            items.add(new SettingsAdapter.SettingItem(
                    getString(R.string.settings_group_developer), null, 0, null, false, false, false, "developer"
            ));

            items.add(new SettingsAdapter.SettingItem(
                    getString(R.string.settings_developer_mode),
                    null,
                    R.drawable.ic_developer,
                    null, true, true, false, "developer"
            ));

            items.add(new SettingsAdapter.SettingItem(
                    getString(R.string.settings_debug_log),
                    null,
                    R.drawable.ic_log,
                    null, true, settingsManager.isDebugLogEnabled(), false, "developer"
            ));

            items.add(new SettingsAdapter.SettingItem(
                    getString(R.string.settings_api_endpoint),
                    settingsManager.getApiEndpoint(),
                    R.drawable.ic_api,
                    null, false, false, true, "developer"
            ));
        }

        items.add(new SettingsAdapter.SettingItem(
                "恢复默认设置",
                null,
                R.drawable.ic_reset,
                null, false, false, true, "reset"
        ));

        return items;
    }

    private void handleSwitchChange(String key, boolean value) {
        switch (key) {
            case "settings_notifications":
                settingsManager.setNotificationsEnabled(value);
                break;
            case "settings_stream_output":
                settingsManager.setStreamOutput(value);
                break;
            case "settings_analytics":
                settingsManager.setAnalyticsEnabled(value);
                break;
            case "settings_crash_report":
                settingsManager.setCrashReportEnabled(value);
                break;
            case "settings_developer_mode":
                settingsManager.setDeveloperMode(value);
                break;
            case "settings_debug_log":
                settingsManager.setDebugLogEnabled(value);
                break;
            case "视觉GPU加速":
                settingsManager.setVisionGpuEnabled(value);
                break;
            case "图像自动OCR":
                settingsManager.setAutoOcrEnabled(value);
                break;
            case "图片缓存自动清理":
                settingsManager.setImageCacheAutoClean(value);
                break;
            case "积分消耗提醒":
                settingsManager.setCreditsConsumeWarning(value);
                break;
            case "云端GPU加速":
                settingsManager.setCloudGpuEnabled(value);
                break;
            case "超长上下文":
                settingsManager.setLongContextEnabled(value);
                break;
        }
    }

    private void handleSettingClick(String key) {
        switch (key) {
            case "settings_language":
                showLanguagePicker();
                break;
            case "settings_theme":
                showThemePicker();
                break;
            case "settings_default_model":
                showModelPicker();
                break;
            case "settings_inference_mode":
                showInferenceModePicker();
                break;
            case "settings_temperature":
                showTemperatureDialog();
                break;
            case "settings_clear_cache":
                clearCache();
                break;
            case "settings_version":
                handleVersionTap();
                break;
            case "settings_check_update":
                checkForUpdate();
                break;
            case "settings_licenses":
                showLicenses();
                break;
            case "settings_api_endpoint":
                showApiEndpointDialog();
                break;
            case "视觉推理模式":
                showVisionInferenceModePicker();
                break;
            case "积分余额":
                navigateToCreditsCenter();
                break;
            case "关于我们":
                showAboutDialog();
                break;
            case "恢复默认设置":
                showResetToDefaultsDialog();
                break;
        }
    }

    private void showLanguagePicker() {
        String[] languages = getResources().getStringArray(R.array.languages);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_language)
                .setItems(languages, (dialog, which) -> {
                    settingsManager.setLanguage(languages[which]);
                    adapter.updateData(buildSettingItems());
                })
                .show();
    }

    private void showThemePicker() {
        String[] themes = getResources().getStringArray(R.array.themes);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_theme)
                .setItems(themes, (dialog, which) -> {
                    settingsManager.setTheme(themes[which]);
                    adapter.updateData(buildSettingItems());
                })
                .show();
    }

    private void showModelPicker() {
        String[] models = settingsManager.getAvailableModels();
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_default_model)
                .setItems(models, (dialog, which) -> {
                    settingsManager.setDefaultModel(models[which]);
                    adapter.updateData(buildSettingItems());
                })
                .show();
    }

    private void showInferenceModePicker() {
        String[] modes = getResources().getStringArray(R.array.inference_modes);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_inference_mode)
                .setItems(modes, (dialog, which) -> {
                    settingsManager.setInferenceMode(modes[which]);
                    adapter.updateData(buildSettingItems());
                })
                .show();
    }

    private void showTemperatureDialog() {
        android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
        seekBar.setMax(20);
        seekBar.setProgress((int) (settingsManager.getTemperature() * 10));
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_temperature)
                .setView(seekBar)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    float temp = seekBar.getProgress() / 10.0f;
                    settingsManager.setTemperature(temp);
                    adapter.updateData(buildSettingItems());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearCache() {
        settingsManager.clearCache(new SettingsManager.CacheCallback() {
            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                    adapter.updateData(buildSettingItems());
                });
            }
        });
    }

    private void handleVersionTap() {
        versionTapCount++;
        tapResetHandler.removeCallbacksAndMessages(null);
        tapResetHandler.postDelayed(() -> versionTapCount = 0, 3000);

        if (versionTapCount >= DEV_MODE_TAP_COUNT) {
            versionTapCount = 0;
            if (!settingsManager.isDeveloperMode()) {
                settingsManager.setDeveloperMode(true);
                Toast.makeText(this, R.string.developer_mode_enabled, Toast.LENGTH_SHORT).show();
                adapter.updateData(buildSettingItems());
            } else {
                Toast.makeText(this, R.string.developer_mode_already_enabled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkForUpdate() {
        settingsManager.checkForUpdate(new SettingsManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String version) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        getString(R.string.update_available, version), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onUpToDate() {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        R.string.already_up_to_date, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showLicenses() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_licenses)
                .setMessage(settingsManager.getLicenses())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showApiEndpointDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(settingsManager.getApiEndpoint());
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.settings_api_endpoint)
                .setView(input)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String endpoint = input.getText().toString().trim();
                    settingsManager.setApiEndpoint(endpoint);
                    adapter.updateData(buildSettingItems());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showVisionInferenceModePicker() {
        String[] modes = {"极速", "均衡", "高精度"};
        String currentMode = settingsManager.getVisionInferenceMode();
        int selectedIndex = 1;
        if ("极速".equals(currentMode)) {
            selectedIndex = 0;
        } else if ("均衡".equals(currentMode)) {
            selectedIndex = 1;
        } else if ("高精度".equals(currentMode)) {
            selectedIndex = 2;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("视觉推理模式")
                .setSingleChoiceItems(modes, selectedIndex, (dialog, which) -> {
                    settingsManager.setVisionInferenceMode(modes[which]);
                    dialog.dismiss();
                    adapter.updateData(buildSettingItems());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void navigateToCreditsCenter() {
        Intent intent = new Intent(this, CreditsCenterActivity.class);
        startActivity(intent);
    }

    private void showAboutDialog() {
        String appName = getString(R.string.app_name);
        String versionName = "";
        int versionCode = 0;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "Unknown";
        }

        String message = "应用名称：" + appName + "\n"
                + "版本号：" + versionName + "\n"
                + "版本代码：" + versionCode + "\n"
                + "推理架构：llama.cpp + JNI\n\n"
                + "Senta AI 是一款基于 llama.cpp 的本地 AI 助手，\n"
                + "支持多模态视觉推理、LoRA 微调、\n"
                + "知识库构建、云端加速等高级功能。";

        new android.app.AlertDialog.Builder(this)
                .setTitle("关于我们")
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showResetToDefaultsDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("恢复默认设置")
                .setMessage("确定要恢复所有设置为默认值吗？此操作不可撤销。")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    settingsManager.resetToDefaults();
                    Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
                    adapter.updateData(buildSettingItems());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
