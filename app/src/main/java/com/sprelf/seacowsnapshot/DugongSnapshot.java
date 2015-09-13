package com.sprelf.seacowsnapshot;

import android.app.Application;

import com.kii.cloud.storage.Kii;

import java.text.SimpleDateFormat;

/**
 * Created by Chris on 13.09.2015.
 */
public class DugongSnapshot extends Application
{
    public static String SHAREDPREFS_FILE = "sharedPrefs";
    public static String ACCESSTOKEN_KEY = "token";
    public static String DATA_BUCKET = "DataPoints";

    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    @Override
    public void onCreate()
    {
        super.onCreate();



        // Initialize Kii
        Kii.initialize("1c4763d1", "1c4c1479ec42ae226c735f3cff8906bc", Kii.Site.SG);


    }
}
