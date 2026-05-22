package club.apk.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(4, 7, 10);
    private static final int CARD = Color.rgb(11, 18, 24);
    private static final int CARD2 = Color.rgb(7, 16, 23);
    private static final int INPUT = Color.rgb(17, 27, 36);
    private static final int NEON = Color.rgb(0, 255, 157);
    private static final int GLOW = Color.rgb(0, 179, 110);
    private static final int TEXT = Color.rgb(226, 241, 236);
    private static final int MUTED = Color.rgb(113, 136, 150);
    private static final int WARN = Color.rgb(255, 209, 102);
    private static final int ERROR = Color.rgb(255, 77, 109);
    private static final int CODE_BG = Color.rgb(6, 23, 29);
    private static final int CODE_HEAD = Color.rgb(8, 36, 43);

    private static final String DEFAULT_BASE_URL = "https://api.freemodel.dev";
    private static final String DEFAULT_MODEL = "gpt-5.5";
    private static final String USDT_BEP20_WALLET = "0x3792db72b616a0620AB76ceA016356246b355897";
    private static final String DEVELOPER_ID = "@darkbiitt";
    private static final String CHANNEL_ID = "@apkclub";
    private static final String DEVELOPER_URL = "https://t.me/darkbiitt";
    private static final String CHANNEL_URL = "https://t.me/apkclub";
    private static final int MAX_HISTORY_ITEMS = 10;
    private static final int MAX_TYPEWRITER_CHARS = 5000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private LinearLayout chatList;
    private LinearLayout connectionPanel;
    private LinearLayout statusCard;
    private ScrollView chatScroll;
    private EditText baseUrlInput;
    private EditText modelInput;
    private EditText apiKeyInput;
    private EditText promptInput;
    private TextView statusText;
    private TextView subStatusText;
    private TextView compactStatusText;
    private TextView connectionSummaryText;
    private Button sendButton;
    private Button showKeyButton;
    private Button toggleConfigButton;
    private Button supportButton;
    private Button copyAnswerButton;
    private Button clearButton;
    private AnimatedNeuralView neuralView;

    private boolean busy = false;
    private boolean cancelled = false;
    private boolean keyVisible = false;
    private boolean configExpanded = true;
    private HttpURLConnection activeConnection;
    private Thread activeThread;
    private long startedAt = 0L;
    private int requestSerial = 0;
    private String lastPrompt = "";
    private String lastAnswer = "";
    private final ArrayList<ChatMsg> history = new ArrayList<>();
    private SharedPreferences prefs;

    private static class ChatMsg {
        final String role;
        final String content;
        ChatMsg(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class RenderBlock {
        final boolean code;
        final String lang;
        final String text;
        RenderBlock(boolean code, String lang, String text) {
            this.code = code;
            this.lang = lang;
            this.text = text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences("apkclub_ai", MODE_PRIVATE);
        buildUi();
        loadSettings();
        setStatus("آماده", "کلید API را وارد کن و پیام بده");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String text, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setIncludeFontPadding(true);
        return t;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(new RoundDrawable(INPUT, Color.rgb(30, 41, 59), dp(10), dp(1)));
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return e;
    }

    private Button button(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(new RoundDrawable(bg, Color.TRANSPARENT, dp(12), 0));
        return b;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        neuralView = new AnimatedNeuralView(this);
        root.addView(neuralView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(118)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(main, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(16), dp(12), dp(16), dp(10));
        brand.setBackground(new RoundDrawable(CARD, Color.rgb(16, 42, 45), dp(18), dp(1)));
        main.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView logo = tv("AI APKCLUB", 25, NEON, Typeface.BOLD);
        logo.setGravity(Gravity.LEFT);
        brand.addView(logo);
        TextView tagline = tv("ChatGPT • موبایل • کدنویسی مرتب", 12, MUTED, Typeface.BOLD);
        tagline.setGravity(Gravity.RIGHT);
        brand.addView(tagline);

        LinearLayout config = new LinearLayout(this);
        config.setOrientation(LinearLayout.VERTICAL);
        config.setPadding(dp(12), dp(10), dp(12), dp(10));
        config.setBackground(new RoundDrawable(CARD2, Color.rgb(12, 64, 48), dp(18), dp(1)));
        LinearLayout.LayoutParams cfgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cfgLp.setMargins(0, dp(10), 0, dp(8));
        main.addView(config, cfgLp);

        LinearLayout configHeader = new LinearLayout(this);
        configHeader.setOrientation(LinearLayout.HORIZONTAL);
        configHeader.setGravity(Gravity.CENTER_VERTICAL);
        config.addView(configHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cfgTitle = tv("⚙ تنظیمات اتصال", 14, TEXT, Typeface.BOLD);
        cfgTitle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        configHeader.addView(cfgTitle, new LinearLayout.LayoutParams(0, dp(42), 1f));

        toggleConfigButton = button("بستن ▲", INPUT, NEON);
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(dp(116), dp(40));
        toggleLp.setMargins(dp(8), 0, 0, 0);
        configHeader.addView(toggleConfigButton, toggleLp);
        toggleConfigButton.setOnClickListener(v -> setConfigExpanded(!configExpanded, true));
        configHeader.setOnClickListener(v -> setConfigExpanded(!configExpanded, true));

        connectionSummaryText = tv("", 11, MUTED, Typeface.NORMAL);
        connectionSummaryText.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.setMargins(0, dp(3), 0, 0);
        config.addView(connectionSummaryText, summaryLp);

        compactStatusText = tv("● آماده • در انتظار پیام", 11, NEON, Typeface.BOLD);
        compactStatusText.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams compactStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        compactStatusLp.setMargins(0, dp(6), 0, dp(2));
        config.addView(compactStatusText, compactStatusLp);

        connectionPanel = new LinearLayout(this);
        connectionPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.setMargins(0, dp(5), 0, 0);
        config.addView(connectionPanel, panelLp);

        baseUrlInput = input("Base URL / آدرس API", false);
        modelInput = input("Model / مدل", false);
        apiKeyInput = input("API Key / کلید", true);
        addInput(connectionPanel, baseUrlInput, dp(8));
        addInput(connectionPanel, modelInput, dp(7));
        addInput(connectionPanel, apiKeyInput, dp(7));

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.RIGHT);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        connectionPanel.addView(toolRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        showKeyButton = button("نمایش کلید", INPUT, TEXT);
        toolRow.addView(showKeyButton, new LinearLayout.LayoutParams(0, dp(36), 1f));
        showKeyButton.setOnClickListener(v -> toggleKey());

        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(8), dp(14), dp(8));
        statusCard.setBackground(new RoundDrawable(Color.rgb(6, 10, 14), Color.rgb(26, 45, 55), dp(16), dp(1)));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.setMargins(0, dp(8), 0, 0);
        connectionPanel.addView(statusCard, stLp);
        statusText = tv("آماده", 13, NEON, Typeface.BOLD);
        statusText.setGravity(Gravity.RIGHT);
        subStatusText = tv("در انتظار پیام", 11, MUTED, Typeface.NORMAL);
        subStatusText.setGravity(Gravity.RIGHT);
        statusCard.addView(statusText);
        statusCard.addView(subStatusText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        actLp.setMargins(0, dp(8), 0, 0);
        connectionPanel.addView(actions, actLp);
        clearButton = button("پاک کردن", INPUT, TEXT);
        copyAnswerButton = button("کپی پاسخ", INPUT, TEXT);
        Button resetButton = button("ریست حافظه", INPUT, TEXT);
        actions.addView(clearButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, dp(38), 1f);
        mid.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(copyAnswerButton, mid);
        actions.addView(resetButton, new LinearLayout.LayoutParams(0, dp(38), 1f));
        clearButton.setOnClickListener(v -> clearChat());
        copyAnswerButton.setOnClickListener(v -> copyText(lastAnswer, "پاسخی برای کپی نیست"));
        resetButton.setOnClickListener(v -> { history.clear(); toast("حافظه چت ریست شد"); });

        supportButton = button("💚 حمایت و ارتباط با ما", Color.rgb(0, 35, 25), NEON);
        supportButton.setBackground(new RoundDrawable(
                Color.rgb(0, 35, 25), Color.rgb(0, 179, 110), dp(13), dp(1)));
        LinearLayout.LayoutParams supportLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        supportLp.setMargins(0, dp(9), 0, dp(2));
        connectionPanel.addView(supportButton, supportLp);
        supportButton.setOnClickListener(v -> showSupportDialog());

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatScroll.setBackground(new RoundDrawable(CARD, Color.rgb(17, 27, 36), dp(18), dp(1)));
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(12), dp(12), dp(12), dp(12));
        chatScroll.addView(chatList);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        chatLp.setMargins(0, dp(8), 0, dp(8));
        main.addView(chatScroll, chatLp);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.HORIZONTAL);
        inputCard.setGravity(Gravity.CENTER_VERTICAL);
        inputCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        inputCard.setBackground(new RoundDrawable(CARD, Color.rgb(17, 27, 36), dp(18), dp(1)));
        main.addView(inputCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        promptInput = new EditText(this);
        promptInput.setHint("پیام بنویسید...");
        promptInput.setHintTextColor(MUTED);
        promptInput.setTextColor(TEXT);
        promptInput.setTextSize(14);
        promptInput.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        promptInput.setPadding(dp(12), dp(6), dp(12), dp(6));
        promptInput.setBackground(new RoundDrawable(INPUT, Color.TRANSPARENT, dp(14), 0));
        inputCard.addView(promptInput, new LinearLayout.LayoutParams(0, dp(56), 1f));

        sendButton = button("ارسال ⚡", NEON, Color.rgb(0, 20, 12));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(96), dp(56));
        sendLp.setMargins(dp(8), 0, 0, 0);
        inputCard.addView(sendButton, sendLp);
        sendButton.setOnClickListener(v -> sendOrStop());
    }

    private void addInput(LinearLayout parent, EditText view, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.setMargins(0, top, 0, 0);
        parent.addView(view, lp);
    }

    private void loadSettings() {
        baseUrlInput.setText(prefs.getString("base_url", DEFAULT_BASE_URL));
        modelInput.setText(prefs.getString("model", DEFAULT_MODEL));
        setConfigExpanded(prefs.getBoolean("config_expanded", true), false);
        updateConnectionSummary();
    }

    private void saveSettings() {
        prefs.edit()
                .putString("base_url", baseUrlInput.getText().toString().trim())
                .putString("model", modelInput.getText().toString().trim())
                .putBoolean("config_expanded", configExpanded)
                .apply();
        updateConnectionSummary();
    }

    private void setConfigExpanded(boolean expanded, boolean animate) {
        configExpanded = expanded;
        if (toggleConfigButton == null || connectionPanel == null) return;

        toggleConfigButton.setText(expanded ? "بستن ▲" : "تنظیمات ▼");
        toggleConfigButton.setTextColor(expanded ? TEXT : NEON);

        if (expanded) {
            connectionPanel.setVisibility(View.VISIBLE);
            if (animate) {
                connectionPanel.setAlpha(0f);
                connectionPanel.setTranslationY(-dp(6));
                connectionPanel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .start();
            } else {
                connectionPanel.setAlpha(1f);
                connectionPanel.setTranslationY(0f);
            }
        } else {
            if (animate && connectionPanel.getVisibility() == View.VISIBLE) {
                connectionPanel.animate()
                        .alpha(0f)
                        .translationY(-dp(6))
                        .setDuration(130)
                        .withEndAction(() -> {
                            if (!configExpanded) {
                                connectionPanel.setVisibility(View.GONE);
                                connectionPanel.setAlpha(1f);
                                connectionPanel.setTranslationY(0f);
                            }
                        })
                        .start();
            } else {
                connectionPanel.setVisibility(View.GONE);
                connectionPanel.setAlpha(1f);
                connectionPanel.setTranslationY(0f);
            }
        }
        prefs.edit().putBoolean("config_expanded", configExpanded).apply();
        updateConnectionSummary();
    }

    private void updateConnectionSummary() {
        if (connectionSummaryText == null || modelInput == null || apiKeyInput == null) return;
        String model = modelInput.getText().toString().trim();
        if (model.length() == 0) model = DEFAULT_MODEL;
        String keyState = apiKeyInput.length() > 0 ? "کلید آماده" : "کلید وارد نشده";
        if (configExpanded) {
            connectionSummaryText.setText("اتصال، حمایت و ارتباط • بعد از ارسال خودکار جمع می‌شود");
        } else {
            connectionSummaryText.setText("ChatGPT • " + shortText(model, 20) + " • " + keyState);
        }
    }

    private void toggleKey() {
        keyVisible = !keyVisible;
        if (keyVisible) {
            apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            showKeyButton.setText("مخفی کردن کلید");
        } else {
            apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            showKeyButton.setText("نمایش کلید");
        }
        apiKeyInput.setSelection(apiKeyInput.length());
    }

    private void sendOrStop() {
        if (busy) {
            stopRequest();
            return;
        }
        String prompt = normalizeFa(promptInput.getText().toString().trim());
        if (prompt.length() == 0) return;
        String baseUrl = baseUrlInput.getText().toString().trim();
        String model = modelInput.getText().toString().trim();
        String key = apiKeyInput.getText().toString().trim();
        if (!(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            setConfigExpanded(true, true);
            baseUrlInput.requestFocus();
            addSystemError("Base URL باید با http:// یا https:// شروع شود.");
            return;
        }
        if (model.length() == 0) {
            setConfigExpanded(true, true);
            modelInput.requestFocus();
            addSystemError("نام مدل خالی است.");
            return;
        }
        if (key.length() == 0) {
            setConfigExpanded(true, true);
            apiKeyInput.requestFocus();
            addSystemError("کلید API را وارد کن.");
            return;
        }

        saveSettings();
        setConfigExpanded(false, true);
        lastPrompt = prompt;
        promptInput.setText("");
        addUserMessage(prompt);
        startRequest(baseUrl, model, key, prompt, new ArrayList<>(history));
    }

    private void startRequest(String baseUrl, String model, String key, String prompt, ArrayList<ChatMsg> historySnapshot) {
        busy = true;
        cancelled = false;
        startedAt = System.currentTimeMillis();
        int id = ++requestSerial;
        sendButton.setText("توقف ⛔");
        sendButton.setBackground(new RoundDrawable(WARN, Color.TRANSPARENT, dp(12), 0));
        setStatus("در حال فکر کردن...", "اتصال به مدل");

        activeThread = new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl.replaceAll("/+$", "") + "/v1/chat/completions");
                conn = (HttpURLConnection) url.openConnection();
                activeConnection = conn;
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(80000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + key);

                JSONObject payload = buildPayload(model, prompt, historySnapshot);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream os = conn.getOutputStream();
                os.write(body);
                os.close();

                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String responseText = readAll(is);
                if (cancelled || id != requestSerial) return;

                if (code < 200 || code >= 300) {
                    runOnUiThread(() -> onError("HTTP " + code + ": " + shortText(responseText, 700)));
                    return;
                }

                String output = extractOutput(new JSONObject(responseText));
                if (output.trim().length() == 0) {
                    runOnUiThread(() -> onError("پاسخ مدل خالی بود یا فرمت شناخته نشد."));
                    return;
                }
                runOnUiThread(() -> onSuccess(id, normalizeFa(output)));
            } catch (Exception e) {
                if (!cancelled) runOnUiThread(() -> onError("خطا در اتصال: " + e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
                activeConnection = null;
            }
        });
        activeThread.start();
    }

    private JSONObject buildPayload(String model, String prompt, ArrayList<ChatMsg> historySnapshot) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.72);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", "You are AI APKCLUB, a fast ChatGPT-style assistant. Always answer in the same language as the user. If the user writes Persian, respond in fluent Persian. For code, always use fenced Markdown code blocks with the correct language name like ```python."));
        int start = Math.max(0, historySnapshot.size() - MAX_HISTORY_ITEMS);
        for (int i = start; i < historySnapshot.size(); i++) {
            ChatMsg m = historySnapshot.get(i);
            messages.put(new JSONObject().put("role", m.role).put("content", m.content));
        }
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        payload.put("messages", messages);
        return payload;
    }

    private String extractOutput(JSONObject data) throws Exception {
        if (data.has("choices")) {
            JSONArray choices = data.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject first = choices.getJSONObject(0);
                if (first.has("message")) {
                    JSONObject msg = first.getJSONObject("message");
                    Object content = msg.opt("content");
                    if (content instanceof String) return (String) content;
                    if (content instanceof JSONArray) {
                        JSONArray arr = (JSONArray) content;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject part = arr.optJSONObject(i);
                            if (part != null) sb.append(part.optString("text", part.optString("content", "")));
                        }
                        return sb.toString();
                    }
                }
                if (first.has("text")) return first.optString("text", "");
            }
        }
        if (data.has("output_text")) return data.optString("output_text", "");
        return "";
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void onSuccess(int id, String output) {
        if (id != requestSerial || cancelled) return;
        long elapsed = System.currentTimeMillis() - startedAt;
        lastAnswer = output;
        setStatus("در حال نمایش پاسخ", "زمان پاسخ: " + String.format(Locale.US, "%.1f", elapsed / 1000.0) + "s");
        addAiMessage(output, () -> {
            busy = false;
            sendButton.setText("ارسال ⚡");
            sendButton.setBackground(new RoundDrawable(NEON, Color.TRANSPARENT, dp(12), 0));
            setStatus("آماده", "آخرین پاسخ: " + String.format(Locale.US, "%.1f", elapsed / 1000.0) + "s");
            history.add(new ChatMsg("user", lastPrompt));
            history.add(new ChatMsg("assistant", lastAnswer));
            while (history.size() > MAX_HISTORY_ITEMS) history.remove(0);
        });
    }

    private void onError(String msg) {
        busy = false;
        sendButton.setText("ارسال ⚡");
        sendButton.setBackground(new RoundDrawable(NEON, Color.TRANSPARENT, dp(12), 0));
        addSystemError(msg);
        setStatus("خطا", shortText(msg, 80));
    }

    private void stopRequest() {
        cancelled = true;
        requestSerial++;
        busy = false;
        try {
            if (activeConnection != null) activeConnection.disconnect();
        } catch (Exception ignored) {}
        sendButton.setText("ارسال ⚡");
        sendButton.setBackground(new RoundDrawable(NEON, Color.TRANSPARENT, dp(12), 0));
        setStatus("متوقف شد", "درخواست لغو شد");
    }

    private void setStatus(String a, String b) {
        statusText.setText(a);
        subStatusText.setText(b);
        if (compactStatusText != null) {
            int statusColor = a.contains("خطا") ? ERROR :
                    (a.contains("فکر") || a.contains("نمایش")) ? WARN : NEON;
            compactStatusText.setTextColor(statusColor);
            compactStatusText.setText("● " + a + " • " + b);
        }
    }

    private void addUserMessage(String text) {
        LinearLayout card = messageCard(true, hasRtl(text));
        TextView title = tv(hasRtl(text) ? "👤 شما" : "👤 You", 13, NEON, Typeface.BOLD);
        title.setGravity(hasRtl(text) ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(title);
        TextView body = tv(text, 15, TEXT, Typeface.NORMAL);
        body.setTextIsSelectable(true);
        body.setGravity(hasRtl(text) ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(body);
        chatList.addView(card);
        scrollDown();
    }

    private void addAiMessage(String output, Runnable onDone) {
        LinearLayout card = messageCard(false, hasRtl(output));
        TextView title = tv(hasRtl(output) ? "🤖 پاسخ هوش مصنوعی" : "🤖 AI Response", 13, NEON, Typeface.BOLD);
        title.setGravity(hasRtl(output) ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(title);
        chatList.addView(card);
        scrollDown();
        ArrayList<RenderBlock> blocks = parseBlocks(output);
        renderBlock(card, blocks, 0, onDone);
    }

    private void renderBlock(LinearLayout parent, ArrayList<RenderBlock> blocks, int index, Runnable onDone) {
        if (cancelled) return;
        if (index >= blocks.size()) {
            if (onDone != null) onDone.run();
            scrollDown();
            return;
        }
        RenderBlock block = blocks.get(index);
        if (block.code) {
            parent.addView(codeCard(block.lang, block.text));
            scrollDown();
            ui.postDelayed(() -> renderBlock(parent, blocks, index + 1, onDone), 80);
        } else {
            TextView body = tv("", 15, TEXT, Typeface.NORMAL);
            body.setTextIsSelectable(true);
            body.setGravity(hasRtl(block.text) ? Gravity.RIGHT : Gravity.LEFT);
            body.setLineSpacing(dp(2), 1.0f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(8), 0, dp(4));
            parent.addView(body, lp);
            String clean = cleanMarkdownText(block.text);
            typeInto(body, clean, 0, () -> renderBlock(parent, blocks, index + 1, onDone));
        }
    }

    private void typeInto(TextView target, String text, int index, Runnable done) {
        if (cancelled) return;
        if (text.length() > MAX_TYPEWRITER_CHARS) {
            target.setText(text);
            scrollDown();
            if (done != null) done.run();
            return;
        }
        if (index >= text.length()) {
            if (done != null) done.run();
            return;
        }
        int step = text.length() > 1500 ? 8 : text.length() > 700 ? 5 : 2;
        int next = Math.min(text.length(), index + step);
        target.append(text.substring(index, next));
        scrollDown();
        ui.postDelayed(() -> typeInto(target, text, next, done), text.length() > 1000 ? 5 : 12);
    }

    private LinearLayout messageCard(boolean user, boolean rtl) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        int border = user ? Color.rgb(13, 88, 65) : Color.rgb(16, 54, 63);
        int fill = user ? Color.rgb(5, 25, 20) : Color.rgb(8, 17, 23);
        card.setBackground(new RoundDrawable(fill, border, dp(18), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        card.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        return card;
    }

    private View codeCard(String lang, String code) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(new RoundDrawable(CODE_BG, Color.rgb(11, 234, 157), dp(16), dp(1)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, dp(10), 0, dp(8));
        card.setLayoutParams(cardLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(8), dp(6), dp(8), dp(6));
        head.setBackground(new RoundDrawable(CODE_HEAD, Color.rgb(15, 51, 64), dp(14), dp(1)));
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = tv(shortLang(lang), 12, Color.rgb(0, 20, 12), Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(new RoundDrawable(NEON, Color.TRANSPARENT, dp(10), 0));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(58), dp(32));
        head.addView(badge, badgeLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.setMargins(dp(8), 0, dp(8), 0);
        head.addView(texts, textsLp);
        TextView title = tv("بلوک کد " + displayLang(lang), 13, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT);
        TextView meta = tv("نمایش مرتب و قابل کپی • " + Math.max(1, code.split("\n", -1).length) + " خط", 10, MUTED, Typeface.NORMAL);
        meta.setGravity(Gravity.LEFT);
        texts.addView(title);
        texts.addView(meta);

        Button copy = button("کپی", NEON, Color.rgb(0, 20, 12));
        head.addView(copy, new LinearLayout.LayoutParams(dp(68), dp(32)));
        copy.setOnClickListener(v -> {
            copyText(code, "کدی برای کپی نیست");
            copy.setText("شد ✅");
            ui.postDelayed(() -> copy.setText("کپی"), 1200);
        });

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(true);
        hsv.setHorizontalScrollBarEnabled(true);
        LinearLayout.LayoutParams hsvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hsvLp.setMargins(0, dp(8), 0, 0);
        card.addView(hsv, hsvLp);

        TextView codeView = tv(code, 13, Color.rgb(231, 255, 248), Typeface.NORMAL);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setGravity(Gravity.LEFT);
        codeView.setPadding(dp(12), dp(10), dp(12), dp(10));
        codeView.setBackground(new RoundDrawable(Color.rgb(3, 12, 16), Color.rgb(12, 42, 52), dp(12), dp(1)));
        codeView.setMovementMethod(new ScrollingMovementMethod());
        hsv.addView(codeView, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private ArrayList<RenderBlock> parseBlocks(String text) {
        ArrayList<RenderBlock> blocks = new ArrayList<>();
        Pattern p = Pattern.compile("```\\s*([\\w.+#-]*)\\s*\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        int pos = 0;
        while (m.find()) {
            String before = text.substring(pos, m.start());
            if (before.trim().length() > 0) blocks.add(new RenderBlock(false, "", before.trim()));
            String lang = m.group(1) == null ? "code" : m.group(1).trim();
            String code = m.group(2) == null ? "" : m.group(2).replaceAll("^\n+|\n+$", "");
            blocks.add(new RenderBlock(true, lang.length() == 0 ? "code" : lang, code));
            pos = m.end();
        }
        String after = text.substring(pos);
        if (after.trim().length() > 0) blocks.add(new RenderBlock(false, "", after.trim()));
        if (blocks.isEmpty()) blocks.add(new RenderBlock(false, "", text));
        return blocks;
    }

    private String cleanMarkdownText(String text) {
        String out = normalizeFa(text);
        out = out.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        out = out.replaceAll("`([^`]+)`", "‹$1›");
        String[] lines = out.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String s = line.trim();
            if (s.startsWith("#")) {
                s = s.replaceFirst("^#+", "").trim();
                sb.append("▌ ").append(s).append('\n');
            } else {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private void addSystemError(String msg) {
        TextView t = tv("❌ " + msg, 13, ERROR, Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(new RoundDrawable(Color.rgb(30, 8, 16), Color.rgb(90, 25, 40), dp(14), dp(1)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        chatList.addView(t, lp);
        scrollDown();
    }

    private void showSupportDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(new RoundDrawable(CARD2, GLOW, dp(20), dp(1)));

        TextView title = tv("💚 حمایت و ارتباط", 18, NEON, Typeface.BOLD);
        title.setGravity(Gravity.RIGHT);
        card.addView(title);

        TextView subtitle = tv("AI APKCLUB • راه‌های رسمی ارتباط و حمایت", 11, MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.setMargins(0, dp(2), 0, dp(14));
        card.addView(subtitle, subtitleLp);

        TextView walletLabel = tv("USDT BEP20 WALLET", 11, MUTED, Typeface.BOLD);
        walletLabel.setGravity(Gravity.LEFT);
        card.addView(walletLabel);

        TextView walletText = tv(USDT_BEP20_WALLET, 12, TEXT, Typeface.BOLD);
        walletText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        walletText.setTextIsSelectable(true);
        walletText.setPadding(dp(12), dp(12), dp(12), dp(12));
        walletText.setBackground(new RoundDrawable(CODE_BG, Color.rgb(12, 84, 60), dp(12), dp(1)));
        LinearLayout.LayoutParams walletLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        walletLp.setMargins(0, dp(6), 0, dp(10));
        card.addView(walletText, walletLp);

        Button copyWallet = button("📋 کپی آدرس ولت", NEON, Color.rgb(0, 24, 18));
        copyWallet.setBackground(new RoundDrawable(NEON, Color.TRANSPARENT, dp(12), 0));
        card.addView(copyWallet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        copyWallet.setOnClickListener(v -> {
            copyText(USDT_BEP20_WALLET, "");
            copyWallet.setText("کپی شد ✅");
            ui.postDelayed(() -> copyWallet.setText("📋 کپی آدرس ولت"), 1500);
        });

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        linksLp.setMargins(0, dp(12), 0, 0);
        card.addView(links, linksLp);

        Button developer = button("💬 " + DEVELOPER_ID, INPUT, NEON);
        developer.setBackground(new RoundDrawable(INPUT, GLOW, dp(12), dp(1)));
        Button channel = button("📢 " + CHANNEL_ID, INPUT, NEON);
        channel.setBackground(new RoundDrawable(INPUT, GLOW, dp(12), dp(1)));
        LinearLayout.LayoutParams developerLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        LinearLayout.LayoutParams channelLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        developerLp.setMargins(0, 0, dp(5), 0);
        channelLp.setMargins(dp(5), 0, 0, 0);
        links.addView(developer, developerLp);
        links.addView(channel, channelLp);
        developer.setOnClickListener(v -> openUrl(DEVELOPER_URL));
        channel.setOnClickListener(v -> openUrl(CHANNEL_URL));

        TextView note = tv("روی آیدی‌ها بزن تا تلگرام باز شود", 10, MUTED, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.setMargins(0, dp(10), 0, 0);
        card.addView(note, noteLp);

        dialog.setView(card, dp(10), dp(10), dp(10), dp(10));
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                lp.copyFrom(window.getAttributes());
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                window.setAttributes(lp);
            }
        });
        dialog.show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            toast("امکان باز کردن لینک وجود ندارد");
        }
    }

    private void clearChat() {
        if (busy) {
            toast("اول پردازش را متوقف کن");
            return;
        }
        chatList.removeAllViews();
        lastAnswer = "";
        lastPrompt = "";
        setStatus("آماده", "چت پاک شد");
    }

    private void copyText(String text, String emptyMsg) {
        if (text == null || text.trim().length() == 0) {
            toast(emptyMsg);
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("AI APKCLUB", text));
        toast("کپی شد ✅");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void scrollDown() {
        ui.postDelayed(() -> chatScroll.fullScroll(View.FOCUS_DOWN), 60);
    }

    private static boolean hasRtl(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u0600' && c <= '\u06FF') || (c >= '\u0750' && c <= '\u077F')) return true;
        }
        return false;
    }

    private static String normalizeFa(String text) {
        if (text == null) return "";
        return text.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace("\u200e", "").replace("\u200f", "");
    }

    private static String shortText(String text, int max) {
        if (text == null) return "";
        text = text.trim();
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static String displayLang(String lang) {
        if (lang == null || lang.trim().length() == 0) return "Code";
        String l = lang.toLowerCase(Locale.US).trim();
        if (l.equals("py") || l.equals("python3")) return "Python";
        if (l.equals("js")) return "JavaScript";
        if (l.equals("ts")) return "TypeScript";
        if (l.equals("sh") || l.equals("shell")) return "Bash";
        if (l.equals("ps1")) return "PowerShell";
        if (l.equals("cmd")) return "Batch";
        return l.substring(0, 1).toUpperCase(Locale.US) + l.substring(1);
    }

    private static String shortLang(String lang) {
        String d = displayLang(lang).toUpperCase(Locale.US);
        if (d.equals("PYTHON")) return "PY";
        if (d.equals("JAVASCRIPT")) return "JS";
        if (d.equals("TYPESCRIPT")) return "TS";
        if (d.equals("BASH")) return "SH";
        if (d.equals("POWERSHELL")) return "PS";
        return d.length() > 5 ? d.substring(0, 5) : d;
    }

    @Override
    protected void onDestroy() {
        stopRequest();
        if (neuralView != null) neuralView.stop();
        super.onDestroy();
    }

    public static class RoundDrawable extends android.graphics.drawable.Drawable {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int radius;
        private final int stroke;
        private final RectF rect = new RectF();

        RoundDrawable(int fill, int strokeColor, int radius, int stroke) {
            this.radius = radius;
            this.stroke = stroke;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fill);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(stroke);
            strokePaint.setColor(strokeColor);
        }

        @Override public void draw(Canvas canvas) {
            rect.set(getBounds());
            if (stroke > 0) {
                rect.inset(stroke / 2f, stroke / 2f);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
                canvas.drawRoundRect(rect, radius, radius, strokePaint);
            } else {
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }
        }
        @Override public void setAlpha(int alpha) { fillPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { fillPaint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    public class AnimatedNeuralView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random rnd = new Random();
        private final ArrayList<Particle> particles = new ArrayList<>();
        private float scanX = 0;
        private float mx = -9999;
        private float my = -9999;
        private boolean running = true;

        class Particle {
            float x, y, dx, dy, r;
            Particle(float x, float y) {
                this.x = x; this.y = y;
                dx = -0.9f + rnd.nextFloat() * 1.8f;
                dy = -0.6f + rnd.nextFloat() * 1.2f;
                r = 1.6f + rnd.nextFloat() * 3.0f;
            }
        }

        public AnimatedNeuralView(Context c) {
            super(c);
            setBackgroundColor(CARD);
        }

        public void stop() { running = false; }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            particles.clear();
            int count = Math.max(28, Math.min(70, (w * h) / 6500));
            for (int i = 0; i < count; i++) particles.add(new Particle(rnd.nextInt(Math.max(1, w)), rnd.nextInt(Math.max(1, h))));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            mx = e.getX(); my = e.getY();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                mx = -9999; my = -9999;
            }
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CARD);
            canvas.drawRect(0, 0, w, h, paint);

            paint.setStrokeWidth(1);
            paint.setColor(Color.rgb(7, 27, 32));
            int gx = Math.max(dp(44), w / 8);
            int gy = Math.max(dp(28), h / 4);
            for (int x = 0; x < w; x += gx) canvas.drawLine(x, 0, x, h, paint);
            for (int y = 0; y < h; y += gy) canvas.drawLine(0, y, w, y, paint);

            scanX = (scanX + Math.max(3, w / 180f)) % Math.max(1, w);
            paint.setColor(Color.rgb(13, 230, 142));
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(scanX, 0, scanX, h, paint);
            paint.setColor(Color.rgb(6, 60, 43));
            canvas.drawLine(Math.max(0, scanX - dp(22)), 0, Math.max(0, scanX - dp(22)), h, paint);
            canvas.drawLine(Math.min(w, scanX + dp(22)), 0, Math.min(w, scanX + dp(22)), h, paint);

            for (Particle p : particles) {
                float dist = (float) Math.hypot(p.x - mx, p.y - my);
                if (dist < dp(110)) {
                    p.x += (mx - p.x) * 0.015f;
                    p.y += (my - p.y) * 0.015f;
                }
                p.x += p.dx; p.y += p.dy;
                if (p.x < 0 || p.x > w) p.dx *= -1;
                if (p.y < 0 || p.y > h) p.dy *= -1;
                p.x = Math.max(0, Math.min(w, p.x));
                p.y = Math.max(0, Math.min(h, p.y));
            }

            paint.setStrokeWidth(1);
            int maxLines = 95;
            int lines = 0;
            for (int i = 0; i < particles.size() && lines < maxLines; i++) {
                Particle a = particles.get(i);
                for (int j = i + 1; j < particles.size() && lines < maxLines; j++) {
                    Particle b = particles.get(j);
                    float d = (float) Math.hypot(a.x - b.x, a.y - b.y);
                    if (d < dp(66)) {
                        int op = Math.max(25, Math.min(160, (int) (160 - d)));
                        paint.setColor(Color.argb(op, 0, 255, 157));
                        canvas.drawLine(a.x, a.y, b.x, b.y, paint);
                        lines++;
                    }
                }
            }

            paint.setStyle(Paint.Style.FILL);
            for (Particle p : particles) {
                paint.setColor(NEON);
                canvas.drawCircle(p.x, p.y, p.r, paint);
            }

            if (running) postInvalidateDelayed(34);
        }
    }
}
