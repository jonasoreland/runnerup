/*
 * Copyright (C) 2012 jonas.oreland@gmail.com
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

package org.runnerup.tracker;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
/**
 * Base class for writing chained LocationListener(s)
 * 
 * @author jonas
 * 
 */


public class LocationListenerBase implements LocationListener {

    private final java.util.LinkedList<LocationListener> mClients = new java.util.LinkedList<>();

    public void register(LocationListener l) {
        synchronized (mClients) {
            mClients.add(l);
        }
    }

    public void unregister(LocationListener l) {
        synchronized (mClients) {
            mClients.remove(l);
        }
    }

    @Override
    public void onLocationChanged(Location arg0) {
        synchronized (mClients) {
            for (LocationListener g : mClients) {
                g.onLocationChanged(arg0);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        synchronized (mClients) {
            for (LocationListener g : mClients) {
                g.onProviderDisabled(provider);
            }
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        synchronized (mClients) {
            for (LocationListener g : mClients) {
                g.onProviderEnabled(provider);
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        synchronized (mClients) {
            for (LocationListener g : mClients) {
                g.onProviderEnabled(provider);
            }
        }
    }
}
