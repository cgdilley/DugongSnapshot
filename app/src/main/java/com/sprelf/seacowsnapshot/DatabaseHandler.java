package com.sprelf.seacowsnapshot;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Chris on 08.09.2015.
 */
public class DatabaseHandler extends SQLiteOpenHelper
{

    final static String TABLE_NAME = "image_data";
    final static String PIC_PATH = "pic_path";
    final static String TIME = "data_time";
    final static String LATITUDE = "data_latitude";
    final static String LONGITUDE = "data_longitude";
    final static String[] COLUMNS = { PIC_PATH, TIME, LATITUDE, LONGITUDE };

    final private static String NAME = "image_db";
    final private static Integer VERSION = 1;
    final private Context mContext;

    public DatabaseHandler(Context context) {
        super(context, NAME, null, VERSION);
        this.mContext = context;
    }

    final private static String CREATE_CMD_DATA =

            "CREATE TABLE " + TABLE_NAME + " ("
            + PIC_PATH + " TEXT PRIMARY KEY, "
            + TIME + " TEXT, "
            + LATITUDE + " FLOAT, "
            + LONGITUDE + " FLOAT)";

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_CMD_DATA);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO Auto-generated method stub

    }
}
