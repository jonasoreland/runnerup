/*
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
package org.runnerup.tracker;

import android.annotation.TargetApi;
import android.os.Build;

/**
* Created by jonas on 12/12/14.
*/
@TargetApi(Build.VERSION_CODES.FROYO)
public enum TrackerState {
    INIT,         // initial state
    INITIALIZING, // initializing components
    INITIALIZED,  // initialized, ready to start
    STARTED,      // Workout started
    PAUSED,       // Workout paused
    CLEANUP,      // Cleaning up components
    ERROR         // Components failed to initialize
}
