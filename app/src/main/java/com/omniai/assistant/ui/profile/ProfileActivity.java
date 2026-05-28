package com.omniai.assistant.ui.profile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.multimodal.MultimodalManager;
import com.omniai.assistant.user.UserManager;
import com.omniai.assistant.user.UserProfile;
import com.omniai.assistant.ui.credits.CreditsCenterActivity;
import com.omniai.assistant.ui.knowledge.KnowledgeBaseActivity;
import com.omniai.assistant.ui.login.LoginActivity;
import com.omniai.assistant.ui.lora.LoraTrainActivity;
import com.omniai.assistant.ui.model.ModelManagerActivity;
import com.omniai.assistant.ui.settings.SettingsActivity;
import com.omniai.assistant.ui.terminal.TerminalActivity;

import java.io.File;

public class ProfileActivity extends AppCompatActivity {

    private ImageView avatarView;
    private TextView nicknameText;
    private TextView uidText;
    private TextView creditsBadgeText;
    private TextView deviceCountText;
    private TextView creditsBalanceText;
    private TextView inviteCodeText;
    private View creditsCard;
    private TextView visionModelText;

    private UserManager userManager;
    private CreditsManager creditsManager;
    private MultimodalManager multimodalManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        userManager = UserManager.getInstance();
        creditsManager = CreditsManager.getInstance();
        multimodalManager = MultimodalManager.getInstance(this);

        initViews();
        loadUserProfile();
        setupCreditsCard();
        setupVisionModelSection();
        setupMenuItems();
        addExtraMenuItems();
        setupLogout();
    }

    private void initViews() {
        avatarView = findViewById(R.id.iv_avatar);
        nicknameText = findViewById(R.id.tv_nickname);
        uidText = findViewById(R.id.tv_uid);
        creditsBadgeText = findViewById(R.id.tv_vip_badge);
        deviceCountText = findViewById(R.id.tv_device_count);
        creditsCard = findViewById(R.id.cv_vip);
        creditsBalanceText = findViewById(R.id.tv_vip_plan);
        inviteCodeText = findViewById(R.id.tv_vip_expiry);
    }

    private void loadUserProfile() {
        UserProfile profile = userManager.getCurrentUser();
        if (profile != null) {
            Glide.with(this)
                    .load(profile.getAvatar())
                    .placeholder(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(avatarView);

            nicknameText.setText(profile.getNickname());
            uidText.setText("UID: " + profile.getUserId());
        }
        deviceCountText.setText("1台设备");
    }

    private void setupCreditsCard() {
        creditsCard.setOnClickListener(v -> {
            startActivity(new Intent(this, CreditsCenterActivity.class));
        });

        MaterialButton upgradeBtn = findViewById(R.id.btn_upgrade);
        if (upgradeBtn != null) {
            upgradeBtn.setText("积分中心");
            upgradeBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, CreditsCenterActivity.class));
            });
        }

        inviteCodeText.setOnClickListener(v -> {
            String inviteCode = creditsManager.getInviteCode();
            if (inviteCode != null && !inviteCode.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Invite Code", inviteCode);
                    clipboard.setPrimaryClip(clip);
                    showSnackbar("邀请码已复制");
                }
            } else {
                showSnackbar("暂无邀请码");
            }
        });

        updateCreditsDisplay();
    }

    private void updateCreditsDisplay() {
        int credits = creditsManager.getCredits();
        String inviteCode = creditsManager.getInviteCode();

        creditsBadgeText.setText("积分");
        creditsBalanceText.setText(credits + " 积分");
        inviteCodeText.setText("邀请码: " + (inviteCode == null || inviteCode.isEmpty() ? "暂无" : inviteCode));
    }

    private void setupVisionModelSection() {
        if (creditsCard == null) return;

        ViewParent parent = creditsCard.getParent();
        if (!(parent instanceof LinearLayout)) return;
        LinearLayout mainLayout = (LinearLayout) parent;
        int cardIndex = mainLayout.indexOfChild(creditsCard);

        LinearLayout visionSection = new LinearLayout(this);
        visionSection.setOrientation(LinearLayout.HORIZONTAL);
        visionSection.setGravity(Gravity.CENTER_VERTICAL);
        int paddingH = (int) (16 * getResources().getDisplayMetrics().density);
        int paddingV = (int) (12 * getResources().getDisplayMetrics().density);
        visionSection.setPadding(paddingH, paddingV, paddingH, paddingV);
        visionSection.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));

        TextView label = new TextView(this);
        label.setText("视觉模型");
        label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        label.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelParams);

        visionModelText = new TextView(this);
        AIModel currentModel = multimodalManager.getCurrentVisionModel();
        visionModelText.setText(currentModel != null ? currentModel.getName() : "未加载");
        visionModelText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        visionModelText.setTextSize(14);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(android.R.drawable.ic_media_play);
        int arrowSize = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(arrowSize, arrowSize);
        int arrowMarginStart = (int) (8 * getResources().getDisplayMetrics().density);
        arrowParams.setMarginStart(arrowMarginStart);
        arrow.setLayoutParams(arrowParams);
        arrow.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary));

        visionSection.addView(label);
        visionSection.addView(visionModelText);
        visionSection.addView(arrow);

        visionSection.setOnClickListener(v -> showVisionModelDialog());

        mainLayout.addView(visionSection, cardIndex + 1);
    }

    private void setupMenuItems() {
        setupSettingItem(R.id.item_model_center, "模型中心", null);
        setupSettingItem(R.id.item_lora_train, "LoRA训练", null);
        setupSettingItem(R.id.item_knowledge_base, "知识库", null);
        setupSettingItem(R.id.item_voice_assistant, "语音助手", null);
        setupSettingItem(R.id.item_cache_manager, "缓存管理", null);
        setupSettingItem(R.id.item_download_manager, "下载管理", null);
        setupSettingItem(R.id.item_privacy_security, "隐私与安全", null);
        setupSettingItem(R.id.item_developer_mode, "开发者模式", null);

        View itemModelCenter = findViewById(R.id.item_model_center);
        if (itemModelCenter != null) {
            itemModelCenter.setOnClickListener(v -> {
                startActivity(new Intent(this, ModelManagerActivity.class));
            });
        }

        View itemLoraTrain = findViewById(R.id.item_lora_train);
        if (itemLoraTrain != null) {
            itemLoraTrain.setOnClickListener(v -> {
                startActivity(new Intent(this, LoraTrainActivity.class));
            });
        }

        View itemKnowledgeBase = findViewById(R.id.item_knowledge_base);
        if (itemKnowledgeBase != null) {
            itemKnowledgeBase.setOnClickListener(v -> {
                startActivity(new Intent(this, KnowledgeBaseActivity.class));
            });
        }

        View itemVoiceAssistant = findViewById(R.id.item_voice_assistant);
        if (itemVoiceAssistant != null) {
            itemVoiceAssistant.setOnClickListener(v -> {
                startVoiceAssistant();
            });
        }

        View itemCacheManager = findViewById(R.id.item_cache_manager);
        if (itemCacheManager != null) {
            itemCacheManager.setOnClickListener(v -> {
                showCacheManagerDialog();
            });
        }

        View itemDownloadManager = findViewById(R.id.item_download_manager);
        if (itemDownloadManager != null) {
            itemDownloadManager.setOnClickListener(v -> {
                showDownloadManagerDialog();
            });
        }

        View itemPrivacySecurity = findViewById(R.id.item_privacy_security);
        if (itemPrivacySecurity != null) {
            itemPrivacySecurity.setOnClickListener(v -> {
                showPrivacySecurityDialog();
            });
        }

        View itemDeveloperMode = findViewById(R.id.item_developer_mode);
        if (itemDeveloperMode != null) {
            itemDeveloperMode.setOnClickListener(v -> {
                showDeveloperModeDialog();
            });
        }
    }

    private void setupSettingItem(int id, String title, String subtitle) {
        View itemView = findViewById(id);
        if (itemView == null) return;
        TextView titleView = itemView.findViewById(R.id.tv_title);
        if (titleView != null) titleView.setText(title);
        if (subtitle != null) {
            TextView subtitleView = itemView.findViewById(R.id.tv_subtitle);
            if (subtitleView != null) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(View.VISIBLE);
            }
        }
        ImageView arrow = itemView.findViewById(R.id.iv_arrow);
        if (arrow != null) arrow.setVisibility(View.VISIBLE);
    }

    private void addExtraMenuItems() {
        LinearLayout menuContainer = findViewById(R.id.ll_menu_items);
        if (menuContainer == null) return;

        addDivider(menuContainer);

        addMenuItem(menuContainer, "积分中心", v -> {
            startActivity(new Intent(this, CreditsCenterActivity.class));
        });

        addMenuItem(menuContainer, "多模态", v -> {
            showMultimodalDialog();
        });

        addMenuItem(menuContainer, "终端", v -> {
            startActivity(new Intent(this, TerminalActivity.class));
        });

        addDivider(menuContainer);

        addMenuItem(menuContainer, "设置", v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        addMenuItem(menuContainer, "关于", v -> {
            showAboutDialog();
        });
    }

    private void addMenuItem(LinearLayout container, String title, View.OnClickListener listener) {
        View itemView = getLayoutInflater().inflate(R.layout.item_setting, container, false);
        TextView titleView = itemView.findViewById(R.id.tv_title);
        if (titleView != null) titleView.setText(title);
        ImageView arrow = itemView.findViewById(R.id.iv_arrow);
        if (arrow != null) arrow.setVisibility(View.VISIBLE);
        itemView.setOnClickListener(listener);
        container.addView(itemView);
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (1 * getResources().getDisplayMetrics().density)
        );
        int marginStart = (int) (16 * getResources().getDisplayMetrics().density);
        params.setMarginStart(marginStart);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
        container.addView(divider);
    }

    private void startVoiceAssistant() {
        multimodalManager.startVoiceRecognition(new MultimodalManager.VoiceCallback() {
            @Override
            public void onResult(String text) {
                runOnUiThread(() -> showSnackbar("语音识别结果: " + text));
            }

            @Override
            public void onPartial(String text) {
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showSnackbar("语音识别失败: " + error));
            }
        });
    }

    private void showCacheManagerDialog() {
        File cacheDir = getCacheDir();
        long cacheSize = calculateDirSize(cacheDir);
        String cacheSizeStr = formatFileSize(cacheSize);

        new AlertDialog.Builder(this)
                .setTitle("缓存管理")
                .setMessage("当前缓存大小: " + cacheSizeStr)
                .setPositiveButton("清除缓存", (dialog, which) -> {
                    deleteDir(cacheDir);
                    showSnackbar("缓存已清除");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDownloadManagerDialog() {
        File downloadDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        long downloadSize = downloadDir != null ? calculateDirSize(downloadDir) : 0;
        String downloadSizeStr = formatFileSize(downloadSize);

        new AlertDialog.Builder(this)
                .setTitle("下载管理")
                .setMessage("已下载文件大小: " + downloadSizeStr)
                .setPositiveButton("模型中心", (dialog, which) -> {
                    startActivity(new Intent(this, ModelManagerActivity.class));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showPrivacySecurityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("隐私与安全")
                .setMessage("所有数据均存储在本地设备，不会上传至服务器。\n\n"
                        + "• 聊天记录仅保存在本地\n"
                        + "• 模型推理完全在设备端运行\n"
                        + "• 不收集任何个人数据")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showDeveloperModeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("开发者模式")
                .setMessage("开发者模式提供高级调试功能。\n\n"
                        + "• 查看推理日志\n"
                        + "• 性能监控\n"
                        + "• 模型调试信息")
                .setPositiveButton("启用", (dialog, which) -> {
                    showSnackbar("开发者模式已启用");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showVisionModelDialog() {
        AIModel currentModel = multimodalManager.getCurrentVisionModel();
        String modelName = currentModel != null ? currentModel.getName() : "未加载";
        boolean isLoaded = multimodalManager.isVisionModelLoaded();

        new AlertDialog.Builder(this)
                .setTitle("视觉模型")
                .setMessage("当前模型: " + modelName + "\n加载状态: " + (isLoaded ? "已加载" : "未加载"))
                .setPositiveButton("模型中心", (dialog, which) -> {
                    startActivity(new Intent(this, ModelManagerActivity.class));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showMultimodalDialog() {
        AIModel currentModel = multimodalManager.getCurrentVisionModel();
        String modelName = currentModel != null ? currentModel.getName() : "未加载";

        new AlertDialog.Builder(this)
                .setTitle("多模态功能")
                .setMessage("当前视觉模型: " + modelName + "\n\n"
                        + "支持功能:\n"
                        + "• 图片识别与描述\n"
                        + "• OCR文字提取\n"
                        + "• 图片问答\n"
                        + "• 语音识别\n"
                        + "• 语音合成")
                .setPositiveButton("模型中心", (dialog, which) -> {
                    startActivity(new Intent(this, ModelManagerActivity.class));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于 Senta AI")
                .setMessage("Senta AI\n"
                        + "基于 llama.cpp + JNI 架构\n"
                        + "本地AI推理，隐私安全\n\n"
                        + "版本: 1.0.0")
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void setupLogout() {
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("退出登录")
                    .setMessage("确定要退出登录吗？")
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        userManager.logout();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
        updateCreditsDisplay();
        updateVisionModelStatus();
    }

    private void updateVisionModelStatus() {
        if (visionModelText != null) {
            AIModel currentModel = multimodalManager.getCurrentVisionModel();
            visionModelText.setText(currentModel != null ? currentModel.getName() : "未加载");
        }
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    private long calculateDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    size += calculateDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }
}
