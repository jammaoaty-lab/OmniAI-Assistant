package com.omniai.assistant.ui.chat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.omniai.assistant.R;
import com.omniai.assistant.adapter.ChatMessageAdapter;
import com.omniai.assistant.adapter.ConversationAdapter;
import com.omniai.assistant.adapter.QuickCommandAdapter;
import com.omniai.assistant.chat.ChatManager;
import com.omniai.assistant.chat.ChatRepository;
import com.omniai.assistant.chat.ContextManager;
import com.omniai.assistant.chat.MarkdownRenderer;
import com.omniai.assistant.chat.QuickCommand;
import com.omniai.assistant.chat.StreamBuffer;
import com.omniai.assistant.common.GlobalExceptionHandler;
import com.omniai.assistant.credits.CreditsFeatureGate;
import com.omniai.assistant.credits.CreditsManager;
import com.omniai.assistant.inference.InferenceEngine;
import com.omniai.assistant.inference.VisionInferenceEngine;
import com.omniai.assistant.model.AIModel;
import com.omniai.assistant.model.ChatMessage;
import com.omniai.assistant.model.Conversation;
import com.omniai.assistant.modelmgmt.PreinstalledModelManager;
import com.omniai.assistant.scheduler.AIScheduler;
import com.omniai.assistant.scheduler.InferenceParams;
import com.omniai.assistant.ui.credits.CreditsCenterActivity;
import com.omniai.assistant.ui.login.LoginActivity;
import com.omniai.assistant.ui.model.ModelManagerActivity;
import com.omniai.assistant.ui.profile.ProfileActivity;
import com.omniai.assistant.ui.settings.SettingsActivity;
import com.omniai.assistant.user.UserManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 1001;
    private static final int REQUEST_DOCUMENT_PICK = 1002;
    private static final int REQUEST_CAMERA_CAPTURE = 1003;
    private static final int REQUEST_OCR_IMAGE_PICK = 1004;
    private static final int PERMISSION_AUDIO = 2001;
    private static final int PERMISSION_CAMERA = 2002;
    private static final int PERMISSION_STORAGE = 2003;

    private DrawerLayout drawerLayout;
    private RecyclerView messageList;
    private RecyclerView quickCommandList;
    private EditText inputText;
    private ImageButton sendBtn;
    private ImageButton voiceBtn;
    private ImageButton attachBtn;
    private TextView modelNameText;
    private TextView modelStatusText;
    private View statusDot;
    private TextView statusText;
    private LinearLayout statusBarLayout;
    private RecyclerView conversationList;
    private TextView userNicknameText;
    private ImageView userAvatar;
    private TextView vipBadge;
    private TextView modelInfoText;
    private ImageButton moreBtn;

    private ChatMessageAdapter messageAdapter;
    private ConversationAdapter conversationAdapter;
    private QuickCommandAdapter quickCommandAdapter;

    private ChatManager chatManager;
    private AIScheduler scheduler;
    private StreamBuffer streamBuffer;
    private ContextManager contextManager;
    private UserManager userManager;
    private VisionInferenceEngine visionEngine;
    private CreditsManager creditsManager;
    private CreditsFeatureGate creditsGate;
    private GlobalExceptionHandler exceptionHandler;
    private MarkdownRenderer markdownRenderer;
    private ChatRepository chatRepository;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private Uri cameraImageUri;
    private String pendingImagePath;
    private ChatMessage currentAiMessage;
    private boolean isVisionAnalyzing = false;
    private boolean isOcrMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initManagers();
        initViews();
        setupAdapters();
        setupDrawer();
        setupQuickCommands();
        setupClickListeners();
        loadConversations();
        updateVisionModelStatus();
        updateModelDisplay();
        updateSendButton();
    }

    private void initManagers() {
        UserManager.init(this);
        CreditsManager.init(this);
        VisionInferenceEngine.init(this);

        userManager = UserManager.getInstance();
        chatManager = ChatManager.getInstance(this);
        scheduler = AIScheduler.getInstance();
        streamBuffer = new StreamBuffer();
        contextManager = new ContextManager();
        visionEngine = VisionInferenceEngine.getInstance();
        creditsManager = CreditsManager.getInstance();
        creditsGate = CreditsFeatureGate.getInstance();
        exceptionHandler = GlobalExceptionHandler.getInstance();
        markdownRenderer = MarkdownRenderer.getInstance();
        chatRepository = new ChatRepository(this);

        ensurePreinstalledModelsLoaded();
    }

    private void ensurePreinstalledModelsLoaded() {
        PreinstalledModelManager preinstalledMgr = PreinstalledModelManager.getInstance(this);
        preinstalledMgr.ensureModelsExtracted(new PreinstalledModelManager.ExtractionCallback() {
            @Override
            public void onProgress(String modelName, float progress) {}

            @Override
            public void onModelReady(AIModel model) {}

            @Override
            public void onAllModelsReady() {
                runOnUiThread(() -> {
                    if (!InferenceEngine.getInstance().isModelLoaded()) {
                        AIModel textModel = PreinstalledModelManager.getInstance(ChatActivity.this).getPreinstalledTextModel();
                        InferenceEngine.getInstance().loadModel(textModel, new InferenceEngine.LoadCallback() {
                            @Override
                            public void onLoaded(AIModel model) {
                                runOnUiThread(() -> updateModelDisplay());
                            }

                            @Override
                            public void onError(String error) {}
                        });
                    }
                    if (!visionEngine.isVisionModelLoaded()) {
                        AIModel visionModel = PreinstalledModelManager.getInstance(ChatActivity.this).getPreinstalledVisionModel();
                        visionEngine.loadVisionModel(visionModel, new VisionInferenceEngine.LoadCallback() {
                            @Override
                            public void onLoaded(AIModel model) {
                                runOnUiThread(() -> updateVisionModelStatus());
                            }

                            @Override
                            public void onError(String error) {}
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {}
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        messageList = findViewById(R.id.rv_chat_messages);
        quickCommandList = findViewById(R.id.rv_quick_commands);
        inputText = findViewById(R.id.et_message);
        sendBtn = findViewById(R.id.btn_send);
        voiceBtn = findViewById(R.id.btn_voice);
        attachBtn = findViewById(R.id.btn_attach);
        modelNameText = findViewById(R.id.tv_model_name);
        modelStatusText = findViewById(R.id.tv_model_status);
        statusDot = findViewById(R.id.view_status_dot);
        statusText = findViewById(R.id.tv_status_text);
        statusBarLayout = findViewById(R.id.layout_status_bar);
        conversationList = findViewById(R.id.rv_conversations);
        userNicknameText = findViewById(R.id.tv_user_nickname);
        userAvatar = findViewById(R.id.iv_user_avatar);
        vipBadge = findViewById(R.id.tv_vip_badge);
        modelInfoText = findViewById(R.id.tv_model_info);
        moreBtn = findViewById(R.id.btn_more);
    }

    private void setupAdapters() {
        messageAdapter = new ChatMessageAdapter(markdownRenderer);
        LinearLayoutManager messageLayoutManager = new LinearLayoutManager(this);
        messageLayoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(messageLayoutManager);
        messageList.setAdapter(messageAdapter);

        conversationAdapter = new ConversationAdapter(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onClick(Conversation conversation) {
                switchConversation(conversation);
            }

            @Override
            public void onLongClick(Conversation conversation) {
                showConversationOptions(conversation);
            }

            @Override
            public void onDelete(Conversation conversation) {
                chatManager.deleteConversation(conversation.getId());
                loadConversations();
            }

            @Override
            public void onPin(Conversation conversation) {
                chatManager.pinConversation(conversation.getId(), !conversation.isPinned());
                loadConversations();
            }
        });
        conversationList.setLayoutManager(new LinearLayoutManager(this));
        conversationList.setAdapter(conversationAdapter);
    }

    private void setupDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        findViewById(R.id.btn_sidebar).setOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {}

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {}

            @Override
            public void onDrawerStateChanged(int newState) {}
        });

        if (userAvatar != null) {
            userAvatar.setOnClickListener(v -> {
                startActivity(new Intent(this, ProfileActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                startActivity(new Intent(this, SettingsActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navAgent = findViewById(R.id.nav_agent);
        if (navAgent != null) {
            navAgent.setOnClickListener(v -> {
                if (!userManager.isLoggedIn()) {
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.login_required))
                            .setMessage(getString(R.string.login_required_deep_think))
                            .setPositiveButton(getString(R.string.login), (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                            .setNegativeButton(getString(R.string.cancel), null)
                            .show();
                    return;
                }
                com.omniai.assistant.ui.agent.AgentActivity.start(this);
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navKnowledge = findViewById(R.id.nav_knowledge);
        if (navKnowledge != null) {
            navKnowledge.setOnClickListener(v -> {
                startActivity(new Intent(this, com.omniai.assistant.ui.knowledge.KnowledgeBaseActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navModel = findViewById(R.id.nav_model);
        if (navModel != null) {
            navModel.setOnClickListener(v -> {
                startActivity(new Intent(this, com.omniai.assistant.ui.model.ModelManagerActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navLora = findViewById(R.id.nav_lora);
        if (navLora != null) {
            navLora.setOnClickListener(v -> {
                startActivity(new Intent(this, com.omniai.assistant.ui.lora.LoraTrainActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navTerminal = findViewById(R.id.nav_terminal);
        if (navTerminal != null) {
            navTerminal.setOnClickListener(v -> {
                startActivity(new Intent(this, com.omniai.assistant.ui.terminal.TerminalActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView navCredits = findViewById(R.id.nav_credits);
        if (navCredits != null) {
            navCredits.setOnClickListener(v -> {
                startActivity(new Intent(this, com.omniai.assistant.ui.credits.CreditsCenterActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        TextView tvVersion = findViewById(R.id.tv_version);
        if (tvVersion != null) {
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvVersion.setText("v" + versionName);
            } catch (Exception e) {
                tvVersion.setText("v1.0.0");
            }
        }
    }

    private void setupQuickCommands() {
        List<QuickCommand> commands = QuickCommand.getDefaultCommands();
        quickCommandAdapter = new QuickCommandAdapter(command -> executeQuickCommand(command));
        LinearLayoutManager cmdLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        quickCommandList.setLayoutManager(cmdLayoutManager);
        quickCommandList.setAdapter(quickCommandAdapter);
        quickCommandAdapter.setCommands(commands);
    }

    private void setupClickListeners() {
        sendBtn.setOnClickListener(v -> sendMessage());
        voiceBtn.setOnClickListener(v -> toggleVoiceInput());
        attachBtn.setOnClickListener(v -> showAddOptions());

        findViewById(R.id.btn_new_chat).setOnClickListener(v -> createNewChat());

        if (moreBtn != null) {
            moreBtn.setOnClickListener(v -> showMoreMenu());
        }

        inputText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadConversations() {
        List<Conversation> conversations = chatManager.getConversations();
        conversationAdapter.setConversations(conversations);

        Conversation current = chatManager.getCurrentConversation();
        if (current != null) {
            conversationAdapter.setSelectedId(current.getId());
            loadMessages(current.getId());
        }

        updateSidebarUserInfo();
    }

    private void loadMessages(String conversationId) {
        List<ChatMessage> messages = chatRepository.loadMessages(conversationId);
        messageAdapter.setMessages(messages);
        if (!messages.isEmpty()) {
            messageList.scrollToPosition(messages.size() - 1);
        }
    }

    private void updateSidebarUserInfo() {
        if (userManager != null && userManager.getCurrentUser() != null) {
            String nickname = userManager.getCurrentUser().getNickname();
            if (userNicknameText != null) {
                userNicknameText.setText(nickname != null ? nickname : "用户");
            }
        }
    }

    private void updateVisionModelStatus() {
        if (modelStatusText == null) return;

        if (visionEngine.isVisionModelLoaded()) {
            AIModel visionModel = visionEngine.getCurrentVisionModel();
            String modelName = visionModel != null ? visionModel.getName() : "";
            modelStatusText.setText(modelName + " · 本地视觉推理");
            modelStatusText.setVisibility(View.VISIBLE);
        } else {
            AIScheduler.InferenceMode mode = scheduler.getCurrentMode();
            if (mode == AIScheduler.InferenceMode.CLOUD) {
                modelStatusText.setText("云端视觉推理");
                modelStatusText.setVisibility(View.VISIBLE);
            } else {
                modelStatusText.setText("视觉模型未加载");
                modelStatusText.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateModelDisplay() {
        AIModel loadedModel = InferenceEngine.getInstance().getLoadedModel();
        if (loadedModel != null) {
            modelNameText.setText(loadedModel.getName());
            if (modelInfoText != null) {
                modelInfoText.setText(loadedModel.getName() + " · 已就绪");
            }
        } else {
            modelNameText.setText("Senta AI");
            if (modelInfoText != null) {
                modelInfoText.setText("未加载模型");
            }
        }
    }

    private void sendMessage() {
        if (isVisionAnalyzing) {
            showSnackbar("图像分析中，请稍候");
            return;
        }

        String text = inputText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            return;
        }

        inputText.setText("");
        ensureConversation();
        sendTextMessage(text);
    }

    private void ensureConversation() {
        if (chatManager.getCurrentConversation() == null) {
            AIModel loadedModel = InferenceEngine.getInstance().getLoadedModel();
            String modelId = loadedModel != null ? loadedModel.getId() : "default";
            Conversation conversation = chatManager.createConversation("新对话", modelId);
            chatManager.setCurrentConversation(conversation.getId());
            conversationAdapter.setConversations(chatManager.getConversations());
            conversationAdapter.setSelectedId(conversation.getId());
        }
    }

    private void sendTextMessage(String text) {
        ChatMessage userMessage = new ChatMessage();
        userMessage.setContent(text);
        userMessage.setUser(true);
        userMessage.setMessageType("text");
        userMessage.setTimestamp(System.currentTimeMillis());

        Conversation current = chatManager.getCurrentConversation();
        if (current != null) {
            userMessage.setConversationId(current.getId());
        }

        messageAdapter.addMessage(userMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        currentAiMessage = new ChatMessage();
        currentAiMessage.setContent("");
        currentAiMessage.setUser(false);
        currentAiMessage.setMessageType("text");
        currentAiMessage.setTimestamp(System.currentTimeMillis());
        if (current != null) {
            currentAiMessage.setConversationId(current.getId());
        }
        messageAdapter.addMessage(currentAiMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        updateStatusIndicator(true);
        if (modelNameText != null) {
            modelNameText.setText(getString(R.string.status_ai_thinking));
        }

        streamBuffer.startStream();
        contextManager.addToContext(userMessage);

        chatManager.sendMessage(text, "text", new ChatManager.SendMessageCallback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> {
                    streamBuffer.append(token);
                    String content = streamBuffer.getContent();
                    currentAiMessage.setContent(content);
                    messageAdapter.updateLastMessage(currentAiMessage);
                    messageList.scrollToPosition(messageAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onComplete(ChatMessage message) {
                runOnUiThread(() -> {
                    streamBuffer.endStream();
                    currentAiMessage.setContent(message.getContent());
                    currentAiMessage.setTimestamp(message.getTimestamp());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    streamBuffer.clear();
                    contextManager.addToContext(message);
                    if (current != null) {
                        current.setTitle(text.length() > 20 ? text.substring(0, 20) + "…" : text);
                        conversationAdapter.setConversations(chatManager.getConversations());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    streamBuffer.endStream();
                    streamBuffer.clear();
                    currentAiMessage.setContent("推理错误: " + error);
                    currentAiMessage.setTimestamp(System.currentTimeMillis());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    showSnackbar("推理失败: " + error);
                });
            }
        });
    }

    private void sendImageMessage(String imagePath) {
        ensureConversation();
        isVisionAnalyzing = true;

        ChatMessage userMessage = new ChatMessage();
        userMessage.setContent("图片分析");
        userMessage.setUser(true);
        userMessage.setMessageType("image");
        userMessage.setAttachmentPath(imagePath);
        userMessage.setTimestamp(System.currentTimeMillis());

        Conversation current = chatManager.getCurrentConversation();
        if (current != null) {
            userMessage.setConversationId(current.getId());
        }

        messageAdapter.addMessage(userMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        currentAiMessage = new ChatMessage();
        currentAiMessage.setContent("");
        currentAiMessage.setUser(false);
        currentAiMessage.setMessageType("text");
        currentAiMessage.setTimestamp(System.currentTimeMillis());
        if (current != null) {
            currentAiMessage.setConversationId(current.getId());
        }
        messageAdapter.addMessage(currentAiMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        updateStatusIndicator(true);
        if (modelNameText != null) {
            modelNameText.setText(getString(R.string.vision_inference_running));
        }

        analyzeImageWithVision(imagePath, "请详细描述这张图片的内容", new VisionInferenceEngine.VisionCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    currentAiMessage.setContent(result);
                    currentAiMessage.setTimestamp(System.currentTimeMillis());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    isVisionAnalyzing = false;

                    Conversation conv = chatManager.getCurrentConversation();
                    if (conv != null) {
                        List<ChatMessage> savedMessages = chatRepository.loadMessages(conv.getId());
                        userMessage.setId(System.currentTimeMillis());
                        savedMessages.add(userMessage);
                        ChatMessage aiMsg = new ChatMessage();
                        aiMsg.setConversationId(conv.getId());
                        aiMsg.setContent(result);
                        aiMsg.setUser(false);
                        aiMsg.setMessageType("text");
                        aiMsg.setTimestamp(System.currentTimeMillis());
                        savedMessages.add(aiMsg);
                        chatRepository.saveMessages(conv.getId(), savedMessages);
                        conv.setTitle("图片分析");
                        conversationAdapter.setConversations(chatManager.getConversations());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isVisionAnalyzing = false;
                    currentAiMessage.setContent("图像分析失败: " + error);
                    currentAiMessage.setTimestamp(System.currentTimeMillis());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    exceptionHandler.handleVisionException(new Exception(error), message -> {
                        showSnackbar(message);
                    });
                });
            }
        });
    }

    private void analyzeImageWithVision(String imagePath, String prompt, VisionInferenceEngine.VisionCallback callback) {
        if (visionEngine.isVisionModelLoaded()) {
            visionEngine.visionChat(imagePath, prompt, callback);
            return;
        }

        AIModel defaultVisionModel = visionEngine.getDefaultVisionModel();
        if (!visionEngine.checkHardwareCompatibility(defaultVisionModel)) {
            if (callback != null) {
                callback.onError("设备硬件不兼容，无法加载视觉模型");
            }
            return;
        }

        showSnackbar("正在加载视觉模型…");
        visionEngine.loadVisionModel(defaultVisionModel, new VisionInferenceEngine.LoadCallback() {
            @Override
            public void onLoaded(AIModel model) {
                runOnUiThread(() -> {
                    updateVisionModelStatus();
                    showSnackbar("视觉模型加载完成");
                    visionEngine.visionChat(imagePath, prompt, callback);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    updateVisionModelStatus();
                    exceptionHandler.handleVisionException(new Exception(error), message -> {
                        if (callback != null) {
                            callback.onError(message);
                        }
                    });
                });
            }
        });
    }

    private void performOcr(String imagePath) {
        isVisionAnalyzing = true;
        updateStatusIndicator(true);
        if (modelNameText != null) {
            modelNameText.setText(getString(R.string.vision_ocr_processing));
        }

        if (visionEngine.isVisionModelLoaded()) {
            executeOcr(imagePath);
            return;
        }

        AIModel defaultVisionModel = visionEngine.getDefaultVisionModel();
        if (!visionEngine.checkHardwareCompatibility(defaultVisionModel)) {
            isVisionAnalyzing = false;
            updateStatusIndicator(false);
            updateModelDisplay();
            exceptionHandler.handleVisionException(new Exception("设备硬件不兼容，无法加载视觉模型"), message -> {
                showSnackbar(message);
            });
            return;
        }

        showSnackbar("正在加载视觉模型以执行OCR…");
        visionEngine.loadVisionModel(defaultVisionModel, new VisionInferenceEngine.LoadCallback() {
            @Override
            public void onLoaded(AIModel model) {
                runOnUiThread(() -> {
                    updateVisionModelStatus();
                    executeOcr(imagePath);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isVisionAnalyzing = false;
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    updateVisionModelStatus();
                    exceptionHandler.handleVisionException(new Exception(error), message -> {
                        showSnackbar(message);
                    });
                });
            }
        });
    }

    private void executeOcr(String imagePath) {
        visionEngine.imageOcr(imagePath, new VisionInferenceEngine.OcrCallback() {
            @Override
            public void onSuccess(String text) {
                runOnUiThread(() -> {
                    isVisionAnalyzing = false;
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    if (!TextUtils.isEmpty(text)) {
                        inputText.setText(text);
                        inputText.setSelection(inputText.getText().length());
                        showSnackbar("OCR识别完成，结果已填入输入框");
                    } else {
                        showSnackbar("未识别到文字内容");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isVisionAnalyzing = false;
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    exceptionHandler.handleVisionException(new Exception(error), message -> {
                        showSnackbar("OCR失败: " + message);
                    });
                });
            }
        });
    }

    private void createNewChat() {
        AIModel loadedModel = InferenceEngine.getInstance().getLoadedModel();
        String modelId = loadedModel != null ? loadedModel.getId() : "default";
        Conversation conversation = chatManager.createConversation("新对话", modelId);
        chatManager.setCurrentConversation(conversation.getId());
        conversationAdapter.setConversations(chatManager.getConversations());
        conversationAdapter.setSelectedId(conversation.getId());
        messageAdapter.setMessages(new ArrayList<>());
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void switchConversation(Conversation conversation) {
        chatManager.setCurrentConversation(conversation.getId());
        conversationAdapter.setSelectedId(conversation.getId());
        loadMessages(conversation.getId());
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void showConversationOptions(Conversation conversation) {
        String[] options = {"重命名", conversation.isPinned() ? "取消置顶" : "置顶", "删除"};
        new AlertDialog.Builder(this)
                .setTitle(conversation.getTitle())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showRenameDialog(conversation);
                            break;
                        case 1:
                            chatManager.pinConversation(conversation.getId(), !conversation.isPinned());
                            loadConversations();
                            break;
                        case 2:
                            new AlertDialog.Builder(this)
                                    .setTitle("删除对话")
                                    .setMessage("确定要删除「" + conversation.getTitle() + "」吗？")
                                    .setPositiveButton("删除", (d, w) -> {
                                        chatManager.deleteConversation(conversation.getId());
                                        if (chatManager.getCurrentConversation() != null
                                                && chatManager.getCurrentConversation().getId().equals(conversation.getId())) {
                                            messageAdapter.setMessages(new ArrayList<>());
                                        }
                                        loadConversations();
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(Conversation conversation) {
        EditText renameInput = new EditText(this);
        renameInput.setText(conversation.getTitle());
        renameInput.setSelection(conversation.getTitle().length());
        new AlertDialog.Builder(this)
                .setTitle("重命名对话")
                .setView(renameInput)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newTitle = renameInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(newTitle)) {
                        chatManager.renameConversation(conversation.getId(), newTitle);
                        loadConversations();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleVoiceInput() {
        if (isListening) {
            stopVoiceInput();
        } else {
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showSnackbar("语音识别不可用");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
            return;
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                    voiceBtn.setImageResource(R.drawable.ic_mic_active);
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    stopVoiceInput();
                }

                @Override
                public void onError(int error) {
                    stopVoiceInput();
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        String errorMsg;
                        switch (error) {
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                errorMsg = "网络超时";
                                break;
                            case SpeechRecognizer.ERROR_NETWORK:
                                errorMsg = "网络错误";
                                break;
                            case SpeechRecognizer.ERROR_AUDIO:
                                errorMsg = "音频错误";
                                break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                errorMsg = "权限不足";
                                break;
                            case SpeechRecognizer.ERROR_CLIENT:
                                errorMsg = "客户端错误";
                                break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                                errorMsg = "未检测到语音";
                                break;
                            default:
                                errorMsg = "语音识别错误";
                                break;
                        }
                        showSnackbar(errorMsg);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        inputText.setText(matches.get(0));
                        inputText.setSelection(inputText.getText().length());
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    private void stopVoiceInput() {
        isListening = false;
        voiceBtn.setImageResource(R.drawable.ic_mic);
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void showAddOptions() {
        String[] options = {"拍照", "选择图片", "选择文档", "OCR识图"};
        new AlertDialog.Builder(this)
                .setTitle("添加内容")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            captureImage();
                            break;
                        case 1:
                            isOcrMode = false;
                            pickImage();
                            break;
                        case 2:
                            pickDocument();
                            break;
                        case 3:
                            isOcrMode = true;
                            pickImageForOcr();
                            break;
                    }
                })
                .show();
    }

    private void pickImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_STORAGE);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    private void pickImageForOcr() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_STORAGE);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_OCR_IMAGE_PICK);
    }

    private void captureImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            return;
        }

        try {
            File imageFile = new File(getExternalCacheDir(), "camera_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = Uri.fromFile(imageFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        } catch (Exception e) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_CAMERA_CAPTURE);
        }
    }

    private void pickDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "text/plain", "text/csv"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_DOCUMENT_PICK);
    }

    private String copyUriToFile(Uri uri) {
        try {
            String fileName = getFileNameFromUri(uri);
            File destDir = new File(getCacheDir(), "uploads");
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File destFile = new File(destDir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            FileOutputStream outputStream = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            exceptionHandler.handleFileException(e, message -> {
                showSnackbar(message);
            });
            return null;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "file_" + System.currentTimeMillis();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            String lastSegment = uri.getLastPathSegment();
            if (lastSegment != null) {
                fileName = lastSegment;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fileName;
    }

    private void executeQuickCommand(QuickCommand command) {
        String category = command.getCategory();
        String name = command.getName();

        if ("创作".equals(category) || "开发".equals(category) || "工具".equals(category)) {
            if ("AI写作".equals(name) || "代码生成".equals(name) || "翻译".equals(name)) {
                if (!creditsGate.canUseAdvancedTextModel()) {
                    creditsGate.showInsufficientCreditsDialog(this);
                    return;
                }
                if (!creditsGate.deductIfNeeded(CreditsManager.CreditsFeature.ADVANCED_TEXT_MODEL)) {
                    creditsGate.showInsufficientCreditsDialog(this);
                    return;
                }
            }
        }

        if ("OCR识图".equals(name)) {
            if (!creditsGate.canUseAdvancedVisionModel()) {
                creditsGate.showInsufficientCreditsDialog(this);
                return;
            }
            if (!creditsGate.deductIfNeeded(CreditsManager.CreditsFeature.ADVANCED_VISION_MODEL)) {
                creditsGate.showInsufficientCreditsDialog(this);
                return;
            }
            isOcrMode = true;
            pickImageForOcr();
            return;
        }

        if ("Agent".equals(name)) {
            if (!userManager.isLoggedIn()) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.login_required))
                        .setMessage(getString(R.string.login_required_deep_think))
                        .setPositiveButton(getString(R.string.login), (d, w) -> startActivity(new Intent(this, LoginActivity.class)))
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
                return;
            }
            if (!creditsGate.canUseAdvancedAgent()) {
                creditsGate.showInsufficientCreditsDialog(this);
                return;
            }
            if (!creditsGate.deductIfNeeded(CreditsManager.CreditsFeature.ADVANCED_AGENT)) {
                creditsGate.showInsufficientCreditsDialog(this);
                return;
            }
            com.omniai.assistant.ui.agent.AgentActivity.start(this);
            return;
        }

        command.execute("", new QuickCommand.QuickCommandCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    inputText.setText(result);
                    inputText.setSelection(inputText.getText().length());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showSnackbar(getString(R.string.quick_command_failed, error)));
            }
        });
    }

    private void showMoreMenu() {
        String[] options = {getString(R.string.option_agent), getString(R.string.option_knowledge), getString(R.string.option_model_management), getString(R.string.option_lora_training), getString(R.string.option_terminal), getString(R.string.option_credits_center), getString(R.string.option_settings)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.more_options))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (!userManager.isLoggedIn()) {
                                new AlertDialog.Builder(ChatActivity.this)
                                        .setTitle(getString(R.string.login_required))
                                        .setMessage(getString(R.string.login_required_deep_think))
                                        .setPositiveButton(getString(R.string.login), (d, w) -> startActivity(new Intent(ChatActivity.this, LoginActivity.class)))
                                        .setNegativeButton(getString(R.string.cancel), null)
                                        .show();
                                return;
                            }
                            com.omniai.assistant.ui.agent.AgentActivity.start(this);
                            break;
                        case 1:
                            startActivity(new Intent(this, com.omniai.assistant.ui.knowledge.KnowledgeBaseActivity.class));
                            break;
                        case 2:
                            startActivity(new Intent(this, ModelManagerActivity.class));
                            break;
                        case 3:
                            startActivity(new Intent(this, com.omniai.assistant.ui.lora.LoraTrainActivity.class));
                            break;
                        case 4:
                            startActivity(new Intent(this, com.omniai.assistant.ui.terminal.TerminalActivity.class));
                            break;
                        case 5:
                            startActivity(new Intent(this, CreditsCenterActivity.class));
                            break;
                        case 6:
                            startActivity(new Intent(this, SettingsActivity.class));
                            break;
                    }
                })
                .show();
    }

    private void updateStatusIndicator(boolean isThinking) {
        if (statusBarLayout == null) return;

        if (isThinking) {
            statusBarLayout.setVisibility(View.VISIBLE);
            if (statusDot != null) {
                statusDot.setBackgroundResource(R.drawable.bg_tag_local);
            }
            if (statusText != null) {
                statusText.setText(R.string.status_ai_thinking);
            }
        } else {
            AIScheduler.InferenceMode mode = scheduler.getCurrentMode();
            if (mode == AIScheduler.InferenceMode.CLOUD) {
                statusBarLayout.setVisibility(View.VISIBLE);
                if (statusDot != null) {
                    statusDot.setBackgroundResource(R.drawable.bg_tag_gpu);
                }
                if (statusText != null) {
                    statusText.setText(R.string.status_cloud_taken_over);
                }
            } else {
                statusBarLayout.setVisibility(View.GONE);
            }
        }
    }

    private void updateSendButton() {
        String text = inputText.getText().toString().trim();
        sendBtn.setEnabled(!TextUtils.isEmpty(text) || pendingImagePath != null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CAMERA_CAPTURE && cameraImageUri != null) {
                File cameraFile = new File(cameraImageUri.getPath());
                if (cameraFile.exists()) {
                    cameraFile.delete();
                }
            }
            return;
        }

        switch (requestCode) {
            case REQUEST_IMAGE_PICK:
                handleImagePickResult(data);
                break;
            case REQUEST_CAMERA_CAPTURE:
                handleCameraCaptureResult();
                break;
            case REQUEST_DOCUMENT_PICK:
                handleDocumentPickResult(data);
                break;
            case REQUEST_OCR_IMAGE_PICK:
                handleOcrImagePickResult(data);
                break;
        }
    }

    private boolean isValidImageMimeType(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) return false;
        return mimeType.startsWith("image/");
    }

    private void handleImagePickResult(Intent data) {
        if (data == null || data.getData() == null) return;

        Uri imageUri = data.getData();
        if (!isValidImageMimeType(imageUri)) {
            showSnackbar(getString(R.string.error_invalid_image));
            return;
        }
        String localPath = copyUriToFile(imageUri);
        if (localPath == null) {
            showSnackbar(getString(R.string.error_image_read_failed));
            return;
        }

        pendingImagePath = localPath;
        sendImageMessage(localPath);
    }

    private void handleCameraCaptureResult() {
        if (cameraImageUri != null) {
            String path = cameraImageUri.getPath();
            if (path != null) {
                File cameraFile = new File(path);
                if (cameraFile.exists()) {
                    pendingImagePath = path;
                    sendImageMessage(path);
                    return;
                }
            }
        }

        showSnackbar(getString(R.string.camera_result_failed));
    }

    private void handleDocumentPickResult(Intent data) {
        if (data == null || data.getData() == null) return;

        Uri docUri = data.getData();
        String localPath = copyUriToFile(docUri);
        if (localPath == null) {
            showSnackbar("文档读取失败");
            return;
        }

        String fileName = getFileNameFromUri(docUri);
        ensureConversation();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setContent("文档: " + fileName);
        userMessage.setUser(true);
        userMessage.setMessageType("document");
        userMessage.setAttachmentPath(localPath);
        userMessage.setTimestamp(System.currentTimeMillis());

        Conversation current = chatManager.getCurrentConversation();
        if (current != null) {
            userMessage.setConversationId(current.getId());
        }

        messageAdapter.addMessage(userMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        currentAiMessage = new ChatMessage();
        currentAiMessage.setContent("");
        currentAiMessage.setUser(false);
        currentAiMessage.setMessageType("text");
        currentAiMessage.setTimestamp(System.currentTimeMillis());
        if (current != null) {
            currentAiMessage.setConversationId(current.getId());
        }
        messageAdapter.addMessage(currentAiMessage);
        messageList.scrollToPosition(messageAdapter.getItemCount() - 1);

        updateStatusIndicator(true);
        if (modelNameText != null) {
            modelNameText.setText(getString(R.string.status_ai_thinking));
        }

        String prompt = "请分析以下文档内容: " + fileName;
        streamBuffer.startStream();

        chatManager.sendMessage(prompt, "text", new ChatManager.SendMessageCallback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> {
                    streamBuffer.append(token);
                    currentAiMessage.setContent(streamBuffer.getContent());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    messageList.scrollToPosition(messageAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onComplete(ChatMessage message) {
                runOnUiThread(() -> {
                    streamBuffer.endStream();
                    currentAiMessage.setContent(message.getContent());
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    streamBuffer.clear();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    streamBuffer.endStream();
                    streamBuffer.clear();
                    currentAiMessage.setContent(getString(R.string.document_analysis_failed, error));
                    messageAdapter.updateLastMessage(currentAiMessage);
                    updateStatusIndicator(false);
                    updateModelDisplay();
                    showSnackbar(getString(R.string.document_analysis_failed, error));
                });
            }
        });
    }

    private void handleOcrImagePickResult(Intent data) {
        if (data == null || data.getData() == null) return;

        Uri imageUri = data.getData();
        if (!isValidImageMimeType(imageUri)) {
            showSnackbar(getString(R.string.error_invalid_image));
            return;
        }
        String localPath = copyUriToFile(imageUri);
        if (localPath == null) {
            showSnackbar(getString(R.string.error_image_read_failed));
            return;
        }

        performOcr(localPath);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_AUDIO:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startVoiceInput();
                } else {
                    if (!shouldShowPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        exceptionHandler.showPermissionErrorDialog(this, Manifest.permission.RECORD_AUDIO);
                    } else {
                        exceptionHandler.handlePermissionException(Manifest.permission.RECORD_AUDIO, message -> {
                            showSnackbar(message);
                        });
                    }
                }
                break;
            case PERMISSION_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage();
                } else {
                    if (!shouldShowPermissionRationale(Manifest.permission.CAMERA)) {
                        exceptionHandler.showPermissionErrorDialog(this, Manifest.permission.CAMERA);
                    } else {
                        exceptionHandler.handlePermissionException(Manifest.permission.CAMERA, message -> {
                            showSnackbar(message);
                        });
                    }
                }
                break;
            case PERMISSION_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isOcrMode) {
                        pickImageForOcr();
                    } else {
                        pickImage();
                    }
                } else {
                    if (!shouldShowPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        exceptionHandler.showPermissionErrorDialog(this, Manifest.permission.READ_EXTERNAL_STORAGE);
                    } else {
                        exceptionHandler.handlePermissionException(Manifest.permission.READ_EXTERNAL_STORAGE, message -> {
                            showSnackbar(message);
                        });
                    }
                }
                break;
        }
    }

    private boolean shouldShowPermissionRationale(String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userManager != null && userManager.isTokenExpired()) {
            try {
                UserManager.init(this);
                userManager = UserManager.getInstance();
            } catch (Exception e) {
                android.util.Log.w("ChatActivity", "Token refresh failed", e);
                Toast.makeText(this, R.string.error_token_expired, Toast.LENGTH_LONG).show();
            }
        }

        if (userManager != null && userManager.isLoggedIn()) {
            userManager.checkTokenExpiry();
        }

        loadConversations();
        updateVisionModelStatus();
        updateModelDisplay();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (streamBuffer != null) {
            streamBuffer.clear();
        }
        pendingImagePath = null;
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show();
    }
}
