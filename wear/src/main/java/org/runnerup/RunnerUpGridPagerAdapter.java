/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.runnerup;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.support.wearable.view.CardFragment;
import android.support.wearable.view.FragmentGridPagerAdapter;
import android.support.wearable.view.ImageReference;
import android.view.Gravity;

public class RunnerUpGridPagerAdapter extends FragmentGridPagerAdapter {

    private final Context mContext;

    public RunnerUpGridPagerAdapter(Context ctx, FragmentManager fm) {
        super(fm);
        mContext = ctx;
    }

    static final int[] BG_IMAGES = new int[] {
            R.drawable.debug_background_1,
            R.drawable.debug_background_2,
            R.drawable.debug_background_3,
            R.drawable.debug_background_4,
            R.drawable.debug_background_5
    };

    /** A simple container for static data in each page */
    private static class Page {
        int titleRes;

        public Page(){}

        public Page(int titleRes){
            this.titleRes = titleRes;
        }
    }

    private final Page[][] PAGES = {
            {
                    new Page(R.string.stats_title_total),
                    new Page(),
            },
            {
                    new Page(R.string.stats_title_lap),
                    new Page(),
            },
            {
                    new Page(R.string.stats_title_interval),
                    new Page(),
            },

    };

    @Override
    public Fragment getFragment(int row, int col) {
        Page page = PAGES[row][col];
        String title = page.titleRes != 0 ? mContext.getString(page.titleRes) : null;
        CardFragment fragment;
        if(col == 0) {
            fragment = RunInformationCardFragment.create(title);
        } else {
            fragment = PauseResumeCardFragment.create(R.string.pause);
        }
        fragment.setCardGravity(Gravity.BOTTOM);
        fragment.setExpansionEnabled(false);
        fragment.setExpansionDirection(CardFragment.EXPAND_DOWN);
        fragment.setExpansionFactor(1.0f);
        return fragment;
    }

    @Override
    public ImageReference getBackground(int row, int column) {
        return ImageReference.forDrawable(BG_IMAGES[row % BG_IMAGES.length]);
    }

    @Override
    public int getRowCount() {
        return PAGES.length;
    }

    @Override
    public int getColumnCount(int rowNum) {
        return PAGES[rowNum].length;
    }
}
