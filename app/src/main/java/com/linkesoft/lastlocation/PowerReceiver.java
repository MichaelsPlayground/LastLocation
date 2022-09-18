package com.linkesoft.lastlocation;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class PowerReceiver extends BroadcastReceiver  {
    private final int LOCATION_JOB_ID = 1;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(getClass().getSimpleName(), "power disconnected broadcast "+isConnected(context));
        // onReceive always runs on main thread
        Toast.makeText(context,R.string.powerDisconnected,Toast.LENGTH_SHORT).show();
        // start a job to get current location
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo.Builder builder = new JobInfo.Builder(LOCATION_JOB_ID, new ComponentName(context, LocationJobService.class));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            builder.setExpedited(true);
        else
            builder.setOverrideDeadline(0);
        jobScheduler.schedule(builder.build());
        // alternative if only a few seconds needed
        //pendingResult = goAsync();
    }

    public static boolean isConnected(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1); // 0 means on battery (disconnected)
        Log.v("PowerReceiver","plugged "+plugged);
        return plugged > 0;
    }
}