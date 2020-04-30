/*
 * Copyright (C) 2014 weides@gmail.com
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

import android.graphics.drawable.AnimationDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.wearable.view.CircledImageView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import org.runnerup.R;

//
public class SearchingFragment extends Fragment {

    public SearchingFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.searching, container, false);
        super.onViewCreated(view, savedInstanceState);

        if (Build.VERSION.SDK_INT >= 23) {
            CircledImageView button = (CircledImageView) view.findViewById(R.id.icon_searching);
            AnimationDrawable frameAnimation = (AnimationDrawable) button.getForeground();
            frameAnimation.start();
        }

        return view;
    }

    //@Override
    //public void onAttach(Activity activity) {
    //    super.onAttach(activity);
    //    //MainActivity activity1 = (MainActivity) activity;
    //}
}
