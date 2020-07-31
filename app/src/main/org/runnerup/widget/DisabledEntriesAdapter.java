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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.HashSet;


class DisabledEntriesAdapter extends BaseAdapter {

    private final String[] entries;
    private final LayoutInflater inflator;
    private HashSet<String> disabled;

    public DisabledEntriesAdapter(Context ctx, int id) {
        inflator = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        entries = ctx.getResources().getStringArray(id);
    }

    public void addDisabled(int i) {
        if (disabled == null)
            disabled = new HashSet<>();
        if (i < entries.length)
            disabled.add(entries[i]);
    }

    public void clearDisabled() {
        if (disabled != null)
            disabled.clear();
    }

    @Override
    public int getCount() {
        return entries.length;
    }

    @Override
    public Object getItem(int position) {
        if (position < entries.length)
            return entries[position];
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String str = (String) getItem(position);
        if (convertView == null) {
            convertView = inflator.inflate(android.R.layout.simple_spinner_dropdown_item, parent,
                    false);
        }
        TextView ret = convertView.findViewById(android.R.id.text1);
        ret.setText(str);

        if (disabled != null && disabled.contains(str))
            convertView.setEnabled(false);
        else
            convertView.setEnabled(true);

        return convertView;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return disabled == null || disabled.size() == 0;
    }

    @Override
    public boolean isEnabled(int position) {
        if (disabled == null)
            return true;

        String str = (String) getItem(position);
        if (str == null)
            return true;

        return !disabled.contains(str);
    }
}
