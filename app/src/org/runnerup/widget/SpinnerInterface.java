package org.runnerup.widget;

import android.view.View;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;

/**
 * @author Miroslav Mazel
 */
public interface SpinnerInterface {
    void setViewPrompt(CharSequence charSequence);
    void setViewLabel(CharSequence charSequence);
    void setViewValue(int itemId);
    void setViewText(CharSequence charSequence);
    CharSequence getViewValueText();
    void setViewOnClickListener(View.OnClickListener onClickListener);
    void setViewAdapter(DisabledEntriesAdapter adapter);
    SpinnerAdapter getViewAdapter();
    void setViewSelection(int value);
    void viewOnClose(OnCloseDialogListener listener, boolean b);
    void setViewOnItemSelectedListener(AdapterView.OnItemSelectedListener listener);
    void setOnClickSpinnerOpen();

    interface OnCloseDialogListener {
        void onClose(SpinnerInterface spinner, boolean ok);
    }

    interface OnSetValueListener {
        /**
         * @param newValue
         * @return
         * @throws java.lang.IllegalArgumentException
         */
        String preSetValue(String newValue) throws java.lang.IllegalArgumentException;

        /**
         * @param newValue
         * @return
         * @throws java.lang.IllegalArgumentException
         */
        int preSetValue(int newValue) throws java.lang.IllegalArgumentException;
    }
}
