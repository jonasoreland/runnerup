/*
 * Copyright (C) 2015 jonas.oreland@gmail.com
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
package org.runnerup.util;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.app.NotificationCompat.Builder;

/**
 * Created by jonas on 2/15/15.
 */
public class NotificationCompat {

    public static void setLocalOnly(Builder builder) {
        if (Build.VERSION.SDK_INT >= 20) {
            builder.setLocalOnly(true);
        }
    }
}
