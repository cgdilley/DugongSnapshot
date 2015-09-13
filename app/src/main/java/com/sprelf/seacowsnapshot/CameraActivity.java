package com.sprelf.seacowsnapshot;

import android.app.Activity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.sprelf.seacowsnapshot.CustomViews.CameraPreview;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class CameraActivity extends Activity
{

    private Camera camera;
    private int cameraId = 0;
    private CameraPreview preview;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private SQLiteDatabase mDb;
    private DatabaseHandler mDbHelper;

    private static int GPS_POLLING_FREQ = 200;  // in milliseconds
    private static float GPS_ACCURACY = 100.0f;  // in meters
    private static int GPS_TIMEOUT = 10000;     // in milliseconds

    private static int INTERNET_POLLING_FREQ = 30 * 60;  // in seconds

    private static int NULL_GPS = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);


        // Initialize database access
        mDbHelper = new DatabaseHandler(this);
        mDb = mDbHelper.getWritableDatabase();

        // Initialize location manager (GPS)
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Start background service for uploading data
        startUpdateService(this);

        // Test for camera
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        {
            Log.e("[Camera]", "No camera on this device");
        }
        else
        {
            // Find the ID of the rear-facing camera.
            cameraId = findCamera();
            if (cameraId < 0)
            {
                Log.e("[Camera]", "No rear-facing camera.");
            }
            else
            {
                // Open the camera and start the camera preview
                camera = Camera.open(cameraId);
                generatePreview();
            }
        }
    }

    @Override
    protected void onPause()
    {
        // Suspend the camera when the activity is paused
        if (camera != null)
        {
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    /** OnClick method for the shutter, redirecting to appropriate code.
     *
     * @param view Reference to View that was clicked
     */
    public void onShutterClick(View view)
    {
        takePicture();
    }

    /** Handles all actions associate with taking a picture with the camera, including collection
     * of GPS and time data and saving to SQL database.
     *
     */
    private void takePicture()
    {
        // Get picture name and snap picture
        final String picPath = PhotoHandler.getDir(this).getPath() + File.separator
                               + DugongSnapshot.DATE_FORMAT.format(new Date());
        camera.takePicture(null, null, new PhotoHandler(getApplicationContext(), picPath));

        // Get current time
        final Calendar time = Calendar.getInstance();

        // Get GPS Coordinates
        final Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setAccuracy(NULL_GPS);
        locationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location newLocation)
            {
                location.set(newLocation);
                if (location.getAccuracy() <= GPS_ACCURACY)
                {
                    addDataEntry(picPath, time, location);
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
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_POLLING_FREQ, 0,
                                               locationListener);

        // Delayed runnable to kill GPS if it can't resolve.
        Executors.newScheduledThreadPool(1).schedule(new Runnable()
        {
            @Override
            public void run()
            {
                if (location.getAccuracy() == NULL_GPS)
                {
                    locationManager.removeUpdates(locationListener);
                    Log.d("[GPS]", "Could not resolve location.");
                    addDataEntry(picPath, time, null);
                }
                else if (location.getAccuracy() > GPS_ACCURACY)
                {
                    locationManager.removeUpdates(locationListener);
                    Log.d("[GPS]", "Could not get accurate measurement.  Settled for "
                                   + Float.toString(location.getAccuracy()) + "m.");
                    addDataEntry(picPath, time, location);
                }
            }
        }, GPS_TIMEOUT, TimeUnit.MILLISECONDS);

    }


    /** Adds values into SQL database.
     *
     * @param picPath Path of the saved picture
     * @param time Time the picture was taken
     * @param location All location information associated with the picture
     */
    private void addDataEntry(String picPath, Calendar time, Location location)
    {
        ContentValues vals = new ContentValues();

        String reportString;

        // Format the time as a String for storage
        String timeString = DugongSnapshot.DATE_FORMAT.format(time.getTime());

        vals.put(DatabaseHandler.PIC_PATH, picPath);
        vals.put(DatabaseHandler.TIME, timeString);
        vals.put(DatabaseHandler.SUBMITTED, 0);

        // Location can be null if GPS could not resolve a location.  If not null, format and store
        //  the location data
        if (location != null)
        {
            double lati = location.getLatitude();
            double longi = location.getLongitude();

            vals.put(DatabaseHandler.LATITUDE, lati);
            vals.put(DatabaseHandler.LONGITUDE, longi);

            reportString = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    .format(time.getTime())) + " - [" + Double.toString(lati) + "][" +
                           Double.toString(longi) + "]";
        }
        else  // If location was null, submit without GPS data
        {
            reportString = (new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    .format(time.getTime())) + " - [NO GPS DATA]";
        }

        // Insert the data into the database
        mDb.replace(DatabaseHandler.TABLE_NAME, null, vals);


        Log.d("[Data]", "Data saved.\n" + reportString);
        Toast.makeText(getApplicationContext(), reportString, Toast.LENGTH_LONG).show();


    }

    /** Identifies the rear-facing camera and returns the camera's ID.
     *
     * @return ID of the rear-facing camera.  Returns -1 if no such camera exists.
     */
    private int findCamera()
    {
        int cameraId = -1;
        // Find rear-facing camera
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++)
        {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
            {
                Log.d("[Camera]", "Camera found");
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    /** Initializes the preview View in the layout for displaying what the camera sees.
     *
     */
    private void generatePreview()
    {
        Camera.Parameters camParams = camera.getParameters();
        camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        camera.setParameters(camParams);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.RootLayout);
        preview = new CameraPreview(this);
        preview.setCamera(camera);
        layout.addView(preview, params);
    }

    public static void startUpdateService(Context context) {
        Intent updateIntent = new Intent(context, UploadReceiver.class);
        PendingIntent pendingUpdateIntent = PendingIntent.getBroadcast(context, 0, updateIntent,
                                                                       PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Calendar time = Calendar.getInstance();
        time.add(Calendar.SECOND, INTERNET_POLLING_FREQ);
        //manager.set(AlarmManager.RTC, time.getTimeInMillis(), pendingUpdateIntent);
        manager.setRepeating(AlarmManager.RTC, time.getTimeInMillis(), INTERNET_POLLING_FREQ * 1000,
                             pendingUpdateIntent);
        Log.d("[Upload]", "Upload service started.");
    }


}
