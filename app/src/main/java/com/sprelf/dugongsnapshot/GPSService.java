package com.sprelf.dugongsnapshot;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GPSService extends Service
{
    private LocationListener locationListener;
    private LocationManager locationManager;

    private String picPath;
    private String time;

    public GPSService()
    {
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    /** Handles polling for GPS location in the background, and then logs all data once polling
     * concludes.
     *
     * @param intent Intent passed to this object, contains picPath and time as String extras.
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        // Initialize location manager (GPS)
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Retrieve pic path and time data
        picPath = intent.getStringExtra("picPath");
        time = intent.getStringExtra("time");


        final Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setAccuracy(DugongSnapshot.NULL_GPS);
        locationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location newLocation)
            {
                location.set(newLocation);
                if (location.getAccuracy() <= DugongSnapshot.GPS_ACCURACY)
                {
                    DugongSnapshot.addDataEntry(getApplicationContext(), picPath, time, location);
                    locationManager.removeUpdates(locationListener);
                    stopSelf();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras)
            {

            }

            @Override
            public void onProviderEnabled(String provider)
            {

            }

            @Override
            public void onProviderDisabled(String provider)
            {

            }
        };

        // Start polling for location updates and attach the above listener
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                               DugongSnapshot.GPS_POLLING_FREQ,
                                               0,
                                               locationListener);

        // Delayed runnable to kill GPS if it can't resolve.
        Executors.newScheduledThreadPool(1).schedule(new Runnable()
        {
            @Override
            public void run()
            {
                if (location.getAccuracy() == DugongSnapshot.NULL_GPS)
                {
                    locationManager.removeUpdates(locationListener);
                    Log.d("[GPS]", "Could not resolve location.");
                    DugongSnapshot.addDataEntry(getApplicationContext(), picPath, time, null);
                    stopSelf();
                }
                else if (location.getAccuracy() > DugongSnapshot.GPS_ACCURACY)
                {
                    locationManager.removeUpdates(locationListener);
                    Log.d("[GPS]", "Could not get accurate measurement.  Settled for "
                                   + Float.toString(location.getAccuracy()) + "m.");
                    DugongSnapshot.addDataEntry(getApplicationContext(), picPath, time, location);
                    stopSelf();
                }
            }
        }, DugongSnapshot.GPS_TIMEOUT, TimeUnit.MILLISECONDS);

        return START_STICKY;
    }
}
