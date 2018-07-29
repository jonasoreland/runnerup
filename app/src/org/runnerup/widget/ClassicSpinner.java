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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;

@TargetApi(Build.VERSION_CODES.FROYO)
public class ClassicSpinner extends AppCompatSpinner implements SpinnerInterface {
    SpinnerPresenter mPresenter;

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
    public void setViewValue(CharSequence charSequence) {
        if (getAdapter() != null) { //todo save adapter
            setSelection(((ArrayAdapter<CharSequence>)getAdapter()).getPosition(charSequence)); //todo get rid of cast
        }
    }
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
    public void setViewAdapter(SpinnerAdapter adapter) {
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

//package org.runnerup.widget;

//import android.annotation.TargetApi;
//import android.content.Context;
//import android.os.Build;
//import android.support.v7.widget.AppCompatSpinner;
//import android.util.AttributeSet;
//import android.widget.AdapterView;
//import android.widget.ArrayAdapter;
//import android.widget.SpinnerAdapter;
//
//@TargetApi(Build.VERSION_CODES.FROYO)
//public class ClassicSpinner extends AppCompatSpinner implements SpinnerInterface {
//    SpinnerPresenter mPresenter;
//
//    public ClassicSpinner(Context context, AttributeSet attrs) {
//        super(context, attrs);
//
//        mPresenter = new SpinnerPresenter(context, attrs, this);
//    }
//
//    @Override
//    public void performSpinnerClick() {
//        performClick();
//    }
//
//    @Override
//    public void setViewPrompt(CharSequence charSequence) {
//        setPrompt(charSequence);
//    }
//
//    @Override
//    public void setViewLabel(CharSequence label) {
//        setContentDescription(label);
//    }
//
//    @Override
//    public void setViewValue(CharSequence charSequence) {
//        if (getAdapter() != null) { //todo save adapter
//            setSelection(((ArrayAdapter<CharSequence>)getAdapter()).getPosition(charSequence));
//        }
//    }
//
//    @Override
//    public CharSequence getViewValueText() {
//        return getSelectedItem().toString();
//    }
//
//    @Override
//    public void setViewOnClickListener(OnClickListener onClickListener) {
//        setOnClickListener(onClickListener);
//    }
//
//    @Override
//    public void setViewAdapter(SpinnerAdapter adapter) {
//        setAdapter(adapter);
//    }
//
//    @Override
//    public SpinnerAdapter getViewAdapter() {
//        return getAdapter();
//    }
//
//    @Override
//    public void setViewSelection(int value) {
//        setSelection(value);
//    }
//
//    @Override
//    public void viewOnClose(OnCloseDialogListener listener, boolean b) {
//        listener.onClose(this, b);
//    }
//
//    @Override
//    public void setViewOnItemSelectedListener(AdapterView.OnItemSelectedListener listener) {
//        setOnItemSelectedListener(listener);
//    }
//
//    public void setAdapter(SpinnerAdapter adapter) {
//        setAdapter(adapter);
//        mPresenter.loadValue(null);
//    }
//
//    public void setValue(int value) {
//        mPresenter.setValue(value);
//    }
//
//    public void setValue(String value) {
//        mPresenter.setValue(value);
//    }
//
//    public CharSequence getValue() {
//        return mPresenter.getValue();
//    }
//
//    public int getValueInt() {
//        return mPresenter.getValueInt();
//    }
//
//    public void addDisabledValue(int value) {
//        int selection = mPresenter.getSelectionValue(value);
//        ((DisabledEntriesAdapter)getAdapter()).addDisabled(selection);
//    }
//
//    public void clearDisabled() {
//        ((DisabledEntriesAdapter)getAdapter()).clearDisabled();
//    }
//
//    public void clear() {
//        mPresenter.clear();
//    }
//
//    public void setOnSetValueListener(SpinnerInterface.OnSetValueListener listener) {
//        mPresenter.setOnSetValueListener(listener);
//    }
//
//    public void setOnCloseDialogListener(SpinnerInterface.OnCloseDialogListener listener) {
//        mPresenter.setOnCloseDialogListener(listener);
//    }
//} todo remove


//        import android.annotation.TargetApi;
//        import android.content.Context;
//        import android.os.Build;
//        import android.support.v7.widget.AppCompatSpinner;
//        import android.util.AttributeSet;
//        import android.widget.ArrayAdapter;
//        import android.widget.SpinnerAdapter;
//
//@TargetApi(Build.VERSION_CODES.FROYO)
//public class ClassicSpinner extends AppCompatSpinner implements SpinnerInterface {
//    SpinnerPresenter mPresenter;
//    ArrayAdapter<CharSequence> mAdapter = null;
//
//    public ClassicSpinner(Context context, AttributeSet attrs) {
//        super(context, attrs);
//
//        mPresenter = new SpinnerPresenter(context, attrs, this);
//    }
//
//    @Override
//    public void setViewPrompt(CharSequence charSequence) {
//        setPrompt(charSequence);
//    }
//
//    @Override
//    public void setViewLabel(CharSequence label) {
//        setContentDescription(label);
//    }
//
//    @Override
//    public void setViewValue(CharSequence charSequence) {
//        if (mAdapter != null) {
//            setSelection(mAdapter.getPosition(charSequence));
//        }
//    }
//
//    public void setAdapter(ArrayAdapter<CharSequence> adapter) {
//        mAdapter = adapter;
//        super.setAdapter(adapter);
//    }
//
//    @Override
//    public CharSequence getViewValueText() {
//        return getSelectedItem().toString();
//    }
//
//    @Override
//    public void setViewOnClickListener(OnClickListener onClickListener) {
//        this.setOnClickListener(onClickListener);
//    }
//
//    @Override
//    public void setViewAdapter(SpinnerAdapter adapter) {
//        setAdapter(adapter);
//    }
//
//    @Override
//    public SpinnerAdapter getViewAdapter() {
//        return getAdapter();
//    }
//
//    @Override
//    public void setViewSelection(int value) {
//        setSelection(value);
//    }
//
//    @Override
//    public void viewOnClose(OnCloseDialogListener listener, boolean b) {
//        listener.onClose(this, b);
//    }
//
//    @Override
//    public void setViewOnItemSelectedListener(OnItemSelectedListener listener) {
//        setOnItemSelectedListener(listener);
//    }
//
//    @Override
//    public void performSpinnerClick() {
//        performClick();
//    }
//}
//


