package com.omniai.assistant.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.omniai.assistant.R;
import com.omniai.assistant.user.UserManager;
import com.omniai.assistant.ui.chat.ChatActivity;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private EditText accountInput;
    private EditText passwordInput;
    private EditText phoneInput;
    private EditText codeInput;
    private MaterialButton loginBtn;
    private MaterialButton sendCodeBtn;
    private View passwordSection;
    private View codeSection;
    private TextView toggleMode;
    private TextView switchToRegister;
    private TextView forgotPassword;

    private boolean isPasswordMode = true;
    private UserManager userManager;
    private GoogleSignInClient googleSignInClient;
    private Handler uiHandler;
    private int codeCountdown = 0;
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        UserManager.init(this);
        userManager = UserManager.getInstance();
        uiHandler = new Handler(Looper.getMainLooper());

        accountInput = findViewById(R.id.input_account);
        passwordInput = findViewById(R.id.input_password);
        phoneInput = findViewById(R.id.input_phone);
        codeInput = findViewById(R.id.input_code);
        loginBtn = findViewById(R.id.btn_login);
        sendCodeBtn = findViewById(R.id.btn_send_code);
        passwordSection = findViewById(R.id.section_password);
        codeSection = findViewById(R.id.section_code);
        toggleMode = findViewById(R.id.tv_toggle_mode);
        switchToRegister = findViewById(R.id.tv_switch_register);
        forgotPassword = findViewById(R.id.tv_forgot_password);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        toggleMode.setOnClickListener(v -> {
            isPasswordMode = !isPasswordMode;
            updateModeUI();
        });

        switchToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        loginBtn.setOnClickListener(v -> attemptLogin());

        findViewById(R.id.btn_google).setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
        });

        findViewById(R.id.btn_wechat).setOnClickListener(v -> loginWithWeChat());
        findViewById(R.id.btn_qq).setOnClickListener(v -> loginWithQQ());
        findViewById(R.id.btn_apple).setOnClickListener(v -> loginWithApple());

        sendCodeBtn.setOnClickListener(v -> sendVerificationCode());

        if (forgotPassword != null) {
            forgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        updateModeUI();
    }

    private void updateModeUI() {
        if (isPasswordMode) {
            passwordSection.setVisibility(View.VISIBLE);
            codeSection.setVisibility(View.GONE);
            accountInput.setVisibility(View.VISIBLE);
            toggleMode.setText(R.string.switch_to_code_login);
        } else {
            passwordSection.setVisibility(View.GONE);
            codeSection.setVisibility(View.VISIBLE);
            accountInput.setVisibility(View.GONE);
            toggleMode.setText(R.string.switch_to_password_login);
        }
    }

    private void attemptLogin() {
        if (isPasswordMode) {
            loginWithPassword();
        } else {
            loginWithCode();
        }
    }

    private void loginWithPassword() {
        String account = accountInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(account)) {
            showSnackbar(getString(R.string.error_account_empty));
            return;
        }

        if (!isValidAccount(account)) {
            showSnackbar(getString(R.string.error_account_format));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            showSnackbar(getString(R.string.error_password_empty));
            return;
        }

        if (password.length() < 6) {
            showSnackbar("密码长度不能少于6位");
            return;
        }

        loginBtn.setEnabled(false);
        userManager.login(account, password, new UserManager.LoginCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiHandler.post(() -> navigateToChat());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> {
                    loginBtn.setEnabled(true);
                    showSnackbar(message);
                });
            }
        });
    }

    private void loginWithCode() {
        String phone = phoneInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) {
            showSnackbar(getString(R.string.error_phone_empty));
            return;
        }

        if (!isValidPhone(phone)) {
            showSnackbar(getString(R.string.error_phone_format));
            return;
        }

        if (TextUtils.isEmpty(code)) {
            showSnackbar(getString(R.string.error_code_empty));
            return;
        }

        if (code.length() != 6) {
            showSnackbar("验证码为6位数字");
            return;
        }

        loginBtn.setEnabled(false);
        userManager.loginWithCode(phone, code, new UserManager.LoginCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiHandler.post(() -> navigateToChat());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> {
                    loginBtn.setEnabled(true);
                    showSnackbar(message);
                });
            }
        });
    }

    private void sendVerificationCode() {
        String phone = phoneInput.getText().toString().trim();
        if (TextUtils.isEmpty(phone)) {
            showSnackbar(getString(R.string.error_phone_empty));
            return;
        }
        if (!isValidPhone(phone)) {
            showSnackbar(getString(R.string.error_phone_format));
            return;
        }

        sendCodeBtn.setEnabled(false);
        userManager.sendVerificationCode(phone, new UserManager.CodeCallback() {
            @Override
            public void onSuccess() {
                uiHandler.post(() -> {
                    showSnackbar(getString(R.string.code_sent));
                    startCodeCountdown();
                });
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> {
                    sendCodeBtn.setEnabled(true);
                    showSnackbar(message);
                });
            }
        });
    }

    private void startCodeCountdown() {
        codeCountdown = 60;
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (codeCountdown > 0) {
                    sendCodeBtn.setText(codeCountdown + "s");
                    sendCodeBtn.setEnabled(false);
                    codeCountdown--;
                    uiHandler.postDelayed(this, 1000);
                } else {
                    sendCodeBtn.setText(R.string.btn_send_code);
                    sendCodeBtn.setEnabled(true);
                }
            }
        };
        uiHandler.post(countdownRunnable);
    }

    private void loginWithWeChat() {
        userManager.loginWithWechat("", new UserManager.LoginCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiHandler.post(() -> navigateToChat());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> showSnackbar(getString(R.string.social_login_wechat_unavailable)));
            }
        });
    }

    private void loginWithQQ() {
        userManager.loginWithQQ("", new UserManager.LoginCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiHandler.post(() -> navigateToChat());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> showSnackbar(getString(R.string.social_login_qq_unavailable)));
            }
        });
    }

    private void loginWithApple() {
        userManager.loginWithApple("", new UserManager.LoginCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                uiHandler.post(() -> navigateToChat());
            }

            @Override
            public void onError(String message) {
                uiHandler.post(() -> showSnackbar(getString(R.string.social_login_apple_unavailable)));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                userManager.loginWithGoogle(account, new UserManager.LoginCallback() {
                    @Override
                    public void onSuccess(UserProfile profile) {
                        uiHandler.post(() -> navigateToChat());
                    }

                    @Override
                    public void onError(String message) {
                        uiHandler.post(() -> showSnackbar(getString(R.string.login_google_failed) + ": " + message));
                    }
                });
            } catch (ApiException e) {
                showSnackbar(getString(R.string.login_google_failed) + ": " + e.getStatusCode());
            }
        }
    }

    private void showForgotPasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_kb, null);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.forgot_password_title))
                .setView(dialogView)
                .setPositiveButton("发送重置链接", (dialog, which) -> {
                    showSnackbar("密码重置链接已发送到您的邮箱");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void navigateToChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private boolean isValidAccount(String account) {
        return isValidEmail(account) || isValidPhone(account);
    }

    private boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && phone.matches("^1[3-9]\\d{9}$");
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) {
            uiHandler.removeCallbacks(countdownRunnable);
        }
    }
}
