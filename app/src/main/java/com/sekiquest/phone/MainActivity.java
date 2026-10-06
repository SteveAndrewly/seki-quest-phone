package com.sekiquest.phone;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seki Quest's phone app: the phone page that Seki Quest on the PC serves (src/phone/ in the desktop app),
 * shown full screen in landscape, with the screen kept on. The address and secret code come from the QR code
 * in Seki Quest (Settings → Phone) and are remembered. Nothing goes to the internet: the PC is on this
 * phone's hotspot, and the page talks only to it. No student names reach the phone.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "seki-quest";
    private static final String KEY_URL = "url";
    private static final int NAVY = 0xFF0E1640, WHITE = 0xFFF2F2EE, MUTED = 0xFFB9BDD6, BLUE = 0xFF6CC4FF;

    private WebView web;
    private LinearLayout panel;
    private TextView message;
    private Button primary, secondary;
    private String url;           // the PC's phone page, with its code (?k=…)
    private boolean failed;       // the page didn't load

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(NAVY);
        web = new WebView(this);
        setUpWebView();
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel = buildPanel();
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        url = prefs().getString(KEY_URL, null);
        if (!acceptShared(getIntent())) {
            if (url != null) load(); else showSetup();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShared(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    // Back keeps the app running in the background, so a stray press in class doesn't close it.
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ---------- the page ----------
    private void setUpWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        web.setBackgroundColor(NAVY);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // The page shows a "Scan the QR code" button when it runs inside this app (a code that no longer works).
        web.addJavascriptInterface(new Bridge(), "SekiQuestApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !sameServer(request.getUrl());   // stay on the PC's page; go nowhere else
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    failed = true;
                    showUnreachable();
                }
            }

            @Override
            public void onPageFinished(WebView view, String loaded) {
                if (!failed) panel.setVisibility(View.GONE);
            }
        });
    }

    private boolean sameServer(Uri u) {
        Uri home = url == null ? null : Uri.parse(url);
        return home != null && u != null && home.getHost() != null && home.getHost().equals(u.getHost()) && home.getPort() == u.getPort();
    }

    private void load() {
        failed = false;
        showMessage(getString(R.string.connecting), null, null);
        web.loadUrl(url);
    }

    private class Bridge {
        @JavascriptInterface
        public void rescan() {
            runOnUiThread(MainActivity.this::scan);
        }
    }

    // ---------- the QR code ----------
    private void scan() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener(barcode -> accept(barcode.getRawValue()))
                .addOnFailureListener(e -> showMessage(getString(R.string.scan_failed), getString(R.string.scan), this::scan));
    }

    // A Seki Quest code: http://<the PC's address>:<port>/?k=<code>
    private boolean accept(String raw) {
        Uri u = raw == null ? null : Uri.parse(raw.trim());
        if (u == null || !"http".equals(u.getScheme()) || u.getHost() == null || u.getQueryParameter("k") == null) {
            Toast.makeText(this, R.string.not_a_code, Toast.LENGTH_LONG).show();
            return false;
        }
        url = u.toString();
        prefs().edit().putString(KEY_URL, url).apply();
        load();
        return true;
    }

    // The link shared from Chrome or the camera app (the fallback when the scanner can't start).
    private boolean acceptShared(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return false;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return false;
        Matcher m = Pattern.compile("http://\\S+").matcher(text);
        return m.find() && accept(m.group());
    }

    // ---------- the app's own screens (setup, connecting, can't connect) ----------
    private void showSetup() {
        showMessage(getString(R.string.setup_message), getString(R.string.scan), this::scan);
    }

    private void showUnreachable() {
        showMessage(getString(R.string.unreachable), getString(R.string.try_again), this::load);
        secondary.setText(R.string.scan_again);
        secondary.setOnClickListener(v -> scan());
        secondary.setVisibility(View.VISIBLE);
    }

    private void showMessage(String text, String action, Runnable onAction) {
        message.setText(text);
        primary.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) {
            primary.setText(action);
            primary.setOnClickListener(v -> onAction.run());
        }
        secondary.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
    }

    private LinearLayout buildPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackgroundColor(NAVY);
        p.setPadding(dp(32), dp(16), dp(32), dp(16));
        p.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        p.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(10), 0, dp(6));
        p.addView(title, wrap());

        message = new TextView(this);
        message.setTextColor(MUTED);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setGravity(Gravity.CENTER);
        message.setMaxWidth(dp(460));
        message.setPadding(0, 0, 0, dp(16));
        p.addView(message, wrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        primary = button(true);
        secondary = button(false);
        row.addView(primary);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(12);
        row.addView(secondary, gap);
        p.addView(row, wrap());
        return p;
    }

    // Buttons in the 16-bit look: the main one filled light blue, the other a black window with a white frame.
    private Button button(boolean main) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(main ? 0xFF041029 : WHITE);
        b.setPadding(dp(22), dp(12), dp(22), dp(12));
        b.setMinHeight(dp(52));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(main ? BLUE : Color.BLACK);
        bg.setStroke(dp(3), main ? BLUE : 0xFFE6E6E6);
        b.setBackground(bg);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // Full screen: the status and navigation bars stay hidden; a swipe from the edge shows them for a moment.
    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
