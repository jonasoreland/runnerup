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
package org.runnerup.view;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import org.runnerup.common.util.Constants;
import org.runnerup.tracker.component.HeadsetButtonReceiver;


public class StartActivityHeadsetButtonReceiver extends HeadsetButtonReceiver {

    public static void registerHeadsetListener(Context context) {
        registerHeadsetListener(context, StartActivityHeadsetButtonReceiver.class);
    }

    public static void unregisterHeadsetListener(Context context) {
        unregisterHeadsetListener(context, StartActivityHeadsetButtonReceiver.class);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent
                    .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (KeyEvent.ACTION_DOWN == event.getAction()) {
                Intent startBroadcastIntent = new Intent()
                        .setAction(Constants.Intents.START_ACTIVITY);
                context.sendBroadcast(startBroadcastIntent);
            }
        }
    }
}
