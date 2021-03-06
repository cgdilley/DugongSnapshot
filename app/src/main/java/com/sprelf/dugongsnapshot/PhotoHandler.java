package com.sprelf.dugongsnapshot;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by Chris on 08.09.2015.
 */
public class PhotoHandler implements Camera.PictureCallback
{

    private final Context context;
    private String picPath;

    public PhotoHandler(Context context, String picPath)
    {
        this.context = context;
        this.picPath = picPath;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera)
    {
        // Immediately restart the preview for the camera
        camera.startPreview();

        // Get location of where to save the picture
        File pictureFileDir = DugongSnapshot.getDir(context);

        // Test for I/O errors
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs())
        {
            Log.d("[Image]", "Can't create directory to save image");
            return;
        }

        // Generate the new picture file
        File file = new File(picPath);

        try {
            // Save the picture data to file, and report success/failure
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
            Log.d("[Image]", "File " + picPath + " was saved.");
        } catch (Exception e) {
            Log.d("[Image]", "File " + picPath + " could not be saved - " + e.getMessage());
        }


    }


}
