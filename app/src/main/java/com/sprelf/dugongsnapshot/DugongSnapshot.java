package com.sprelf.dugongsnapshot;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.kii.cloud.storage.Kii;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Chris on 13.09.2015.
 */
public class DugongSnapshot extends Application
{
    public static String SHAREDPREFS_FILE = "sharedPrefs";
    public static String ACCESSTOKEN_KEY = "token";
    public static String DATA_BUCKET = "DataPoints";
    public static String TRACKING_BUCKET = "TrackingPoints";

    public static String KII_APPID = "1c4763d1";
    public static String KII_APPKEY = "1c4c1479ec42ae226c735f3cff8906bc";
    public static Kii.Site KII_SITE = Kii.Site.SG;

    public static int GPS_POLLING_FREQ = 200;  // in milliseconds
    public static float GPS_ACCURACY = 100.0f;  // in meters
    public static int GPS_TIMEOUT = 10000;     // in milliseconds
    public static int NULL_GPS = -1;

    private static int INTERNET_POLLING_FREQ = 30 * 60;  // in seconds
    private static int TRACKING_POLL_FREQ = 30 * 60;     // in seconds
    private static int PENDINGINTENT_UPLOAD_ID = 1;
    private static int PENDINGINTENT_TRACK_ID = 2;

    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public void onCreate()
    {
        super.onCreate();


        // Initialize Kii
        Kii.initialize(KII_APPID, KII_APPKEY, KII_SITE);


    }


    /**
     * Starts the alarm service for periodically attempting to upload any data in the database.
     *
     * @param context Context within which to perform the operation.
     */
    public static void startUpdateService(Context context)
    {
        Intent updateIntent = new Intent(context, UploadReceiver.class);
        PendingIntent pendingUpdateIntent = PendingIntent
                .getBroadcast(context,
                              PENDINGINTENT_UPLOAD_ID,
                              updateIntent,
                              PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar time = Calendar.getInstance();
        //time.add(Calendar.SECOND, INTERNET_POLLING_FREQ);

        // Creates an alarm that fires immediately, and then repeating at set intervals.
        manager.setRepeating(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(),
                             INTERNET_POLLING_FREQ * 1000,
                             pendingUpdateIntent);
        Log.d("[Upload]", "Upload service started.");
    }

    /**
     * Starts the alarm service for periodically attempting to poll for current GPS location.
     *
     * @param context Context within which to perform the operation.
     */
    public static void startTrackingService(Context context)
    {
        Intent updateIntent = new Intent(context, TrackingReceiver.class);
        PendingIntent pendingUpdateIntent = PendingIntent
                .getBroadcast(context,
                              PENDINGINTENT_TRACK_ID,
                              updateIntent,
                              PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar time = Calendar.getInstance();

        // Creates an alarm that fires immediately, and then repeating at set intervals.
        manager.setRepeating(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(),
                             TRACKING_POLL_FREQ * 1000,
                             pendingUpdateIntent);
        Log.d("[Tracking]", "Tracking service started.");
    }

    /**
     * Identifies and returns the appropriate file storage location for pictures.
     *
     * @param context Context to use as basis for finding file directory
     * @return File object referring to the picture directory
     */
    public static File getDir(Context context)
    {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath());
        return new File(dir, "DugongSnapshot");
    }

    /**
     * Adds values into SQL database.
     *
     * @param context  Context within which to perform the operation
     * @param picPath  Path of the saved picture
     * @param time     Time the picture was taken,
     *                 as a string formatted by DugongSnapshot.DATE_FORMAT
     * @param location All location information associated with the picture
     */
    public static void addDataEntry(Context context, String picPath, String time, Location location)
    {
        // Initialize database access
        DatabaseHandler mDbHelper = new DatabaseHandler(context);
        SQLiteDatabase mDb = mDbHelper.getWritableDatabase();

        ContentValues vals = new ContentValues();

        String reportString;

        vals.put(DatabaseHandler.PIC_PATH, picPath);
        vals.put(DatabaseHandler.TIME, time);
        vals.put(DatabaseHandler.SUBMITTED, 0);

        try
        {
            Date date = DugongSnapshot.DATE_FORMAT.parse(time);


            // Location can be null if GPS could not resolve a location.  If not null, format and store
            //  the location data
            if (location != null)
            {
                double lati = location.getLatitude();
                double longi = location.getLongitude();

                vals.put(DatabaseHandler.LATITUDE, lati);
                vals.put(DatabaseHandler.LONGITUDE, longi);

                reportString = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                        .format(date.getTime())) + " - [" + Double.toString(lati) + "][" +
                               Double.toString(longi) + "]";
            }
            else  // If location was null, submit without GPS data
            {
                reportString = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                        .format(date.getTime())) + " - [NO GPS DATA]";
            }

            // Insert the data into the database
            mDb.replace(DatabaseHandler.TABLE_NAME, null, vals);


            Log.d("[Data]", "Data saved. (" + reportString + ")");
            Toast.makeText(context, reportString, Toast.LENGTH_LONG).show();

        } catch (ParseException e) { e.printStackTrace(); }

    }
}
