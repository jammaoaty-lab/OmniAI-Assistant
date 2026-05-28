package com.omniai.assistant.credits;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

public class CreditsFeatureGate {

    private static volatile CreditsFeatureGate instance;

    private CreditsManager creditsManager;

    private CreditsFeatureGate() {
        this.creditsManager = CreditsManager.getInstance();
    }

    public static CreditsFeatureGate getInstance() {
        if (instance == null) {
            synchronized (CreditsFeatureGate.class) {
                if (instance == null) {
                    instance = new CreditsFeatureGate();
                }
            }
        }
        return instance;
    }

    public boolean canUseAdvancedTextModel() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.ADVANCED_TEXT_MODEL.getCost());
    }

    public boolean canUseAdvancedVisionModel() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.ADVANCED_VISION_MODEL.getCost());
    }

    public boolean canTrainLora() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.UNLIMITED_LORA.getCost());
    }

    public boolean canUseCloudGpu() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.CLOUD_GPU.getCost());
    }

    public boolean canUseLongContext() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.LONG_CONTEXT.getCost());
    }

    public boolean canUseAdvancedAgent() {
        return creditsManager.hasSufficientCredits(CreditsManager.CreditsFeature.ADVANCED_AGENT.getCost());
    }

    public int getCost(CreditsManager.CreditsFeature feature) {
        return feature.getCost();
    }

    public boolean deductIfNeeded(CreditsManager.CreditsFeature feature) {
        return creditsManager.checkAndDeduct(feature);
    }

    public void showInsufficientCreditsDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("积分不足")
                .setMessage("您的积分余额不足以使用此功能，请充值后继续使用高级功能。")
                .setPositiveButton("去充值", (dialog, which) -> {
                    Intent intent = new Intent(context, com.omniai.assistant.ui.credits.CreditsCenterActivity.class);
                    context.startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
