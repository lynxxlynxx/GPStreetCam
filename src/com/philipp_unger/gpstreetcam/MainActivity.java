package com.philipp_unger.gpstreetcam;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.philipp_unger.gpstreetcam.R.layout;

@SuppressLint("SimpleDateFormat")
@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SensorListener {

    /**
     * Mobile Phone Camera.
     */
    private Camera mCamera;

    /**
     * The {@link CameraPreview} where the liveview of the camera is shown.
     */
    private CameraPreview mPreview;

    /**
     * The {@link MediaRecorder}.
     */
    private MediaRecorder mMediaRecorder;

    /**
     * The actual {@link layout} shown in the {@link View}. <blockquote> activity_main.xml = 0;
     * capture_video_and_gps.xml = 1; about.xml = 2; </blockquote>
     */
    private int actualLayer = 0;

    /**
     * <code>true</code> if the the camera is recording.
     */
    private boolean isRecording = false;

    /**
     * {@link LocationManager} for the GPS recorder.
     */
    private LocationManager lManager;

    /**
     * {@link LocationListener} for the GPS recorder.
     */
    private LocationListener lListener;

    /**
     * Update at least every <b>x</b> seconds.
     */
    private static int UPDATE_INTERVAL = 1;

    /**
     * Update at least every <b>x</b> meters.
     */
    private static int UPDATE_DIST = 0;

    /**
     * {@link TextView} for the longitude value.
     */
    private TextView longitude;

    /**
     * {@link TextView} fot the latitude value.
     */
    private TextView latitude;

    /**
     * {@link TextView} for the speed value.
     */
    private TextView speed;

    /**
     * Root file from the external storage.
     */
    private File root = null;

    /**
     * GPX file where the GPS log is written. Same name as Videofile.
     */
    private File GPXfile = null;

    /**
     * Save the Video here.
     */
    private File Mediafile = null;

    /**
     * Save your GPS trackpoints here as XML. For example:<blockquote>trackpoints = trackpoints +
     * ("<a><</a>trkpt lat=\"" + location.getLatitude() + "\" lon=\"" + location.getLongitude() +
     * "\"><a><</a>/trkpt> \n");</blockquote>
     */
    private String trackpoints = "";

    // private String waypoints = "";

    /**
     * Beginning of the GPX file.
     */
    private final String GPXFILE_BEGINNING = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n "
            + "<gpx version=\"1.1\" creator=\"GPStreetCam\">\n ";

    /**
     * Ending of the GPX file.
     */
    private final String GPXFILE_ENDING = "</gpx>";

    /**
     * Beginning of trackpoints XML.
     */
    private final String TRACKPOINTS_BEGINNING = "<trk>\n" + "<trkseg>\n";

    /**
     * Ending of trackpoints XML.
     */
    private final String TRACKPOINTS_ENDING = "</trkseg>\n" + "</trk>\n";

    /**
     * The capture {@link Button} shown in the capture_video_and_gps.xml layout
     */
    private Button captureButton;

    /**
     * {@link PowerManager} for the wakelock during recording
     */
    private PowerManager pManager;

    /**
     * Save the rotation angle of the {@link SensorListener} here.
     */
    private float degree = 0;

    /**
     * The {@link PowerManager.WakeLock}
     */
    private PowerManager.WakeLock wLock;

    /**
     * The {@link SensorManager}
     */
    private SensorManager sensorManager;

    /**
     * The {@link Integer} value of the SENSOR_ORIENTATION constant
     */
    private int orientationSensor = SensorManager.SENSOR_ORIENTATION;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // disable lock screen, because it's annoying
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    /**
     * Go back to the main menu screen.
     * 
     * @see android.app.Activity#onBackPressed()
     */
    @Override
    public void onBackPressed() {
        switch (actualLayer) {
        // camera layer
        case 1:
            if (!isRecording) {
                mCamera.release();
                actualLayer = 0;
                GPSstop();
                setContentView(R.layout.activity_main);
            }
            break;
        default:
            actualLayer = 0;
            setContentView(R.layout.activity_main);
            break;
        }
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this, orientationSensor);
        if (actualLayer == 1) {
            // if camera and gps is recording, stop recording and save files when going onPause.
            // I.e. when the power button is pressed. The power button event could not be prevented
            // with original firmware.
            if (isRecording) {
                stopRecording();
            }
            GPSstop(); // remove updates from gps listener
            releaseMediaRecorder(); // release MediaRecorder
            releaseCamera(); // release the camera
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        sensorManager.registerListener(this, orientationSensor);
        if (actualLayer == 1) {
            startRecorder(new View(this)); // restart the recorder
        }
        super.onResume();
    }

    /**
     * Get an instance of the {@link Camera} object.
     * 
     * @return an instance of the {@link Camera}
     */
    private static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    /**
     * Set actual content view to about layout.
     * 
     * @param view
     */
    public void aboutLayout(View view) {
        // set actual layer to about layer
        setContentView(R.layout.about);
        actualLayer = 2;
    }

    /**
     * The {@link ProgressBar} is shown when the recorder is started, but the GPS connection is not
     * initialized yet.
     */
    private ProgressBar mProgress;

    /**
     * Set actual content view to capture_video_and_gps.xml and prepare the {@link MediaRecorder}
     * and the GPS recorder.
     * 
     * @param view
     *            the actual View.
     */
    public void startRecorder(View view) {

        setContentView(R.layout.capture_video_and_gps);

        // initialize powermanager to set a screen wake lock during the videorecording
        pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // Allow the display to dim
        wLock = pManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "Lock while recording");

        // set actual layer to camera layer
        actualLayer = 1;

        // initialize GPS listener
        GPSinit();

        // Create an instance of Camera
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview_video_and_gps);
        preview.addView(mPreview);

        // Initialize the capture button
        captureButton = (Button) findViewById(R.id.button_capture_video_and_gps);
        captureButton.setEnabled(false);

        mProgress = (ProgressBar) findViewById(R.id.progressBar_video_and_gps);

        // Set onClickListener
        captureButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                if (captureButton.isEnabled()) {

                    /*
                     * If recorder is NOT recording initialize the camera, create the gpx logfile
                     * and start the media recorder. If recorder is recording, write end of gpx
                     * file, stop the media recorder and release the camera.
                     */
                    if (isRecording) {
                        stopRecording();
                    } else {
                        startRecording();

                    }
                }
            }
        });

    }

    /**
     * When Capture button is pressed and isRecording = false use this method to start recording.
     * initialize the camera, create the gpx logfile and start the media recorder.
     */
    private void startRecording() {
        // initialize video camera
        // test if MediaRecorder is working correctly and if file could be written
        if (prepareVideoRecorder() & write(GPXFILE_BEGINNING)) {

            // set wakelock until recording is stopped
            wLock.acquire();

            // write XML header for Trackpoints
            write(TRACKPOINTS_BEGINNING);

            // Camera is available and unlocked, MediaRecorder is prepared,
            // now you can start recording
            mMediaRecorder.start();

            // inform the user that recording has started
            captureButton.setText("Stop");

            // set the backround color to red when recording
            findViewById(R.id.background_capture_video_and_gps)
                    .setBackgroundColor(Color.RED);

            isRecording = true;
        } else {
            // prepare didn't work, release the camera
            releaseMediaRecorder();
            Toast.makeText(getApplicationContext(),
                    "ERROR! Could not start Recorder", Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * When capture button is pressed and isRecording = true use this method to stop recording.
     * write end of gpx file, stop the media recorder and release the camera.
     */
    private void stopRecording() {

        write(trackpoints);
        write(TRACKPOINTS_ENDING);
        write(GPXFILE_ENDING);
        trackpoints = "";
        // stop recording and release camera
        mMediaRecorder.stop(); // stop the recording
        releaseMediaRecorder(); // release the MediaRecorder object
        mCamera.lock(); // take camera access back from MediaRecorder
        // inform the user that recording has stopped
        captureButton.setText("Capture");

        findViewById(R.id.background_capture_video_and_gps).setBackgroundColor(
                Color.WHITE);
        isRecording = false;

        // release wakelock
        wLock.release();

    }

    /**
     * Initialize GPS.
     */
    private void GPSinit() {

        longitude = (TextView) findViewById(R.id.text_longitude_value_video_and_gps);
        latitude = (TextView) findViewById(R.id.text_latitude_value_video_and_gps);
        speed = (TextView) findViewById(R.id.text_speed_value_video_and_gps);

        // init location Updater
        lManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = lManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        // Check if enabled and if not send user to the GSP settings
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
        lListener = new GPSLocListener();

        lManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL * 1000, UPDATE_DIST, lListener);

    }

    /**
     * Stop GPS. Remove the current {@link LocationListener}.
     */
    private void GPSstop() {
        if (lListener != null) {
            try {
                lManager.removeUpdates(lListener);
            } catch (Exception e) {

            }
        }
    }

    /**
     * Set the gpx file.
     * 
     * @param filename
     *            the url as {@link String}.
     */
    private void setGPXfile(String filename) {
        GPXfile = new File(filename);
    }

    /**
     * Set the media file.
     * 
     * @param filename
     *            the url as {@link String}.
     */
    private void setMediafile(String filename) {
        Mediafile = new File(filename);
    }

    /**
     * Private method to write into the GPXfile. Strings will be attached.
     * 
     * @param string
     *            {@link String} which is attached to the GPXfile.
     * @return <code>true</code> if writing was successful.
     */
    private boolean write(String string) {

        try {
            if (root.canWrite()) {
                FileWriter filewriter = new FileWriter(GPXfile, true);
                BufferedWriter out = new BufferedWriter(filewriter);

                out.write(string);

                out.close();
                filewriter.close();

            }
        } catch (IOException e) {
            Log.e("TAG", "Could not write file " + e.getMessage());
            return false;
        }
        return true;

    }

    /**
     * Location manager class
     */
    private final class GPSLocListener implements LocationListener {

        private boolean firstTime = true;

        public void onLocationChanged(Location location) {
            if (firstTime) {
                Toast.makeText(getApplicationContext(),
                        "Connection established", Toast.LENGTH_LONG).show();
                captureButton.setEnabled(true);
                mProgress.setVisibility(ProgressBar.INVISIBLE);
                firstTime = false;
            }

            // set texts on display
            longitude.setText("" + location.getLongitude());
            latitude.setText("" + location.getLatitude());
            speed.setText("" + Math.round(location.getSpeed() * 3.6) + " km/h");

            // save trackpoint data while recording
            if (isRecording) {
                trackpoints = trackpoints
                        + ("<trkpt lat=\"" + location.getLatitude()
                                + "\" lon=\"" + location.getLongitude() + "\"></trkpt> \n");
            }

        }

        public void onProviderDisabled(String provider) {}

        public void onProviderEnabled(String provider) {}

        public void onStatusChanged(String provider, int status, Bundle extras) {}

    }

    /**
     * Prepare and set up the MediaRecorder.
     */
    private boolean prepareVideoRecorder() {

        // Just in case the camera is used at the moment, release it first
        mCamera.release();
        mCamera = getCameraInstance();
        mCamera.setDisplayOrientation(90);

        mMediaRecorder = new MediaRecorder();

        // The next steps have to be in this exact order
        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        // mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        // mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        // mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        mMediaRecorder.setProfile(CamcorderProfile
                .get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        setOutputFiles();
        mMediaRecorder.setOutputFile(Mediafile.toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());
        
        // Set OrientationHint of mMediaRecorder according to the actual rotation of the phone
        mMediaRecorder.setOrientationHint(getRotation());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d("TAG",
                    "IllegalStateException preparing MediaRecorder: "
                            + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d("TAG",
                    "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    /**
     * Return the rotation angle for the mediaRecorder.setOrientationHint method
     * 
     * @return an {@link Integer} roundet to 0, 90, 180 or 270 degree
     */
    private int getRotation() {
        return degree > 0 && degree <= 90 ? 270
                : degree > 90 && degree <= 180 ? 0 : degree > 180
                        && degree <= 270 ? 90 : 180;
    }

    /**
     * Release the current {@link MediaRecorder} object and lock the {@link Camera} object for later
     * use.
     */
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset(); // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock(); // lock camera for later use
        }
    }

    /**
     * Release the current {@link Camera} object.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }
    }

    /**
     * Set the output files GPXfile and Mediafile. Both get the same path to the external storage,
     * prefix (VID_ or GPSLog_) and timestamp.
     */
    private void setOutputFiles() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "GPStreetCam");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("GPStreetCam", "failed to create directory");
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
                .format(new Date());

        // Set the media file where the video is saved
        setMediafile(mediaStorageDir.getPath() + File.separator + "VID_"
                + timeStamp + ".mp4");
        // Set root file of the external storage
        root = Environment.getExternalStorageDirectory();
        // Set the Gpx file where the gps log is saved
        setGPXfile(mediaStorageDir.getPath() + File.separator + "GPSLog_"
                + timeStamp + ".gpx");
    }

    /**
     * AccurancyChanged event from the {@link SensorListener} interface
     */
    public void onAccuracyChanged(int sensor, int accuracy) {}

    /**
     * SensorChanged event from the {@link SensorListener} interface
     */
    public void onSensorChanged(int sensor, float[] values) {
        if (sensor == this.orientationSensor) {
            degree = values[0];
            System.out.println(degree);
        }
    }
}
