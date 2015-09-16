package com.sprelf.dugongsnapshot;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TrackingReceiver extends BroadcastReceiver
{

    private SQLiteDatabase mDb;
    private DatabaseHandler mDbHelper;

    private Context context;
    private LocationListener locationListener;
    private LocationManager locationManager;

    public TrackingReceiver()
    {

    }

    @Override
    public void onReceive(Context context, Intent intent)
    {

        Log.d("[Tracking]", "Starting GPS polling...");

        this.context = context;

        // Initialize location manager (GPS)
        locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);

        // Initialize database access
        mDbHelper = new DatabaseHandler(context);
        mDb = mDbHelper.getWritableDatabase();

        // Get GPS Coordinates
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
                    addDataEntry(location);
                    locationManager.removeUpdates(locationListener);
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
                    addDataEntry(null);
                }
                else if (location.getAccuracy() > DugongSnapshot.GPS_ACCURACY)
                {
                    locationManager.removeUpdates(locationListener);
                    Log.d("[GPS]", "Could not get accurate measurement.  Settled for "
                                   + Float.toString(location.getAccuracy()) + "m.");
                    addDataEntry(location);
                }
            }
        }, DugongSnapshot.GPS_TIMEOUT, TimeUnit.MILLISECONDS);

    }

    private void addDataEntry(Location location)
    {
        ContentValues val = new ContentValues();

        val.put(DatabaseHandler.TRACK_TIME, DugongSnapshot.DATE_FORMAT.format(new Date()));

        if (location != null)
        {
            double lati = location.getLatitude();
            double longi = location.getLongitude();

            val.put(DatabaseHandler.TRACK_LATITUDE, lati);
            val.put(DatabaseHandler.TRACK_LONGITUDE, longi);
            Log.d("[Tracking]", "Logged GPS location - [" + lati + ", " + longi + "]");
        }
        else
        {
            Log.d("[Tracking]", "Failed to log GPS location");
        }

        mDb.replace(DatabaseHandler.TRACK_TABLE_NAME, null, val);
    }
}
