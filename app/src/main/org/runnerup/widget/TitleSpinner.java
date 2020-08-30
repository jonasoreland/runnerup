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
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.runnerup.R;

public class TitleSpinner extends LinearLayout implements SpinnerInterface {
    final SpinnerPresenter mPresenter;
    final LinearLayout mLayout;
    final TextView mLabel;
    final TextView mValue;
    final Spinner mSpinner;

    public TitleSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.title_spinner, this);

        mLayout = findViewById(R.id.title_spinner_layout);
        mLabel = findViewById(R.id.title);
        mValue = findViewById(R.id.value);
        mSpinner = findViewById(R.id.spinner);
        mSpinner.setSaveEnabled(false);

        mPresenter = new SpinnerPresenter(context, attrs, this);
    }

    @Override
    public void setOnClickSpinnerOpen() {
        setViewOnClickListener(view -> mSpinner.performClick());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mLayout.setEnabled(enabled);
        mSpinner.setEnabled(enabled);
    }

    @Override
    public void setViewPrompt(CharSequence charSequence) {
        mSpinner.setPrompt(charSequence);
    }

    @Override
    public void setViewLabel(CharSequence label) {
        mLabel.setText(label);
    }

    @Override
    public void setViewValue(int itemId) {
        Object val = mSpinner.getAdapter().getItem(itemId);
        if (val != null)
            setViewText(val.toString());
        else
            setViewText("");
    }

    @Override
    public void setViewText(CharSequence charSequence) {
        mValue.setText(charSequence);
    }

    @Override
    public CharSequence getViewValueText() {
        return mValue.getText();
    }

    @Override
    public void setViewOnClickListener(OnClickListener onClickListener) {
        mLayout.setOnClickListener(onClickListener);
    }

    @Override
    public void setViewAdapter(DisabledEntriesAdapter adapter) {
        mSpinner.setAdapter(adapter);
    }

    @Override
    public SpinnerAdapter getViewAdapter() {
        return mSpinner.getAdapter();
    }

    @Override
    public void setViewSelection(int value) {
        mSpinner.setSelection(value);
    }

    @Override
    public void viewOnClose(OnCloseDialogListener listener, boolean b) {
        listener.onClose(this, b);
    }

    @Override
    public void setViewOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
        mSpinner.setOnItemSelectedListener(listener);
    }

    public void setAdapter(SpinnerAdapter adapter) {
        mSpinner.setAdapter(adapter);
        mPresenter.loadValue(null);
    }

    public void setValue(int value) {
        mPresenter.setValue(value);
    }

    public void setValue(String value) {
        mPresenter.setValue(value);
    }

    public CharSequence getValue() {
        return mPresenter.getValue();
    }

    public int getValueInt() {
        return mPresenter.getValueInt();
    }

    public void addDisabledValue(int value) {
        int selection = mPresenter.getSelectionValue(value);
        ((DisabledEntriesAdapter)mSpinner.getAdapter()).addDisabled(selection);
    }

    public void clearDisabled() {
        ((DisabledEntriesAdapter)mSpinner.getAdapter()).clearDisabled();
    }

    public void clear() {
        mPresenter.clear();
    }

    public void setOnSetValueListener(SpinnerInterface.OnSetValueListener listener) {
        mPresenter.setOnSetValueListener(listener);
    }

    public void setOnCloseDialogListener(SpinnerInterface.OnCloseDialogListener listener) {
        mPresenter.setOnCloseDialogListener(listener);
    }
}