package com.omniai.assistant.ui.credits;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;

import java.util.ArrayList;
import java.util.List;

public class CreditsCenterActivity extends AppCompatActivity {

    private TextView creditsBalanceText;
    private TextView inviteCodeText;
    private TextView inviteCountText;
    private RecyclerView rechargePlansList;
    private RecyclerView creditsRecordsList;
    private Button copyCodeBtn;
    private Button shareBtn;
    private Button inviteRecordsBtn;

    private CreditsManager creditsManager;
    private RechargePlanAdapter rechargeAdapter;
    private CreditsRecordAdapter recordAdapter;

    private Handler debounceHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credits_center);

        creditsManager = CreditsManager.getInstance();

        creditsBalanceText = findViewById(R.id.tv_credits_balance);
        inviteCodeText = findViewById(R.id.tv_invite_code);
        inviteCountText = findViewById(R.id.tv_invite_count);
        rechargePlansList = findViewById(R.id.rv_recharge_plans);
        creditsRecordsList = findViewById(R.id.rv_credits_records);
        copyCodeBtn = findViewById(R.id.btn_copy_code);
        shareBtn = findViewById(R.id.btn_share_invite);
        inviteRecordsBtn = findViewById(R.id.btn_invite_records);

        setupRechargePlans();
        loadCreditsData();
        loadCreditsRecords();
        setupClickListeners();
    }

    private void loadCreditsData() {
        int credits = creditsManager.getCredits();
        String inviteCode = creditsManager.getInviteCode();
        creditsBalanceText.setText(String.valueOf(credits));
        inviteCodeText.setText(inviteCode);
        inviteCountText.setText(getString(R.string.credits_invite_count_format, 0));
    }

    private void setupRechargePlans() {
        List<CreditsManager.RechargePlan> plans = creditsManager.getRechargePlans();
        rechargeAdapter = new RechargePlanAdapter(plans, plan -> onRechargeClick(plan));
        rechargePlansList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        rechargePlansList.setAdapter(rechargeAdapter);
    }

    private void onRechargeClick(CreditsManager.RechargePlan plan) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.credits_recharge_confirm))
                .setMessage("确认购买 " + plan.getName() + " 套餐（¥" + plan.getPrice() + "，" + plan.getCredits() + "积分）？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    performRecharge(plan);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performRecharge(CreditsManager.RechargePlan plan) {
        creditsManager.rechargeCredits(plan.getId(), new CreditsManager.RechargeCallback() {
            @Override
            public void onSuccess(CreditsManager.CreditsRecord record) {
                runOnUiThread(() -> {
                    Toast.makeText(CreditsCenterActivity.this,
                            R.string.credits_recharge_success, Toast.LENGTH_SHORT).show();
                    loadCreditsData();
                    loadCreditsRecords();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(CreditsCenterActivity.this, message, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void onCopyCodeClick() {
        if (!debounceClick(copyCodeBtn)) return;

        boolean success = creditsManager.copyInviteCode();
        if (success) {
            Toast.makeText(this, R.string.credits_copy_code_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.credits_copy_code_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void onShareClick() {
        if (!debounceClick(shareBtn)) return;

        Intent shareIntent = creditsManager.shareInviteLink();
        startActivity(shareIntent);
    }

    private void onInviteRecordsClick() {
        if (!debounceClick(inviteRecordsBtn)) return;

        showInviteRecordsDialog();
    }

    private void showInviteRecordsDialog() {
        List<CreditsManager.CreditsRecord> allRecords = creditsManager.getCreditsRecords(0, 100);
        List<CreditsManager.CreditsRecord> inviteRecords = new ArrayList<>();
        for (CreditsManager.CreditsRecord r : allRecords) {
            if ("INVITE".equals(r.getType())) {
                inviteRecords.add(r);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (CreditsManager.CreditsRecord r : inviteRecords) {
            sb.append("+").append(r.getAmount())
                    .append(" ").append(r.getDescription())
                    .append("\n");
        }
        if (sb.length() == 0) {
            sb.append(getString(R.string.credits_no_invite_records));
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.credits_invite_records)
                .setMessage(sb.toString().trim())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void loadCreditsRecords() {
        List<CreditsManager.CreditsRecord> records = creditsManager.getCreditsRecords(0, 50);
        if (recordAdapter == null) {
            recordAdapter = new CreditsRecordAdapter(records);
            creditsRecordsList.setLayoutManager(new LinearLayoutManager(this));
            creditsRecordsList.setAdapter(recordAdapter);
        } else {
            recordAdapter.updateRecords(records);
        }
    }

    private void showInsufficientCreditsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.credits_insufficient)
                .setMessage(R.string.credits_insufficient_message)
                .setPositiveButton(R.string.credits_recharge, (dialog, which) -> {
                    rechargePlansList.smoothScrollToPosition(0);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupClickListeners() {
        copyCodeBtn.setOnClickListener(v -> onCopyCodeClick());
        shareBtn.setOnClickListener(v -> onShareClick());
        inviteRecordsBtn.setOnClickListener(v -> onInviteRecordsClick());
    }

    private boolean debounceClick(View view) {
        view.setEnabled(false);
        debounceHandler.postDelayed(() -> view.setEnabled(true), 1000L);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        creditsManager.refreshCredits(new CreditsManager.SyncCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    loadCreditsData();
                    loadCreditsRecords();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    loadCreditsData();
                    loadCreditsRecords();
                });
            }
        });
    }
}
