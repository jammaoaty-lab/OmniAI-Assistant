package com.omniai.assistant.ui.agent;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.agent.AgentCore;
import com.omniai.assistant.agent.AgentStep;
import com.omniai.assistant.agent.AgentTool;
import com.omniai.assistant.adapter.AgentAdapter;
import com.omniai.assistant.model.Agent;

import java.util.List;

public class AgentActivity extends AppCompatActivity {

    private RecyclerView rvSteps;
    private EditText etInput;
    private ImageButton btnSend;
    private ImageButton btnAbort;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private LinearLayout layoutToolApproval;
    private TextView tvToolRequest;
    private ImageButton btnApprove;
    private ImageButton btnReject;
    private ScrollView scrollView;

    private AgentCore agentCore;
    private AgentAdapter adapter;
    private Agent currentAgent;

    public static void start(Context context) {
        context.startActivity(new Intent(context, AgentActivity.class));
    }

    public static void startWithAgent(Context context, Agent agent) {
        Intent intent = new Intent(context, AgentActivity.class);
        intent.putExtra("agent_id", agent.getId());
        intent.putExtra("agent_name", agent.getName());
        intent.putExtra("agent_prompt", agent.getSystemPrompt());
        intent.putExtra("agent_avatar", agent.getAvatar());
        intent.putExtra("agent_auto", agent.isAutoExecute());
        intent.putExtra("agent_max_steps", agent.getMaxSteps());
        intent.putExtra("agent_temperature", agent.getTemperature());
        intent.putStringArrayListExtra("agent_tools", new java.util.ArrayList<>(agent.getEnabledTools()));
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent);

        initViews();
        setupToolbar();
        setupAgent();
        setupListeners();
    }

    private void initViews() {
        rvSteps = findViewById(R.id.rv_agent_steps);
        etInput = findViewById(R.id.et_agent_input);
        btnSend = findViewById(R.id.btn_agent_send);
        btnAbort = findViewById(R.id.btn_agent_abort);
        progressBar = findViewById(R.id.progress_agent);
        tvStatus = findViewById(R.id.tv_agent_status);
        layoutToolApproval = findViewById(R.id.layout_tool_approval);
        tvToolRequest = findViewById(R.id.tv_tool_request);
        btnApprove = findViewById(R.id.btn_approve_tool);
        btnReject = findViewById(R.id.btn_reject_tool);
        scrollView = findViewById(R.id.scroll_agent);

        adapter = new AgentAdapter();
        rvSteps.setLayoutManager(new LinearLayoutManager(this));
        rvSteps.setAdapter(adapter);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupAgent() {
        agentCore = new AgentCore(this);
        agentCore.setCallback(new AgentCore.AgentCallback() {
            @Override
            public void onStep(AgentStep step) {
                runOnUiThread(() -> {
                    adapter.addStep(step);
                    rvSteps.scrollToPosition(adapter.getItemCount() - 1);
                });
            }

            @Override
            public void onThinking(String thought) {
                runOnUiThread(() -> tvStatus.setText("思考中..."));
            }

            @Override
            public void onToolCall(String toolName, String toolInput) {
                runOnUiThread(() -> {
                    AgentTool tool = AgentTool.getToolByName(toolName);
                    String displayName = tool != null ? tool.getDisplayName() : toolName;
                    tvStatus.setText("调用工具: " + displayName);

                    if (currentAgent != null && !currentAgent.isAutoExecute()) {
                        layoutToolApproval.setVisibility(View.VISIBLE);
                        tvToolRequest.setText(displayName + ": " + toolInput);
                        btnApprove.setOnClickListener(v -> {
                            layoutToolApproval.setVisibility(View.GONE);
                            agentCore.approveToolExecution();
                        });
                        btnReject.setOnClickListener(v -> {
                            layoutToolApproval.setVisibility(View.GONE);
                            agentCore.rejectToolExecution();
                        });
                    }
                });
            }

            @Override
            public void onToolResult(String toolName, String result) {
                runOnUiThread(() -> tvStatus.setText("工具返回结果"));
            }

            @Override
            public void onFinalAnswer(String answer) {
                runOnUiThread(() -> {
                    tvStatus.setText("完成");
                    progressBar.setVisibility(View.GONE);
                    btnAbort.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    etInput.setEnabled(true);
                    layoutToolApproval.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    tvStatus.setText("错误: " + message);
                    progressBar.setVisibility(View.GONE);
                    btnAbort.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    etInput.setEnabled(true);
                    layoutToolApproval.setVisibility(View.GONE);
                });
            }

            @Override
            public void onComplete(List<AgentStep> steps) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnAbort.setVisibility(View.GONE);
                    btnSend.setEnabled(true);
                    etInput.setEnabled(true);
                    layoutToolApproval.setVisibility(View.GONE);
                });
            }
        });

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("agent_name")) {
            currentAgent = new Agent();
            currentAgent.setName(intent.getStringExtra("agent_name"));
            currentAgent.setSystemPrompt(intent.getStringExtra("agent_prompt"));
            currentAgent.setAvatar(intent.getStringExtra("agent_avatar"));
            currentAgent.setAutoExecute(intent.getBooleanExtra("agent_auto", false));
            currentAgent.setMaxSteps(intent.getIntExtra("agent_max_steps", 10));
            currentAgent.setTemperature(intent.getFloatExtra("agent_temperature", 0.7f));
            List<String> tools = intent.getStringArrayListExtra("agent_tools");
            if (tools != null) {
                currentAgent.setEnabledTools(tools);
            }

            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setTitle(currentAgent.getAvatar() + " " + currentAgent.getName());
            }
        } else {
            currentAgent = new Agent();
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.setTitle("🤖 智能Agent");
            }
        }
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> executeAgentInput());

        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeAgentInput();
                return true;
            }
            return false;
        });

        btnAbort.setOnClickListener(v -> {
            agentCore.abort();
            tvStatus.setText("已中止");
            progressBar.setVisibility(View.GONE);
            btnAbort.setVisibility(View.GONE);
            btnSend.setEnabled(true);
            etInput.setEnabled(true);
            layoutToolApproval.setVisibility(View.GONE);
        });
    }

    private void executeAgentInput() {
        String input = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(input)) return;
        if (agentCore.isRunning()) return;

        etInput.setText("");
        adapter.clear();

        progressBar.setVisibility(View.VISIBLE);
        btnAbort.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);
        etInput.setEnabled(false);
        tvStatus.setText("执行中...");

        agentCore.execute(currentAgent, input);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (agentCore != null && agentCore.isRunning()) {
            agentCore.abort();
        }
    }
}
