package com.sprelf.seacowsnapshot;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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

    private static int GPS_POLLING_FREQ = 100;  // in milliseconds
    private static float GPS_ACCURACY = 10.0f;  // in meters
    private static int GPS_TIMEOUT = 30000;     // in milliseconds
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddhhmmss");

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mDbHelper = new DatabaseHandler(this);
        mDb = mDbHelper.getWritableDatabase();

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Test for camera
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))
        {
            Log.e("[Camera]", "No camera on this device");
        }
        else
        {
            cameraId = findCamera();
            if (cameraId < 0)
            {
                Log.e("[Camera]", "No rear-facing camera.");
            }
            else
            {
                camera = Camera.open(cameraId);
                generatePreview();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config)
    {
        super.onConfigurationChanged(config);
        adjustCameraOrientation();
    }

    public void onShutterClick(View view)
    {
        takePicture();
    }

    private void takePicture()
    {
        // Get picture name and snap picture
        final String picPath = PhotoHandler.getDir(this).getPath() + File.separator
                               + DATE_FORMAT.format(new Date());
        camera.takePicture(null, null, new PhotoHandler(getApplicationContext(), picPath));

        // Get current time
        final Calendar time = Calendar.getInstance();

        // Get GPS Coordinates
        locationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location)
            {
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

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_POLLING_FREQ, 0,
                                               locationListener);

        // Delayed runnable to kill GPS if it can't resolve.
        Executors.newScheduledThreadPool(1).schedule(new Runnable()
        {
            @Override
            public void run()
            {
                locationManager.removeUpdates(locationListener);
                Log.d("[GPS]", "Could not resolve location.");
            }
        }, GPS_TIMEOUT, TimeUnit.MILLISECONDS);


    }

    private void addDataEntry(String picPath, Calendar time, Location location)
    {
        ContentValues vals = new ContentValues();

        double lat = location.getLatitude();
        double longi = location.getLongitude();
        String timeString = DATE_FORMAT.format(time.getTime());

        vals.put(DatabaseHandler.PIC_PATH, picPath);
        vals.put(DatabaseHandler.TIME, timeString);
        vals.put(DatabaseHandler.LATITUDE, lat);
        vals.put(DatabaseHandler.LONGITUDE, longi);
        mDb.replace(DatabaseHandler.TABLE_NAME, null, vals);

        Log.d("[Data]", (new SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
                .format(time.getTime())) + " - [" + Double.toString(lat) + "][" +
                        Double.toString(longi) + "]");

        Toast.makeText(getApplicationContext(), (new SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
                .format(time.getTime())) + " - [" + Double.toString(lat) + "][" +
                Double.toString(longi)+"]", Toast.LENGTH_SHORT).show();


    }

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

    private void generatePreview()
    {
        adjustCameraOrientation();

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

    private void adjustCameraOrientation()
    {
        /*
        Camera.CameraInfo info = new Camera.CameraInfo();

        Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
        case Surface.ROTATION_0: degrees = 0; break;
        case Surface.ROTATION_90: degrees = 90; break;
        case Surface.ROTATION_180: degrees = 180; break;
        case Surface.ROTATION_270: degrees = 270; break;
        }

        camera.setDisplayOrientation((info.orientation - degrees + 360)%360);
        */
    }

    @Override
    protected void onPause()
    {
        if (camera != null)
        {
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        super.onPause();
    }
}
