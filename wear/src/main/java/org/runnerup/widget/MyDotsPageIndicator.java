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
package org.runnerup.widget;

import android.annotation.TargetApi;
import android.graphics.Point;
import android.os.Build;
import android.support.wearable.view.GridPagerAdapter;
import android.support.wearable.view.GridViewPager;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;

import org.runnerup.R;

/**
 * Created by jonas on 12/27/14.
 */
@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class MyDotsPageIndicator implements GridViewPager.OnPageChangeListener, GridViewPager.OnAdapterChangeListener {

    // margin, size
    private Point unselected = new Point(4,2);
    private Point selected = new Point(6,1);

    private LinearLayout layout;
    private GridViewPager pager;

    public MyDotsPageIndicator(LinearLayout layout) {
        this.layout = layout;
    }

    public void setPager(GridViewPager pager) {
        this.pager = pager;
        this.onDataSetChanged();
        this.onPageSelected(0, 0);
    }

    @Override
    public void onPageScrolled(int i, int i2, float v, float v2, int i3, int i4) {

    }

    @Override
    public void onPageSelected(int row, int col) {
        for (int i = 0; i < layout.getChildCount(); i++)
            configDot((Button) layout.getChildAt(i), false);
        if (row < layout.getChildCount())
            configDot((Button) layout.getChildAt(row), true);
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    public void onAdapterChanged(GridPagerAdapter gridPagerAdapter, GridPagerAdapter gridPagerAdapter2) {

    }

    @Override
    public void onDataSetChanged() {
        layout.removeAllViews();

        /* skip dot for only 1 row */
        if (pager.getAdapter().getRowCount() <= 1)
            return;

        for (int i = 0; i < pager.getAdapter().getRowCount(); i++) {
            Button b = new Button(layout.getContext());
            layout.addView(configDot(b, false));
        }
    }

    private Button configDot(Button btn, boolean selected) {
        btn.setBackgroundResource(R.drawable.dot);
        Point measures = selected ? this.selected : this.unselected;
        int size = getPxFromDp(measures.x);
        int margin = getPxFromDp(measures.y);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
        if (layout.getOrientation() == LinearLayout.VERTICAL) {
            p.gravity = Gravity.CENTER_HORIZONTAL;
            p.setMargins(0, margin, 0, margin);
        } else {
            p.gravity = Gravity.CENTER_VERTICAL;
            p.setMargins(margin, 0, margin, 0);
        }
        btn.setLayoutParams(p);
        return btn;
    }

    private int getPxFromDp(int dp){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                layout.getResources().getDisplayMetrics());
    }
}
