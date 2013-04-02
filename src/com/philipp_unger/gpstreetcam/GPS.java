package com.philipp_unger.gpstreetcam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

@SuppressLint("SimpleDateFormat")
public class GPS {

    // location manager
    private LocationManager lManager;

    // listener
    private LocationListener lListener;

    // saves last location
    private Location lastLocation;

    // update at least every x seconds
    private static int UPDATE_INTERVAL = 1;

    // update at least every x meters
    private static int UPDATE_DIST = 0;

    private boolean recording = false;

    private Context context;
    private TextView lon;
    private TextView lat;
    private TextView speed;
    private String timeStamp;
    private File root;
    private File file;
    private String waypoints;
    private String trackpoints = "<trk>\n" + "<trkseg>\n";

    public GPS(Context context, TextView lat, TextView lon, TextView speed) {
        this.context = context;
        this.lat = lat;
        this.lon = lon;
        this.speed = speed;
    }

    /**
     * Startup GPS Tracking
     */
    public void init() {
        // init location Updater
        lManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        lListener = new GPSLocListener();

        lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL * 1000,
                UPDATE_DIST, lListener);
        saveLocation(lManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));

        writeXML();
    }

    /**
     * Remove the current LocationListener
     */
    public void stop() {
        lManager.removeUpdates(lListener);
        trackpoints = trackpoints + ("</trkseg>\n" + "</trk>\n" + "</gpx>");
        write(waypoints);
        write(trackpoints);
    }


    private void writeXML() {

        timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        root = Environment.getExternalStorageDirectory();
        file = new File(root, timeStamp + "GPSlog.gpx");
        write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n "
                + "<gpx version=\"1.1\" creator=\"GPStreetCam\">\n ");

    }

    private void write(String string) {

        try {
            if (root.canWrite()) {
                FileWriter filewriter = new FileWriter(file, true);
                BufferedWriter out = new BufferedWriter(filewriter);

                out.write(string);

                out.close();

            }
        } catch (IOException e) {
            Log.e("TAG", "Could not write file " + e.getMessage());
        }

    }

    /**
     * Location manager class
     */
    private final class GPSLocListener implements LocationListener {

        public void onLocationChanged(Location location) {
            saveLocation(location);

            lon.setText("" + location.getLongitude());
            lat.setText("" + location.getLatitude());
            speed.setText("" + Math.round(location.getSpeed() * 3.6) + " km/h");
            
            if (recording) {
                trackpoints = trackpoints
                        + ("<trkpt lat=\"" + location.getLatitude() + "\" lon=\""
                                + location.getLongitude() + "\"></trkpt> \n");
            }

        }

        public void onProviderDisabled(String provider) {
            //
        }

        public void onProviderEnabled(String provider) {
            //
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            //
        }

    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    /**
     * Save a location
     */
    private void saveLocation(Location l) {
        lastLocation = l;
    }

    /**
     * Returns last known location
     */
    public Location getLocation() {
        return lastLocation;
    }

}
