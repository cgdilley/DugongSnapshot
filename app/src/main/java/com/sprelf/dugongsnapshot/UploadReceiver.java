package com.sprelf.dugongsnapshot;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.kii.cloud.storage.GeoPoint;
import com.kii.cloud.storage.Kii;
import com.kii.cloud.storage.KiiObject;
import com.kii.cloud.storage.KiiUser;
import com.kii.cloud.storage.UserFields;
import com.kii.cloud.storage.callback.KiiObjectCallBack;
import com.kii.cloud.storage.exception.app.AppException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;

public class UploadReceiver extends BroadcastReceiver
{
    public static String TIME_KEY = "time";
    public static String PIC_KEY = "image";
    public static String GEOPOINT_KEY = "geopoint";
    public static String USER_KEY = "user";

    public static int UPLOAD_IMAGE_MAX_WIDTH = 800;  // in pixels
    public static int UPLOAD_IMAGE_MAX_HEIGHT = 450; // in pixels
    public static int UPLOAD_IMAGE_MAX_SIZE = 45000;  // in bytes

    private KiiUser user;
    private SQLiteDatabase mDb;
    private DatabaseHandler mDbHelper;

    private Context context;

    public UploadReceiver()
    {
    }

    @Override
    public void onReceive(Context cont, Intent intent)
    {

        this.context = cont;

        // Initialize database access
        mDbHelper = new DatabaseHandler(context);
        mDb = mDbHelper.getWritableDatabase();

        // Test for internet connectivity
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        // If we are connected to internet, do all uploading.  Otherwise do nothing.
        if (info != null && info.isConnected())
        {
            Log.d("[Upload]", "Internet detected.");

            (new AsyncTask<String, String, String>()
            {
                @Override
                protected String doInBackground(String... arg0)
                {

                    // Retrieve all database elements that need to be submitted
                    Cursor dataCursor = mDb.query(DatabaseHandler.TABLE_NAME, DatabaseHandler.COLUMNS,
                                                  DatabaseHandler.SUBMITTED + " = '0'",
                                                  null, null, null, null);
                    Cursor trackCursor = mDb.query(DatabaseHandler.TRACK_TABLE_NAME,
                                                   DatabaseHandler.TRACK_COLUMNS,
                                                   null, null, null, null, null);

                    // If there are pending submissions, submit them.
                    if (dataCursor.getCount() > 0 || trackCursor.getCount() > 0)
                    {
                        Log.d("[Upload]", "Found " + dataCursor.getCount() + " data entries"
                                          + " and " + trackCursor.getCount() + " tracking entries to upload.");
                        performNetworkActivities(dataCursor, trackCursor);
                    }
                    else
                    {
                        Log.d("[Upload]", "No entries to upload.");
                    }
                    return null;
                }
            }).execute();
        }
        else
        {
            Log.d("[Upload]", "No internet available.  Disregarding upload.");
        }


    }

    /**
     * Handles uploading of all data based by the Cursor to the Kii database.
     *
     * @param dataCursor Cursor containing data to upload
     */
    private void performNetworkActivities(Cursor dataCursor, Cursor trackCursor)
    {

        Log.d("[Upload]", "Starting Kii connection and upload...");

        // Initialize Kii
        Kii.initialize(DugongSnapshot.KII_APPID, DugongSnapshot.KII_APPKEY,
                       DugongSnapshot.KII_SITE);

        // Get access token for this device's login
        SharedPreferences settings = context
                .getSharedPreferences(DugongSnapshot.SHAREDPREFS_FILE, 0);
        String accessToken = settings.getString(DugongSnapshot.ACCESSTOKEN_KEY, null);

        // If no such token exists, create a new one and store it.
        if (accessToken == null)
        {
            try
            {
                user = KiiUser.registerAsPseudoUser(new UserFields());
                accessToken = user.getAccessToken();
                Log.d("[Kii]", "Logged in as new user.\nAccess token = " + accessToken
                               + "\nUser ID = " + user.getID());

                SharedPreferences.Editor editor = settings.edit();
                editor.putString(DugongSnapshot.ACCESSTOKEN_KEY, accessToken);
                editor.commit();
            } catch (IOException e)
            {
                Log.e("[Kii]", e.getMessage());
                e.printStackTrace();
            } catch (AppException e)
            {
                Log.e("[Kii]", e.getMessage());
                e.printStackTrace();
            }
        }
        else // If the token exists, log in using it
        {
            try
            {
                KiiUser.loginWithToken(accessToken);
                user = KiiUser.getCurrentUser();
                Log.d("[Kii]", "Logged in as existing user.\nAccess token = " + accessToken
                               + "\nUser ID = " + user.getID());

            } catch (IOException e)
            {
                Log.e("[Kii]", e.getMessage());
                e.printStackTrace();
            } catch (AppException e)
            {
                Log.e("[Kii]", e.getMessage());
                e.printStackTrace();
            }

        }


        // Iterate through all results
        while (dataCursor.moveToNext())
        {
            KiiObject object = Kii.bucket(DugongSnapshot.DATA_BUCKET).object();

            final String picPath = dataCursor.getString(dataCursor.getColumnIndex(DatabaseHandler.PIC_PATH));
            String time = dataCursor.getString(dataCursor.getColumnIndex(DatabaseHandler.TIME));
            float lati = dataCursor.getFloat(dataCursor.getColumnIndex(DatabaseHandler.LATITUDE));
            float longi = dataCursor.getFloat(dataCursor.getColumnIndex(DatabaseHandler.LONGITUDE));
            GeoPoint geoPoint = new GeoPoint(lati, longi);

            // Break down the date and time data into a JSON object with fields representing
            //  the different denominations of time
            Calendar calendar = Calendar.getInstance();
            try
            {
                calendar.setTime(DugongSnapshot.DATE_FORMAT.parse(time));

                JSONObject timeObject = new JSONObject();
                timeObject.put("year", calendar.get(Calendar.YEAR));
                timeObject.put("month", calendar.get(Calendar.MONTH));
                timeObject.put("day", calendar.get(Calendar.DAY_OF_MONTH));
                timeObject.put("hour", calendar.get(Calendar.HOUR_OF_DAY));
                timeObject.put("minute", calendar.get(Calendar.MINUTE));
                timeObject.put("second", calendar.get(Calendar.SECOND));

                object.set(TIME_KEY, timeObject);
            } catch (ParseException e)
            {
                Log.e("[Data]", e.getMessage());
                e.printStackTrace();
            } catch (JSONException e)
            {
                Log.e("[Data]", e.getMessage());
                e.printStackTrace();
            }

            object.set(GEOPOINT_KEY, geoPoint);
            object.set(USER_KEY, user.getID());

            // Load the image, convert it to PNG, and store
            //  the byte array.
            try
            {
                // Open the base image.
                File file = new File(picPath);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(new FileInputStream(file), null, options);

                // Resize the image as needed to fit in require width
                int sampleSize = options.outWidth / UPLOAD_IMAGE_MAX_WIDTH;
                if (sampleSize < 1) { sampleSize = 1; }
                options.inSampleSize = sampleSize;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file), null, options);

                // Find the maximum quality that will fit inside the maximum file size by trying
                //  lower and lower quality until it fits.
                int compressStep = 10;  // Larger amount means faster, but less precise
                int compressQuality = 100 + compressStep;
                int streamLength = UPLOAD_IMAGE_MAX_SIZE;
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                while (streamLength >= UPLOAD_IMAGE_MAX_SIZE && compressQuality > compressStep)
                {
                    try
                    {
                        stream.flush();
                        stream.reset();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                    compressQuality -= compressStep;
                    bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, stream);
                    byte[] byteArray = stream.toByteArray();
                    streamLength = byteArray.length;
                }

                if (compressQuality > 0)
                {
                    Log.d("[Image Rescaling]", "Image scaled to " + streamLength + " bytes, " +
                                               "at quality " + compressQuality);
                    object.set(PIC_KEY, stream.toByteArray());
                }
                else
                {
                    Log.d("[Image Rescaling]", "Unable to scale image to appropriate size.");
                }

                // Save the object, and if successful, report success and mark data point as submitted.
                object.save(new KiiObjectCallBack()
                {
                    @Override
                    public void onSaveCompleted(int token, KiiObject object, Exception exception)
                    {
                        if (exception != null)
                        {
                            Log.e("[Data]", exception.getMessage());
                            exception.printStackTrace();
                        }
                        else
                        {
                            // Mark entry in database as having been submitted.
                            ContentValues val = new ContentValues();
                            val.put(DatabaseHandler.SUBMITTED, 1);
                            mDb.update(DatabaseHandler.TABLE_NAME, val, DatabaseHandler.PIC_PATH
                                                                        + " = '" + picPath + "'", null);
                            Log.d("[Upload]", "Image data upload successful.\n" + picPath);
                        }
                    }
                });

            } catch (FileNotFoundException e)
            {
                Log.e("[Data]", e.getMessage());
            } catch (IOException e)
            {
                Log.e("[Data]", e.getMessage());
            }
        }

        while (trackCursor.moveToNext())
        {
            KiiObject object = Kii.bucket(DugongSnapshot.TRACKING_BUCKET).object();

            final String time = trackCursor.getString(trackCursor.getColumnIndex(
                    DatabaseHandler.TRACK_TIME));
            float lati = trackCursor.getFloat(trackCursor.getColumnIndex(
                    DatabaseHandler.TRACK_LATITUDE));
            float longi = trackCursor.getFloat(trackCursor.getColumnIndex(
                    DatabaseHandler.TRACK_LONGITUDE));
            GeoPoint geoPoint = new GeoPoint(lati, longi);

            // Break down the date and time data into a JSON object with fields representing
            //  the different denominations of time
            Calendar calendar = Calendar.getInstance();
            try
            {
                calendar.setTime(DugongSnapshot.DATE_FORMAT.parse(time));

                JSONObject timeObject = new JSONObject();
                timeObject.put("year", calendar.get(Calendar.YEAR));
                timeObject.put("month", calendar.get(Calendar.MONTH));
                timeObject.put("day", calendar.get(Calendar.DAY_OF_MONTH));
                timeObject.put("hour", calendar.get(Calendar.HOUR_OF_DAY));
                timeObject.put("minute", calendar.get(Calendar.MINUTE));
                timeObject.put("second", calendar.get(Calendar.SECOND));

                object.set(TIME_KEY, timeObject);
            } catch (ParseException e)
            {
                Log.e("[Data]", e.getMessage());
                e.printStackTrace();
            } catch (JSONException e)
            {
                Log.e("[Data]", e.getMessage());
                e.printStackTrace();
            }

            object.set(GEOPOINT_KEY, geoPoint);
            object.set(USER_KEY, user.getID());

            // Save the object, and if successful, report success and delete tracking entry.
            object.save(new KiiObjectCallBack()
            {
                @Override
                public void onSaveCompleted(int token, KiiObject object, Exception exception)
                {
                    if (exception != null)
                    {
                        Log.e("[Upload]", exception.getMessage());
                        exception.printStackTrace();
                    }
                    else
                    {
                        // Delete tracking entry in database
                        mDb.delete(DatabaseHandler.TRACK_TABLE_NAME,
                                   DatabaseHandler.TRACK_TIME + " = " + time,
                                   null);

                        Log.d("[Upload]", "Tracking data upload successful.");
                    }
                }
            });
        }


    }
}

