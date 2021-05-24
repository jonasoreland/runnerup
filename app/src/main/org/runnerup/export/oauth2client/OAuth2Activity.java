/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.export.oauth2client;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.export.Synchronizer;
import org.runnerup.export.util.FormValues;
import org.runnerup.export.util.SyncHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


@SuppressLint("SetJavaScriptEnabled")
public class OAuth2Activity extends AppCompatActivity {

    /**
     * Names used in Bundle to/from OAuth2Activity
     */
    public interface OAuth2ServerCredentials {

        String AUTH_ARGUMENTS = "auth_arguments";

        /**
         * Used as title when opening authorization dialog
         */
        String NAME = "name";
        String CLIENT_ID = "client_id";
        String CLIENT_SECRET = "client_secret";
        String AUTH_URL = "auth_url";
        String AUTH_EXTRA = "auth_extra";
        String TOKEN_URL = "token_url";
        String REDIRECT_URI = "redirect_uri";
        String REVOKE_URL = "revoke_url";

        String AUTH_TOKEN = "auth_token";
    }

    private boolean mFinished = false;
    private String mRedirectUri = null;
    private ProgressDialog mSpinner = null;
    private Bundle mArgs = null;

    private void setSavedPassword(WebView wv, boolean val) {
        wv.getSettings().setSavePassword(false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Bundle b = mArgs = intent
                .getBundleExtra(OAuth2ServerCredentials.AUTH_ARGUMENTS);
        String auth_url = b.getString(OAuth2ServerCredentials.AUTH_URL);
        String client_id = b.getString(OAuth2ServerCredentials.CLIENT_ID);
        mRedirectUri = b.getString(OAuth2ServerCredentials.REDIRECT_URI);
        String auth_extra = null;
        if (b.containsKey(OAuth2ServerCredentials.AUTH_EXTRA))
            auth_extra = b.getString(OAuth2ServerCredentials.AUTH_EXTRA);

        mSpinner = new ProgressDialog(this);
        mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mSpinner.setMessage(getString(R.string.Loading));
        
        // https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview/58131421#58131421
        WebView wvt;
        try {
            wvt = new WebView(this);
        } catch (Exception e) {
            wvt = new WebView(getApplicationContext());
        }
        final WebView wv = wvt;
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
        wv.getSettings().setJavaScriptEnabled(true);
        // https://stackoverflow.com/questions/40591090/403-error-thats-an-error-error-disallowed-useragent
        // I think any mobile browser user-agent should work here
        wv.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10; Android SDK built for x86) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.185 Mobile Safari/537.36");
        setSavedPassword(wv, false);

        StringBuilder tmp = new StringBuilder();
        tmp.append(auth_url);
        tmp.append("?client_id=").append(SyncHelper.URLEncode(client_id));
        tmp.append("&response_type=code");
        tmp.append("&redirect_uri=").append(SyncHelper.URLEncode(mRedirectUri));
        if (auth_extra != null) {
            tmp.append("&").append(auth_extra);
        }
        final String url = tmp.toString();

        CookieSyncManager.createInstance(this);
        CookieManager.getInstance().removeAllCookie();
        wv.loadUrl(url);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String loadurl) {
                if (loadurl
                        .startsWith("https://runkeeper.com/jsp/widgets/streetTeamWidgetClose.jsp")
                        ||
                        loadurl.startsWith("https://runkeeper.com/jsp/widgets/friendWidgetClose.jsp")) {
                    wv.loadUrl("https://runkeeper.com/facebookSignIn");
                    return true;
                }
                if (loadurl.startsWith("https://runkeeper.com/home")) {
                    wv.loadUrl(url);
                    return true;
                }

                return super.shouldOverrideUrlLoading(view, loadurl);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!isFinishing())
                    mSpinner.show();
            }

            // TODO: Fix "WrongThread"
            @SuppressLint({"StaticFieldLeak", "WrongThread"})
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                try {// to avoid crashing the app add try-catch block, avoid
                     // this stupid crash!
                    if (mSpinner != null && mSpinner.isShowing())
                        mSpinner.dismiss();
                } catch (Exception ex) {

                }

                if (url.startsWith(mRedirectUri)) {
                    Uri u = Uri.parse(url);
                    String e = null;
                    String[] check = {
                            "error", "error_type"
                    };
                    for (String aCheck : check) {
                        e = u.getQueryParameter(aCheck);
                        if (e != null) {
                            break;
                        }
                    }

                    if (e != null) {
                        Log.e(getClass().getName(), "e: " + e);
                        Intent res = new Intent()
                                .putExtra("error", e);
                        OAuth2Activity.this.setResult(AppCompatActivity.RESULT_CANCELED, res);
                        OAuth2Activity.this.finish();
                        return;
                    }

                    synchronized (this) {
                        if (mFinished) {
                            return;
                        }
                        mFinished = true;
                    }

                    Bundle b = mArgs;
                    String code = u.getQueryParameter("code");
                    final String token_url = b.getString(OAuth2ServerCredentials.TOKEN_URL);
                    final FormValues fv = new FormValues();
                    fv.put("client_id", b.getString(OAuth2ServerCredentials.CLIENT_ID));
                    fv.put("client_secret", b.getString(OAuth2ServerCredentials.CLIENT_SECRET));
                    fv.put("grant_type", "authorization_code");
                    fv.put("redirect_uri", b.getString(OAuth2ServerCredentials.REDIRECT_URI));
                    fv.put("code", code);

                    final Intent res = new Intent()
                            .putExtra("url", token_url);

                    new AsyncTask<String, String, Integer>() {
                        @Override
                        protected Integer doInBackground(String... params) {
                            int resultCode = AppCompatActivity.RESULT_CANCELED;
                            HttpURLConnection conn = null;

                            try {
                                URL newUrl = new URL(token_url);
                                conn = (HttpURLConnection) newUrl.openConnection();
                                conn.setDoOutput(true);
                                conn.setRequestMethod(Synchronizer.RequestMethod.POST.name());
                                conn.setRequestProperty("Content-Type",
                                        "application/x-www-form-urlencoded");
                                SyncHelper.postData(conn, fv);
                                StringBuilder obj = new StringBuilder();
                                int responseCode =conn.getResponseCode();
                                String amsg = conn.getResponseMessage();

                                try {
                                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    char[] buf = new char[1024];
                                    int len;
                                    while ((len = in.read(buf)) != -1) {
                                        obj.append(buf, 0, len);
                                    }

                                    res.putExtra(DB.ACCOUNT.AUTH_CONFIG, obj.toString());
                                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                                        resultCode = AppCompatActivity.RESULT_OK;
                                    }
                                } catch (IOException e) {
                                    InputStream inS = conn.getErrorStream();
                                    String msg = inS == null ? "" : SyncHelper.readInputStream(inS);
                                    Log.w("oath2", "Error stream: " +
                                            responseCode + " " + amsg + "; " +msg);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace(System.err);
                                res.putExtra("ex", ex.toString());
                            } finally {
                                if (conn != null) {
                                    conn.disconnect();
                                }
                            }

                            return resultCode;
                        }
                        @Override
                        protected void onPostExecute(Integer resultCode) {
                            setResult(resultCode, res);
                            finish();
                        }
                    }.execute();
                }
            }

            public void onReceivedError(WebView view, int errorCode,
                    String description, String failingUrl) {
                if (failingUrl.startsWith(mRedirectUri)) {
                    view.setVisibility(View.INVISIBLE);
                    return; // we know this is will give error...
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
                finish();
            }
        });

        setContentView(wv);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public static Intent getIntent(AppCompatActivity activity, OAuth2Server server) {
        Bundle b = new Bundle();
        b.putString(OAuth2ServerCredentials.CLIENT_ID, server.getClientId());
        b.putString(OAuth2ServerCredentials.CLIENT_SECRET,
                server.getClientSecret());
        b.putString(OAuth2ServerCredentials.AUTH_URL, server.getAuthUrl());
        b.putString(OAuth2ServerCredentials.TOKEN_URL, server.getTokenUrl());
        b.putString(OAuth2ServerCredentials.REDIRECT_URI,
                server.getRedirectUri());
        String extra = server.getAuthExtra();
        if (extra != null) {
            b.putString(OAuth2ServerCredentials.AUTH_EXTRA, extra);
        }

        return new Intent(activity, OAuth2Activity.class)
                .putExtra(OAuth2ServerCredentials.AUTH_ARGUMENTS, b);
    }
}
