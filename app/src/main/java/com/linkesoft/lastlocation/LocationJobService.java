package com.linkesoft.lastlocation;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Date;

/**
 * A job service to get the current location. Requests location updates until accuracy reached.
 */
public class LocationJobService extends JobService implements LocationListener {
    private final double desiredAccuracyMeters = 30;
    private final long maxTime = 120; // maximum time to search for a location
    private LocationManager locationManager;
    private JobParameters jobParameters;
    private long startTime;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // runs on UI thread
        this.jobParameters = jobParameters;
        startTime = new Date().getTime();
        Toast.makeText(this,getString(R.string.getLocation), Toast.LENGTH_SHORT).show();
        startGettingLocation(); // runs in background
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.v(getClass().getSimpleName(), "current location job service cancelled");
        stopGettingLocationUpdates();
        return false; // we do not want to run this job again
    }

    @SuppressLint("MissingPermission")
    private void startGettingLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!locationManager.isLocationEnabled())
                Log.e(getClass().getSimpleName(), "Location not enabled");
        }
        // log all available providers, FUSED_PROVIDER would be best
        Log.v(getClass().getSimpleName(), "location providers " + locationManager.getAllProviders());
        // get best location provider for high accuracy
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String bestProvider = locationManager.getBestProvider(criteria, true);
        Log.e(getClass().getSimpleName(), "best location provider " + bestProvider);
        // try last know location first
        Location location = locationManager.getLastKnownLocation(bestProvider);
        // do we have fresh current location
        if (location != null && location.getTime() > new Date().getTime() - 100) {
            Log.v(getClass().getSimpleName(), "current location " + location);
            Prefs.setLastLocation(this, location);
            notifyMainActivity();
        }
        // start requesting async location updates, see onLocationChanged
        locationManager.requestLocationUpdates(bestProvider, 100, 0, this);
    }
    private void stopGettingLocationUpdates() {
        locationManager.removeUpdates(this);
    }

    private void notifyMainActivity() {
        // notify main activity (if it is running) to update UI
        final Intent intent = new Intent();
        intent.setAction(MainActivity.LOCATION_SERVICE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.v(getClass().getSimpleName(), "location " + location);
        Prefs.setLastLocation(this, location);
        notifyMainActivity();
        if (location.getAccuracy() < desiredAccuracyMeters) {
            Log.v(getClass().getSimpleName(), "location accuracy ok, finish");
            stopGettingLocationUpdates();
            jobFinished(jobParameters, false);
        } else {
            // check time since job started, finish it if no accurate location found
            if(new Date().getTime() > startTime + maxTime) {
                Log.e(getClass().getSimpleName(),"No accurate location found since "+new Date(startTime));
                stopGettingLocationUpdates();
                jobFinished(jobParameters, false);
            }
        }
    }
}
