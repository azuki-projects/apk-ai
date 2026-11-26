package com.azuki.groqchat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.text.InputType;
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
import java.io.BufferedOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "groq_settings";
    private static final String KEY_API = "groq_api_key";
    private String apiKey = "";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final ArrayList<JSONObject> messages = new ArrayList<>();
    private final ArrayList<JSONObject> chats = new ArrayList<>();
    private int currentChatIndex = -1;
    private static final int CREATE_ZIP_REQUEST = 4107;
    private ArrayList<CodeFile> pendingZipFiles = new ArrayList<>();
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
        apiKey = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_API, "");
        buildInterface();
        loadChats();
        if (chats.isEmpty()) createChat(); else switchChat(chats.size() - 1);
        if (apiKey.trim().isEmpty()) main.post(this::showApiKeyDialog);
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

        Button chatsButton = new Button(this);
        chatsButton.setText("Chats"); chatsButton.setTextSize(11); chatsButton.setAllCaps(false);
        header.addView(chatsButton, new LinearLayout.LayoutParams(dp(66), dp(42)));
        Button keyButton = new Button(this);
        keyButton.setText("API key");
        keyButton.setTextSize(11);
        keyButton.setAllCaps(false);
        header.addView(keyButton, new LinearLayout.LayoutParams(dp(78), dp(42)));

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
        clear.setOnClickListener(v -> createChat());
        keyButton.setOnClickListener(v -> showApiKeyDialog());
        chatsButton.setOnClickListener(v -> showChatsDialog());
    }

    private void showApiKeyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(14);
        keyInput.setHint("gsk_...");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(apiKey);
        keyInput.setSelection(keyInput.getText().length());

        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(keyInput, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Groq API key")
                .setMessage("Enter your Groq API key. It is saved only in this app on this device.")
                .setView(wrap)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear key", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = keyInput.getText().toString().trim();
                if (value.isEmpty()) { keyInput.setError("Enter an API key"); return; }
                apiKey = value;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_API, apiKey).apply();
                status.setText("● API key saved");
                status.setTextColor(0xFF63D6A5);
                Toast.makeText(this, "API key saved on this device.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                apiKey = "";
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_API).apply();
                status.setText("● API key required");
                status.setTextColor(0xFFFFC66D);
                Toast.makeText(this, "API key cleared.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });
        dialog.show();
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
        if (apiKey.trim().isEmpty()) {
            showApiKeyDialog();
            return;
        }

        composer.setText("");
        hideKeyboard();
        if (currentChatIndex >= 0 && messages.size() <= 1) { try { chats.get(currentChatIndex).put("title", makeChatTitle(prompt)); } catch (Exception ignored) {} }
        addHistoryMessage("user", prompt); addMessageBubble("user", prompt); saveCurrentChat();
        setWaiting(true);
        status.setText("● Thinking...");
        status.setTextColor(0xFFFFC66D);

        executor.execute(() -> {
            try {
                String answer = requestGroq();
                main.post(() -> {
                    status.setText(answer.contains("```") ? "● Working..." : "● Ready");
                    addHistoryMessage("assistant", answer); addMessageBubble("assistant", answer); saveCurrentChat();
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
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", new JSONArray(messages));
            payload.put("temperature", 0.7);
            payload.put("max_completion_tokens", 4096);

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

    private static class CodeFile {
        final String name, code;
        CodeFile(String name, String code) { this.name=name; this.code=code; }
    }

    private String makeChatTitle(String prompt) {
        String clean=prompt.replaceAll("\\s+"," ").trim();
        if(clean.length()>38) clean=clean.substring(0,38).trim()+"...";
        return clean.isEmpty()?"New chat":clean;
    }

    private void loadChats() {
        chats.clear();
        try {
            JSONArray a=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString("saved_chats","[]"));
            for(int i=0;i<a.length();i++) chats.add(a.getJSONObject(i));
        } catch(Exception ignored) { chats.clear(); }
    }

    private void saveCurrentChat() {
        if(currentChatIndex<0||currentChatIndex>=chats.size()) return;
        try {
            JSONArray history=new JSONArray();
            for(JSONObject item:messages) history.put(item);
            chats.get(currentChatIndex).put("messages",history);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("saved_chats",new JSONArray(chats).toString()).apply();
        } catch(Exception ignored) {}
    }

    private void createChat() {
        try {
            JSONObject chat=new JSONObject(); chat.put("title","New chat"); chat.put("messages",new JSONArray());
            chats.add(chat); currentChatIndex=chats.size()-1; messages.clear();
            addHistoryMessage("system","You are a helpful AI assistant. Help with analysis, explanations, planning, and creating project files. For source files, use fenced blocks with the filename and extension on the opening line, followed by a newline and complete file contents. Do not reveal private hidden chain-of-thought; provide concise conclusions and progress summaries.");
            saveCurrentChat();
        } catch(Exception ignored) {}
        messageList.removeAllViews();
        addMessageBubble("assistant","New chat started. What would you like to work on?");
        status.setText("● Ready"); status.setTextColor(0xFF63D6A5);
    }

    private void switchChat(int index) {
        if(index<0||index>=chats.size()) return;
        saveCurrentChat(); currentChatIndex=index; messages.clear();
        try {
            JSONArray a=chats.get(index).optJSONArray("messages");
            if(a!=null) for(int i=0;i<a.length();i++) messages.add(a.getJSONObject(i));
        } catch(Exception ignored) {}
        messageList.removeAllViews(); boolean visible=false;
        for(JSONObject item:messages) {
            String role=item.optString("role");
            if(!"system".equals(role)) { addMessageBubble(role,item.optString("content")); visible=true; }
        }
        if(!visible) addMessageBubble("assistant","Chat opened. What would you like to work on?");
        status.setText("● Ready"); status.setTextColor(0xFF63D6A5);
    }

    private void showChatsDialog() {
        saveCurrentChat();
        String[] names=new String[chats.size()+1]; names[0]="+ New chat";
        for(int i=0;i<chats.size();i++) names[i+1]=chats.get(i).optString("title","New chat");
        new AlertDialog.Builder(this).setTitle("Your chats")
            .setItems(names,(d,w)->{if(w==0)createChat();else switchChat(w-1);})
            .setNegativeButton("Close",null).show();
    }

    private void renderRichContent(LinearLayout bubble,String content) {
        Pattern p=Pattern.compile("(?s)\\x60{3}([A-Za-z0-9_./-]+\\.[A-Za-z0-9_-]+)\\s*\\n(.*?)\\x60{3}");
        Matcher m=p.matcher(content); int cursor=0; ArrayList<CodeFile> files=new ArrayList<>();
        while(m.find()) {
            String before=content.substring(cursor,m.start()).trim();
            if(!before.isEmpty()) addPlainText(bubble,before);
            CodeFile file=new CodeFile(m.group(1),m.group(2)); files.add(file); addCodeCard(bubble,file);
            cursor=m.end();
        }
        String after=content.substring(cursor).trim(); if(!after.isEmpty()) addPlainText(bubble,after);
        if(!files.isEmpty()) {
            Button zip=new Button(this); zip.setText("Create ZIP ("+files.size()+" files)");
            zip.setAllCaps(false); zip.setTextSize(12); zip.setOnClickListener(v->createZip(files)); bubble.addView(zip);
        }
        if(cursor==0) addPlainText(bubble,content);
    }

    private void addPlainText(LinearLayout parent,String value) {
        TextView t=label(value,15,0xFFF1F5F8); t.setTextIsSelectable(true); t.setLineSpacing(dp(2),1f); parent.addView(t);
    }

    private void addCodeCard(LinearLayout parent,CodeFile file) {
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10),dp(8),dp(10),dp(8)); card.setBackground(shape(0xFF111923,12));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=label(file.name,12,0xFF8DE2C1); name.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=new Button(this); copy.setText("Copy"); copy.setAllCaps(false); copy.setTextSize(10);
        top.addView(copy,new LinearLayout.LayoutParams(dp(62),dp(36))); card.addView(top);
        String[] lines=file.code.split("\\n",-1);
        final String preview=lines.length>4?lines[0]+"\n"+lines[1]+"\n"+lines[2]+"\n...":file.code;
        TextView code=label(preview,12,0xFFE0E7EF); code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true); code.setPadding(dp(4),dp(8),dp(4),dp(4)); card.addView(code);
        Button expand=new Button(this); expand.setText(lines.length>4?"Show full code":"Hide code");
        expand.setAllCaps(false); expand.setTextSize(10); card.addView(expand);
        final boolean[] open={false};
        expand.setOnClickListener(v->{open[0]=!open[0];code.setText(open[0]?file.code:preview);expand.setText(open[0]?"Minimize code":"Show full code");});
        copy.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(file.name,file.code));
            Toast.makeText(this,"Copied "+file.name,Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(8); parent.addView(card,lp);
    }

    private void createZip(ArrayList<CodeFile> files) {
        if(files==null||files.isEmpty()) return;
        pendingZipFiles=new ArrayList<>(files);
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip"); i.putExtra(Intent.EXTRA_TITLE,"GroqAI-Project.zip");
        status.setText("● Working..."); status.setTextColor(0xFFFFC66D);
        try { startActivityForResult(i,CREATE_ZIP_REQUEST); }
        catch(Exception e) { status.setText("● Ready"); Toast.makeText(this,"Unable to open file saver.",Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=CREATE_ZIP_REQUEST) return;
        if(resultCode!=RESULT_OK||data==null||data.getData()==null) { status.setText("● Ready"); return; }
        Uri uri=data.getData(); ArrayList<CodeFile> files=new ArrayList<>(pendingZipFiles);
        executor.execute(()->{
            try {
                OutputStream raw=getContentResolver().openOutputStream(uri);
                if(raw==null) throw new Exception("Could not create ZIP.");
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(raw))) {
                    for(CodeFile file:files) {
                        String safe=file.name.replace("\\","/").replaceAll("^/+","");
                        if(safe.contains("../")) throw new Exception("Unsafe generated file path.");
                        zip.putNextEntry(new ZipEntry(safe)); zip.write(file.code.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    }
                    zip.finish();
                }
                main.post(()->{status.setText("● ZIP created");status.setTextColor(0xFF63D6A5);Toast.makeText(this,"Project ZIP created.",Toast.LENGTH_LONG).show();});
            } catch(Exception e) {
                main.post(()->{status.setText("● ZIP error");Toast.makeText(this,"ZIP failed: "+e.getMessage(),Toast.LENGTH_LONG).show();});
            }
        });
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
