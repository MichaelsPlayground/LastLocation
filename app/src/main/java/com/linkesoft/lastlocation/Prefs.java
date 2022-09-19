package com.linkesoft.lastlocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.util.Date;

/**
 * Wrapper for storing and retrieving shared preferenses
 */
public class Prefs {
    private final static String TIMESTAMP = "TIMESTAMP";
    private final static String LATITUDE = "LATITUDE";
    private final static String LONGITUDE = "LONGITUDE";

    public static String formattedLastPowerTimeStamp(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Date date = new Date(prefs.getLong(TIMESTAMP,0));
        return DateFormat.getDateTimeInstance().format(date);
    }
    public static @Nullable Location lastPowerLocation(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        double latitude = prefs.getFloat(LATITUDE,0);
        double longitude = prefs.getFloat(LONGITUDE,0);
        if(latitude == 0 && longitude == 0)
            return null;
        Location location = new Location("");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setTime(prefs.getLong(TIMESTAMP,0));
        return location;
    }
    public static void setLastPowerLocation(Context context, @Nullable  Location location) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if(location==null)
            prefs.edit().remove(LATITUDE).apply();
        else
            prefs.edit().putFloat(LATITUDE,(float)location.getLatitude()).putFloat(LONGITUDE,(float)location.getLongitude())
                    .putLong(TIMESTAMP,new Date().getTime())
                    .apply();
    }
}
