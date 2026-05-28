package com.omniai.assistant.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.omniai.assistant.R;
import com.omniai.assistant.manager.UserManager;
import com.omniai.assistant.ui.chat.ChatActivity;

public class RegisterActivity extends AppCompatActivity {

    private EditText accountInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private EditText codeInput;
    private Button registerBtn;
    private Button sendCodeBtn;

    private UserManager userManager;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userManager = UserManager.getInstance(this);

        accountInput = findViewById(R.id.input_account);
        passwordInput = findViewById(R.id.input_password);
        confirmPasswordInput = findViewById(R.id.input_confirm_password);
        codeInput = findViewById(R.id.input_code);
        registerBtn = findViewById(R.id.btn_register);
        sendCodeBtn = findViewById(R.id.btn_send_code);

        sendCodeBtn.setOnClickListener(v -> sendVerificationCode());
        registerBtn.setOnClickListener(v -> attemptRegister());
    }

    private void sendVerificationCode() {
        String account = accountInput.getText().toString().trim();
        if (TextUtils.isEmpty(account)) {
            showSnackbar(getString(R.string.error_account_empty));
            return;
        }
        if (!isValidPhone(account) && !isValidEmail(account)) {
            showSnackbar(getString(R.string.error_account_format));
            return;
        }

        userManager.sendVerificationCode(account, new UserManager.CodeCallback() {
            @Override
            public void onSuccess() {
                startCountDown();
                showSnackbar(getString(R.string.code_sent));
            }

            @Override
            public void onError(String message) {
                showSnackbar(message);
            }
        });
    }

    private void startCountDown() {
        sendCodeBtn.setEnabled(false);
        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                sendCodeBtn.setText(getString(R.string.resend_code, millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                sendCodeBtn.setEnabled(true);
                sendCodeBtn.setText(R.string.send_code);
            }
        }.start();
    }

    private void attemptRegister() {
        String account = accountInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (TextUtils.isEmpty(account)) {
            showSnackbar(getString(R.string.error_account_empty));
            return;
        }

        if (!isValidPhone(account) && !isValidEmail(account)) {
            showSnackbar(getString(R.string.error_account_format));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            showSnackbar(getString(R.string.error_password_empty));
            return;
        }

        if (password.length() < 6) {
            showSnackbar(getString(R.string.error_password_too_short));
            return;
        }

        if (!password.equals(confirmPassword)) {
            showSnackbar(getString(R.string.error_password_mismatch));
            return;
        }

        if (TextUtils.isEmpty(code)) {
            showSnackbar(getString(R.string.error_code_empty));
            return;
        }

        registerBtn.setEnabled(false);
        userManager.register(account, password, code, new UserManager.RegisterCallback() {
            @Override
            public void onSuccess() {
                autoLogin(account, password);
            }

            @Override
            public void onError(String message) {
                registerBtn.setEnabled(true);
                showSnackbar(message);
            }
        });
    }

    private void autoLogin(String account, String password) {
        userManager.login(account, password, new UserManager.LoginCallback() {
            @Override
            public void onSuccess() {
                navigateToChat();
            }

            @Override
            public void onError(String message) {
                navigateToLogin();
            }
        });
    }

    private void navigateToChat() {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
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
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
