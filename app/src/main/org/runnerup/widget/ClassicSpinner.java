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
import android.util.AttributeSet;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;

import androidx.appcompat.widget.AppCompatSpinner;

public class ClassicSpinner extends AppCompatSpinner implements SpinnerInterface {
    final SpinnerPresenter mPresenter;

    public ClassicSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPresenter = new SpinnerPresenter(context, attrs, this);
    }

    @Override
    public void setViewPrompt(CharSequence charSequence) {
        setPrompt(charSequence);
    }

    @Override
    public void setViewLabel(CharSequence label) {
        setContentDescription(label);
    }

    @Override
    public void setViewValue(int itemId) {
        setSelection(itemId);
    }

    @Override
    public void setViewText(CharSequence charSequence) { }
    @Override
    public CharSequence getViewValueText() {
        return getSelectedItem().toString();
    }

    @Override
    public void setViewOnClickListener(OnClickListener onClickListener) {
         setOnClickListener(onClickListener);
    }

    @Override
    public void setOnClickSpinnerOpen() {}

    @Override
    public void setViewAdapter(DisabledEntriesAdapter adapter) {
        setAdapter(adapter);
    }

    @Override
    public SpinnerAdapter getViewAdapter() {
        return getAdapter();
    }

    @Override
    public void setViewSelection(int value) {
        setSelection(value);
    }

    @Override
    public void viewOnClose(OnCloseDialogListener listener, boolean b) {
        listener.onClose(this, b);
    }

    @Override
    public void setViewOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        setOnItemSelectedListener(listener);
    }
}

