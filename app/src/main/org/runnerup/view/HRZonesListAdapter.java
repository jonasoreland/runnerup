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

package org.runnerup.view;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.runnerup.util.HRZones;


class HRZonesListAdapter extends BaseAdapter {

    /**
	 * 
	 */
    final HRZones hrZones;
    private final LayoutInflater inflater;

    public HRZonesListAdapter(Context ctx, LayoutInflater inflater) {
        super();
        this.inflater = inflater;
        this.hrZones = new HRZones(ctx);
    }

    @Override
    public int getCount() {
        return hrZones.getCount();
    }

    private String lastString = null;
    private int lastPosition = -1;

    @Override
    public Object getItem(int position) {
        if (position == lastPosition)
            return lastString;

        if (position < hrZones.getCount()) {
            Pair<Integer, Integer> val = hrZones.getHRValues(position + 1);
            String str = "Zone " + (position + 1) + " (" + val.first + " - " + val.second + ")";

            lastPosition = position;
            lastString = str;

            return str;
        }

        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent,
                    false);
        }

        TextView ret = convertView.findViewById(android.R.id.text1);
        Object obj = getItem(position);
        if (obj != null)
            ret.setText(obj.toString());
        else
            ret.setText("???");

        return convertView;
    }

    public void reload() {
        lastPosition = -1;
        hrZones.reload();
    }
}
