package org.runnerup.workout;

/**
 * This is just constant
 */
public enum Speed {

	SPEED(1), // meters per seconds, "base unit", i.e one returned by getSpeed()
				// or similar
	SPEED_KPH(2), // kilometer per hour
	SPEED_MPH(3), // miles per hour
	PACE_SPK(4), // seconds per kilometer;
	PACE_MPM(5); // minutes per mile

	int value = 0;

	private Speed(int val) {
		this.value = val;
	}

	/**
	 * @return the scopeValue
	 */
	public int getValue() {
		return value;
	}

	public boolean equals(Speed what) {
		if (what == null || what.value != this.value)
			return false;
		return true;
	}

	public static double convert(double val, Speed to) {
		if (to == Speed.SPEED)
			return val;

		if (val == 0)
			return 0;

		switch (to) {
		case SPEED:
			return val;
		case SPEED_KPH:
			return 3.6 * val;
		case SPEED_MPH:
			return val * 2.23693629;
		case PACE_SPK:
			return 1000.0 / val;
		case PACE_MPM:
			return 26.882 / val;
		}
		return 0;
	}
}
