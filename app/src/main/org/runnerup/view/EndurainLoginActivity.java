package org.runnerup.view;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create WebView programmatically
        webView = new WebView(this);
        setContentView(webView);

        instanceUrl = getIntent().getStringExtra(EXTRA_URL);
        if (instanceUrl == null) {
            finish();
            return;
        }

        // Clean up URL
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

        // Only load if not restoring state
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
                // Load the standard login page.
                // The WebViewClient will intercept the specific provider click.
                loadWebView(instanceUrl + "/login");
            });
        });
    }

    private void setupWebViewClient() {
        try {
            final String challenge = PKCEUtil.generateCodeChallenge(verifier);
            Log.d("EndurainLogin", "Verifier: " + verifier);
            Log.d("EndurainLogin", "Challenge: " + challenge);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    Log.d("EndurainLogin", "onPageStarted: " + url);
                    checkUrlAndExtractTokens(view, url, challenge);
                }

                @Override
                public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                    Log.d("EndurainLogin", "doUpdateVisitedHistory: " + url);
                    checkUrlAndExtractTokens(view, url, challenge);
                    super.doUpdateVisitedHistory(view, url, isReload);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    Log.d("EndurainLogin", "shouldOverride (WebResourceRequest): " + url);
                    return handleOverride(view, url, challenge);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Log.d("EndurainLogin", "shouldOverride (String): " + url);
                    return handleOverride(view, url, challenge);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Intercepts navigation to inject the correct PKCE challenge or capture success.
     */
    private boolean handleOverride(WebView view, String url, String challenge) {
        // 1. Capture Success
        if (url.contains("sso=success") && url.contains("session_id=")) {
            Uri uri = Uri.parse(url);
            String sessionId = uri.getQueryParameter("session_id");
            exchangeSessionForTokens(sessionId);
            return true;
        }

        // 2. Intercept Login Initiation (The Fix)
        // When the user clicks "Login" on the web page, it goes to /api/v1/public/idp/login/...
        // We must ensure the code_challenge matches OUR verifier, not the one generated by the website's JS.
        if (url.contains("/api/v1/public/idp/login/")) {
            if (!url.contains("code_challenge=" + challenge)) {
                Log.d("EndurainLogin", "Intercepting login flow to inject Android PKCE challenge");

                Uri baseUri = Uri.parse(url);
                Uri.Builder newUri = baseUri.buildUpon();

                // Clear existing query params (like the JS-generated challenge)
                newUri.clearQuery();

                // Append our challenge
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

        // 1. Capture Success (in case shouldOverride didn't catch it)
        if (url.contains("sso=success") && url.contains("session_id=")) {
            view.stopLoading();
            Uri uri = Uri.parse(url);
            String sessionId = uri.getQueryParameter("session_id");
            exchangeSessionForTokens(sessionId);
            return;
        }

        // 2. Intercept Login Initiation (in case it was a direct load/redirect)
        if (url.contains("/api/v1/public/idp/login/")) {
            if (!url.contains("code_challenge=" + challenge)) {
                Log.d("EndurainLogin", "Intercepted SSO login load, reloading with Android PKCE");
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

        // 3. Fallback: Check for existing session (User already logged in)
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
        Log.d("EndurainLogin", "Loading: " + url);
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
        super.onDestroy();
        isFinishing = true;
        executor.shutdown();
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
                e.printStackTrace();
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
                    Log.e("EndurainLogin", "Token exchange failed: " + response.code() + " " + response.message() + " Body: " + errorBody);
                }
            } catch (IOException | JSONException e) {
                Log.e("EndurainLogin", "Token exchange failed", e);
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