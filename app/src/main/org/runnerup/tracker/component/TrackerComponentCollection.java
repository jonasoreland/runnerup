/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.tracker.component;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import org.runnerup.BuildConfig;

import java.util.HashMap;

/**
 * Created by jonas on 12/11/14.
 *
 * Class for managing a set of TrackerComponents as if they were one
 */

public class TrackerComponentCollection implements TrackerComponent {

    private final Handler handler = new Handler();
    private final HashMap<String, Pair<TrackerComponent, ResultCode>> components =
            new HashMap<>();
    private final HashMap<String, TrackerComponent> pending =
            new HashMap<>();

    public TrackerComponent addComponent(TrackerComponent component) {
        components.put(component.getName(),
                new Pair<>(component, ResultCode.RESULT_OK));
        return component;
    }

    public TrackerComponent getComponent(String key) {
        synchronized (components) {
            if (components.containsKey(key))
                return components.get(key).first;
            else if (pending.containsKey(key))
                return pending.get(key);
            return null;
        }
    }

    public ResultCode getResultCode(String key) {
        synchronized (components) {
            if (components.containsKey(key))
                return components.get(key).second;
            else if (pending.containsKey(key))
                return ResultCode.RESULT_PENDING;
            return ResultCode.RESULT_ERROR;
        }
    }

    @Override
    public String getName() {
        return "TrackerComponentCollection";
    }

    @Override
    public boolean isConnected() { return true; }

    /**
     * Called by Tracker during initialization
     */
    @Override
    public ResultCode onInit(final Callback callback, Context context) {
        return forEach("onInit", (comp0, currentResultCode, callback0, context0) -> {
            if (currentResultCode == ResultCode.RESULT_OK)
                return comp0.onInit(callback0, context0);
            else
                return currentResultCode;
        }, callback, context);
    }

    @Override
    public ResultCode onConnecting(final Callback callback, Context context) {
        return forEach("onConnecting", (comp0, currentResultCode, callback0, context0) -> {
            if (currentResultCode == ResultCode.RESULT_OK ||
                currentResultCode == ResultCode.RESULT_UNKNOWN)
                return comp0.onConnecting(callback0, context0);
            else
                return currentResultCode;
        }, callback, context);
    }

    @Override
    public void onConnected() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onConnected();
            }
        }
    }

    private ResultCode getResult(HashMap<String, Pair<TrackerComponent, ResultCode>> components) {
        ResultCode res = ResultCode.RESULT_OK;
        for (Pair<TrackerComponent,ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_ERROR_FATAL) {
                // can't get any worse than this
                return ResultCode.RESULT_ERROR_FATAL;
            } else if (pair.second == ResultCode.RESULT_ERROR) {
                res = ResultCode.RESULT_ERROR;
            }
        }
        return res;
    }

    /**
     * Called by Tracker before start
     *   Component shall populate bindValues
     *   with objects that will then be passed
     *   to workout
     */
    @Override
    public void onBind(HashMap<String, Object> bindValues) {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onBind(bindValues);
            }
        }
    }

    /**
     * Called by Tracker when workout starts
     */
    @Override
    public void onStart() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onStart();
            }
        }
    }

    /**
     * Called by Tracker when workout is paused
     */
    @Override
    public void onPause() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onPause();
            }
        }
    }

    /**
     * Called by Tracker when workout is resumed
     */
    @Override
    public void onResume() {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onResume();
            }
        }
    }

    /**
     * Called by Tracker when workout is complete
     */
    @Override
    public void onComplete(boolean discarded) {
        for (Pair<TrackerComponent, ResultCode> pair : components.values()) {
            if (pair.second == ResultCode.RESULT_OK) {
                pair.first.onComplete(discarded);
            }
        }
    }

    /**
     * Called by tracked after workout has ended
     */
    @Override
    public ResultCode onEnd(final Callback callback, Context context) {
        return forEach("onEnd", (comp0, currentResultCode, callback0, context0) -> {
            // Ignore current result code, always run onEnd()
            return comp0.onEnd(callback0, context0);
        }, callback, context);
    }

    private interface Func1 {
        ResultCode apply(TrackerComponent component, ResultCode currentResultCode,
                         Callback callback, Context context);
    }

    private ResultCode forEach(final String msg, final Func1 func, final Callback callback,
                               Context context) {
        synchronized (components) {
            HashMap<String, Pair<TrackerComponent, ResultCode>> list =
                    new HashMap<>(components);
            components.clear();

            for (TrackerComponent component : pending.values()) {
                list.put(component.getName(), new Pair<>(component, ResultCode.RESULT_PENDING));
            }
            pending.clear();

            for (final String key : list.keySet()) {
                final Pair<TrackerComponent, ResultCode> p = list.get(key);
                final TrackerComponent component = p.first;
                final ResultCode currentResultCode = p.second;
                ResultCode res = func.apply(component, currentResultCode, (component1, resultCode) -> handler.post(() -> {
                    synchronized (components) {
                        Log.e(getName(), component1.getName() + " " + msg + " => " + resultCode);
                        if (!pending.containsKey(key))
                            return;
                        TrackerComponent check = pending.remove(key);
                        if (BuildConfig.DEBUG && check != component1) {
                            Log.e(getName(), component1.getName() + " != " + check.getName());
                            throw new AssertionError();
                        }
                        components.put(key, new Pair<>(
                                component1, resultCode));
                        if (pending.isEmpty()) {
                            Log.e(getName(), " => runCallback()");
                            callback.run(TrackerComponentCollection.this,
                                    getResult(components));
                        }
                    }
                }), context);
                Log.e(getName(), component.getName() + " " + msg + " => " + res);
                if (res != ResultCode.RESULT_PENDING) {
                    components.put(key, new Pair<>(component, res));
                } else {
                    pending.put(key, component);
                }
            }
        }
        if (!pending.isEmpty())
            return ResultCode.RESULT_PENDING;
        else {
            Log.e(getName(), " => return directly");
            return getResult(components);
        }
    }
}
