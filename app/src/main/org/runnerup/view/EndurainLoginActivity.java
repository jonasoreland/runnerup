package org.runnerup.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.export.util.PKCEUtil;

public class EndurainLoginActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_ACCESS_TOKEN = "access_token";
    public static final String EXTRA_REFRESH_TOKEN = "refresh_token";
    public static final String EXTRA_CSRF_TOKEN = "csrf_token";
    private static final String KEY_VERIFIER = "pkce_verifier";

    private String verifier;
    private String instanceUrl;
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isFinishing = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        instanceUrl = getIntent().getStringExtra(EXTRA_URL);
        if (instanceUrl == null) {
            finish();
            return;
        }

        if (instanceUrl.endsWith("/")) {
            instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
        }
        if (instanceUrl.endsWith("/login")) {
            instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 6);
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        if (savedInstanceState != null) {
            verifier = savedInstanceState.getString(KEY_VERIFIER);
            webView.restoreState(savedInstanceState);
        }

        if (verifier == null) {
            verifier = PKCEUtil.generateCodeVerifier();
        }

        if (savedInstanceState == null) {
            fetchIdpsAndLoad();
        } else {
            setupWebViewClient();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_VERIFIER, verifier);
        webView.saveState(outState);
    }

    private void fetchIdpsAndLoad() {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            runOnUiThread(() -> {
                setupWebViewClient();
                loadWebView(instanceUrl + "/login");
            });
        });
    }

    private void setupWebViewClient() {
        try {
            final String challenge = PKCEUtil.generateCodeChallenge(verifier);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    checkUrlAndExtractTokens(view, url, challenge);
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    checkUrlAndExtractTokens(view, url, challenge);
                    super.doUpdateVisitedHistory(view, url, isReload);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    return handleOverride(view, url, challenge);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return handleOverride(view, url, challenge);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean handleOverride(WebView view, String url, String challenge) {
        if (isFinishing) return true;

        if (url.contains("sso=success") && url.contains("session_id=")) {
            Uri uri = Uri.parse(url);
            String sessionId = uri.getQueryParameter("session_id");

            if (sessionId == null || sessionId.trim().isEmpty()) {
                Log.e("EndurainLoginActivity", "SSO redirect missing valid session_id");
                setResult(Activity.RESULT_CANCELED);
                finish();
                return true;
            }

            exchangeSessionForTokens(sessionId);
            return true;
        }

        if (url.contains("/api/v1/public/idp/login/")) {
            if (!url.contains("code_challenge=" + challenge)) {

                Uri baseUri = Uri.parse(url);
                Uri.Builder newUri = baseUri.buildUpon();

                newUri.clearQuery();

                newUri.appendQueryParameter("code_challenge", challenge);
                newUri.appendQueryParameter("code_challenge_method", "S256");

                view.loadUrl(newUri.build().toString());
                return true;
            }
        }
        return false;
    }

    private void checkUrlAndExtractTokens(WebView view, String url, String challenge) {
        if (isFinishing) return;

        if (url.contains("sso=success") && url.contains("session_id=")) {
            view.stopLoading();
            Uri uri = Uri.parse(url);
            String sessionId = uri.getQueryParameter("session_id");
            exchangeSessionForTokens(sessionId);
            return;
        }

        if (url.contains("/api/v1/public/idp/login/")) {
            if (!url.contains("code_challenge=" + challenge)) {
                Log.d("EndurainLoginActivity", "Intercepted SSO login load, reloading with Android PKCE");
                view.stopLoading();

                Uri baseUri = Uri.parse(url);
                Uri.Builder newUri = baseUri.buildUpon();
                newUri.clearQuery();
                newUri.appendQueryParameter("code_challenge", challenge);
                newUri.appendQueryParameter("code_challenge_method", "S256");

                view.loadUrl(newUri.build().toString());
                return;
            }
        }

        if (url.equals(instanceUrl + "/") || url.equals(instanceUrl) || (url.startsWith(instanceUrl) && !url.contains("/login") && !url.contains("/api/"))) {
            Log.d("EndurainLogin", "Detected authenticated page load: " + url);

            view.evaluateJavascript(
                    "(function() { return localStorage.getItem('access_token'); })();",
                    value -> {
                        if (value != null && !value.equals("null")) {
                            String token = value.replaceAll("^\"|\"$", "");
                            view.evaluateJavascript(
                                    "(function() { return localStorage.getItem('refresh_token'); })();",
                                    refreshValue -> {
                                        String refreshToken = null;
                                        if (refreshValue != null && !refreshValue.equals("null")) {
                                            refreshToken = refreshValue.replaceAll("^\"|\"$", "");
                                        }
                                        finishWithSuccess(token, refreshToken, null);
                                    }
                            );
                        }
                    }
            );
        }
    }

    private void loadWebView(String url) {
        webView.loadUrl(url);
    }

    private void finishWithSuccess(String accessToken, String refreshToken, String csrfToken) {
        if (isFinishing) return;
        isFinishing = true;

        Intent result = new Intent();
        if (accessToken != null) result.putExtra(EXTRA_ACCESS_TOKEN, accessToken);
        if (refreshToken != null) result.putExtra(EXTRA_REFRESH_TOKEN, refreshToken);
        if (csrfToken != null) result.putExtra(EXTRA_CSRF_TOKEN, csrfToken);

        setResult(Activity.RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        isFinishing = true;

        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        executor.shutdown();
        super.onDestroy();
    }

    private void exchangeSessionForTokens(String sessionId) {
        if (executor.isShutdown()) return;
        executor.execute(() -> {
            boolean success = false;
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            JSONObject jsonBody = new JSONObject();
            try {
                jsonBody.put("code_verifier", verifier);
            } catch (JSONException e) {
                Log.e("EndurainLoginActivity", "JSON issue", e);
            }

            RequestBody body = RequestBody.create(JSON, jsonBody.toString());
            Request request = new Request.Builder()
                    .url(instanceUrl + "/api/v1/public/idp/session/" + sessionId + "/tokens")
                    .addHeader("X-Client-Type", "mobile")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseString = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseString);

                    String accessToken = jsonResponse.optString("access_token", null);
                    String refreshToken = jsonResponse.optString("refresh_token", null);
                    String csrfToken = jsonResponse.optString("csrf_token", null);

                    runOnUiThread(() -> finishWithSuccess(accessToken, refreshToken, csrfToken));
                    success = true;
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e("EndurainLoginActivity", "Token exchange failed: " + response.code() + " " + response.message() + " Body: " + errorBody);
                }
            } catch (IOException | JSONException e) {
                Log.e("EndurainLoginActivity", "Token exchange failed", e);
            }

            if (!success) {
                runOnUiThread(() -> {
                    if (!isFinishing) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }
                });
            }
        });
    }
}