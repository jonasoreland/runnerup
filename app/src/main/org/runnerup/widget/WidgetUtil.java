/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
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

package org.runnerup.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import org.runnerup.R;


public class WidgetUtil {

    public static void setEditable(EditText editText, boolean onoff) {
        editText.setClickable(onoff);
        editText.setFocusable(onoff);
        if (onoff) {
            //noinspection ConstantConditions
            editText.setFocusableInTouchMode(onoff);
        }
    }

    public static View createHoloTabIndicator(Context ctx, String title) {
        Resources res = ctx.getResources(); // Resource object to get Drawables
        TextView txtTab = new TextView(ctx);
        txtTab.setText(title);
        //txtTab.setTextColor(Color.WHITE);
        //txtTab.setGravity(Gravity.CENTER_HORIZONTAL);
        Drawable drawable = res.getDrawable(R.drawable.tab_indicator_holo);
        WidgetUtil.setBackground(txtTab, drawable);

        int h = (25 * drawable.getIntrinsicHeight()) / 10;
        txtTab.setPadding(0, h, 0, h);
        // txtTab.setHeight(1 + 10 * drawable.getIntrinsicHeight());
        // txtTab.setLineSpacing(1 + 5 * drawable.getIntrinsicHeight(), 1);
        return txtTab;
    }

    @SuppressWarnings("deprecation")
    
    public static void setBackground(View v, Drawable d) {
        if (Build.VERSION.SDK_INT < 16) {
            v.setBackgroundDrawable(d);
        } else {
            v.setBackground(d);
        }
    }

    public static void addLegacyOverflowButton(Window window) {
        if (window.peekDecorView() == null) {
            return;
        }

        try {
            //noinspection JavaReflectionMemberAccess
            window.addFlags(WindowManager.LayoutParams.class.getField("FLAG_NEEDS_MENU_KEY").getInt(null));
        } catch (NoSuchFieldException e) {
            // Ignore since this field won't exist in most versions of Android
        } catch (IllegalAccessException e) {
        }
    }
}
