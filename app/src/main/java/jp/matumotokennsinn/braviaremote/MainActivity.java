package jp.matumotokennsinn.braviaremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BRAVIA Remote — UI は res/layout の XML で定義し、このクラスは配線と通信だけを担う。
 * （2026-11-21 にプログラム生成 UI から XML レイアウトへ移行）
 */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 17, 24);
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private View scrollRoot;
    private View tabScroll;
    private ViewGroup screenContainer;
    private final int[] tabIds = {R.id.tabRemote, R.id.tabInputs, R.id.tabText, R.id.tabApps, R.id.tabMemo};
    private final String[] tabScreens = {"remote", "inputs", "text", "apps", "memo"};
    private TextView status;
    private EditText tvText;
    private LinearLayout inputList;
    private String screen = "remote";
    private static final String[][] KEYS = {
        {"電源","AAAAAQAAAAEAAAAVAw=="},{"入力","AAAAAQAAAAEAAAAlAw=="},{"ホーム","AAAAAQAAAAEAAABgAw=="},
        {"戻る","AAAAAgAAAJcAAAAjAw=="},{"上","AAAAAQAAAAEAAAB0Aw=="},{"オプション","AAAAAgAAAJcAAAA2Aw=="},
        {"左","AAAAAQAAAAEAAAA0Aw=="},{"決定","AAAAAQAAAAEAAABlAw=="},{"右","AAAAAQAAAAEAAAAzAw=="},{"下","AAAAAQAAAAEAAAB1Aw=="},
        {"音量＋","AAAAAQAAAAEAAAASAw=="},{"消音","AAAAAQAAAAEAAAAUAw=="},{"音量−","AAAAAQAAAAEAAAATAw=="},
        {"CH＋","AAAAAQAAAAEAAAAQAw=="},{"番組表","AAAAAQAAAAEAAAAOAw=="},{"CH−","AAAAAQAAAAEAAAARAw=="},
        {"再生","AAAAAgAAAJcAAAAaAw=="},{"一時停止","AAAAAgAAAJcAAAAZAw=="},{"停止","AAAAAgAAAJcAAAAYAw=="},
        {"表示","AAAAAQAAAAEAAAA6Aw=="},{"TV","AAAAAQAAAAEAAAAiAw=="},{"0","AAAAAQAAAAEAAAAJAw=="},
        {"小数点","AAAAAgAAAJcAAAAdAw=="},{"字幕","AAAAAgAAAJcAAAAoAw=="},
        {"早戻し","AAAAAgAAAJcAAAAbAw=="},{"早送り","AAAAAgAAAJcAAAAcAw=="},{"ヘルプ","AAAAAgAAAMQAAABNAw=="}
    };
    private static final String[] NUM_LABELS = {"1","2","3","4","5","6","7","8","9","10","11","12"};
    private static final int[] NUM_IDS = {R.id.btnNum1, R.id.btnNum2, R.id.btnNum3, R.id.btnNum4, R.id.btnNum5, R.id.btnNum6, R.id.btnNum7, R.id.btnNum8, R.id.btnNum9, R.id.btnNum10, R.id.btnNum11, R.id.btnNum12};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(R.layout.activity_main);
        scrollRoot = findViewById(R.id.scrollRoot);
        tabScroll = findViewById(R.id.tabScroll);
        screenContainer = findViewById(R.id.screenContainer);
        findViewById(R.id.btnGear).setOnClickListener(v -> { haptic(v); screen = "settings"; render(); });
        for (int i = 0; i < tabIds.length; i++) {
            final String id = tabScreens[i];
            findViewById(tabIds[i]).setOnClickListener(v -> { haptic(v); screen = id; render(); });
        }
        render();
    }

    private SharedPreferences prefs() { return getSharedPreferences("bravia", MODE_PRIVATE); }
    private String host() { return prefs().getString("host", "").trim().replaceFirst("^https?://", "").replaceAll("/+$", ""); }
    private String psk() { return prefs().getString("psk", ""); }

    private void render() {
        boolean mainScreen = !(screen.equals("settings") || screen.equals("connection"));
        tabScroll.setVisibility(mainScreen ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabIds.length; i++) {
            findViewById(tabIds[i]).setBackgroundResource(
                    tabScreens[i].equals(screen) ? R.drawable.bg_btn_blue : R.drawable.bg_btn_panel);
        }
        scrollRoot.scrollTo(0, 0);
        screenContainer.removeAllViews();
        status = null;
        tvText = null;
        inputList = null;
        if (screen.equals("settings")) { settingsMenu(); return; }
        if (screen.equals("connection")) { connectionSettings(); return; }
        if (screen.equals("remote")) remote();
        else if (screen.equals("inputs")) inputs();
        else if (screen.equals("text")) textInput();
        else if (screen.equals("apps")) apps();
        else reminders();
    }

    @Override public void onBackPressed() {
        if ("connection".equals(screen)) { screen = "settings"; render(); return; }
        if ("settings".equals(screen)) { screen = "remote"; render(); return; }
        super.onBackPressed();
    }

    private View inflate(int layout) {
        View v = getLayoutInflater().inflate(layout, screenContainer, false);
        screenContainer.addView(v);
        return v;
    }

    private void bind(View root, int id, String[] key) {
        root.findViewById(id).setOnClickListener(v -> { haptic(v); send(key[1], key[0]); });
    }

    private void bindSend(View root, int id, String code, String label) {
        root.findViewById(id).setOnClickListener(v -> { haptic(v); send(code, label); });
    }

    private void remote() {
        View v = inflate(R.layout.screen_remote);
        status = v.findViewById(R.id.status);
        status.setText(host().isEmpty() ? "⚙ 設定からテレビの接続先を登録してください" : "接続先  " + host());
        bind(v, R.id.btnDisplay, KEYS[19]);
        bind(v, R.id.btnGuide, KEYS[14]);
        v.findViewById(R.id.btnPower).setOnClickListener(x -> { haptic(x); send(KEYS[0][1], KEYS[0][0]); });
        bind(v, R.id.btnVolUp, KEYS[10]);
        bind(v, R.id.btnVolDown, KEYS[12]);
        bind(v, R.id.btnChUp, KEYS[13]);
        bind(v, R.id.btnChDown, KEYS[15]);
        bind(v, R.id.btnInput, KEYS[1]);
        bind(v, R.id.btnOption, KEYS[5]);
        bind(v, R.id.btnUp, KEYS[4]);
        bind(v, R.id.btnLeft, KEYS[6]);
        bind(v, R.id.btnConfirm, KEYS[7]);
        bind(v, R.id.btnRight, KEYS[8]);
        bind(v, R.id.btnDown, KEYS[9]);
        bind(v, R.id.btnBack, KEYS[3]);
        bind(v, R.id.btnHome, KEYS[2]);
        v.findViewById(R.id.btnTv).setOnClickListener(x -> { haptic(x); selectTvInput(); });
        String[] numCodes = {
            "AAAAAQAAAAEAAAAAAw==", "AAAAAQAAAAEAAAABAw==", "AAAAAQAAAAEAAAACAw==",
            "AAAAAQAAAAEAAAADAw==", "AAAAAQAAAAEAAAAEAw==", "AAAAAQAAAAEAAAAFAw==",
            "AAAAAQAAAAEAAAAGAw==", "AAAAAQAAAAEAAAAHAw==", "AAAAAQAAAAEAAAAIAw==",
            KEYS[21][1], KEYS[22][1], KEYS[23][1]};
        for (int i = 0; i < NUM_IDS.length; i++) {
            final String label = NUM_LABELS[i], code = numCodes[i];
            v.findViewById(NUM_IDS[i]).setOnClickListener(x -> { haptic(x); send(code, label); });
        }
        bindSend(v, R.id.btnRewind, KEYS[24][1], "早戻し");
        bindSend(v, R.id.btnPlay, KEYS[16][1], "再生");
        bindSend(v, R.id.btnFastForward, KEYS[25][1], "早送り");
        bindSend(v, R.id.btnStop, KEYS[18][1], "停止");
        bindSend(v, R.id.btnPause, KEYS[17][1], "一時停止");
        bindSend(v, R.id.btnHelp, KEYS[26][1], "ヘルプ");
        v.findViewById(R.id.btnRecordings).setOnClickListener(x -> { haptic(x); launchTvApp("recordings"); });
    }

    private void inputs() {
        View v = inflate(R.layout.screen_inputs);
        status = v.findViewById(R.id.status);
        inputList = v.findViewById(R.id.inputList);
        v.findViewById(R.id.btnRefreshInputs).setOnClickListener(x -> { haptic(x); refreshInputs(); });
        v.findViewById(R.id.btnTvBroadcast).setOnClickListener(x -> { haptic(x); selectTvInput(); });
        refreshInputs();
    }

    private void refreshInputs() {
        String target = host();
        if (inputList == null) return;
        inputList.removeAllViews();
        if (target.isEmpty()) { statusMessage("設定からテレビの接続先を登録してください。"); return; }
        statusMessage("入力一覧を取得中…");
        network.execute(() -> { try {
            JSONObject req = new JSONObject(); req.put("method", "getCurrentExternalInputsStatus"); req.put("id", 1); req.put("params", new JSONArray()); req.put("version", "1.0");
            JSONObject response = postJson(target, "avContent", req);
            JSONArray items = response.optJSONArray("result");
            if (items != null && items.length() > 0 && items.optJSONArray(0) != null) items = items.optJSONArray(0);
            JSONArray sources = items;
            runOnUiThread(() -> {
                if (inputList == null) return;
                inputList.removeAllViews();
                if (sources == null || sources.length() == 0) { statusMessage("テレビから入力一覧を取得できませんでした。"); return; }
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject source = sources.optJSONObject(i);
                    if (source == null) continue;
                    String uri = source.optString("uri", "");
                    if (uri.isEmpty()) continue;
                    String name = source.optString("label", "");
                    if (name.isEmpty()) name = source.optString("title", uri);
                    final String inputName = name;
                    Button b = (Button) getLayoutInflater().inflate(R.layout.item_input, inputList, false);
                    b.setText(inputName);
                    inputList.addView(b);
                    b.setOnClickListener(x -> { haptic(x); switchInput(uri, inputName); });
                }
                statusMessage("入力一覧を読み込みました。");
            });
        } catch (Exception e) { statusMessage("入力一覧を取得できません。テレビのAPI対応を確認してください。"); } });
    }

    private void switchInput(String uri, String title) {
        String target = host();
        statusMessage(title + "へ切り替えています…");
        network.execute(() -> { try {
            JSONObject p = new JSONObject(); p.put("uri", uri);
            JSONObject req = new JSONObject(); req.put("method", "setPlayContent"); req.put("id", 2); req.put("params", new JSONArray().put(p)); req.put("version", "1.0");
            postJson(target, "avContent", req);
            statusMessage(title + "へ切り替えました。");
        } catch (Exception e) { statusMessage("入力を切り替えられませんでした。"); } });
    }

    private void textInput() {
        View v = inflate(R.layout.screen_text);
        status = v.findViewById(R.id.status);
        tvText = v.findViewById(R.id.tvText);
        v.findViewById(R.id.btnSendText).setOnClickListener(x -> { haptic(x); sendTvText(false); });
        v.findViewById(R.id.btnSendTextConfirm).setOnClickListener(x -> { haptic(x); sendTvText(true); });
        v.findViewById(R.id.btnClearText).setOnClickListener(x -> { haptic(x); tvText.setText(""); });
    }

    private void sendTvText(boolean confirm) {
        String target = host();
        if (target.isEmpty()) { statusMessage("設定からテレビの接続先を登録してください。"); return; }
        String value = tvText == null ? "" : tvText.getText().toString();
        statusMessage("テレビへ文字を送信中…");
        network.execute(() -> { try {
            JSONObject req = new JSONObject(); req.put("method", "setTextForm"); req.put("id", 1); req.put("params", new JSONArray().put(value)); req.put("version", "1.0");
            postJson(target, "appControl", req);
            statusMessage("文字をテレビへ送信しました。" + (confirm ? " 決定を送信中…" : ""));
            if (confirm) send(KEYS[7][1], "決定");
        } catch (Exception e) { statusMessage("文字を送信できません。テレビ側で入力欄を開き、PSK/API設定を確認してください。"); } });
    }

    private void apps() {
        View v = inflate(R.layout.screen_apps);
        status = v.findViewById(R.id.status);
        v.findViewById(R.id.btnYoutube).setOnClickListener(x -> { haptic(x); launchTvApp("youtube"); });
        v.findViewById(R.id.btnPrime).setOnClickListener(x -> { haptic(x); launchTvApp("prime"); });
    }

    private void reminders() {
        View v = inflate(R.layout.screen_memo);
        v.findViewById(R.id.btnAddMemo).setOnClickListener(x -> { haptic(x); addReminder(); });
        LinearLayout list = v.findViewById(R.id.memoList);
        SharedPreferences p = prefs();
        int n = p.getInt("count", 0);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            String val = p.getString("reminder_" + i, null);
            if (val == null) continue;
            any = true;
            int idx = i;
            View item = getLayoutInflater().inflate(R.layout.item_reminder, list, false);
            String[] parts = val.split("\\|", 2);
            ((TextView) item.findViewById(R.id.memoTitle)).setText(parts[0]);
            TextView when = item.findViewById(R.id.memoWhen);
            if (parts.length > 1) when.setText(parts[1]); else when.setVisibility(View.GONE);
            item.findViewById(R.id.memoDelete).setOnClickListener(x -> { haptic(x); p.edit().remove("reminder_" + idx).apply(); render(); });
            list.addView(item);
        }
        v.findViewById(R.id.memoEmpty).setVisibility(any ? View.GONE : View.VISIBLE);
    }

    private void settingsMenu() {
        View v = inflate(R.layout.screen_settings);
        status = v.findViewById(R.id.status);
        v.findViewById(R.id.cardConnection).setOnClickListener(x -> { haptic(x); screen = "connection"; render(); });
        v.findViewById(R.id.btnTestConnection).setOnClickListener(x -> { haptic(x); testConnection(); });
        v.findViewById(R.id.cardAbout).setOnClickListener(x -> { haptic(x);
            new AlertDialog.Builder(this).setTitle("BRAVIA Remote").setMessage("バージョン 1.1.0\nテレビの同じネットワーク内でご利用ください。").setPositiveButton("閉じる", null).show(); });
        v.findViewById(R.id.btnBackRemote).setOnClickListener(x -> { haptic(x); screen = "remote"; render(); });
    }

    private void connectionSettings() {
        View v = inflate(R.layout.screen_connection);
        EditText ip = v.findViewById(R.id.editIp);
        ip.setText(prefs().getString("host", ""));
        EditText secret = v.findViewById(R.id.editPsk);
        secret.setText(prefs().getString("psk", ""));
        v.findViewById(R.id.btnSaveConnection).setOnClickListener(x -> { haptic(x);
            prefs().edit().putString("host", ip.getText().toString().trim()).putString("psk", secret.getText().toString()).apply();
            screen = "settings"; render(); });
        v.findViewById(R.id.btnClearConnection).setOnClickListener(x -> { haptic(x);
            new AlertDialog.Builder(this).setTitle("接続設定を削除しますか？").setMessage("保存したIPアドレスとPSKを削除します。").setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (d, w) -> { prefs().edit().remove("host").remove("psk").apply(); render(); }).show(); });
        v.findViewById(R.id.btnBackSettings).setOnClickListener(x -> { haptic(x); screen = "settings"; render(); });
    }

    private void testConnection() {
        String target = host();
        if (target.isEmpty()) { statusMessage("先に接続先を登録してください。"); return; }
        statusMessage("テレビへ接続中…");
        network.execute(() -> { try {
            JSONObject req = new JSONObject(); req.put("method", "getSystemInformation"); req.put("id", 1); req.put("params", new JSONArray()); req.put("version", "1.0");
            JSONObject result = postJson(target, "system", req);
            JSONArray rows = result.optJSONArray("result");
            if (rows != null && rows.length() > 0 && rows.optJSONObject(0) != null) {
                JSONObject info = rows.optJSONObject(0);
                String model = info.optString("model", info.optString("name", "テレビ"));
                statusMessage("接続しました：" + model);
            } else statusMessage("テレビに接続しました。");
        } catch (Exception e) { statusMessage("接続できません。IP・Wi-Fi・IPコントロール設定を確認してください。"); } });
    }

    private void addReminder() {
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("番組名・チャンネル");
        field(title);
        Calendar cal = Calendar.getInstance();
        DatePickerDialog date = new DatePickerDialog(this, (v, y, m, d) -> {
            cal.set(y, m, d);
            new TimePickerDialog(this, (t, h, min) -> {
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                String when = new SimpleDateFormat("yyyy年M月d日（E） HH:mm", Locale.JAPAN).format(cal.getTime());
                String name = title.getText().toString().trim();
                if (name.isEmpty()) name = "視聴予定";
                int n = prefs().getInt("count", 0);
                prefs().edit().putString("reminder_" + n, name + "|" + when).putInt("count", n + 1).apply();
                render();
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        new AlertDialog.Builder(this).setTitle("視聴メモ").setView(title).setNegativeButton("キャンセル", null).setPositiveButton("日時を選ぶ", (d, w) -> date.show()).show();
    }

    private void launchTvApp(String query) {
        String target = host();
        if (target.isEmpty()) { statusMessage("先に⚙設定でテレビの接続先を登録してください"); return; }
        statusMessage("テレビのアプリ一覧を確認中…");
        network.execute(() -> { try {
            JSONObject req = new JSONObject(); req.put("method", "getApplicationList"); req.put("id", 1); req.put("params", new JSONArray()); req.put("version", "1.0");
            JSONObject result = postJson(target, "appControl", req);
            JSONArray list = result.optJSONArray("result");
            if (list != null && list.length() > 0 && list.optJSONArray(0) != null) list = list.optJSONArray(0);
            String uri = null;
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject app = list.optJSONObject(i);
                if (app == null) continue;
                String title = app.optString("title", "");
                String lowerTitle = title.toLowerCase(Locale.ROOT);
                String all = (title + " " + app.optString("uri", "")).toLowerCase(Locale.ROOT);
                boolean match = query.equals("youtube") && all.contains("youtube")
                        || query.equals("prime") && (all.contains("prime") || all.contains("amazon"))
                        || query.equals("tv") && (title.equalsIgnoreCase("tv") || all.contains("tvx.mainactivity"))
                        || query.equals("recordings") && (lowerTitle.contains("record") || lowerTitle.contains("録画") || all.contains("titlelist"));
                if (match) { uri = app.optString("uri", null); if (uri != null) break; }
            }
            if (uri == null) {
                statusMessage(query.equals("youtube") ? "テレビのアプリ一覧にYouTubeが見つかりません"
                        : query.equals("prime") ? "テレビのアプリ一覧にPrime Videoが見つかりません"
                        : query.equals("recordings") ? "録画リストアプリが見つかりません" : "テレビアプリを起動できませんでした");
                return;
            }
            JSONObject param = new JSONObject(); param.put("uri", uri);
            JSONObject launch = new JSONObject(); launch.put("method", "setActiveApp"); launch.put("id", 2); launch.put("params", new JSONArray().put(param)); launch.put("version", "1.0");
            postJson(target, "appControl", launch);
            statusMessage(query.equals("recordings") ? "録画リストを起動しました" : "テレビでアプリを起動しました");
        } catch (Exception e) { statusMessage("テレビアプリを起動できません。PSK・IPコントロール設定を確認してください"); } });
    }

    private void selectTvInput() {
        String target = host();
        if (target.isEmpty()) { statusMessage("先に⚙設定でテレビの接続先を登録してください"); return; }
        statusMessage("テレビ入力を切り替えています…");
        network.execute(() -> { try {
            JSONObject req = new JSONObject(); req.put("method", "getSourceList"); req.put("id", 1); req.put("params", new JSONArray().put(new JSONObject().put("scheme", "tv"))); req.put("version", "1.0");
            JSONObject result = postJson(target, "avContent", req);
            JSONArray list = result.optJSONArray("result");
            if (list != null && list.length() > 0 && list.optJSONArray(0) != null) list = list.optJSONArray(0);
            String source = null;
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) continue;
                    String candidate = item.optString("source", "");
                    if (candidate.startsWith("tv:dvbt")) { source = candidate; break; }
                    if (source == null && candidate.startsWith("tv:")) source = candidate;
                }
            }
            if (source == null) { runOnUiThread(() -> launchTvApp("tv")); return; }
            JSONObject param = new JSONObject(); param.put("uri", source);
            JSONObject play = new JSONObject(); play.put("method", "setPlayContent"); play.put("id", 2); play.put("params", new JSONArray().put(param)); play.put("version", "1.0");
            postJson(target, "avContent", play);
            statusMessage("テレビ放送に切り替えました");
        } catch (Exception e) { runOnUiThread(() -> launchTvApp("tv")); } });
    }

    private JSONObject postJson(String target, String service, JSONObject payload) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + target + "/sony/" + service).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (!psk().isEmpty()) c.setRequestProperty("X-Auth-PSK", psk());
            byte[] data = payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = c.getOutputStream()) { out.write(data); }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            JSONObject response = new JSONObject(body);
            if (response.has("error")) throw new IllegalStateException(response.opt("error").toString());
            return response;
        } finally { c.disconnect(); }
    }

    private String read(InputStream in) throws Exception {
        if (in == null) return "{}";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[1024];
            int n;
            while ((n = input.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }

    private void send(String code, String label) {
        String target = host(), secret = psk();
        if (target.isEmpty()) { statusMessage("⚙ 設定から接続先を登録してください"); return; }
        statusMessage(label + " を送信中…");
        network.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL("http://" + target + "/sony/IRCC").openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(3500);
                c.setReadTimeout(3500);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
                c.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"");
                c.setRequestProperty("Accept", "*/*");
                if (!secret.isEmpty()) c.setRequestProperty("X-Auth-PSK", secret);
                String xml = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>" + code + "</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>";
                byte[] data = xml.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream o = c.getOutputStream()) { o.write(data); }
                int result = c.getResponseCode();
                statusMessage(result >= 200 && result < 300 ? label + " を送信しました" : "HTTP " + result + "：設定を確認してください");
            } catch (Exception e) {
                statusMessage("接続できません。IP・同じWi-Fi・テレビ側設定を確認してください");
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private void statusMessage(String value) { runOnUiThread(() -> { if (status != null) status.setText(value); }); }
    private void haptic(View v) { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); }
    private void field(EditText e) {
        e.setTextSize(15);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        e.setBackgroundResource(R.drawable.bg_field);
        e.setTextColor(Color.rgb(240, 244, 250));
        e.setHintTextColor(Color.rgb(151, 163, 181));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
}
