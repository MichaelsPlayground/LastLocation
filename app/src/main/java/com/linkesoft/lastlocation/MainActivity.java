package com.linkesoft.lastlocation;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.linkesoft.lastlocation.databinding.ActivityMainBinding;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.location.GeocoderNominatim;
import org.osmdroid.bonuspack.location.NominatimPOIProvider;
import org.osmdroid.bonuspack.location.POI;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public final static GeoPoint geoPointHeise = new GeoPoint(52.3859132, 9.8089183); // heise Verlag Hannover
    private final double initialZoom = 12;

    protected static final String LOCATION_SAVED_NOTIFICATION = "com.linkesoft.lastlocation.LOCATION_SAVED_NOTIFICATION"; // app internal broadcast
    private final int PERMISSIONS_REQUEST = 1;

    private ActivityMainBinding binding;
    private MapView mapView;
    private @Nullable Marker lastPowerLocationMarker;
    private MyLocationNewOverlay currentLocationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSM map default configuration, user agent etc.
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mapView = binding.mapView;

        mapView.setMultiTouchControls(true); // pinch to zoom
        // center on heise Verlag until we know the current location
        IMapController mapController = mapView.getController();
        mapController.setZoom(initialZoom);
        mapController.setCenter(new GeoPoint(geoPointHeise));
        showSatImages();
    }
    @Override
    protected void onResume() {
        super.onResume();
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(!isLocationEnabled())
            Toast.makeText(this,"You need to enable location services to be able to use this app",Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermissions();
        }
        refreshOverlays();
        mapView.onResume(); // resume map updates
        LocalBroadcastManager.getInstance(this).registerReceiver(locationSavedBroadcastReceiver, new IntentFilter(LOCATION_SAVED_NOTIFICATION));
        checkBackgroundLocationAccess();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationSavedBroadcastReceiver);
        mapView.onPause(); // pause map updates
    }

    boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isLocationEnabled();
        } else {
            // This was deprecated in API 28
            int mode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }
    void showSatImages() {
        String[] urlArray = {"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"};
        mapView.setTileSource(new OnlineTileSourceBase("ARCGisOnline", 0, 18, 256, "", urlArray) {
            @Override
            public String getTileURLString(long tileIndex) {
                return getBaseUrl() + MapTileIndex.getZoom(tileIndex) + "/"
                        + MapTileIndex.getY(tileIndex) + "/" + MapTileIndex.getX(tileIndex)
                        + ".png";
            }
        });
    }

    void checkBackgroundLocationAccess() {
        // on Android 10 and higher, user needs to allow location access "All the time" in Android App Settings.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if(checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!= PackageManager.PERMISSION_GRANTED) {
                CharSequence backgroundPermissionOptionLabel = getString(R.string.backgroundPermissionOptionLabel);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    backgroundPermissionOptionLabel = getPackageManager().getBackgroundPermissionOptionLabel();
                Toast.makeText(this,getString(R.string.needBackgroundAccess,backgroundPermissionOptionLabel), Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void refreshOverlays() {
        mapView.getOverlays().clear();

        // Maßstab in lower right
        ScaleBarOverlay scaleBar = new ScaleBarOverlay(mapView);
        scaleBar.setAlignBottom(true);
        scaleBar.setAlignRight(false);
        mapView.getOverlays().add(scaleBar);

        // mini map in lower right
        MinimapOverlay minimap = new MinimapOverlay(this, mapView.getTileRequestCompleteHandler());
        //DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        //minimap.setWidth(displayMetrics.widthPixels/5);
        //minimap.setHeight(displayMetrics.heightPixels/5);
        minimap.setZoomDifference(5);
        mapView.getOverlays().add(minimap);

        showMarkerWithText(geoPointHeise,"heise Verlag");

        showCurrentLocation();

        showLastPowerLocation();
    }

    private void showCurrentLocation() {
        // get current location with updates
        currentLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        currentLocationOverlay.setDrawAccuracyEnabled(true);
        Bitmap meBitmap = ((BitmapDrawable) ContextCompat.getDrawable (this,R.drawable.me)).getBitmap();
        currentLocationOverlay.setPersonIcon(meBitmap);
        // zoom on current location first time we know it
        currentLocationOverlay.runOnFirstFix(() -> {
            GeoPoint currentPoint = new GeoPoint(currentLocationOverlay.getLastFix());
            Location lastPowerLocation = Prefs.lastPowerLocation(this);
            if(lastPowerLocation != null) {
                // zoom to include current and last location
                GeoPoint lastPoint = new GeoPoint(lastPowerLocation);
                if(lastPoint.distanceToAsDouble(currentPoint)>100) {
                    BoundingBox boundingBox = BoundingBox.fromGeoPoints(Arrays.asList(currentPoint, lastPoint)).increaseByScale(1.1f);
                    runOnUiThread(() -> mapView.zoomToBoundingBox(boundingBox, true));
                } else {
                    // very close, center map on current location
                    runOnUiThread(() -> mapView.getController().setCenter(currentPoint));
                }
            } else {
                // no last point, center map on current location
                runOnUiThread(() -> mapView.getController().setCenter(currentPoint));
            }
        });
        mapView.getOverlays().add(currentLocationOverlay);
    }

    private void showLastPowerLocation() {
        if(lastPowerLocationMarker !=null)
            mapView.getOverlays().remove(lastPowerLocationMarker);
        Location lastLocation = Prefs.lastPowerLocation(this);
        if(lastLocation != null) {
            GeoPoint point = new GeoPoint(lastLocation);
            lastPowerLocationMarker = showMarker(point);
            lastPowerLocationMarker.setIcon(ContextCompat.getDrawable (this,R.drawable.power));
            lastPowerLocationMarker.setSubDescription("" + Prefs.formattedLastPowerTimeStamp(this));
            lookupAddress(point, lastPowerLocationMarker);
            lastPowerLocationMarker.setOnMarkerClickListener((marker, mapView) -> {
                showLastLocationMenu(marker);
                return false;
            });
        }
    }

    private void showLastLocationMenu(Marker lastLocationMarker) {
        new AlertDialog.Builder(this).
                setTitle(lastLocationMarker.getTitle()).setMessage(lastLocationMarker.getSubDescription()).
                setPositiveButton(R.string.routeToHere, (dialogInterface, i) -> {
                    GeoPoint currentPoint = currentLocationOverlay.getMyLocation();
                    if(currentPoint != null)
                        showRoute(currentPoint, lastLocationMarker.getPosition());
                }).
                setNeutralButton(R.string.fastFootPOIs, (dialogInterface, i) -> showPOIs("fast_food")).create().show();
    }

    private void showMarkerWithText(GeoPoint point, String text) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        marker.setTextIcon(text);
        mapView.getOverlays().add(marker);
    }

    private Marker showMarker(GeoPoint point) {
        Marker marker = new Marker(mapView);
        marker.setPosition(point);
        mapView.getOverlays().add(marker);
        return marker;
    }

    private void lookupAddress(GeoPoint point, Marker marker) {
        final GeocoderNominatim geocoder = new GeocoderNominatim(Configuration.getInstance().getUserAgentValue());
        // don't make network calls on main
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Address> addresses = geocoder.getFromLocation(point.getLatitude(), point.getLongitude(), 1);
                    if (!addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        StringBuilder str = new StringBuilder();
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            str.append(address.getAddressLine(i)).append(", ");
                        }
                        Log.v(getClass().getSimpleName(),"Reverse geocoding for "+point+" is "+str);
                        marker.setTitle(str.toString());
                    }
                } catch (IOException e) {
                    Log.e(getClass().getSimpleName(),"Error reverse geocoding",e);
                }
            }
        });
        thread.start();
    }
    private void showPOIs(String poiName) {
        final NominatimPOIProvider poiProvider = new NominatimPOIProvider(Configuration.getInstance().getUserAgentValue());
        final int maxResults = 100;
        final BoundingBox boundingBox =  mapView.getBoundingBox();
        // don't make network calls on main
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<POI> pois = poiProvider.getPOIInside(boundingBox,poiName,maxResults);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for(POI poi:pois) {
                            Log.v(getClass().getSimpleName(), "POI " + poi);
                            Marker poiMarker = new Marker(mapView);
                            poiMarker.setSnippet(poi.mDescription); // name and address
                            poiMarker.setPosition(poi.mLocation);
                            mapView.getOverlays().add(poiMarker);
                        }
                    }
                });

             }
        });
        thread.start();
    }

    private void showRoute(GeoPoint from, GeoPoint to) {
        final OSRMRoadManager roadManager = new OSRMRoadManager(this, Configuration.getInstance().getUserAgentValue());
        roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT);
        final ArrayList<GeoPoint> waypoints = new ArrayList<>( Arrays.asList(from,to) );
        // don't make network calls on main
        Thread thread = new Thread(() -> {
            try  {
                Road road = roadManager.getRoad(waypoints);
                MainActivity.this.runOnUiThread(() -> {
                    Polyline roadOverlay = RoadManager.buildRoadOverlay(road);
                    roadOverlay.getOutlinePaint().setStrokeWidth(dp2px(8)); // thicker line
                    roadOverlay.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
                    mapView.getOverlays().add(roadOverlay);
                    // show length and duration
                    Toast.makeText(MainActivity.this,road.getLengthDurationText(MainActivity.this,-1),Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(),"Error routing",e);
            }
        });

        thread.start();
    }

    private final BroadcastReceiver locationSavedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(LOCATION_SAVED_NOTIFICATION)) {
                // update last location marker on map (if power disconnected while app is in foreground)
                Toast.makeText(context,R.string.locationSaved, Toast.LENGTH_SHORT).show();
                showLastPowerLocation();
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    void checkPermissions() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        ) {
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int result: grantResults) {
            if(result != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"This app requires location permissions to work",Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            // not used
        }
        return super.onOptionsItemSelected(item);
    }
     */
    private float dp2px(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

}