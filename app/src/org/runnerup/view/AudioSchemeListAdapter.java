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

import android.annotation.TargetApi;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.FROYO)
class AudioSchemeListAdapter extends BaseAdapter {

    /**
	 * 
	 */
    LayoutInflater inflater = null;
    SQLiteDatabase mDB = null;
    boolean createNewItem = true;
    final ArrayList<String> audioSchemes = new ArrayList<String>();

    public AudioSchemeListAdapter(SQLiteDatabase db, LayoutInflater inflater, boolean createNew) {
        super();
        this.mDB = db;
        this.inflater = inflater;
        this.createNewItem = createNew;
    }

    @Override
    public int getCount() {
        return audioSchemes.size() +
                /** default */
                1 +
                (createNewItem ? 1 : 0);
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return inflater.getContext().getString(R.string.Default);
        }

        position -= 1;

        if (position < audioSchemes.size())
            return audioSchemes.get(position);

        return inflater.getContext().getString(R.string.New_audio_scheme);
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

        TextView ret = (TextView) convertView.findViewById(android.R.id.text1);
        ret.setText(getItem(position).toString());
        return ret;
    }

    public int find(String name) {
        for (int i = 0; i < getCount(); i++) {
            if (name.contentEquals(getItem(i).toString()))
                return i;
        }
        return 0;
    }

    public void reload() {
        audioSchemes.clear();
        String[] from = new String[] {
            DB.AUDIO_SCHEMES.NAME
        };

        Cursor c = mDB.query(DB.AUDIO_SCHEMES.TABLE, from, null, null, null, null,
                DB.AUDIO_SCHEMES.SORT_ORDER + " desc");
        if (c.moveToFirst()) {
            do {
                audioSchemes.add(c.getString(0));
            } while (c.moveToNext());
        }
        c.close();
        this.notifyDataSetChanged();
    }
}
