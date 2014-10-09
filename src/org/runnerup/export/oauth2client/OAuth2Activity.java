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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.runnerup.export.FormCrawler;
import org.runnerup.export.FormCrawler.FormValues;
import org.runnerup.util.Constants.DB;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@TargetApi(Build.VERSION_CODES.FROYO)
@SuppressLint("SetJavaScriptEnabled")
public class OAuth2Activity extends Activity {

    /**
     * Names used in Bundle to/from OAuth2Activity
     */
    public interface OAuth2ServerCredentials {

        public static final String AUTH_ARGUMENTS = "auth_arguments";

        /**
         * Used as title when opening authorization dialog
         * 
         * @return
         */
        public static final String NAME = "name";
        public static final String CLIENT_ID = "client_id";
        public static final String CLIENT_SECRET = "client_secret";
        public static final String AUTH_URL = "auth_url";
        public static final String AUTH_EXTRA = "auth_extra";
        public static final String TOKEN_URL = "token_url";
        public static final String REDIRECT_URI = "redirect_uri";
        public static final String REVOKE_URL = "revoke_url";

        public static final String AUTH_TOKEN = "auth_token";
    }

    boolean mFinished = false;
    String mRedirectUri = null;
    ProgressDialog mSpinner = null;
    Bundle mArgs = null;

    @SuppressWarnings("deprecation")
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
        mSpinner.setMessage("Loading...");

        final WebView wv = new WebView(this);
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);
        wv.getSettings().setJavaScriptEnabled(true);
        setSavedPassword(wv, false);

        StringBuilder tmp = new StringBuilder();
        tmp.append(auth_url);
        tmp.append("?client_id=").append(FormCrawler.URLEncode(client_id));
        tmp.append("&response_type=code");
        tmp.append("&redirect_uri=").append(FormCrawler.URLEncode(mRedirectUri));
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
                        .startsWith("http://runkeeper.com/jsp/widgets/streetTeamWidgetClose.jsp")
                        ||
                        loadurl.startsWith("http://runkeeper.com/jsp/widgets/friendWidgetClose.jsp")) {
                    wv.loadUrl("http://runkeeper.com/facebookSignIn");
                    return true;
                }
                if (loadurl.startsWith("http://runkeeper.com/home")) {
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
                    String check[] = {
                            "error", "error_type"
                    };
                    for (int i = 0; i < check.length; i++) {
                        e = u.getQueryParameter(check[i]);
                        if (e != null) {
                            break;
                        }
                    }

                    if (e != null) {
                        System.err.println("e: " + e);
                        Intent res = new Intent();
                        res.putExtra("error", e);
                        OAuth2Activity.this.setResult(Activity.RESULT_CANCELED,
                                res);
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
                    String token_url = b.getString(OAuth2ServerCredentials.TOKEN_URL);
                    FormValues fv = new FormValues();
                    fv.put("client_id", b.getString(OAuth2ServerCredentials.CLIENT_ID));
                    fv.put("client_secret", b.getString(OAuth2ServerCredentials.CLIENT_SECRET));
                    fv.put("grant_type", "authorization_code");
                    fv.put("redirect_uri", b.getString(OAuth2ServerCredentials.REDIRECT_URI));
                    fv.put("code", code);

                    HttpURLConnection conn = null;

                    Intent res = new Intent();
                    int resultCode = Activity.RESULT_OK;
                    res.putExtra("url", token_url);
                    try {
                        URL newurl = new URL(token_url);
                        conn = (HttpURLConnection) newurl.openConnection();
                        conn.setDoOutput(true);
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type",
                                "application/x-www-form-urlencoded");
                        {
                            OutputStream wr = new BufferedOutputStream(conn.getOutputStream());
                            fv.write(wr);
                            wr.flush();
                            wr.close();
                        }
                        StringBuilder obj = new StringBuilder();
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn
                                .getInputStream()));
                        char buf[] = new char[1024];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            obj.append(buf, 0, len);
                        }

                        res.putExtra(DB.ACCOUNT.AUTH_CONFIG, obj.toString());
                    } catch (Exception ex) {
                        ex.printStackTrace(System.err);
                        res.putExtra("ex", ex.toString());
                        resultCode = Activity.RESULT_CANCELED;
                    }

                    if (conn != null) {
                        conn.disconnect();
                    }

                    setResult(resultCode, res);
                    finish();
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

    public static Intent getIntent(Activity activity, OAuth2Server server) {
        Intent args = new Intent(activity, OAuth2Activity.class);
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
        args.putExtra(OAuth2Activity.OAuth2ServerCredentials.AUTH_ARGUMENTS, b);
        return args;
    }
}
