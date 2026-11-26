package com.azuki.groqchat;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Add your own Groq key locally before building. Do not commit real keys to GitHub.
    private static final String GROQ_API_KEY = "PASTE_YOUR_GROQ_API_KEY_HERE";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final ArrayList<JSONObject> messages = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout messageList;
    private ScrollView scroll;
    private EditText composer;
    private Button sendButton;
    private TextView status;
    private boolean waiting = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
        addHistoryMessage("system", "You are a helpful, friendly AI assistant. Answer clearly and naturally.");
        addMessageBubble("assistant", "Hey! I'm ready. What would you like to talk about?");
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0C1118);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(14), dp(12));
        header.setBackgroundColor(0xFF121922);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Groq AI", 21, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        titles.addView(label(MODEL, 11, 0xFF8EA1B5));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        Button clear = new Button(this);
        clear.setText("New chat");
        clear.setTextSize(11);
        clear.setAllCaps(false);
        header.addView(clear, new LinearLayout.LayoutParams(dp(92), dp(42)));
        root.addView(header);

        status = label("● Ready", 11, 0xFF63D6A5);
        status.setPadding(dp(18), dp(7), dp(18), dp(7));
        root.addView(status);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(13), dp(8), dp(13), dp(14));
        scroll.addView(messageList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), dp(10), dp(12), dp(12));
        bottom.setBackgroundColor(0xFF121922);

        LinearLayout compose = new LinearLayout(this);
        compose.setGravity(Gravity.CENTER_VERTICAL);
        composer = new EditText(this);
        composer.setHint("Message Groq AI...");
        composer.setTextColor(Color.WHITE);
        composer.setHintTextColor(0xFF8292A3);
        composer.setTextSize(15);
        composer.setPadding(dp(15), dp(9), dp(15), dp(9));
        composer.setMinHeight(dp(48));
        composer.setMaxLines(5);
        composer.setBackground(shape(0xFF263342, 22));
        compose.addView(composer, new LinearLayout.LayoutParams(0, -2, 1));

        sendButton = new Button(this);
        sendButton.setText("Send");
        sendButton.setAllCaps(false);
        sendButton.setTextColor(Color.WHITE);
        sendButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF16A078));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(76), dp(48));
        sendLp.setMargins(dp(8), 0, 0, 0);
        compose.addView(sendButton, sendLp);
        bottom.addView(compose);

        TextView footer = label("Powered by Groq  •  " + MODEL, 10, 0xFF728398);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(7), 0, 0);
        bottom.addView(footer);
        root.addView(bottom);
        setContentView(root);

        sendButton.setOnClickListener(v -> sendMessage());
        composer.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        clear.setOnClickListener(v -> newChat());
    }

    private void addHistoryMessage(String role, String content) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("role", role);
            obj.put("content", content);
            messages.add(obj);
        } catch (Exception ignored) {}
    }

    private void addMessageBubble(String role, String content) {
        boolean user = role.equals("user");
        LinearLayout line = new LinearLayout(this);
        line.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        line.setPadding(0, dp(5), 0, dp(5));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(shape(user ? 0xFF126B57 : 0xFF202C39, 17));

        TextView who = label(user ? "YOU" : "GROQ AI", 10,
                user ? 0xFFB9F1DF : 0xFF78DDBA);
        who.setTypeface(Typeface.DEFAULT_BOLD);
        TextView body = label(content, 15, 0xFFF1F5F8);
        body.setTextIsSelectable(true);
        body.setLineSpacing(dp(2), 1.0f);
        bubble.addView(who);
        bubble.addView(body);

        int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.82f);
        line.addView(bubble, new LinearLayout.LayoutParams(width, -2));
        messageList.addView(line);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendMessage() {
        if (waiting) return;
        String prompt = composer.getText().toString().trim();
        if (prompt.isEmpty()) return;
        if (GROQ_API_KEY.startsWith("PASTE_")) {
            Toast.makeText(this, "Add your Groq API key in MainActivity.java first.", Toast.LENGTH_LONG).show();
            return;
        }

        composer.setText("");
        hideKeyboard();
        addHistoryMessage("user", prompt);
        addMessageBubble("user", prompt);
        setWaiting(true);
        status.setText("● Thinking...");
        status.setTextColor(0xFFFFC66D);

        executor.execute(() -> {
            try {
                String answer = requestGroq();
                main.post(() -> {
                    addHistoryMessage("assistant", answer);
                    addMessageBubble("assistant", answer);
                    status.setText("● Ready");
                    status.setTextColor(0xFF63D6A5);
                    setWaiting(false);
                });
            } catch (Exception e) {
                String error = e.getMessage() == null ? "Unknown error" : e.getMessage();
                main.post(() -> {
                    addMessageBubble("assistant", "Request failed: " + error);
                    status.setText("● Connection error");
                    status.setTextColor(0xFFFF8585);
                    setWaiting(false);
                });
            }
        });
    }

    private String requestGroq() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(25000);
            connection.setReadTimeout(90000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", new JSONArray(messages));
            payload.put("temperature", 0.7);
            payload.put("max_completion_tokens", 2048);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300)
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            JSONObject json = new JSONObject(response);
            if (code < 200 || code >= 300) {
                String error = json.optJSONObject("error") == null ? "HTTP " + code
                        : json.getJSONObject("error").optString("message", "HTTP " + code);
                throw new Exception(error);
            }
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new Exception("The API returned no choices.");
            String answer = choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim();
            if (answer.isEmpty()) throw new Exception("The model returned an empty response.");
            return answer;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private void setWaiting(boolean value) {
        waiting = value;
        sendButton.setEnabled(!value);
        sendButton.setText(value ? "..." : "Send");
    }

    private void newChat() {
        if (waiting) return;
        messages.clear();
        addHistoryMessage("system", "You are a helpful, friendly AI assistant. Answer clearly and naturally.");
        messageList.removeAllViews();
        addMessageBubble("assistant", "New chat started. What would you like to talk about?");
        status.setText("● Ready");
        status.setTextColor(0xFF63D6A5);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(composer.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
