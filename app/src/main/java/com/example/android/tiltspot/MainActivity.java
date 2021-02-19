/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.tiltspot;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.MediaPlayer;
import android.opengl.Visibility;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.ubidots.updated.ApiClient;
import com.ubidots.updated.Value;
import com.ubidots.updated.Variable;
/*
import com.ubidots.ApiClient;
import com.ubidots.Variable;
import com.ubidots.Value;
*/
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String API_KEY = "BBFF-19b2b1922ae8f93c2d9f58afd1d4ae9e3be";
    private final String ACCELERATION_ID = "5f1c39431d84720148bf0437";
    private final String GPS_ID = "5f1c393c1d847201d8c27e8f";
    private final String THRESHOLD_ID = "5f1f0a711d8472568bab28d7";
    private final String HEARTRATE_ID = "5f243f2f1d847227cba8d9c6";
    private final String ROTATIONAL_VALUES_ID = "5f245b701d847225403283c6";


    // System sensor manager instance.
    private SensorManager mSensorManager;
    // Accelerometer and magnetometer sensors, as retrieved from the
    // sensor manager.
    private MediaPlayer mediaPlayer;
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetometer;
    private Sensor mHeartRateSensor;
    // Current data from accelerometer & magnetometer.  The arrays hold values
    // for X, Y, and Z.
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];
    private float[] mGravity = new float[3];
    private double Acceleration;
    private double Speed = 0;
    private double UbidotsAcceleration;
    private float mAzimuth;
    private float mPitch;
    private float mRoll;
    private static float mReferenceAzimuth;
    private static float mReferencePitch;
    private static float mReferenceRoll;
    private Map<String, Object> mLocationMap;
    //constants
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final String TRACKING_LOCATION_KEY = "tracking_location";
    // TextViews to display current sensor values.
    private TextView mTextSensorAzimuth;
    private TextView mTextSensorPitch;
    private TextView mTextSensorRoll;
    private TextView mLocationTextView;
    private TextView mThresholdTextView;

    private Button mTrackButton;
    private static float SHAKE_THRESHOLD = 2.5f; // m/S**2
    private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
    private long mLastShakeTime;
    private boolean mTrackingLocation;
    private boolean mShakeEvent = false;
    private boolean mCollapseEvent = false;
    private boolean mStartingUpOverwatch = false;
    private boolean mSendLastVal = false;
    private int mHeartRate = 65;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Location mLastLocation;
    Handler handler = new Handler();
    int delay = 1000; //milliseconds


    // System display. Need this for determining rotation.
    private Display mDisplay;

    // Very small values for the accelerometer (on all three axes) should
    // be interpreted as 0. This value is the amount of acceptable
    // non-zero drift.
    private static final float VALUE_DRIFT = 0.05f;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextSensorAzimuth = (TextView) findViewById(R.id.value_azimuth);
        mTextSensorPitch = (TextView) findViewById(R.id.value_pitch);
        mTextSensorRoll = (TextView) findViewById(R.id.value_roll);
        mTrackButton = (Button) findViewById(R.id.track_button);
        mLocationTextView = (TextView) findViewById(R.id.textview_location);
        mThresholdTextView = (TextView) findViewById(R.id.textview_current_threshold);
        mThresholdTextView.setText(String.valueOf(SHAKE_THRESHOLD));


        mTrackButton.setText(R.string.start_tracking_building);

        mLocationTextView.setText(R.string.location_text_placeholder);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (mTrackingLocation) {
                    mLastLocation = locationResult.getLastLocation();
                    mLocationTextView.setText(
                            getString(R.string.location_text,
                                    mLastLocation.getLatitude(),
                                    mLastLocation.getLongitude(),
                                    mLastLocation.getTime()));
                    mLocationMap = TestFunctionMap();
                }
            }
        };

// Initialize the FusedLocationClient.
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(
                this);

        mTrackButton.setOnClickListener(new View.OnClickListener() {
            /**
             * Toggle the tracking state.
             * @param v The track location button.
             */
            @Override
            public void onClick(View v) {
                if (!mTrackingLocation) {
                    startTrackingLocation();
                    mReferenceAzimuth = mAzimuth;
                    mReferencePitch = mPitch;
                    mReferenceRoll = mRoll;
                    mStartingUpOverwatch = true;

                } else {
                    stopTrackingLocation();
                    mStartingUpOverwatch = false;

                }
            }
        });
        // Get accelerometer and magnetometer sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        mSensorManager = (SensorManager) getSystemService(
                Context.SENSOR_SERVICE);
        mSensorAccelerometer = mSensorManager.getDefaultSensor(
                Sensor.TYPE_ACCELEROMETER);
        mSensorMagnetometer = mSensorManager.getDefaultSensor(
                Sensor.TYPE_MAGNETIC_FIELD);


        // Get the display from the window manager (for rotation).
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplay = wm.getDefaultDisplay();

        handler.postDelayed(new Runnable() {
            public void run() {
                new ApiUbidots().execute();
                mThresholdTextView.setText(String.valueOf(SHAKE_THRESHOLD));
                handler.postDelayed(this, delay);
            }
        }, delay);


    }

    private void startTrackingLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]
                            {Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            mTrackingLocation = true;
            mTrackButton.setText(R.string.stop_tracking_building);
            mFusedLocationClient.requestLocationUpdates
                    (getLocationRequest(),
                            mLocationCallback,
                            null /* Looper */);

            // Set a loading text while you wait for the address to be
            // returned

        }
    }

    private void stopTrackingLocation() {
        if (mTrackingLocation) {
            mTrackingLocation = false;
            mTrackButton.setText(R.string.start_tracking_building);
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }


    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(3600000);//3600000
        locationRequest.setFastestInterval(500);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                // If the permission is granted, get the location,
                // otherwise, show a Toast
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startTrackingLocation();
                } else {
                    Toast.makeText(this,
                            R.string.location_permission_denied,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    public Map<String, Object> TestFunctionMap() {
        Map<String, Object> TestMap = new HashMap<String, Object>();
        TestMap.put("lng", mLastLocation.getLongitude());
        TestMap.put("lat", mLastLocation.getLatitude());
        return TestMap;
    }

    /**
     * Listeners for the sensors are registered in this callback so that
     * they can be unregistered in onStop().
     */
    @Override
    protected void onStart() {
        super.onStart();
        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.uyari);

        // Listeners for the sensors are registered in this callback and
        // can be unregistered in onStop().
        //
        // Check to ensure sensors are available before registering listeners.
        // Both listeners are registered with a "normal" amount of delay
        // (SENSOR_DELAY_NORMAL).
        if (mSensorAccelerometer != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mSensorMagnetometer != null) {
            mSensorManager.registerListener(this, mSensorMagnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mediaPlayer.release();
        mediaPlayer = null;
        // Unregister all sensor listeners in this callback so they don't
        // continue to use resources when the app is stopped.
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // The sensor type (as defined in the Sensor class).
        int sensorType = sensorEvent.sensor.getType();

        // The sensorEvent object is reused across calls to onSensorChanged().
        // clone() gets a copy so the data doesn't change out from under us
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = sensorEvent.values.clone();
                //BURAYI LOW PASS FILTER İLE DEĞİŞTİRMEYİ UNUTMA!! https://stackoverflow.com/questions/19473819/removing-gravity-from-accelerometer-documentation-code
                long curTime = System.currentTimeMillis();
                if ((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLISECS) {
                    // In this example, alpha is calculated as t / (t + dT),
                    // where t is the low-pass filter's time-constant and
                    // dT is the event delivery rate.

                    final float FILTER_ALPHA = 0.80f;

                    // Isolate the force of gravity with the low-pass filter.
                    mGravity[0] = FILTER_ALPHA * mGravity[0] + (1 - FILTER_ALPHA) * sensorEvent.values[0];
                    mGravity[1] = FILTER_ALPHA * mGravity[1] + (1 - FILTER_ALPHA) * sensorEvent.values[1];
                    mGravity[2] = FILTER_ALPHA * mGravity[2] + (1 - FILTER_ALPHA) * sensorEvent.values[2];
                    // Remove the gravity contribution with the high-pass filter.
                    float x = sensorEvent.values[0] - mGravity[0];
                    float y = sensorEvent.values[1] - mGravity[1];
                    float z = sensorEvent.values[2] - mGravity[2];

                    Acceleration = Math.sqrt(x * x + y * y + z * z);
                    Speed = Speed + Acceleration * (curTime - mLastShakeTime);
                    Log.d("APP_NAME", "Acceleration is " + Acceleration + "m/s^2");

                    if ((Acceleration > SHAKE_THRESHOLD && mTrackingLocation)) {
                        mediaPlayer.start();
                        UbidotsAcceleration = Acceleration;
                        mLastShakeTime = curTime;
                        Log.d("APP_NAME", "Shake, Rattle, and Roll");
                        mShakeEvent = true;
                        new ApiUbidots().execute();
                    } else if (mTrackingLocation && Acceleration > 9.81) {
                        mCollapseEvent = true;
                    }
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = sensorEvent.values.clone();
                break;
            default:
                return;
        }
        // Compute the rotation matrix: merges and translates the data
        // from the accelerometer and magnetometer, in the device coordinate
        // system, into a matrix in the world's coordinate system.
        // The second argument is an inclination matrix, which isn't
        // used in this example.
        float[] rotationMatrix = new float[9];
        boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix,
                null, mAccelerometerData, mMagnetometerData);
        // Remap the matrix based on current device/activity rotation.
        float[] rotationMatrixAdjusted = new float[9];
        switch (mDisplay.getRotation()) {
            case Surface.ROTATION_0:
                rotationMatrixAdjusted = rotationMatrix.clone();
                break;
            case Surface.ROTATION_90:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_180:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y,
                        rotationMatrixAdjusted);
                break;
            case Surface.ROTATION_270:
                SensorManager.remapCoordinateSystem(rotationMatrix,
                        SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X,
                        rotationMatrixAdjusted);
                break;
        }
        // Get the orientation of the device (azimuth, pitch, roll) based
        // on the rotation matrix. Output units are radians.
        float orientationValues[] = new float[3];
        if (rotationOK) {
            SensorManager.getOrientation(rotationMatrixAdjusted,
                    orientationValues);
        }
        // Pull out the individual values from the array.
        mAzimuth = (float) (((orientationValues[0] * 180) / Math.PI) + 180);
        mPitch = (float) ((((orientationValues[1] * 180) / Math.PI)) + 90);
        mRoll = (float) ((((orientationValues[2] * 180) / Math.PI)));


        // Fill in the string placeholders and set the textview text.
        mTextSensorAzimuth.setText(getResources().getString(
                R.string.value_format, mAzimuth));
        mTextSensorPitch.setText(getResources().getString(
                R.string.value_format, mPitch));
        mTextSensorRoll.setText(getResources().getString(
                R.string.value_format, mRoll));

        if ((Math.abs(mPitch - mReferencePitch) > 10 || Math.abs(mRoll - mReferenceRoll) > 10) && mTrackingLocation) {
            mCollapseEvent = true;
        }

    }


    /**
     * Must be implemented to satisfy the SensorEventListener interface;
     * unused in this app.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }


    public class ApiUbidots extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... params) {
            ApiClient apiClient = new ApiClient(API_KEY, "https://industrial.api.ubidots.com/api/v1.6/");
            //Titreşim değeri eşik değerini aştığında titreşim değerini ve koordinatı merkeze iletir

            if (mCollapseEvent) {
                if (!mSendLastVal) {
                    Map<String, Object> HM
                            = new HashMap<String, Object>();
                    Variable uAzimuth = apiClient.getVariable(ROTATIONAL_VALUES_ID);
                    HM.put("Azimuth collapse Value", mAzimuth);
                    HM.put("Pitch collapse Value", mPitch);
                    HM.put("Roll collapse Value", mRoll);
                    uAzimuth.saveValue(0, HM);
                    mSendLastVal = true;

                }
                Variable HeartRateValue = apiClient.getVariable(HEARTRATE_ID);
                HeartRateValue.saveValue(mHeartRate);


            } else if (mTrackingLocation && mShakeEvent) {
                Variable AccelerationLevel = apiClient.getVariable(ACCELERATION_ID);
                Variable Azimuth = apiClient.getVariable(ROTATIONAL_VALUES_ID);
                AccelerationLevel.saveValue(UbidotsAcceleration);
                mShakeEvent = false;
            } else if (mStartingUpOverwatch) {
                Map<String, Object> HM
                        = new HashMap<String, Object>();
                Variable ULocation = apiClient.getVariable(GPS_ID);
                ULocation.saveValue(0, mLocationMap);
                Variable uAzimuth = apiClient.getVariable(ROTATIONAL_VALUES_ID);
                HM.put("Azimuth reference Value", mReferenceAzimuth);
                HM.put("Pitch reference Value", mReferencePitch);
                HM.put("Roll reference Value", mReferenceRoll);
                uAzimuth.saveValue(0, HM);
                mStartingUpOverwatch = false;


            }
            //çökme durumunda sadece ve sadece kurtarmacının kalp atış değerlerini iletir

//kurtarmacının belirlediği eşik değerinin değişip değişmediğini kontrol eder ve kalp ritmi gönderir
            else {


            }

            return null;
        }
    }
}