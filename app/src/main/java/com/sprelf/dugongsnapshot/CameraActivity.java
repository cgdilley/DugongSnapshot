package com.sprelf.dugongsnapshot;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.sprelf.dugongsnapshot.CustomViews.CameraPreview;

import java.io.File;
import java.util.Date;


public class CameraActivity extends Activity
{

    private Camera camera;
    private int cameraId = 0;
    private CameraPreview preview;

    private long pictureTime = 0;

    private MediaPlayer mediaPlayer;

    private static long PICTURE_DELAY = 10000;  // in milliseconds



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);






        // Start background service for uploading data
        DugongSnapshot.startUpdateService(this);
        DugongSnapshot.startTrackingService(this);

    }

    @Override
    protected  void onResume()
    {
        super.onResume();

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
        if (mediaPlayer != null)
            mediaPlayer.release();

        super.onPause();
    }



    /**
     * OnClick method for the shutter, redirecting to appropriate code.
     *
     * @param view Reference to View that was clicked
     */
    public void onShutterClick(View view)
    {
        if (System.currentTimeMillis() - pictureTime >= PICTURE_DELAY)
        {
            takePicture();
            pictureTime = System.currentTimeMillis();
        }
    }

    /**
     * Handles all actions associate with taking a picture with the camera, including collection
     * of GPS and time data and saving to SQL database.
     */
    private void takePicture()
    {


        // Get picture name and snap picture
        final String picPath = DugongSnapshot.getDir(this).getPath() + File.separator
                               + DugongSnapshot.DATE_FORMAT.format(new Date()) + ".jpg";
        camera.takePicture(null, null, new PhotoHandler(getApplicationContext(), picPath));

        // Perform shutter press confirmation actions
        shutterClick();

        // Get current time
        String timeString = DugongSnapshot.DATE_FORMAT.format(new Date());

        // Start service for obtaining GPS location.  Data is submitted when this finalizes.
        Intent intent = new Intent(this, GPSService.class);
        intent.putExtra("picPath", picPath);
        intent.putExtra("time", timeString);
        startService(intent);
    }

    /** Performs all actions for providing confirmation feedback on press of the shutter.
     *
     */
    private void shutterClick()
    {
        // Play shutter click sound
        mediaPlayer = MediaPlayer.create(this, R.raw.camera_shutter_click_01);
        mediaPlayer.start();
    }




    /**
     * Identifies the rear-facing camera and returns the camera's ID.
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

    /**
     * Initializes the preview View in the layout for displaying what the camera sees.
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




}
