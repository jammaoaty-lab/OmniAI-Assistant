package com.omniai.assistant.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.omniai.assistant.R;

public class CodeBlockView extends LinearLayout {

    private static final String BACKGROUND_COLOR = "#1E1E2E";
    private static final String HEADER_COLOR = "#2D2D3F";
    private static final String CODE_TEXT_COLOR = "#CDD6F4";
    private static final String LINE_NUMBER_COLOR = "#6C7086";
    private static final String LANGUAGE_TEXT_COLOR = "#CDD6F4";
    private static final int CORNER_RADIUS = 12;
    private static final int CODE_TEXT_SIZE = 13;
    private static final int LANGUAGE_TEXT_SIZE = 12;
    private static final int HEADER_HEIGHT = 36;
    private static final int PADDING = 16;
    private static final int PADDING_SM = 8;

    private TextView languageLabel;
    private TextView codeTextView;
    private ImageButton copyButton;
    private String currentCode;

    public CodeBlockView(Context context) {
        super(context);
        init(context);
    }

    public CodeBlockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CodeBlockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor(BACKGROUND_COLOR));
        background.setCornerRadius(dpToPx(context, CORNER_RADIUS));
        setBackground(background);

        addView(createHeader(context));
        addView(createCodeArea(context));
    }

    private LinearLayout createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(context, PADDING), dpToPx(context, PADDING_SM), dpToPx(context, PADDING_SM), dpToPx(context, PADDING_SM));

        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.parseColor(HEADER_COLOR));
        headerBg.setCornerRadii(new float[]{
                dpToPx(context, CORNER_RADIUS), dpToPx(context, CORNER_RADIUS),
                dpToPx(context, CORNER_RADIUS), dpToPx(context, CORNER_RADIUS),
                0, 0, 0, 0
        });
        header.setBackground(headerBg);

        LayoutParams headerParams = new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(context, HEADER_HEIGHT));
        header.setLayoutParams(headerParams);

        languageLabel = new TextView(context);
        languageLabel.setTextColor(Color.parseColor(LANGUAGE_TEXT_COLOR));
        languageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, LANGUAGE_TEXT_SIZE);
        languageLabel.setTypeface(null, Typeface.BOLD);
        languageLabel.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        languageLabel.setLayoutParams(labelParams);

        copyButton = new ImageButton(context);
        copyButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_copy));
        copyButton.setBackgroundColor(Color.TRANSPARENT);
        copyButton.setPadding(dpToPx(context, PADDING_SM), dpToPx(context, PADDING_SM), dpToPx(context, PADDING_SM), dpToPx(context, PADDING_SM));
        copyButton.setOnClickListener(v -> copyCode());

        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dpToPx(context, 32), dpToPx(context, 32));
        copyParams.gravity = Gravity.CENTER_VERTICAL;
        copyButton.setLayoutParams(copyParams);

        header.addView(languageLabel);
        header.addView(copyButton);

        return header;
    }

    private ScrollView createCodeArea(Context context) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LayoutParams scrollParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setPadding(dpToPx(context, PADDING), dpToPx(context, PADDING), dpToPx(context, PADDING), dpToPx(context, PADDING));

        codeTextView = new TextView(context);
        codeTextView.setTextColor(Color.parseColor(CODE_TEXT_COLOR));
        codeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, CODE_TEXT_SIZE);
        codeTextView.setTypeface(Typeface.MONOSPACE);
        codeTextView.setLineSpacing(dpToPx(context, 4), 1f);

        scrollView.addView(codeTextView);

        return scrollView;
    }

    public void setLanguage(String language) {
        if (language != null && !language.isEmpty()) {
            languageLabel.setText(language.toUpperCase());
        } else {
            languageLabel.setText("CODE");
        }
    }

    public void setCode(String code) {
        this.currentCode = code;
        codeTextView.setText(formatCodeWithLineNumbers(code));
    }

    private String formatCodeWithLineNumbers(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }

        String[] lines = code.split("\n", -1);
        int maxLineNumber = lines.length;
        int lineNumberWidth = String.valueOf(maxLineNumber).length();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String lineNum = String.valueOf(i + 1);
            while (lineNum.length() < lineNumberWidth) {
                lineNum = " " + lineNum;
            }
            sb.append(lineNum);
            sb.append("  ");
            sb.append(lines[i]);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private void copyCode() {
        if (currentCode == null || currentCode.isEmpty()) {
            return;
        }

        Context context = getContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("code", currentCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
