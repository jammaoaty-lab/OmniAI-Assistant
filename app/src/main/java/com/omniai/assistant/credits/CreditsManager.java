package com.omniai.assistant.credits;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.omniai.assistant.common.Result;
import com.omniai.assistant.security.DataEncryptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreditsManager {

    private static volatile CreditsManager instance;

    private int currentCredits;
    private String inviteCode;
    private int inviteCount;
    private List<CreditsRecord> records;
    private CreditsApiService apiService;
    private SharedPreferences prefs;
    private DataEncryptor dataEncryptor;
    private Context context;
    private ExecutorService executorService;
    private final Object creditsLock = new Object();

    private static final String PREFS_NAME = "omniai_credits_prefs";
    private static final String KEY_CREDITS = "current_credits";
    private static final String KEY_INVITE_CODE = "invite_code";
    private static final String KEY_INVITE_COUNT = "invite_count";

    public static class CreditsRecord {
        private long id;
        private int amount;
        private String type;
        private String description;
        private long timestamp;

        public CreditsRecord(long id, int amount, String type, String description, long timestamp) {
            this.id = id;
            this.amount = amount;
            this.type = type;
            this.description = description;
            this.timestamp = timestamp;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class RechargePlan {
        private int id;
        private String name;
        private int price;
        private int credits;
        private boolean isPopular;

        public RechargePlan(int id, String name, int price, int credits, boolean isPopular) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.credits = credits;
            this.isPopular = isPopular;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPrice() {
            return price;
        }

        public void setPrice(int price) {
            this.price = price;
        }

        public int getCredits() {
            return credits;
        }

        public void setCredits(int credits) {
            this.credits = credits;
        }

        public boolean isPopular() {
            return isPopular;
        }

        public void setPopular(boolean popular) {
            isPopular = popular;
        }
    }

    public enum CreditsFeature {
        ADVANCED_TEXT_MODEL(10),
        ADVANCED_VISION_MODEL(20),
        UNLIMITED_LORA(30),
        CLOUD_GPU(15),
        LONG_CONTEXT(5),
        ADVANCED_AGENT(10);

        private final int cost;

        CreditsFeature(int cost) {
            this.cost = cost;
        }

        public int getCost() {
            return cost;
        }
    }

    public interface InviteCallback {
        void onSuccess(int rewardAmount);
        void onError(String message);
    }

    public interface RechargeCallback {
        void onSuccess(CreditsRecord record);
        void onError(String message);
    }

    public interface SyncCallback {
        void onSuccess();
        void onError(String message);
    }

    private CreditsManager() {
        this.currentCredits = 0;
        this.inviteCode = "";
        this.inviteCount = 0;
        this.records = Collections.synchronizedList(new ArrayList<>());
        this.apiService = new CreditsApiService();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public static CreditsManager getInstance() {
        if (instance == null) {
            synchronized (CreditsManager.class) {
                if (instance == null) {
                    instance = new CreditsManager();
                }
            }
        }
        return instance;
    }

    public static synchronized void init(Context context) {
        CreditsManager manager = getInstance();
        manager.context = context.getApplicationContext();
        manager.prefs = manager.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        manager.dataEncryptor = new DataEncryptor(context);
        manager.loadFromPrefs();
    }

    public int getCredits() {
        if (prefs != null) {
            currentCredits = prefs.getInt(KEY_CREDITS, 0);
        }
        return currentCredits;
    }

    public void refreshCredits(SyncCallback callback) {
        syncWithServer(callback);
    }

    public void setCredits(int credits) {
        synchronized (creditsLock) {
            this.currentCredits = credits;
            saveCreditsEncrypted(credits);
        }
    }

    public void addCredits(int amount, String type, String description) {
        synchronized (creditsLock) {
            currentCredits += amount;
            saveCreditsEncrypted(currentCredits);
        }

        CreditsRecord record = new CreditsRecord(
                System.currentTimeMillis(),
                amount,
                type,
                description,
                System.currentTimeMillis()
        );
        records.add(record);
    }

    public boolean deductCredits(int amount, String type, String description) {
        synchronized (creditsLock) {
            if (currentCredits < amount) {
                return false;
            }
            currentCredits -= amount;
            saveCreditsEncrypted(currentCredits);
        }

        CreditsRecord record = new CreditsRecord(
                System.currentTimeMillis(),
                -amount,
                type,
                description,
                System.currentTimeMillis()
        );
        records.add(record);
        return true;
    }

    public boolean hasSufficientCredits(int amount) {
        return currentCredits >= amount;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public boolean copyInviteCode() {
        if (context == null || inviteCode == null || inviteCode.isEmpty()) {
            return false;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            ClipData clip = ClipData.newPlainText("Invite Code", inviteCode);
            clipboard.setPrimaryClip(clip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Intent shareInviteLink() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareText = "Join Senta AI! Use my invite code: " + inviteCode + "\nhttps://senta-ai.com/invite/" + inviteCode;
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        return Intent.createChooser(shareIntent, "Share Invite Link");
    }

    public void processInviteReward(String code, InviteCallback callback) {
        executorService.execute(() -> {
            try {
                if (code == null || code.isEmpty() || code.equals(inviteCode)) {
                    if (callback != null) callback.onError("Invalid invite code");
                    return;
                }

                String userId = prefs.getString("user_id", "");
                Result<Integer> result = apiService.processInvite(userId, code);
                if (result.isSuccess()) {
                    int reward = result.getData();
                    addCredits(reward, "INVITE", "Invite reward for code: " + code);
                    inviteCount++;
                    prefs.edit().putInt(KEY_INVITE_COUNT, inviteCount).apply();
                    if (callback != null) callback.onSuccess(reward);
                } else {
                    if (callback != null) callback.onError(result.getError());
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    public void rechargeCredits(int planId, RechargeCallback callback) {
        executorService.execute(() -> {
            try {
                String userId = prefs.getString("user_id", "");
                String paymentToken = prefs.getString("payment_token", "");
                Result<CreditsRecord> result = apiService.recharge(userId, planId, paymentToken);
                if (result.isSuccess()) {
                    CreditsRecord record = result.getData();
                    addCredits(record.getAmount(), "RECHARGE", record.getDescription());
                    if (callback != null) callback.onSuccess(record);
                } else {
                    if (callback != null) callback.onError(result.getError());
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    public List<RechargePlan> getRechargePlans() {
        List<RechargePlan> plans = new ArrayList<>();
        plans.add(new RechargePlan(1, "Starter", 99, 100, false));
        plans.add(new RechargePlan(2, "Popular", 299, 500, true));
        plans.add(new RechargePlan(3, "Pro", 599, 1200, false));
        plans.add(new RechargePlan(4, "Ultimate", 999, 2500, false));
        return plans;
    }

    public List<CreditsRecord> getCreditsRecords(int page, int pageSize) {
        int start = page * pageSize;
        int end = Math.min(start + pageSize, records.size());
        if (start >= records.size()) {
            return new ArrayList<>();
        }
        List<CreditsRecord> pageRecords = new ArrayList<>(records.subList(start, end));
        Collections.reverse(pageRecords);
        return pageRecords;
    }

    public boolean checkAndDeduct(CreditsFeature feature) {
        int cost = feature.getCost();
        synchronized (creditsLock) {
            if (currentCredits < cost) {
                return false;
            }
            currentCredits -= cost;
            saveCreditsEncrypted(currentCredits);
        }

        CreditsRecord record = new CreditsRecord(
                System.currentTimeMillis(),
                -cost,
                "CONSUME",
                feature.name(),
                System.currentTimeMillis()
        );
        records.add(record);
        return true;
    }

    public int getCreditsCost(CreditsFeature feature) {
        return feature.getCost();
    }

    public void syncWithServer(SyncCallback callback) {
        executorService.execute(() -> {
            try {
                String userId = prefs.getString("user_id", "");
                if (userId.isEmpty()) {
                    if (callback != null) callback.onError("User not logged in");
                    return;
                }

                Result<Integer> balanceResult = apiService.getCreditsBalance(userId);
                if (balanceResult.isSuccess()) {
                    synchronized (creditsLock) {
                        currentCredits = balanceResult.getData();
                        saveCreditsEncrypted(currentCredits);
                    }
                }

                Result<String[]> inviteResult = apiService.getInviteInfo(userId);
                if (inviteResult.isSuccess()) {
                    String[] info = inviteResult.getData();
                    if (info != null && info.length >= 2) {
                        inviteCode = info[0];
                        inviteCount = Integer.parseInt(info[1]);
                        saveInviteCodeEncrypted(inviteCode);
                        prefs.edit()
                                .putInt(KEY_INVITE_COUNT, inviteCount)
                                .apply();
                    }
                }

                if (callback != null) callback.onSuccess();
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }

    private void loadFromPrefs() {
        if (dataEncryptor != null) {
            try {
                String encCredits = prefs.getString(KEY_CREDITS + "_enc", null);
                if (encCredits != null) {
                    currentCredits = Integer.parseInt(dataEncryptor.decryptString(encCredits));
                } else {
                    currentCredits = prefs.getInt(KEY_CREDITS, 0);
                }
                String encInviteCode = prefs.getString(KEY_INVITE_CODE + "_enc", null);
                if (encInviteCode != null) {
                    inviteCode = dataEncryptor.decryptString(encInviteCode);
                } else {
                    inviteCode = prefs.getString(KEY_INVITE_CODE, "");
                }
            } catch (Exception e) {
                Log.w("CreditsManager", "Failed to decrypt, falling back to plaintext", e);
                currentCredits = prefs.getInt(KEY_CREDITS, 0);
                inviteCode = prefs.getString(KEY_INVITE_CODE, "");
            }
        } else {
            currentCredits = prefs.getInt(KEY_CREDITS, 0);
            inviteCode = prefs.getString(KEY_INVITE_CODE, "");
        }
        inviteCount = prefs.getInt(KEY_INVITE_COUNT, 0);
    }

    private void saveCreditsEncrypted(int credits) {
        if (prefs == null) return;
        if (dataEncryptor != null) {
            try {
                String encCredits = dataEncryptor.encryptString(String.valueOf(credits));
                prefs.edit()
                        .putString(KEY_CREDITS + "_enc", encCredits)
                        .remove(KEY_CREDITS)
                        .apply();
            } catch (Exception e) {
                Log.w("CreditsManager", "Encryption failed, falling back to plaintext", e);
                prefs.edit().putInt(KEY_CREDITS, credits).apply();
            }
        } else {
            prefs.edit().putInt(KEY_CREDITS, credits).apply();
        }
    }

    private void saveInviteCodeEncrypted(String code) {
        if (prefs == null) return;
        if (dataEncryptor != null) {
            try {
                String encCode = dataEncryptor.encryptString(code);
                prefs.edit()
                        .putString(KEY_INVITE_CODE + "_enc", encCode)
                        .remove(KEY_INVITE_CODE)
                        .apply();
            } catch (Exception e) {
                Log.w("CreditsManager", "Encryption failed, falling back to plaintext", e);
                prefs.edit().putString(KEY_INVITE_CODE, code).apply();
            }
        } else {
            prefs.edit().putString(KEY_INVITE_CODE, code).apply();
        }
    }
}
