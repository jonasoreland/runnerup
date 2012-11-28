package org.runnerup.workout;

import org.runnerup.R;

import android.content.Context;
import android.content.res.Resources;

/**
 * This is just constant
 */
public enum Scope {

	WORKOUT(1), ACTIVITY(2), LAP(3);

	int value = 0;

	private Scope(int val) {
		this.value = val;
	}

	/**
	 * @return the scopeValue
	 */
	public int getValue() {
		return value;
	}

	public boolean equal(Scope what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	public String getCue(Context ctx) {
		Resources res = ctx.getResources();
		switch (this) {
		case WORKOUT:
			return res.getString(R.string.workout);
		case ACTIVITY:
			return res.getString(R.string.activity);
		case LAP:
			return res.getString(R.string.lap);
		}
		return "";
	}
}
