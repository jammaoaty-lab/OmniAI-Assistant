package com.omniai.assistant.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.omniai.assistant.modelmgmt.ModelDownloadInfo;

public class ModelDownloadDialog {

    private final Context context;
    private Dialog dialog;
    private TextView tvTitle;
    private TextView tvStatus;
    private TextView tvProgress;
    private TextView tvSpeed;
    private TextView tvSize;
    private ProgressBar progressBar;
    private Button btnAction;
    private Button btnCancel;
    private ImageView ivIcon;

    private String currentModelId;
    private boolean isDownloading = false;

    public ModelDownloadDialog(Context context) {
        this.context = context;
        createDialog();
    }

    private void createDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 32);

        int dialogBg = 0xFFF8FAFC;
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setColor((int) dialogBg);
        bgDrawable.setCornerRadius(24);
        root.setBackground(bgDrawable);

        ivIcon = new ImageView(context);
        ivIcon.setLayoutParams(new LinearLayout.LayoutParams(80, 80, Gravity.CENTER_HORIZONTAL));
        root.addView(ivIcon);

        tvTitle = new TextView(context);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF1E293B);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 16, 0, 4);
        root.addView(tvTitle);

        tvSize = new TextView(context);
        tvSize.setTextSize(13);
        tvSize.setTextColor(0xFF64748B);
        tvSize.setGravity(Gravity.CENTER);
        tvSize.setPadding(0, 0, 0, 16);
        root.addView(tvSize);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 16);
        progressParams.setMargins(0, 8, 0, 8);
        progressBar.setLayoutParams(progressParams);
        root.addView(progressBar);

        LinearLayout infoRow = new LinearLayout(context);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        tvProgress = new TextView(context);
        tvProgress.setTextSize(13);
        tvProgress.setTextColor(0xFF334155);
        LinearLayout.LayoutParams progressTextParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvProgress.setLayoutParams(progressTextParams);
        infoRow.addView(tvProgress);

        tvSpeed = new TextView(context);
        tvSpeed.setTextSize(13);
        tvSpeed.setTextColor(0xFF64748B);
        infoRow.addView(tvSpeed);

        root.addView(infoRow);

        tvStatus = new TextView(context);
        tvStatus.setTextSize(12);
        tvStatus.setTextColor(0xFF2563EB);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 12, 0, 12);
        tvStatus.setVisibility(View.GONE);
        root.addView(tvStatus);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, 8, 0, 0);

        btnCancel = new Button(context);
        btnCancel.setText("取消");
        btnCancel.setTextColor(0xFF64748B);
        btnCancel.setBackgroundColor(0x00000000);
        btnCancel.setPadding(32, 12, 32, 12);
        btnCancel.setOnClickListener(v -> dismiss());
        buttonRow.addView(btnCancel);

        btnAction = new Button(context);
        btnAction.setText("下载");
        btnAction.setTextColor(0xFFFFFFFF);
        GradientDrawable actionBg = new GradientDrawable();
        actionBg.setColor(0xFF2563EB);
        actionBg.setCornerRadius(12);
        btnAction.setBackground(actionBg);
        btnAction.setPadding(48, 12, 48, 12);
        btnAction.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttonRow.addView(btnAction);

        root.addView(buttonRow);

        dialog.setContentView(root);

        android.view.ViewGroup.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.85);
        dialog.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
    }

    public void showForModel(ModelDownloadInfo modelInfo, OnDownloadActionListener listener) {
        currentModelId = modelInfo.getModelId();
        tvTitle.setText(modelInfo.getModelName());
        tvSize.setText(modelInfo.getDisplaySize() + " · " + modelInfo.getQuantType() + " · " + modelInfo.getDescription());
        tvProgress.setText("0%");
        tvSpeed.setText("");
        tvStatus.setVisibility(View.GONE);
        progressBar.setProgress(0);
        isDownloading = false;

        btnAction.setText("开始下载");
        btnAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStartDownload(modelInfo);
            }
        });

        btnCancel.setText("取消");
        btnCancel.setOnClickListener(v -> {
            if (isDownloading && listener != null) {
                listener.onPauseDownload(modelInfo);
            }
            dismiss();
        });

        dialog.show();
    }

    public void updateProgress(int percent, long downloadedBytes, long totalBytes, float speedMBps) {
        if (dialog != null && dialog.isShowing()) {
            progressBar.setProgress(Math.max(0, percent));
            tvProgress.setText(percent + "%");
            if (speedMBps > 0) {
                tvSpeed.setText(String.format("%.1f MB/s", speedMBps));
            }
            String downloaded = formatSize(downloadedBytes);
            String total = formatSize(totalBytes);
            tvStatus.setText(downloaded + " / " + total);
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    public void setDownloading(boolean downloading) {
        isDownloading = downloading;
        if (dialog != null && dialog.isShowing()) {
            if (downloading) {
                btnAction.setText("暂停");
                btnCancel.setText("取消下载");
            } else {
                btnAction.setText("继续下载");
                btnCancel.setText("关闭");
            }
        }
    }

    public void setPaused(long downloadedBytes) {
        isDownloading = false;
        if (dialog != null && dialog.isShowing()) {
            btnAction.setText("继续下载");
            btnCancel.setText("关闭");
            tvStatus.setText("已暂停 · " + formatSize(downloadedBytes) + " 已下载");
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    public void setVerifying() {
        if (dialog != null && dialog.isShowing()) {
            tvStatus.setText("正在校验文件完整性...");
            tvStatus.setVisibility(View.VISIBLE);
            btnAction.setEnabled(false);
            btnAction.setText("校验中");
        }
    }

    public void setCompleted(String filePath) {
        if (dialog != null && dialog.isShowing()) {
            progressBar.setProgress(100);
            tvProgress.setText("100%");
            tvStatus.setText("下载完成！");
            tvStatus.setTextColor(0xFF16A34A);
            tvStatus.setVisibility(View.VISIBLE);
            btnAction.setText("完成");
            btnAction.setEnabled(true);
            btnAction.setOnClickListener(v -> dismiss());
            btnCancel.setVisibility(View.GONE);
        }
    }

    public void setError(String error) {
        if (dialog != null && dialog.isShowing()) {
            tvStatus.setText(error);
            tvStatus.setTextColor(0xFFDC2626);
            tvStatus.setVisibility(View.VISIBLE);
            btnAction.setText("重试");
            btnAction.setEnabled(true);
            isDownloading = false;
        }
    }

    public void setActionListener(OnDownloadActionListener listener, ModelDownloadInfo modelInfo) {
        btnAction.setOnClickListener(v -> {
            if (isDownloading) {
                if (listener != null) listener.onPauseDownload(modelInfo);
            } else {
                if (listener != null) listener.onStartDownload(modelInfo);
            }
        });
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
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

    public interface OnDownloadActionListener {
        void onStartDownload(ModelDownloadInfo modelInfo);
        void onPauseDownload(ModelDownloadInfo modelInfo);
    }
}
