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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.runnerup.R;
import org.runnerup.workout.WorkoutSerializer;

import java.io.File;


class WorkoutListAdapter extends BaseAdapter {

    /**
	 * 
	 */
    private final LayoutInflater inflater;
    private String[] workoutList = new String[0];

    public WorkoutListAdapter(LayoutInflater inflater) {
        super();
        this.inflater = inflater;
    }

    @Override
    public int getCount() {
        return workoutList.length + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position < workoutList.length)
            return workoutList[position];

        Context context = inflater.getContext();
        return String.format(context.getString(R.string.dialog_ellipsis), context.getString(R.string.Manage_workouts));
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
        String[] list = load(inflater.getContext());
        if (list == null) {
            workoutList = new String[0];
        } else {
            workoutList = new String[list.length];
            int index = 0;
            for (String s : list) {
                workoutList[index++] = s.substring(0, s.lastIndexOf('.'));
            }
        }
        this.notifyDataSetChanged();
    }

    public static String[] load(Context ctx) {
        File f = ctx.getDir(WorkoutSerializer.WORKOUTS_DIR, 0);
        return f.list((dir, filename) -> filename.endsWith(".json"));
    }
}
