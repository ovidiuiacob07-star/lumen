package ro.lumen.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://lumen.home/";
    private static final String[] ENGINES = {"DuckDuckGo", "Google", "Bing", "Brave"};
    private static final String[] SEARCH = {"https://duckduckgo.com/", "https://www.google.com/search",
            "https://www.bing.com/search", "https://search.brave.com/search"};
    private static final int PICK_FILE = 1;
    private static final int HISTORY_MAX = 500;
    private static final String[] BLOCK = {"doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "adservice.google.com", "connect.facebook.net",
            "scorecardresearch.com", "taboola.com", "outbrain.com", "adnxs.com", "criteo.com", "hotjar.com",
            "amazon-adsystem.com"};
    private static final String HOME_HTML = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Lumen</title></head><body style='margin:0;height:100vh;display:flex;flex-direction:column;"
            + "align-items:center;justify-content:center;gap:24px;background:#070b14;color:#e6e9f5;font-family:sans-serif'>"
            + "<h1 style='font:600 60px serif;margin:0;color:#8fb4ff'>Lumen</h1>"
            + "<form action='%ACTION%' style='width:85%'><input name='q' placeholder='Cauta cu %NAME%' "
            + "style='width:100%;box-sizing:border-box;height:48px;border-radius:24px;border:1px solid #333c55;"
            + "background:#121a2f;color:#e6e9f5;padding:0 20px;font-size:16px'></form></body></html>";

    private final ArrayList<WebView> tabs = new ArrayList<>();
    private WebView current;
    private LinearLayout root;
    private FrameLayout pages;
    private EditText address;
    private ProgressBar progress;
    private Button tabButton;
    private SharedPreferences prefs;
    private volatile boolean blockAds;
    private ValueCallback<Uri[]> fileCallback;
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("lumen", MODE_PRIVATE);
        blockAds = prefs.getBoolean("ads", true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#070b14"));

        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Color.parseColor("#121a2f"));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Cauta sau scrie o adresa");
        address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_URI);
        address.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO && (event == null || event.getAction() != KeyEvent.ACTION_DOWN))
                return false;
            String text = address.getText().toString().trim();
            if (!text.isEmpty()) {
                current.loadUrl(toUrl(text));
                address.clearFocus();
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(address.getWindowToken(), 0);
            }
            return true;
        });
        bar.addView(address, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tabButton = button(bar, "1", v -> showTabs());
        button(bar, "⋮", v -> showMenu());
        root.addView(bar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));
        pages = new FrameLayout(this);
        root.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        Uri data = getIntent().getData();
        newTab(data != null && isWeb(data.toString()) ? data.toString() : HOME);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && isWeb(data.toString())) newTab(data.toString());
    }

    private Button button(LinearLayout bar, String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(click);
        int width = (int) (52 * getResources().getDisplayMetrics().density);
        bar.addView(b, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private static boolean isWeb(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private String toUrl(String text) {
        if (isWeb(text)) return text;
        if (!text.contains(" ") && text.contains(".")) return "https://" + text;
        return SEARCH[engine()] + "?q=" + Uri.encode(text);
    }

    // ---- motor de cautare ----
    private int engine() {
        int i = prefs.getInt("engine", 0);
        return i >= 0 && i < ENGINES.length ? i : 0;
    }

    private String homeHtml() {
        int i = engine();
        return HOME_HTML.replace("%ACTION%", SEARCH[i]).replace("%NAME%", ENGINES[i]);
    }

    private void chooseEngine() {
        new AlertDialog.Builder(this).setTitle("Motor de cautare")
                .setSingleChoiceItems(ENGINES, engine(), (d, i) -> {
                    prefs.edit().putInt("engine", i).apply();
                    for (WebView w : tabs)
                        if (w.getUrl() != null && w.getUrl().startsWith(HOME)) w.reload();
                    d.dismiss();
                    toast("Cautare cu " + ENGINES[i]);
                })
                .setNegativeButton("Inchide", null).show();
    }

    // ---- taburi ----
    private WebView createTab() {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#070b14"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportMultipleWindows(true);
        s.setAllowFileAccess(false);
        if (Build.VERSION.SDK_INT >= 33) s.setAlgorithmicDarkeningAllowed(prefs.getBoolean("dark", true));
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                if (host == null) return null;
                if (host.equals("lumen.home"))
                    return new WebResourceResponse("text/html", "utf-8",
                            new ByteArrayInputStream(homeHtml().getBytes(StandardCharsets.UTF_8)));
                if (blockAds && !r.isForMainFrame())
                    for (String b : BLOCK)
                        if (host.equals(b) || host.endsWith("." + b))
                            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String scheme = r.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                if (r.hasGesture() && ("tel".equals(scheme) || "mailto".equals(scheme) || "market".equals(scheme)))
                    open(new Intent(Intent.ACTION_VIEW, r.getUrl()));
                return true;
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
                if (v == current) showAddress(url);
                if (saveable(url)) put("history", url, v.getTitle(), HISTORY_MAX);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (v == current) showProgress(p);
            }

            @Override
            public void onReceivedTitle(WebView v, String title) {
                String url = v.getUrl();
                JSONObject first = load("history").optJSONObject(0);
                if (url != null && first != null && url.equals(first.optString("url")))
                    put("history", url, title, HISTORY_MAX);
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
                if (!gesture) return false;                    // blocheaza ferestrele pop-up nedorite
                WebView child = createTab();
                select(child);
                ((WebView.WebViewTransport) msg.obj).setWebView(child);
                msg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView v) {
                closeTab(v);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {      // video pe tot ecranul
                if (fullView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullView = view;
                fullCallback = callback;
                root.setVisibility(View.GONE);
                ((ViewGroup) getWindow().getDecorView()).addView(view);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                leaveFullscreen();
            }
        });

        web.setDownloadListener((url, agent, disposition, mime, length) -> askDownload(web, url, agent, disposition, mime));
        tabs.add(web);
        return web;
    }

    private void newTab(String url) {
        WebView web = createTab();
        select(web);
        web.loadUrl(url);
    }

    private void select(WebView web) {
        current = web;
        pages.removeAllViews();
        pages.addView(web);
        tabButton.setText(String.valueOf(tabs.size()));
        address.clearFocus();
        showAddress(web.getUrl());
        showProgress(web.getProgress());
    }

    private void closeTab(WebView web) {
        int i = tabs.indexOf(web);
        if (i < 0) return;
        tabs.remove(i);
        pages.removeView(web);
        web.destroy();
        if (tabs.isEmpty()) newTab(HOME);
        else if (web == current) select(tabs.get(Math.max(0, i - 1)));
        else tabButton.setText(String.valueOf(tabs.size()));
    }

    private void showTabs() {
        String[] names = new String[tabs.size()];
        for (int i = 0; i < names.length; i++) {
            String t = tabs.get(i).getTitle();
            names[i] = (tabs.get(i) == current ? "● " : "") + (t == null || t.isEmpty() ? "Tab nou" : t);
        }
        new AlertDialog.Builder(this).setTitle("Taburi")
                .setItems(names, (d, i) -> select(tabs.get(i)))
                .setPositiveButton("Tab nou", (d, w) -> newTab(HOME))
                .setNegativeButton("Inchide tabul curent", (d, w) -> closeTab(current))
                .show();
    }

    private void showAddress(String url) {
        if (!address.hasFocus()) address.setText(url == null || url.startsWith(HOME) ? "" : url);
    }

    private void showProgress(int p) {
        progress.setProgress(p);
        progress.setVisibility(p < 100 ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- meniu ----
    private void showMenu() {
        String url = current.getUrl();
        boolean saved = url != null && indexOf(load("bookmarks"), url) >= 0;
        String[] items = {"Inapoi", "Inainte", "Reincarca", "Pagina de start",
                saved ? "Scoate de la favorite" : "Adauga la favorite", "Favorite", "Istoric", "Descarcari",
                "Trimite linkul", "Blocare reclame: " + (blockAds ? "DA" : "NU"),
                "Mod dark pentru site-uri: " + (prefs.getBoolean("dark", true) ? "DA" : "NU"),
                "Sterge istoricul, cookies si cache",
                "Motor de cautare: " + ENGINES[engine()]};
        new AlertDialog.Builder(this).setItems(items, (d, i) -> {
            switch (i) {
                case 0: if (current.canGoBack()) current.goBack(); break;
                case 1: if (current.canGoForward()) current.goForward(); break;
                case 2: current.reload(); break;
                case 3: current.loadUrl(HOME); break;
                case 4:
                    if (!saveable(url)) toast("Pagina aceasta nu se poate salva");
                    else if (saved) delete("bookmarks", url);
                    else put("bookmarks", url, current.getTitle(), Integer.MAX_VALUE);
                    break;
                case 5: showList("bookmarks", "Favorite"); break;
                case 6: showList("history", "Istoric"); break;
                case 7: open(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); break;
                case 8:
                    if (saveable(url)) open(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Trimite linkul"));
                    break;
                case 9:
                    blockAds = !blockAds;
                    prefs.edit().putBoolean("ads", blockAds).apply();
                    current.reload();
                    break;
                case 10: toggleDark(); break;
                case 11: confirmClear(); break;
                case 12: chooseEngine(); break;
            }
        }).show();
    }

    private void toggleDark() {
        if (Build.VERSION.SDK_INT < 33) {
            toast("Disponibil de la Android 13");
            return;
        }
        boolean dark = !prefs.getBoolean("dark", true);
        prefs.edit().putBoolean("dark", dark).apply();
        for (WebView w : tabs) w.getSettings().setAlgorithmicDarkeningAllowed(dark);
        current.reload();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setMessage("Stergi istoricul, cookies si cache? Favoritele raman.")
                .setPositiveButton("Sterge", (d, w) -> {
                    prefs.edit().remove("history").apply();
                    CookieManager.getInstance().removeAllCookies(null);
                    for (WebView t : tabs) t.clearHistory();
                    current.clearCache(true);
                    toast("Sters");
                })
                .setNegativeButton("Renunta", null).show();
    }

    // ---- istoric si favorite (liste JSON) ----
    private static boolean saveable(String url) {
        return url != null && isWeb(url) && !url.startsWith(HOME);
    }

    private JSONArray load(String key) {
        try {
            return new JSONArray(prefs.getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static int indexOf(JSONArray list, String url) {
        for (int i = 0; i < list.length(); i++)
            if (url.equals(list.optJSONObject(i).optString("url"))) return i;
        return -1;
    }

    /** Pune intrarea prima in lista, fara dubluri. */
    private void put(String key, String url, String title, int max) {
        try {
            JSONArray old = load(key), list = new JSONArray();
            list.put(new JSONObject().put("url", url).put("title", title == null || title.isEmpty() ? url : title));
            for (int i = 0; i < old.length() && list.length() < max; i++)
                if (!url.equals(old.optJSONObject(i).optString("url"))) list.put(old.optJSONObject(i));
            prefs.edit().putString(key, list.toString()).apply();
        } catch (JSONException e) {
            toast("Nu am putut salva");
        }
    }

    private void delete(String key, String url) {
        JSONArray list = load(key);
        int i = indexOf(list, url);
        if (i >= 0) list.remove(i);
        prefs.edit().putString(key, list.toString()).apply();
    }

    private void showList(String key, String title) {
        JSONArray list = load(key);
        if (list.length() == 0) {
            toast("Lista este goala");
            return;
        }
        String[] names = new String[list.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject e = list.optJSONObject(i);
            names[i] = e.optString("title") + "\n" + e.optString("url");
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title + " (tine apasat pentru stergere)")
                .setItems(names, (d, i) -> current.loadUrl(list.optJSONObject(i).optString("url")))
                .setNegativeButton("Inchide", null).create();
        dialog.show();
        dialog.getListView().setOnItemLongClickListener((parent, view, i, id) -> {
            delete(key, list.optJSONObject(i).optString("url"));
            dialog.dismiss();
            showList(key, title);
            return true;
        });
    }

    // ---- descarcari: le face managerul de descarcari din Android ----
    private void askDownload(WebView web, String url, String agent, String disposition, String mime) {
        if (!isWeb(url)) {
            toast("Acest tip de descarcare nu este suportat");
            return;
        }
        String name = URLUtil.guessFileName(url, disposition, mime);
        new AlertDialog.Builder(this).setTitle("Descarci fisierul?").setMessage(name)
                .setPositiveButton("Descarca", (d, w) -> {
                    try {
                        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                        String cookies = CookieManager.getInstance().getCookie(url);
                        if (cookies != null) r.addRequestHeader("Cookie", cookies);
                        r.addRequestHeader("User-Agent", agent);
                        r.setMimeType(mime);
                        r.setTitle(name);
                        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                        getSystemService(DownloadManager.class).enqueue(r);
                        toast("Se descarca in Descarcari");
                    } catch (Exception e) {
                        toast("Eroare: " + e.getMessage());
                    }
                })
                .setNegativeButton("Renunta", null).show();
    }

    // ---- diverse ----
    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("Nicio aplicatie nu poate deschide asta");
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void leaveFullscreen() {
        if (fullView == null) return;
        ((ViewGroup) getWindow().getDecorView()).removeView(fullView);
        fullView = null;
        root.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        fullCallback.onCustomViewHidden();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (fullView != null) leaveFullscreen();
        else if (current.canGoBack()) current.goBack();
        else if (tabs.size() > 1) closeTab(current);
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
}
