package com.sprelf.dugongsnapshot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver
{
    public BootReceiver()
    {
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d("[Boot]", "Boot completed broadcast received.");
        // Called when the phone boots.  Start all necessary services
        DugongSnapshot.startUpdateService(context);
    }
}
