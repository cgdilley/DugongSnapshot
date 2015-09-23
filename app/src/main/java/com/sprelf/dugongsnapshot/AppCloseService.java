package com.sprelf.dugongsnapshot;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AppCloseService extends Service
{
    public AppCloseService()
    {
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onTaskRemoved(Intent intent)
    {
        DugongSnapshot.stopTrackingService(this);
    }
}
