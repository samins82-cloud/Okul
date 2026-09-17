package com.elak.izintakip;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String START_URL = "https://www.mcoaihl.com/izin/index.php";
    private static final String ALT_URL = "https://mcoaihl.com/izin/index.php";
    private static final String INTERNAL_HOST = "mcoaihl.com";
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean alternateHostTried = false;
    private boolean currentHostReloadTried = false;
    private boolean showingLocalError = false;

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        configureWebView();
        configureDownloads();

        if (savedInstanceState == null) {
            Uri deepLink = getIntent() != null ? getIntent().getData() : null;
            String initialUrl = deepLink != null && isInternalUrl(deepLink.toString())
                    ? normalizeInternalUrl(deepLink.toString())
                    : START_URL;
            loadMainUrl(initialUrl, true);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(MOBILE_UA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!showingLocalError) {
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                progressBar.setVisibility(ProgressBar.GONE);
                if (!showingLocalError && isInternalUrl(url)) {
                    scheduleRealBlankCheck(url);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    String detail = "WebView hata kodu " + error.getErrorCode() + ": " + error.getDescription();
                    handleMainFrameFailure(request.getUrl() != null ? request.getUrl().toString() : START_URL,
                            "Sayfa yüklenemedi", detail);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame() && errorResponse != null && errorResponse.getStatusCode() >= 400) {
                    handleMainFrameFailure(request.getUrl().toString(),
                            "Sunucu sayfayı açamadı",
                            "HTTP " + errorResponse.getStatusCode() + " " + errorResponse.getReasonPhrase());
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler sslHandler, SslError error) {
                sslHandler.cancel();
                String failingUrl = error != null && error.getUrl() != null ? error.getUrl() : START_URL;
                if (tryAlternateHost(failingUrl)) return;
                showLocalError(
                        "Güvenli bağlantı kurulamadı",
                        "Sunucunun SSL sertifikası Android tarafından doğrulanamadı. Sertifika kontrolü güvenlik nedeniyle kapatılmadı.",
                        failingUrl
                );
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                showLocalError(
                        "Android WebView yeniden başlatılmalı",
                        "Android System WebView işlemi kapandı. Uygulamayı kapatıp yeniden açın; sorun sürerse Android System WebView ve Chrome'u güncelleyin.",
                        START_URL
                );
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (!showingLocalError) {
                    progressBar.setVisibility(newProgress >= 100 ? ProgressBar.GONE : ProgressBar.VISIBLE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallbackParam,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = filePathCallbackParam;
                openFileChooser();
                return true;
            }
        });
    }

    private String normalizeInternalUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.equals("/izin") || path.equals("/izin/")) {
                Uri.Builder builder = uri.buildUpon().path("/izin/index.php");
                return builder.build().toString();
            }
        } catch (Exception ignored) {}
        return url;
    }

    private void loadMainUrl(String url, boolean resetAttempts) {
        showingLocalError = false;
        if (resetAttempts) {
            currentHostReloadTried = false;
            alternateHostTried = false;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.put("Cache-Control", "no-cache");
        webView.loadUrl(normalizeInternalUrl(url), headers);
    }

    private boolean isInternalUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            return host.equals(INTERNAL_HOST) || host.endsWith("." + INTERNAL_HOST);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWwwHost(String url) {
        try {
            Uri uri = Uri.parse(url);
            return "www.mcoaihl.com".equalsIgnoreCase(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryAlternateHost(String failingUrl) {
        if (alternateHostTried) return false;
        alternateHostTried = true;
        currentHostReloadTried = false;
        String target = isWwwHost(failingUrl) ? ALT_URL : START_URL;
        Toast.makeText(this, "Alternatif okul adresi deneniyor…", Toast.LENGTH_SHORT).show();
        loadMainUrl(target, false);
        return true;
    }

    private void handleMainFrameFailure(String failingUrl, String title, String detail) {
        if (tryAlternateHost(failingUrl)) return;
        showLocalError(title, detail, failingUrl);
    }

    private void scheduleRealBlankCheck(final String url) {
        handler.postDelayed(() -> {
            if (isFinishing() || showingLocalError || webView == null) return;

            webView.evaluateJavascript(
                    "(function(){try{return JSON.stringify({html:(document.documentElement&&document.documentElement.outerHTML?document.documentElement.outerHTML.length:0),body:(document.body&&document.body.innerHTML?document.body.innerHTML.length:0),title:(document.title||''),ready:document.readyState});}catch(e){return JSON.stringify({html:-1,body:-1,title:'',ready:'error'});}})();",
                    value -> {
                        if (showingLocalError || value == null) return;

                        String decoded = value.replace("\\\"", "\"");
                        if (decoded.startsWith("\"") && decoded.endsWith("\"")) {
                            decoded = decoded.substring(1, decoded.length() - 1);
                        }

                        int htmlLen = extractJsonInt(decoded, "html");
                        int bodyLen = extractJsonInt(decoded, "body");

                        // Gerçek HTML varsa ekrana müdahale etme. Önceki sürümdeki yanlış boş-sayfa tespiti kaldırıldı.
                        if (htmlLen >= 200 || bodyLen >= 80 || htmlLen < 0) return;

                        if (!currentHostReloadTried) {
                            currentHostReloadTried = true;
                            webView.clearCache(true);
                            Toast.makeText(this, "Sayfa yeniden yükleniyor…", Toast.LENGTH_SHORT).show();
                            loadMainUrl(url, false);
                            return;
                        }

                        if (tryAlternateHost(url)) return;

                        showLocalError(
                                "Sunucudan boş içerik geldi",
                                "Sunucu bağlantıyı kabul etti ancak HTML içeriği boş döndü. Telefon tarayıcısında çalışan aynı adresi açmak için aşağıdaki Tarayıcıda Aç düğmesini kullanabilirsiniz.",
                                url
                        );
                    }
            );
        }, 4500);
    }

    private int extractJsonInt(String json, String key) {
        try {
            String token = "\"" + key + "\":";
            int start = json.indexOf(token);
            if (start < 0) return -1;
            start += token.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void showLocalError(String title, String detail, String url) {
        showingLocalError = true;
        progressBar.setVisibility(ProgressBar.GONE);

        final String safeTitle = htmlEscape(title);
        final String safeDetail = htmlEscape(detail);
        final String safeUrl = htmlEscape(url == null ? START_URL : url);

        String html = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;background:#eef4fb;font-family:Arial,sans-serif;color:#10233f}"
                + ".wrap{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}"
                + ".card{width:100%;max-width:520px;background:#fff;border:1px solid #d9e4f1;border-radius:22px;padding:24px;box-shadow:0 18px 55px rgba(15,47,92,.13)}"
                + ".icon{width:56px;height:56px;border-radius:18px;background:#e8f1ff;color:#1769e0;display:grid;place-items:center;font-size:29px;font-weight:bold}"
                + "h1{font-size:22px;margin:16px 0 8px}p{font-size:14px;line-height:1.55;color:#566a84}"
                + ".url{font-size:11px;background:#f6f8fb;border:1px solid #e1e8f0;border-radius:10px;padding:10px;word-break:break-all;color:#50627a}"
                + ".btn{display:block;text-align:center;text-decoration:none;background:#1769e0;color:#fff;padding:13px 15px;border-radius:12px;font-weight:bold;margin-top:12px}"
                + ".btn2{background:#0f766e}.small{font-size:12px;color:#7a899d;margin-top:12px}</style></head><body><div class='wrap'><div class='card'>"
                + "<div class='icon'>!</div><h1>" + safeTitle + "</h1><p>" + safeDetail + "</p>"
                + "<div class='url'>" + safeUrl + "</div>"
                + "<a class='btn' href='elak://retry'>Tekrar Dene</a>"
                + "<a class='btn btn2' href='elak://browser'>Tarayıcıda Aç</a>"
                + "<div class='small'>ELAK İzin Takip Android · v1.0.2</div>"
                + "</div></div></body></html>";

        webView.loadDataWithBaseURL("https://www.mcoaihl.com/", html, "text/html", "UTF-8", null);
    }

    private String htmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean handleNavigation(String url) {
        if (url == null || url.trim().isEmpty()) return false;

        if (url.startsWith("elak://retry")) {
            loadMainUrl(START_URL, true);
            return true;
        }
        if (url.startsWith("elak://browser")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(START_URL)));
            } catch (Exception e) {
                Toast.makeText(this, "Telefon tarayıcısı açılamadı.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";

            if (("https".equals(scheme) || "http".equals(scheme)) &&
                    (host.equals(INTERNAL_HOST) || host.endsWith("." + INTERNAL_HOST))) {
                showingLocalError = false;
                return false;
            }

            if ("intent".equals(scheme)) {
                try {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else if (intent.getStringExtra("browser_fallback_url") != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(intent.getStringExtra("browser_fallback_url"))));
                    }
                } catch (Exception ignored) {
                    Toast.makeText(this, "Bağlantı açılamadı.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            Intent external = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(external);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Bu bağlantıyı açabilecek uygulama bulunamadı.", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openFileChooser() {
        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("*/*");
        contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "image/jpeg",
                "image/png",
                "image/webp"
        });

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File cameraDir = new File(getCacheDir(), "camera");
            if (!cameraDir.exists()) cameraDir.mkdirs();
            File photoFile = File.createTempFile("student_", ".jpg", cameraDir);
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (IOException e) {
            cameraIntent = null;
            cameraImageUri = null;
        }

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_INTENT, contentIntent);
        chooser.putExtra(Intent.EXTRA_TITLE, "Dosya / Fotoğraf Seç");
        if (cameraIntent != null && cameraIntent.resolveActivity(getPackageManager()) != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }

        try {
            startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
        } catch (ActivityNotFoundException e) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
            Toast.makeText(this, "Dosya seçici açılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    private void configureDownloads() {
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    pendingDownloadUrl = url;
                    pendingDownloadUserAgent = userAgent;
                    pendingDownloadContentDisposition = contentDisposition;
                    pendingDownloadMimeType = mimetype;
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
                    return;
                }
                enqueueDownload(url, userAgent, contentDisposition, mimetype);
            }
        });
    }

    private void enqueueDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (filename == null || filename.trim().isEmpty()) filename = "ELAK-dosya";

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("ELAK İzin Takip dosyası indiriliyor");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            request.addRequestHeader("User-Agent", MOBILE_UA);
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Dosya İndirilenler klasörüne kaydediliyor.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
                Toast.makeText(this, "Dosya indirilemedi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data == null || data.getData() == null) {
                if (cameraImageUri != null) results = new Uri[]{cameraImageUri};
            } else {
                results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraImageUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingDownloadUrl != null) {
                enqueueDownload(pendingDownloadUrl, pendingDownloadUserAgent,
                        pendingDownloadContentDisposition, pendingDownloadMimeType);
            } else {
                Toast.makeText(this, "Dosyayı indirmek için depolama izni gerekli.", Toast.LENGTH_LONG).show();
            }
            pendingDownloadUrl = null;
            pendingDownloadUserAgent = null;
            pendingDownloadContentDisposition = null;
            pendingDownloadMimeType = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && webView != null && isInternalUrl(uri.toString())) {
            loadMainUrl(uri.toString(), true);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        CookieManager.getInstance().flush();
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
