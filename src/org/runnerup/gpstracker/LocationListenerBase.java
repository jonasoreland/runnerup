package org.runnerup.gpstracker;

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

	private java.util.LinkedList<LocationListener> mClients = new java.util.LinkedList<LocationListener>();

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
