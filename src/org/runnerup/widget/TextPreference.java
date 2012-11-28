package org.runnerup.widget;

import android.content.Context;
import android.util.AttributeSet;

public class TextPreference extends android.preference.EditTextPreference {

	public TextPreference(Context context) {
		super(context);
	}

	public TextPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TextPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue,
			Object defaultValue) {
		super.onSetInitialValue(restorePersistedValue, defaultValue);
		super.setSummary(super.getPersistedString(""));
	}

	@Override
	protected void onDialogClosed(boolean ok) {
		super.onDialogClosed(ok);
		if (ok) {
			super.setSummary(super.getPersistedString(""));
		}
	}
};
