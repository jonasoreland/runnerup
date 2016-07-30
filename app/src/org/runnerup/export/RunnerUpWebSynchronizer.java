/* Copyright (c) 2016, Ricardo Martin Camarero <rickyepoderi@yahoo.es>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.runnerup.export;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.runnerup.common.util.Constants;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RunnerUpWebSynchronizer extends DigifitSynchronizer {

    public static final String NAME = "RunnerUpWeb";

    private String _url;

    public RunnerUpWebSynchronizer(SyncManager unused) {
        super(unused);
    }

    protected String getConnectionURL() {
        Log.d(getName(), "URL used: " + _url);
        return _url;
    }

    @Override
    public void init(ContentValues config) {
        // configure all the things in digifit
        super.init(config);
        // then get the URL from the auth config too
        String auth = config.getAsString(Constants.DB.ACCOUNT.AUTH_CONFIG);
        if (auth != null) {
            try {
                JSONObject json = new JSONObject(auth);
                _url = json.optString(Constants.DB.ACCOUNT.URL, null);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getAuthConfig() {
        JSONObject json = null;
        try {
            json = new JSONObject(super.getAuthConfig());
            json.put(Constants.DB.ACCOUNT.URL, _url);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    @Override
    public Status connect() {
        Status s = super.connect();
        if (Status.NEED_AUTH.equals(s)) {
            s.authMethod = AuthMethod.USER_PASS_URL;
        }
        return s;
    }

}
