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
import android.text.Html;
import android.text.TextUtils;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://lumen.home/";
    private static final String LUMEN_SEARCH = "https://lumen.search/";
    private static final String[] ENGINES = {"Lumen", "DuckDuckGo", "Google", "Bing", "Brave"};
    private static final String[] SEARCH = {LUMEN_SEARCH, "https://duckduckgo.com/",
            "https://www.google.com/search", "https://www.bing.com/search", "https://search.brave.com/search"};
    private static final Pattern LINK = Pattern.compile("<a\\s([^>]*)>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern CLASS = Pattern.compile("class=\"([^\"]*)\"");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");
    private static final int PICK_FILE = 1;
    private static final int HISTORY_MAX = 500;
    private static final String[] BLOCK = {"doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "adservice.google.com", "connect.facebook.net",
            "scorecardresearch.com", "taboola.com", "outbrain.com", "adnxs.com", "criteo.com", "hotjar.com",
            "amazon-adsystem.com"};
    private static final String HOME_HTML = """
<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>@NAME@</title></head><body style="margin:0;height:100vh;display:flex;flex-direction:column;
align-items:center;justify-content:center;gap:28px;color:#e6e9f5;font-family:Segoe UI,system-ui,sans-serif;
background:radial-gradient(900px 420px at 50% -10%,rgba(40,90,255,.55),transparent 70%),#070b14">
<h1 style="font-family:'@FONT@',Georgia,serif;font-size:60px;font-weight:600;margin:0;letter-spacing:-2px;background:linear-gradient(90deg,#a9c4ff,#4f8cff);
-webkit-background-clip:text;color:transparent">@NAME@</h1>
<form action="@ACTION@" style="width:min(560px,80%)">
<input name="q" autofocus placeholder="Cauta cu @ENGINE@ sau scrie o adresa" style="width:100%;box-sizing:border-box;
height:46px;border-radius:23px;border:1px solid rgba(255,255,255,.15);background:rgba(255,255,255,.06);
color:#e6e9f5;padding:0 20px;font-size:15px;outline:none"></form>
<div style="display:flex;flex-wrap:wrap;justify-content:center;gap:12px;padding:0 16px">
<a href="https://google.com" style="width:80px;padding:16px 0;text-align:center;border-radius:16px;color:#e6e9f5;text-decoration:none;border:1px solid rgba(255,255,255,.15);background:rgba(255,255,255,.06)">Google</a><a href="https://youtube.com" style="width:80px;padding:16px 0;text-align:center;border-radius:16px;color:#e6e9f5;text-decoration:none;border:1px solid rgba(255,255,255,.15);background:rgba(255,255,255,.06)">YouTube</a>
<a href="https://wikipedia.org" style="width:80px;padding:16px 0;text-align:center;border-radius:16px;color:#e6e9f5;text-decoration:none;border:1px solid rgba(255,255,255,.15);background:rgba(255,255,255,.06)">Wikipedia</a><a href="https://github.com" style="width:80px;padding:16px 0;text-align:center;border-radius:16px;color:#e6e9f5;text-decoration:none;border:1px solid rgba(255,255,255,.15);background:rgba(255,255,255,.06)">GitHub</a>
</div></body></html>""";
    private static final String RESULTS_HTML = """
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>@Q@ - @NAME@</title><style>
body{margin:0;background:#070b14;color:#e6e9f5;font-family:sans-serif}
.top{display:flex;align-items:center;gap:24px;padding:22px 40px;border-bottom:1px solid rgba(255,255,255,.08);
 background:radial-gradient(700px 200px at 20% -40%,rgba(40,90,255,.45),transparent 70%)}
.logo{font:600 34px Georgia,serif;background:linear-gradient(90deg,#a9c4ff,#4f8cff);-webkit-background-clip:text;
 color:transparent;text-decoration:none;letter-spacing:-1px}
form{flex:1;max-width:640px;margin:0}
input{width:100%;box-sizing:border-box;height:44px;border-radius:22px;border:1px solid rgba(255,255,255,.15);
 background:rgba(255,255,255,.06);color:#e6e9f5;padding:0 20px;font-size:15px;outline:none;font-family:inherit}
input:focus{border-color:#4f8cff}
.wrap{max-width:760px;margin-left:40px;padding:24px 0 60px}
.r{padding:16px 20px;margin-bottom:14px;border-radius:16px;border:1px solid rgba(255,255,255,.08);background:rgba(255,255,255,.04)}
.r:hover{border-color:rgba(79,140,255,.6)}
.u{font-size:12px;color:#7fd6a4;margin-bottom:4px;word-break:break-all}
.t{font-size:19px;color:#8fb4ff;text-decoration:none}.t:hover{text-decoration:underline}
.s{margin-top:6px;font-size:14px;line-height:1.5;color:#c4cbe3}
.m{color:#8b93ad;font-size:14px}.m a{color:#8fb4ff}
@media(max-width:700px){.top{flex-direction:column;gap:12px;padding:18px 16px}form{width:100%;flex:none}
.wrap{margin:0;padding:16px 12px 40px}}
</style></head><body>
<div class="top"><a class="logo" href="https://lumen.search/">@NAME@</a>
<form action="https://lumen.search/"><input name="q" value="@Q@" autofocus></form></div>
<div class="wrap">@BODY@</div></body></html>""";
    private static final String THEME_CSS = """
html, html * { font-family: system-ui, sans-serif !important; }
html, body, body > div, body > div > div { background: #070b14 !important; }
a { color: #8fb4ff !important; }
article, li[data-layout] { background: rgba(255,255,255,.04) !important; border: 1px solid rgba(255,255,255,.08) !important;
  border-radius: 16px !important; padding: 14px 18px !important; margin-bottom: 14px !important; }
form { background: rgba(255,255,255,.06) !important; border: 1px solid rgba(255,255,255,.12) !important;
  border-radius: 28px !important; }
::selection { background: #4f8cff; color: #fff; }""";
    private static final String BRAND_JS = """
(function(){
 if(!/(^|\\.)duckduckgo\\.com$/.test(location.hostname)) return;
 var NAME = @NAME@, FONT = @FONT@;
 function mark(size){
  var sp = document.createElement("span");
  sp.textContent = NAME;
  sp.style.cssText = "font:600 " + size + "px '" + FONT + "',Georgia,serif;color:#8fb4ff;letter-spacing:-.5px;padding:0 8px;white-space:nowrap";
  return sp;
 }
 function swap(el, r){
  el.dataset.lumen = "1";
  var big = r.width > 150;
  if(el.tagName === "A" || el.tagName === "BUTTON"){ el.textContent = ""; el.appendChild(mark(big ? 56 : 26)); el.style.width = "auto"; }
  else { el.replaceWith(mark(big ? 56 : 26)); }
 }
 var STYLE = @CSS@, THEME = @THEME@;
 function theme(){
  if(!THEME) return;
  if(!document.getElementById("lumen-style")){
   var st = document.createElement("style"); st.id = "lumen-style"; st.textContent = STYLE;
   (document.head || document.documentElement).appendChild(st);
  }
  var w = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT, null), n;
  while((n = w.nextNode())){
   var tx = n.nodeValue.trim();
   if(tx === "Share Feedback" || tx === "Protection. Privacy. Peace of mind."){
    var h = n.parentNode.closest("button,a") || n.parentNode; h.style.display = "none";
   }
  }
 }
 var scans = 0, lastHref = "";
 function brand(){
  theme();
  // logo: element vizual (imagine / svg / fundal) in zona de sus-stanga, sau sus-centru pe pagina de start
  if(lastHref !== location.href){ lastHref = location.href; scans = 0; }
  if(scans < 60){
   scans++;
   var home = location.pathname === "/" && !/[?&]q=/.test(location.search);
   document.querySelectorAll("img,svg,a,button,span,div").forEach(function(el){
    if(el.dataset.lumen || el.closest("[data-lumen]")) return;
    if(el.closest("form,nav,[role=tablist],[role=listbox],[role=dialog],input,textarea")) return;
    var r = el.getBoundingClientRect();
    if(r.width < 24 || r.height < 24 || r.width > 600 || r.height > 300) return;
    if(!((r.top < 150 && r.left < 320) || (home && r.top < 450))) return;
    var tag = el.tagName.toLowerCase();
    var visual = tag === "img" || tag === "svg" || getComputedStyle(el).backgroundImage !== "none" ||
                 (el.querySelector("img,svg") && el.textContent.trim() === "");
    if(!visual) return;
    var t = el.closest("a,button") || el;
    if(t.dataset.lumen || t.querySelector("input,textarea")) return;
    if(!/^$|Duck/.test(t.textContent.trim())) return;
    swap(t, t.getBoundingClientRect());
   });
  }
  document.querySelectorAll('img[alt*="DuckDuckGo" i], svg[aria-label*="DuckDuckGo" i]').forEach(function(el){
   if(!el.dataset.lumen && !el.closest("[data-lumen]")) swap(el, el.getBoundingClientRect());
  });
  // text
  if(document.body){
   var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null), n;
   while((n = w.nextNode())){
    var v = n.nodeValue;
    if(v && /Duck ?Duck ?Go/.test(v) && !/^(SCRIPT|STYLE|TEXTAREA|INPUT)$/.test(n.parentNode.nodeName))
     n.nodeValue = v.replace(/Duck ?Duck ?Go/g, NAME);
    if(v && v.indexOf("Duck.ai") > -1 && !/^(SCRIPT|STYLE|TEXTAREA|INPUT)$/.test(n.parentNode.nodeName))
     n.nodeValue = n.nodeValue.replace(/Duck\\.ai/g, NAME + " AI");
   }
  }
  if(/Duck ?Duck ?Go/.test(document.title)) document.title = document.title.replace(/Duck ?Duck ?Go/g, NAME);
 }
 var timer = null;
 function run(){ timer = null; try{ brand(); }catch(e){} }
 function sched(){ if(timer === null) timer = setTimeout(run, 150); }
 run();
 new MutationObserver(sched).observe(document.documentElement, {childList:true, subtree:true});
})();""";

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
        int i = Arrays.asList(ENGINES).indexOf(prefs.getString("motor", ENGINES[0]));
        return Math.max(i, 0);
    }

    private String brandName() {
        return prefs.getString("name", "Lumen");
    }

    private String homeHtml() {
        int i = engine();
        return HOME_HTML.replace("@ACTION@", SEARCH[i]).replace("@ENGINE@", ENGINES[i])
                .replace("@FONT@", "Georgia").replace("@NAME@", TextUtils.htmlEncode(brandName()));
    }

    // ---- cautarea Lumen: rezultate de la DuckDuckGo, afisate in designul tau ----
    private static String plain(String markup) {
        return Html.fromHtml(markup, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
    }

    /** Fiecare rezultat: {adresa, titlu, descriere}. */
    private static ArrayList<String[]> parseResults(String raw) {
        ArrayList<String[]> out = new ArrayList<>();
        String[] last = null;
        Matcher m = LINK.matcher(raw);
        while (m.find()) {
            Matcher c = CLASS.matcher(m.group(1)), h = HREF.matcher(m.group(1));
            if (!c.find() || !h.find()) continue;
            String classes = " " + c.group(1) + " ";
            if (classes.contains(" result__a ")) {
                last = null;
                String url = h.group(1).replace("&amp;", "&");
                if (url.startsWith("//")) url = "https:" + url;
                if (url.contains("duckduckgo.com/y.js")) continue;      // reclame
                Uri uri = Uri.parse(url);
                String real = uri.isHierarchical() ? uri.getQueryParameter("uddg") : null;
                if (real != null) url = real;
                String title = plain(m.group(2));
                if (!isWeb(url) || url.contains("duckduckgo.com/y.js") || title.isEmpty()) continue;
                last = new String[]{url, title, ""};
                out.add(last);
            } else if (classes.contains(" result__snippet ") && last != null) {
                last[2] = plain(m.group(2));
            }
        }
        return out;
    }

    /** Se apeleaza pe un fir de fundal (din shouldInterceptRequest). */
    private String resultsHtml(String query) {
        StringBuilder body = new StringBuilder();
        String error = "Posibil sa fi cerut verificare.";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://html.duckduckgo.com/html/?q=" + Uri.encode(query))
                    .openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1; ) bytes.write(buffer, 0, n);
            }
            for (String[] result : parseResults(new String(bytes.toByteArray(), StandardCharsets.UTF_8))) {
                String shown = result[0].replaceFirst("^https?://(www\\.)?", "");
                if (shown.length() > 90) shown = shown.substring(0, 90);
                body.append("<div class='r'><div class='u'>").append(TextUtils.htmlEncode(shown))
                        .append("</div><a class='t' href=\"").append(TextUtils.htmlEncode(result[0])).append("\">")
                        .append(TextUtils.htmlEncode(result[1])).append("</a><div class='s'>")
                        .append(TextUtils.htmlEncode(result[2])).append("</div></div>");
            }
        } catch (Exception e) {
            error = "Eroare: " + e.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        if (body.length() == 0)
            body.append("<p class='m'>Nu am putut afisa rezultate. ").append(TextUtils.htmlEncode(error))
                    .append("<br><a href=\"https://duckduckgo.com/?q=").append(Uri.encode(query))
                    .append("\">Deschide cautarea direct</a></p>");
        else body.append("<p class='m'>Rezultate: DuckDuckGo</p>");
        // @BODY@ la urma, ca textul din rezultate sa nu fie inlocuit
        return RESULTS_HTML.replace("@NAME@", TextUtils.htmlEncode(brandName()))
                .replace("@Q@", TextUtils.htmlEncode(query)).replace("@BODY@", body);
    }

    // ---- "acoperirea": numele tau in loc de DuckDuckGo ----
    private void brand(WebView web) {
        String url = web.getUrl();
        String host = url == null ? null : Uri.parse(url).getHost();
        if (host == null || !(host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com"))) return;
        String js = BRAND_JS.replace("@CSS@", JSONObject.quote(THEME_CSS))
                .replace("@THEME@", String.valueOf(prefs.getBoolean("theme", true)))
                .replace("@FONT@", JSONObject.quote("Georgia"))
                .replace("@NAME@", JSONObject.quote(brandName()));
        web.evaluateJavascript("if(!window.__lumen){window.__lumen=true;" + js + "}", null);
    }

    private void editName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(brandName());
        new AlertDialog.Builder(this).setTitle("Numele afisat in loc de DuckDuckGo").setView(input)
                .setPositiveButton("Salveaza", (d, w) -> {
                    String name = input.getText().toString().trim();
                    prefs.edit().putString("name", name.isEmpty() ? "Lumen" : name).apply();
                    current.reload();
                })
                .setNegativeButton("Renunta", null).show();
    }

    private void chooseEngine() {
        new AlertDialog.Builder(this).setTitle("Motor de cautare")
                .setSingleChoiceItems(ENGINES, engine(), (d, i) -> {
                    prefs.edit().putString("motor", ENGINES[i]).apply();
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
                if (host.equals("lumen.search") && r.isForMainFrame()) {
                    String q = r.getUrl().getQueryParameter("q");
                    String page = q == null || q.trim().isEmpty() ? homeHtml() : resultsHtml(q.trim());
                    return new WebResourceResponse("text/html", "utf-8",
                            new ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8)));
                }
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
            public void onPageFinished(WebView v, String url) {
                brand(v);
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
                if (p >= 40) brand(v);
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
        if (address.hasFocus()) return;
        if (url == null || url.startsWith(HOME)) address.setText("");
        else if (url.startsWith(LUMEN_SEARCH)) address.setText(Uri.parse(url).getQueryParameter("q"));
        else address.setText(url);
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
                "Motor de cautare: " + ENGINES[engine()], "Nume afisat: " + brandName(),
                "Tema pe DuckDuckGo: " + (prefs.getBoolean("theme", true) ? "DA" : "NU")};
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
                case 13: editName(); break;
                case 14:
                    prefs.edit().putBoolean("theme", !prefs.getBoolean("theme", true)).apply();
                    current.reload();
                    break;
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
