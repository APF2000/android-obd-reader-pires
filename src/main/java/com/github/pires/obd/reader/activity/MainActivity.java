package com.github.pires.obd.reader.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;

import android.os.AsyncTask;
import android.os.Build;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.Preference;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.enums.AvailableCommandNames;
import com.github.pires.obd.reader.R;
import com.github.pires.obd.reader.config.ObdConfig;
import com.github.pires.obd.reader.io.AbstractGatewayService;
import com.github.pires.obd.reader.io.LogCSVWriter;
import com.github.pires.obd.reader.io.MockObdGatewayService;
import com.github.pires.obd.reader.io.ObdCommandJob;
import com.github.pires.obd.reader.io.ObdGatewayService;
import com.github.pires.obd.reader.io.ObdProgressListener;
import com.github.pires.obd.reader.net.ObdReading;
import com.github.pires.obd.reader.trips.TripLog;
import com.github.pires.obd.reader.trips.TripRecord;
import com.google.inject.Inject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.DecimalFormat;

import java.text.SimpleDateFormat;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import java.util.ArrayList;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import retrofit.RestAdapter;
//import retrofit.RetrofitError;
//import retrofit.client.Response;
import roboguice.RoboGuice;
import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

import static com.github.pires.obd.reader.activity.ConfigActivity.getGpsDistanceUpdatePeriod;
import static com.github.pires.obd.reader.activity.ConfigActivity.getGpsUpdatePeriod;

import java.util.concurrent.TimeUnit;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;


// Some code taken from https://github.com/barbeau/gpstest

@RequiresApi(api = Build.VERSION_CODES.O)
@ContentView(R.layout.main)
public class MainActivity extends RoboActivity implements ObdProgressListener, LocationListener, GpsStatus.Listener {
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private String userEmail;
    private String userId;
    private String userToken;
    private float gravity[] = {0, 0, 0};
//    private String user_email = "";
    private boolean isDataAcquisitionEnabled = false;
    private boolean isDataIndependentOfBluetoothConnectionEnabled = true;
    JSONArray accAddRequests = new JSONArray();
    JSONArray obdAddRequests = new JSONArray();
    JSONArray locationAddRequests = new JSONArray();
//    JSONArray createPDFRequest = new JSONArray();

    GnssStatus.Callback mGnssStatusCallback;
    LocationManager mLocationManager;
    private float compass_last_measured_bearing = 0;
    private final float SMOOTHING_FACTOR_COMPASS = 0.8F;
    private float gravity_vec[] = {0, 0, 0};
    private float magnetic_field_vec[] = {0, 0, 0};
    private float linear_acceleration[] = {0, 0, 0};

    private HashMap<String, Date> resourceNameTolastDataUpdate = new HashMap<String, Date>();
    //    private Date lastHeadingUpdate = new Date();
    private Date lastOrientUpdate = new Date();
    private Date lastRotationUpdate = new Date();
    private Date lastUpdateTimeAcceleration;
    private Date lastUpdateTimeGPS = new Date();
    private Date lastBearingUpdate = new Date();

    private final long minMillisBetweenData = 50;

    private static final String TAG = MainActivity.class.getName();
    private static final int NO_BLUETOOTH_ID = 0;
    private static final int BLUETOOTH_DISABLED = 1;
    private static final int START_LIVE_DATA = 2;
    private static final int STOP_LIVE_DATA = 3;
    private static final int SETTINGS = 4;
    private static final int GET_DTC = 5;
    private static final int TABLE_ROW_MARGIN = 7;
    private static final int NO_ORIENTATION_SENSOR = 8;
    private static final int NO_GPS_SUPPORT = 9;
    private static final int TRIPS_LIST = 10;
    private static final int SAVE_TRIP_NOT_AVAILABLE = 11;
    private static final int REQUEST_ENABLE_BT = 1234;
    private static boolean bluetoothDefaultIsEnable = false;

    RequestQueue queue;

    public String selected_date_filter = "última hora";

    static {
        RoboGuice.setUseAnnotationDatabases(false);
    }

    public Map<String, String> commandResult = new HashMap<String, String>();
    boolean mGpsIsStarted = false;
    private LocationManager mLocService;
    private LocationProvider mLocProvider;
    private LogCSVWriter myCSVWriter;
    private Location mLastLocation;
    /// the trip log
    private TripLog triplog;
    private TripRecord currentTrip;

    private int FASTEST_INTERVAL = 8 * 1000; // 8 SECOND
    private int UPDATE_INTERVAL = 2000; // 2 SECOND
    private int FINE_LOCATION_REQUEST = 888;
    private Toast toast;
    private LocationRequest locationRequest;

    private TextView tvLocationDetails;
    private LinearLayout mainLayout;

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Entered onStart...");
//        user_email = prefs.getString(ConfigActivity.UPLOAD_URL_KEY, "");
//        Log.d(TAG, "user email: " + user_email);

        ActivityCompat.requestPermissions( this,    new String[]{
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE
                }, 1
        );

        // https://stackoverflow.com/questions/6822319/what-to-use-instead-of-addpreferencesfromresource-in-a-preferenceactivity
        // https://developer.android.com/reference/android/preference/PreferenceFragment.html
        // https://developer.android.com/reference/android/preference/PreferenceActivity.html
        isDataIndependentOfBluetoothConnectionEnabled = prefs.getBoolean(ConfigActivity.UPLOAD_DATA_KEY, true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationManager.registerGnssStatusCallback(mGnssStatusCallback);
        }
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 30000, 0, this
        );

        Log.d(TAG, "Entered onStart...");

        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT
                }, 1
        );

        queue = Volley.newRequestQueue(this);

    }

    private void sendDataToLambda(JSONObject bodyJson){
        String url = "https://pntdpvkdsc.execute-api.us-east-1.amazonaws.com/default/app_data";
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    // response
                    Log.d("Response", response);
                },
                error -> {
                    // error
                    Log.d("Error.Response", error.toString());
                }
        ) {
            @Override
            public byte[] getBody() throws AuthFailureError {

                String body_str = bodyJson.toString();

                return body_str.getBytes();
            }

            @Override
            public String getBodyContentType()
            {
                return "application/json";
            }
        };
        queue.add(postRequest);
    }

    private void getDataFromLambda(JSONObject bodyJson){
        String url = "https://pntdpvkdsc.execute-api.us-east-1.amazonaws.com/default/app_data";
        StringRequest getRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // response
                    Log.d("Response", response);
                },
                error -> {
                    // error
                    Log.d("Error.Response", error.toString());
                }
        ) {
            @Override
            public byte[] getBody() throws AuthFailureError {

                String body_str = bodyJson.toString();

                return body_str.getBytes();
            }

            @Override
            public String getBodyContentType()
            {
                return "application/json";
            }
        };
        queue.add(getRequest);
    }


    private void createPDFLambda(JSONObject bodyJson){
        String url = "https://udk2uz8gkd.execute-api.us-east-1.amazonaws.com/default/hello-world";
        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    // response
                    Log.d("Response", response);
                },
                error -> {
                    // error
                    Log.d("Error.Response", error.toString());
                }
        ) {
            @Override
            public byte[] getBody() throws AuthFailureError {

                String body_str = bodyJson.toString();

                return body_str.getBytes();
            }

            @Override
            public String getBodyContentType()
            {
                return "application/json";
            }
        };
        queue.add(postRequest);
    }




    void sendDataToAws()
    {
        // send to aws
        try {
            // acceleration
            if(accAddRequests.length() > 0)
            {
                JSONObject accData = new JSONObject();
                accData.put("data", accAddRequests);
                accData.put("method", "add_acceleration");

                sendDataToLambda(accData);
                Log.d(TAG, "sent acc data to AWS");
            }

            // obd
            if(obdAddRequests.length() > 0)
            {
                JSONObject obdData = new JSONObject();
                obdData.put("data", obdAddRequests);
                obdData.put("method", "add_obd_info");

                sendDataToLambda(obdData);
                Log.d(TAG, "sent obd data to AWS");
            }

            // gps location
            if(locationAddRequests.length() > 0)
            {
                JSONObject locationData = new JSONObject();
                locationData.put("data", locationAddRequests);
                locationData.put("method", "add_location");

                sendDataToLambda(locationData);
                Log.d(TAG, "sent location data to AWS");
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }finally{
            accAddRequests = new JSONArray();
            obdAddRequests = new JSONArray();
            locationAddRequests = new JSONArray();
        }
    }

//    @InjectView(R.id.acceleration_text)
//    private TextView acceleration;
    private final SensorEventListener accelerationListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event)
        {
            if(!isDataAcquisitionEnabled) return;

            // alpha is calculated as t / (t + dT)
            // with t, the low-pass filter's time-constant
            // and dT, the event delivery rate
            final float alpha = 0.8F;
//            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
//            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
//            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            float gravity_x = gravity_vec[0];
            float gravity_y = gravity_vec[1];
            float gravity_z = gravity_vec[2];
            float gravity_val = (float) Math.sqrt(gravity_x * gravity_x + gravity_y * gravity_y + gravity_z * gravity_z);

            linear_acceleration[0] = event.values[0] - gravity_x;
            linear_acceleration[1] = event.values[1] - gravity_y;
            linear_acceleration[2] = event.values[2] - gravity_z;

            float x = linear_acceleration[0];
            float y = linear_acceleration[1];
            float z = linear_acceleration[2];
            DecimalFormat df = new DecimalFormat("0.00");
            String acc_x_string = df.format(x);
            String acc_y_string = df.format(y);
            String acc_z_string = df.format(z);

            String gravity_x_string = df.format(gravity_x);
            String gravity_y_string = df.format(gravity_y);
            String gravity_z_string = df.format(gravity_z);

            String gravity_val_string = df.format(gravity_val);


            JSONArray jsonAcceleration = new JSONArray();
            jsonAcceleration.put(acc_x_string);
            jsonAcceleration.put(acc_y_string);
            jsonAcceleration.put(acc_z_string);

            jsonAcceleration.put(gravity_x_string);
            jsonAcceleration.put(gravity_y_string);
            jsonAcceleration.put(gravity_z_string);

            jsonAcceleration.put(gravity_val_string);

            Date currentTime = Calendar.getInstance().getTime();

            if (lastUpdateTimeAcceleration == null) {
                lastUpdateTimeAcceleration = new Date();
                lastUpdateTimeAcceleration.setTime(currentTime.getTime() - 2000 * minMillisBetweenData);
            }

            long diffInMillis = currentTime.getTime() - lastUpdateTimeAcceleration.getTime();
//            long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);

            if (diffInMillis <= minMillisBetweenData) return;
            lastUpdateTimeAcceleration = currentTime;

            JSONObject jsonObjAcc = new JSONObject();
            try {
                jsonObjAcc.put("timestamp", fmt.format(currentTime));

                jsonObjAcc.put("acceleration_x", acc_x_string);
                jsonObjAcc.put("acceleration_y", acc_y_string);
                jsonObjAcc.put("acceleration_z", acc_z_string);

                jsonObjAcc.put("gravity_x", gravity_x_string);
                jsonObjAcc.put("gravity_y", gravity_y_string);
                jsonObjAcc.put("gravity_z", gravity_z_string);
                jsonObjAcc.put("user_token", userEmail);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            accAddRequests.put(jsonObjAcc);

            // backup

            String accelerationString = jsonAcceleration.toString();
            Log.d("arthur", "Getting acceleration data: " + accelerationString);
            writeDataToFile("DELETEME_ACCELERATION.txt",
                    fmt.format(currentTime) + " " + accelerationString);

//            updateTextView(acceleration, acc);

        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };


//        @InjectView(R.id.acceleration_text)
//        private TextView acceleration;
    private final SensorEventListener gravityListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            gravity_vec[0] = event.values[0];
            gravity_vec[1] = event.values[1];
            gravity_vec[2] = event.values[2];
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };


    @InjectView(R.id.compass_text)
    private TextView compass;
    private final SensorEventListener orientListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event)
        {
            if(!isDataAcquisitionEnabled) return;

            float azimuth = event.values[0];
            float pitch = event.values[1];
            float roll = event.values[2];

            String dir = "";
            if (azimuth >= 337.5 || azimuth < 22.5) {
                dir = "N";
            } else if (azimuth >= 22.5 && azimuth < 67.5) {
                dir = "NE";
            } else if (azimuth >= 67.5 && azimuth < 112.5) {
                dir = "E";
            } else if (azimuth >= 112.5 && azimuth < 157.5) {
                dir = "SE";
            } else if (azimuth >= 157.5 && azimuth < 202.5) {
                dir = "S";
            } else if (azimuth >= 202.5 && azimuth < 247.5) {
                dir = "SW";
            } else if (azimuth >= 247.5 && azimuth < 292.5) {
                dir = "W";
            } else if (azimuth >= 292.5 && azimuth < 337.5) {
                dir = "NW";
            }
            updateTextView(compass, dir);

            Date currentTime = Calendar.getInstance().getTime();

            Date lastUpdateTime = lastOrientUpdate;

            long diffInMillis = currentTime.getTime() - lastUpdateTime.getTime();
//            long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);

            if (diffInMillis <= minMillisBetweenData) return;
            lastOrientUpdate = currentTime;

            DecimalFormat df = new DecimalFormat("0.00");
            String azimuth_string = df.format(azimuth);
            String pitch_string = df.format(pitch);
            String roll_string = df.format(roll);

            JSONArray jsonContent = new JSONArray();
            jsonContent.put(azimuth_string);
            jsonContent.put(pitch_string);
            jsonContent.put(roll_string);

            Log.d("arthur", "Getting orientation data");
            writeDataToFile("DELETEME_ORIENTATION.txt", fmt.format(currentTime) + " " + jsonContent.toString());
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };

    private final SensorEventListener rotationListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
//            float x_sin = event.values[0]; // east
//            float y_sin = event.values[1]; // north
//            float z_sin = event.values[2]; // sky
//            float cos_theta = event.values[3];
//            float heading_accuracy = event.values[4];
//
//            float xyz_norm = (float) Math.sqrt(x_sin * x_sin + y_sin * y_sin + z_sin * z_sin);
//
//            float normal_x = x_sin / xyz_norm;
//            float normal_y = y_sin / xyz_norm;
//            float normal_z = z_sin / xyz_norm;
//
//            float sin_theta = (float) Math.sqrt(1 - cos_theta * cos_theta);
//
//            Date currentTime = Calendar.getInstance().getTime();
//
//            Date lastUpdateTime = lastRotationUpdate;
//
//            long diffInMillis = currentTime.getTime() - lastUpdateTime.getTime();
//            long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);
//
//            if (diffSeconds <= minSecondsBetweenData) return;
//            lastRotationUpdate = currentTime;
//
//            DecimalFormat df = new DecimalFormat("0.00");
////            String azimuth_string = df.format(azimuth);
////            String pitch_string = df.format(pitch);
////            String roll_string = df.format(roll);
//
//            JSONArray jsonContent = new JSONArray();
////            jsonContent.put(azimuth_string);
////            jsonContent.put(pitch_string);
////            jsonContent.put(roll_string);
//
//            Log.d("arthur", "Getting rotation data");
//            writeDataToFile("DELETEME_ROTATION.txt", currentTime.toString() + " " + jsonContent.toString());
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // do nothing
        }
    };


    @InjectView(R.id.BT_STATUS)
    private TextView btStatusTextView;
    @InjectView(R.id.OBD_STATUS)
    private TextView obdStatusTextView;
    @InjectView(R.id.GPS_POS)
    private TextView gpsStatusTextView;
    @InjectView(R.id.vehicle_view)
    private LinearLayout vv;
    @InjectView(R.id.data_table)
    private TableLayout tl;
    @Inject
    private SensorManager sensorManager;
    @Inject
    private PowerManager powerManager;
    @Inject
    private SharedPreferences prefs;
    private boolean isServiceBound;
    private AbstractGatewayService service;
    private final Runnable mQueueCommands = new Runnable() {
        public void run() {
            if (service != null && service.isRunning() && service.queueEmpty()) {
                queueCommands();

                double lat = 0;
                double lon = 0;
                double alt = 0;
                final int posLen = 7;
                if (mGpsIsStarted && mLastLocation != null) {
                    lat = mLastLocation.getLatitude();
                    lon = mLastLocation.getLongitude();
                    alt = mLastLocation.getAltitude();

                    StringBuilder sb = new StringBuilder();
                    sb.append("Lat: ");
                    sb.append(String.valueOf(mLastLocation.getLatitude()).substring(0, posLen));
                    sb.append(" Lon: ");
                    sb.append(String.valueOf(mLastLocation.getLongitude()).substring(0, posLen));
                    sb.append(" Alt: ");
                    sb.append(String.valueOf(mLastLocation.getAltitude()));
                    gpsStatusTextView.setText(sb.toString());
                }
                if (prefs.getBoolean(ConfigActivity.UPLOAD_DATA_KEY, false)) {
                    // UplFoad the current reading by http
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
//                    new UploadAsyncTask().execute(reading);

//                    isDataIndependentOfBluetoothConnectionEnabled = prefs.getBoolean(ConfigActivity.UPLOAD_DATA_KEY, false);

                } else if (prefs.getBoolean(ConfigActivity.UPLOAD_URL_KEY, false)) {
//                    user_email = prefs.getString(ConfigActivity.UPLOAD_URL_KEY, "");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);

                } else if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {
                    // Write the current reading to CSV
                    final String vin = prefs.getString(ConfigActivity.VEHICLE_ID_KEY, "UNDEFINED_VIN");
                    Map<String, String> temp = new HashMap<String, String>();
                    temp.putAll(commandResult);
                    ObdReading reading = new ObdReading(lat, lon, alt, System.currentTimeMillis(), vin, temp);
                    if (reading != null) myCSVWriter.writeLineCSV(reading);
                }
                commandResult.clear();
            }
            // run again in period defined in preferences
            new Handler().postDelayed(mQueueCommands, ConfigActivity.getObdUpdatePeriod(prefs));
        }
    };
    private Sensor orientSensor = null;
    private Sensor rotationSensor = null;
    private Sensor accelerationSensor = null;
    private Sensor magneticFieldSensor = null;
    private Sensor gravitySensor = null;
    //    private Sensor headingSensor = null;
    private PowerManager.WakeLock wakeLock = null;
    private boolean preRequisites = true;
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, className.toString() + " service is bound");
            isServiceBound = true;
            service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
            service.setContext(MainActivity.this);
            Log.d(TAG, "Starting live data");
            try {
                service.startService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } catch (IOException ioe) {
                Log.e(TAG, "Failure Starting live data");
                btStatusTextView.setText(getString(R.string.status_bluetooth_error_connecting));
                doUnbindService();
            }
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        // This method is *only* called when the connection to the service is lost unexpectedly
        // and *not* when the client unbinds (http://developer.android.com/guide/components/bound-services.html)
        // So the isServiceBound attribute should also be set to false when we unbind from the service.
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, className.toString() + " service is unbound");
            isServiceBound = false;
        }
    };

    public static String LookUpCommand(String txt) {
        for (AvailableCommandNames item : AvailableCommandNames.values()) {
            if (item.getValue().equals(txt)) return item.name();
        }
        return txt;
    }

    public void updateTextView(final TextView view, final String txt) {
        new Handler().post(new Runnable() {
            public void run() {
                view.setText(txt);
            }
        });
    }

    void foo() {
        Log.d("arthur", "request worked");
    }

    public void stateUpdate(final ObdCommandJob job)
    {
        if(!isDataAcquisitionEnabled) return;

        final String cmdName = job.getCommand().getName();
        String cmdResult = "";
        final String cmdID = LookUpCommand(cmdName);


        if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
            cmdResult = job.getCommand().getResult();
            if (cmdResult != null && isServiceBound) {
                obdStatusTextView.setText(cmdResult.toLowerCase());
            }
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.BROKEN_PIPE)) {
            if (isServiceBound)
                stopLiveData();
        } else if (job.getState().equals(ObdCommandJob.ObdCommandJobState.NOT_SUPPORTED)) {
            cmdResult = getString(R.string.status_obd_no_support);
        } else {
            cmdResult = job.getCommand().getFormattedResult();
            if (isServiceBound)
                obdStatusTextView.setText(getString(R.string.status_obd_data));
        }

        if (vv.findViewWithTag(cmdID) != null) {
            TextView existingTV = (TextView) vv.findViewWithTag(cmdID);
            existingTV.setText(cmdResult);
        } else addTableRow(cmdID, cmdName, cmdResult);
        commandResult.put(cmdID, cmdResult);
        updateTripStatistic(job, cmdID);


//        String[] listData = new String[]{cmdID, cmdName, cmdResult};
//        String wholeData = TextUtils.join(" ", listData);
        Date currentTime = Calendar.getInstance().getTime();

        Date lastUpdateTime = resourceNameTolastDataUpdate.get(cmdName);
        if (lastUpdateTime == null) {
            lastUpdateTime = new Date();
            lastUpdateTime.setTime(currentTime.getTime() - 2000 * minMillisBetweenData);
        }

        long diffInMillis = currentTime.getTime() - lastUpdateTime.getTime();
//        long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);

        if (diffInMillis <= minMillisBetweenData) return;
        resourceNameTolastDataUpdate.put(cmdName, currentTime);

        JSONObject jsonObjObd = new JSONObject();
        try {
            jsonObjObd.put("timestamp", fmt.format(currentTime));
            jsonObjObd.put("name", cmdName);
            jsonObjObd.put("result", cmdResult);
            jsonObjObd.put("user_token", userEmail);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        obdAddRequests.put(jsonObjObd);

        // backup

        // https://stackoverflow.com/questions/10717838/how-to-create-json-format-data-in-android
        JSONArray jsonContent = new JSONArray();
        jsonContent.put(cmdID);
        jsonContent.put(cmdName);
        jsonContent.put(cmdResult);


        Log.d("arthur", "Getting data from OBD");
        writeDataToFile("DELETEME.txt", fmt.format(currentTime) + " " + jsonContent.toString());

    }

    @SuppressLint("NewApi")
    private void writeDataToFile(String fileName, String content) {

        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", this.getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }

        File path = Environment.getExternalStorageDirectory();
        File file = new File(path, fileName);

        content += "\n";

        if (!path.exists()) {
            path.mkdirs();
        }

        try {
            // append to file
            FileOutputStream writer = new FileOutputStream(file, true);
            writer.write(content.getBytes());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private boolean gpsInit() {
        mLocService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (mLocService != null) {
            mLocProvider = mLocService.getProvider(LocationManager.GPS_PROVIDER);
            if (mLocProvider != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return false;
                }

//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    LocationManager.registerGnssStatusCallback(GnssStatus.Callback)
//                }
//                mLocService.addGpsStatusListener(this);
                if (mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
//                    gpsStatusTextView.setText(getString(R.string.status_gps_ready));
                    return true;
                }
            }
        }
        gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
        showDialog(NO_GPS_SUPPORT);
        Log.e(TAG, "Unable to get GPS PROVIDER");
        // todo disable gps controls into Preferences
        return false;
    }

    private void updateTripStatistic(final ObdCommandJob job, final String cmdID) {

        if (currentTrip != null) {
            if (cmdID.equals(AvailableCommandNames.SPEED.toString())) {
                SpeedCommand command = (SpeedCommand) job.getCommand();
                currentTrip.setSpeedMax(command.getMetricSpeed());
            } else if (cmdID.equals(AvailableCommandNames.ENGINE_RPM.toString())) {
                RPMCommand command = (RPMCommand) job.getCommand();
                currentTrip.setEngineRpmMax(command.getRPM());
            } else if (cmdID.endsWith(AvailableCommandNames.ENGINE_RUNTIME.toString())) {
                RuntimeCommand command = (RuntimeCommand) job.getCommand();
                currentTrip.setEngineRuntime(command.getFormattedResult());
            }
        }
    }


    private static final int REQUEST_BLUETOOTH_PERMISSION = 1;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSION);
            } else {
                // Você já tem permissão, pode prosseguir com as operações Bluetooth.
            }
        }

        ActivityCompat.requestPermissions( this,    new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                }, 1
        );

        //get the spinner from the xml.
        Spinner filter = findViewById(R.id.date_filter);
        //create a list of items for the spinner.
        String[] items = new String[]{"última hora", "últimas 24h", "últimos 5 dias"};
        //create an adapter to describe how the items are displayed, adapters are used in several places in android.
        //There are multiple variations of this, but this is the basic variant.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        //set the spinners adapter to the previously created one.
        filter.setAdapter(adapter);


//        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> parent, View view,
            int pos, long id) {
                // An item was selected. You can retrieve the selected item using
                // parent.getItemAtPosition(pos)
                selected_date_filter = (String) parent.getItemAtPosition(pos);
                Toast.makeText(getApplicationContext(), selected_date_filter , Toast.LENGTH_LONG).show();
            }

            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
                Toast.makeText(getApplicationContext(), "nenhum filtro selecionado", Toast.LENGTH_LONG).show();
            }
        });



//        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT});
////                    , REQUEST_BLUETOOTH_CONNECT_PERMISSION);
//        }


        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                userEmail = null;
            } else {
                userEmail = extras.getString("userEmail");
            }
        } else {
            userEmail = (String) savedInstanceState.getSerializable("userEmail");
        }

        mLocationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mGnssStatusCallback = new GnssStatus.Callback() {
                // TODO: add your code here!
            };
        }

//        user_email = prefs.getString(ConfigActivity.UPLOAD_URL_KEY, "");
//        Log.d(TAG, "user email: " + user_email);

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendDataToAws();
                handler.postDelayed(this,30 * 1000);
            }
        },20000);

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null)
            bluetoothDefaultIsEnable = btAdapter.isEnabled();


        // get Orientation sensor
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
        if (sensors.size() > 0)
            orientSensor = sensors.get(0);
        else
            showDialog(NO_ORIENTATION_SENSOR);

        sensors = sensorManager.getSensorList(Sensor.TYPE_ROTATION_VECTOR);
        if (sensors.size() > 0)
            rotationSensor = sensors.get(0);
        else {
            throw new RuntimeException();
        }

        sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
        if (sensors.size() > 0)
            accelerationSensor = sensors.get(0);
        else
            showDialog(NO_GPS_SUPPORT);

        sensors = sensorManager.getSensorList(Sensor.TYPE_GRAVITY);
        if (sensors.size() > 0)
            gravitySensor = sensors.get(0);
        else {
            throw new RuntimeException();
//            showDialog(NO_GPS_SUPPORT);
        }

        sensors = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
        if (sensors.size() > 0)
            magneticFieldSensor = sensors.get(0);
        else {
            throw new RuntimeException();
//            showDialog(NO_GPS_SUPPORT);
        }

//        sensors = sensorManager.getSensorList(Sensor.TYPE_HEADING);
//        if (sensors.size() > 0)
//            headingSensor = sensors.get(0);
//        else {
//            throw new RuntimeException();
////            showDialog(NO_GPS_SUPPORT);
//        }

        // create a log instance for use by this application
        triplog = TripLog.getInstance(this.getApplicationContext());

        obdStatusTextView.setText(getString(R.string.status_obd_disconnected));

//        setContentView(R.layout.activity_main);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
        initViewsAndListener();
        if (checkPermissions()) {
            initLocationUpdate();
        }

        Button create_pdf = findViewById(R.id.create_pdf);
        create_pdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "funcionou", Toast.LENGTH_LONG).show();

                Calendar c = Calendar.getInstance();
                Date currentTime = c.getTime();

                c.setTime(currentTime);
                Date dateBegFilter = new Date();

                if (selected_date_filter == "última hora") {
                    c.add(Calendar.HOUR, -1);
                }else if (selected_date_filter == "últimas 24h") {
                    c.add(Calendar.DATE, -1);
                }else if (selected_date_filter == "últimos 5 dias") {
                    c.add(Calendar.DATE, -5);
                }else{
                    c.add(Calendar.HOUR, -1);
                }
                dateBegFilter = c.getTime();

                JSONObject jsonObjPDF = new JSONObject();
                try {
                    jsonObjPDF.put("date_beg", fmt.format(dateBegFilter));
                    jsonObjPDF.put("date_end", fmt.format(currentTime));
                    jsonObjPDF.put("user_token", userEmail);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

//                createPDFRequest.put(jsonObjPDF);
//                createPDFLambda(jsonObjPDF);

            }
        });

/*
        Preference button = findPreference(getString(R.string.myCoolButton));
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //code for what you want it to do
                return true;
            }
        });
*/
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mLocService != null) {
            mLocService.removeGpsStatusListener(this);
            mLocService.removeUpdates(this);
        }

        releaseWakeLockIfHeld();
        if (isServiceBound) {
            doUnbindService();
        }

        endTrip();

        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null && btAdapter.isEnabled() && !bluetoothDefaultIsEnable) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

            btAdapter.disable();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Pausing..");
        releaseWakeLockIfHeld();

        if (toast != null) {
            toast.cancel();
        }
    }

    private SensorEventListener magneticFieldListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event)
        {
            if(!isDataAcquisitionEnabled) return;

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magnetic_field_vec = event.values.clone();
            }

            if (gravity_vec != null && magnetic_field_vec != null) {

                /* Create rotation Matrix */
                float[] rotationMatrix = new float[9];
                if (SensorManager.getRotationMatrix(rotationMatrix, null,
                        gravity_vec, magnetic_field_vec)) {

                    /* Compensate device orientation */
                    // http://android-developers.blogspot.de/2010/09/one-screen-turn-deserves-another.html
                    float[] remappedRotationMatrix = new float[9];
                    switch (getWindowManager().getDefaultDisplay()
                            .getRotation()) {
                        case Surface.ROTATION_0:
                            SensorManager.remapCoordinateSystem(rotationMatrix,
                                    SensorManager.AXIS_X, SensorManager.AXIS_Y,
                                    remappedRotationMatrix);
                            break;
                        case Surface.ROTATION_90:
                            SensorManager.remapCoordinateSystem(rotationMatrix,
                                    SensorManager.AXIS_Y,
                                    SensorManager.AXIS_MINUS_X,
                                    remappedRotationMatrix);
                            break;
                        case Surface.ROTATION_180:
                            SensorManager.remapCoordinateSystem(rotationMatrix,
                                    SensorManager.AXIS_MINUS_X,
                                    SensorManager.AXIS_MINUS_Y,
                                    remappedRotationMatrix);
                            break;
                        case Surface.ROTATION_270:
                            SensorManager.remapCoordinateSystem(rotationMatrix,
                                    SensorManager.AXIS_MINUS_Y,
                                    SensorManager.AXIS_X, remappedRotationMatrix);
                            break;
                    }

                    /* Calculate Orientation */
                    float results[] = new float[3];
                    SensorManager.getOrientation(remappedRotationMatrix,
                            results);

                    /* Get measured value */
                    float current_measured_bearing = (float) (results[0] * 180 / Math.PI);
                    if (current_measured_bearing < 0) {
                        current_measured_bearing += 360;
                    }

                    /* Smooth values using a 'Low Pass Filter' */
                    current_measured_bearing = current_measured_bearing
                            + SMOOTHING_FACTOR_COMPASS
                            * (current_measured_bearing - compass_last_measured_bearing);

                    /* Update normal output */
//                    visual_compass_value.setText(String.valueOf(Math
//                            .round(current_bearing))
//                            + getString(R.string.degrees));

                    /*
                     * Update variables for next use (Required for Low Pass
                     * Filter)
                     */
                    compass_last_measured_bearing = current_measured_bearing;


                    Date currentTime = Calendar.getInstance().getTime();


                    if (lastBearingUpdate == null) {
                        lastBearingUpdate = new Date();
                        lastBearingUpdate.setTime(currentTime.getTime() - 2000 * minMillisBetweenData);
                    }

                    long diffInMillis = currentTime.getTime() - lastBearingUpdate.getTime();
//                    long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);

                    if (diffInMillis <= minMillisBetweenData) return;
                    lastBearingUpdate = currentTime;

                    DecimalFormat df = new DecimalFormat("0.00");
                    String bearing_string = df.format(current_measured_bearing);

                    JSONArray jsonContent = new JSONArray();
                    jsonContent.put(bearing_string);

                    String contentString = jsonContent.toString();
                    Log.d(TAG, "obd.pires.data: Getting bearing data: " + contentString);
                    writeDataToFile("DELETEME_BEARING.txt", fmt.format(currentTime) + " " + contentString);
                }
            }
        }
    };

    /**
     * If lock is held, release. Lock will be held when the service is running.
     */
    private void releaseWakeLockIfHeld() {
        if (wakeLock.isHeld())
            wakeLock.release();
    }

    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resuming..");
        sensorManager.registerListener(orientListener, orientSensor,
                SensorManager.SENSOR_DELAY_UI);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "obdapp:debug");

        sensorManager.registerListener(rotationListener, rotationSensor,
                SensorManager.SENSOR_DELAY_UI);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "obdapp:debug");

        sensorManager.registerListener(accelerationListener, accelerationSensor,
                SensorManager.SENSOR_DELAY_UI);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "obdapp:debug");

        sensorManager.registerListener(gravityListener, gravitySensor,
                SensorManager.SENSOR_DELAY_UI);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "obdapp:debug");

        sensorManager.registerListener(magneticFieldListener, magneticFieldSensor,
                SensorManager.SENSOR_DELAY_UI);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "obdapp:debug");

//        /* Initialize the magnetic field sensor */
//        if (magneticFieldSensor != null) {
//            Log.i(TAG, "Magnetic field sensor available. (TYPE_MAGNETIC_FIELD)");
//            sensorManager.registerListener(magneticFieldListener,
//                    magneticFieldSensor, SensorManager.SENSOR_DELAY_GAME);
//        } else {
//            Log.i(TAG,
//                    "Magnetic field sensor unavailable. (TYPE_MAGNETIC_FIELD)");
//            throw new RuntimeException();
//        }


//        sensorManager.registerListener(headingListener, headingSensor,
//                SensorManager.SENSOR_DELAY_UI);
//        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
//                "obdapp:debug");

        // get Bluetooth device
        final BluetoothAdapter btAdapter = BluetoothAdapter
                .getDefaultAdapter();

        preRequisites = btAdapter != null && btAdapter.isEnabled();
        if (!preRequisites && prefs.getBoolean(ConfigActivity.ENABLE_BT_KEY, false)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            preRequisites = btAdapter != null && btAdapter.enable();
        }

        gpsInit();

        if (!preRequisites) {
            showDialog(BLUETOOTH_DISABLED);
            btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
        } else {
            btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
        }
    }

    private void updateConfig() {
        startActivity(new Intent(this, ConfigActivity.class));
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, START_LIVE_DATA, 0, getString(R.string.menu_start_live_data));
        menu.add(0, STOP_LIVE_DATA, 0, getString(R.string.menu_stop_live_data));
//        menu.add(0, GET_DTC, 0, getString(R.string.menu_get_dtc));
//        menu.add(0, TRIPS_LIST, 0, getString(R.string.menu_trip_list));
        menu.add(0, SETTINGS, 0, getString(R.string.menu_settings));
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case START_LIVE_DATA:
                startLiveData();
                return true;
            case STOP_LIVE_DATA:
                stopLiveData();
                return true;
            case SETTINGS:
                updateConfig();
                return true;
//            case GET_DTC:
//                getTroubleCodes();
//                return true;
//            case TRIPS_LIST:
//                startActivity(new Intent(this, TripListActivity.class));
//                return true;
        }
        return false;
    }

    private void getTroubleCodes() {
        startActivity(new Intent(this, TroubleCodesActivity.class));
    }

    private void startLiveData() {
        Log.d(TAG, "Starting live data..");

        tl.removeAllViews(); //start fresh
        doBindService();

        currentTrip = triplog.startTrip();
        if (currentTrip == null)
            showDialog(SAVE_TRIP_NOT_AVAILABLE);

        // start command execution
        new Handler().post(mQueueCommands);

        if (prefs.getBoolean(ConfigActivity.ENABLE_GPS_KEY, false))
            gpsStart();
        else
            gpsStatusTextView.setText(getString(R.string.status_gps_not_used));

        // screen won't turn off until wakeLock.release()
        wakeLock.acquire();

        if (prefs.getBoolean(ConfigActivity.ENABLE_FULL_LOGGING_KEY, false)) {

            // Create the CSV Logger
            long mils = System.currentTimeMillis();
            SimpleDateFormat sdf = new SimpleDateFormat("_dd_MM_yyyy_HH_mm_ss");

            try {
                myCSVWriter = new LogCSVWriter("Log" + sdf.format(new Date(mils)).toString() + ".csv",
                        prefs.getString(ConfigActivity.DIRECTORY_FULL_LOGGING_KEY,
                                getString(R.string.default_dirname_full_logging))
                );
            } catch (FileNotFoundException | RuntimeException e) {
                Log.e(TAG, "Can't enable logging to file.", e);
            }
        }

        if(isDataIndependentOfBluetoothConnectionEnabled || (service != null && service.isRunning()))
        {
            isDataAcquisitionEnabled = true;
        }
    }

    private void stopLiveData() {
        Log.d(TAG, "Stopping live data..");

        gpsStop();

        doUnbindService();
        endTrip();

        releaseWakeLockIfHeld();

        final String devemail = prefs.getString(ConfigActivity.DEV_EMAIL_KEY, null);
        if (devemail != null && !devemail.isEmpty()) {
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            ObdGatewayService.saveLogcatToFile(getApplicationContext(), devemail);
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Where there issues?\nThen please send us the logs.\nSend Logs?").setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show();
        }

        if (myCSVWriter != null) {
            myCSVWriter.closeLogCSVWriter();
        }

        if(isDataIndependentOfBluetoothConnectionEnabled || service == null || !service.isRunning())
        {
            isDataAcquisitionEnabled = false;
        }
    }

    protected void endTrip() {
        if (currentTrip != null) {
            currentTrip.setEndDate(new Date());
            triplog.updateRecord(currentTrip);
        }
    }

    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        switch (id) {
            case NO_BLUETOOTH_ID:
                build.setMessage(getString(R.string.text_no_bluetooth_id));
                return build.create();
            case BLUETOOTH_DISABLED:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, R.string.text_bluetooth_disabled, Toast.LENGTH_LONG).show();
                    throw new RuntimeException("bluetooth is disabled");
                }
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return build.create();
            case NO_ORIENTATION_SENSOR:
                build.setMessage(getString(R.string.text_no_orientation_sensor));
                return build.create();
            case NO_GPS_SUPPORT:
                build.setMessage(getString(R.string.text_no_gps_support));
                return build.create();
            case SAVE_TRIP_NOT_AVAILABLE:
                build.setMessage(getString(R.string.text_save_trip_not_available));
                return build.create();
        }
        return null;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem startItem = menu.findItem(START_LIVE_DATA);
        MenuItem stopItem = menu.findItem(STOP_LIVE_DATA);
        MenuItem settingsItem = menu.findItem(SETTINGS);
//        MenuItem getDTCItem = menu.findItem(GET_DTC);

        if (service != null && service.isRunning()) {
//            getDTCItem.setEnabled(false);
            startItem.setEnabled(false);
            stopItem.setEnabled(true);
            settingsItem.setEnabled(false);
        } else {
//            getDTCItem.setEnabled(true);
            stopItem.setEnabled(false);
            startItem.setEnabled(true);
            settingsItem.setEnabled(true);
        }

        return true;
    }

    private void addTableRow(String id, String key, String val) {

        TableRow tr = new TableRow(this);
        MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(TABLE_ROW_MARGIN, TABLE_ROW_MARGIN, TABLE_ROW_MARGIN,
                TABLE_ROW_MARGIN);
        tr.setLayoutParams(params);

        TextView name = new TextView(this);
        name.setGravity(Gravity.RIGHT);
        name.setText(key + ": ");
        TextView value = new TextView(this);
        value.setGravity(Gravity.LEFT);
        value.setText(val);
        value.setTag(id);
        tr.addView(name);
        tr.addView(value);
        tl.addView(tr, params);
    }

    /**
     *
     */
    private void queueCommands() {
        if (isServiceBound) {
            for (ObdCommand Command : ObdConfig.getCommands()) {
                if (prefs.getBoolean(Command.getName(), true))
                    service.queueJob(new ObdCommandJob(Command));
            }
        }
    }

    private void doBindService() {
        if (!isServiceBound) {
            Log.d(TAG, "Binding OBD service..");
            if (preRequisites) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connecting));
                Intent serviceIntent = new Intent(this, ObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            } else {
                btStatusTextView.setText(getString(R.string.status_bluetooth_disabled));
                Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
                bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
            }
        }
    }

    private void doUnbindService() {
        if (isServiceBound) {
            if (service.isRunning()) {
                service.stopService();
                if (preRequisites)
                    btStatusTextView.setText(getString(R.string.status_bluetooth_ok));
            }
            Log.d(TAG, "Unbinding OBD service..");
            unbindService(serviceConn);
            isServiceBound = false;
            obdStatusTextView.setText(getString(R.string.status_obd_disconnected));
        }
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    public void onGpsStatusChanged(int event) {

        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                gpsStatusTextView.setText(getString(R.string.status_gps_started));
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                gpsStatusTextView.setText(getString(R.string.status_gps_fix));
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                btStatusTextView.setText(getString(R.string.status_bluetooth_connected));
            } else {
                Toast.makeText(this, R.string.text_bluetooth_disabled, Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private synchronized void gpsStart() {
        if (!mGpsIsStarted && mLocProvider != null && mLocService != null && mLocService.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mLocService.requestLocationUpdates(mLocProvider.getName(), getGpsUpdatePeriod(prefs), getGpsDistanceUpdatePeriod(prefs), this);
            mGpsIsStarted = true;
        } else {
            gpsStatusTextView.setText(getString(R.string.status_gps_no_support));
        }
    }

    private synchronized void gpsStop() {
        if (mGpsIsStarted) {
            mLocService.removeUpdates(this);
            mGpsIsStarted = false;
            gpsStatusTextView.setText(getString(R.string.status_gps_stopped));
        }
    }

    /**
     * Uploading asynchronous task
     */
//    private class UploadAsyncTask extends AsyncTask<ObdReading, Void, Void> {
//
//        @Override
//        protected Void doInBackground(ObdReading... readings) {
//            Log.d(TAG, "Uploading " + readings.length + " readings..");
//            // instantiate reading service client
//            final String endpoint = prefs.getString(ConfigActivity.UPLOAD_URL_KEY, "");
//            RestAdapter restAdapter = new RestAdapter.Builder()
//                    .setEndpoint(endpoint)
//                    .build();
//            ObdService service = restAdapter.create(ObdService.class);
//            // upload readings
//            for (ObdReading reading : readings) {
//                try {
//                    Response response = service.uploadReading(reading);
//                    assert response.getStatus() == 200;
//                } catch (RetrofitError re) {
//                    Log.e(TAG, re.toString());
//                }
//
//            }
//            Log.d(TAG, "Done");
//            return null;
//        }
//
//    }

    private void initViewsAndListener() {
        toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
//        tvLocationDetails=findViewById(R.id.tvLocationDetails);
//        mainLayout=findViewById(R.id.mainLayout);
//        findViewById(R.id.btnGetLocation).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if (checkPermissions()) {
//                    initLocationUpdate();
//                }
//            }
//        });
    }

    @SuppressLint("MissingPermission")
    //Start Location update as define intervals
    private void initLocationUpdate() {

        // Check API revision for New Location Update
        //https://developers.google.com/android/guides/releases#june_2017_-_version_110

        //init location request to start retrieving location update
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval( UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        //Create LocationSettingRequest object using locationRequest
        LocationSettingsRequest.Builder locationSettingBuilder =new LocationSettingsRequest.Builder();
        locationSettingBuilder.addLocationRequest(locationRequest);
        LocationSettingsRequest locationSetting = locationSettingBuilder.build();

        //Need to check whether location settings are satisfied
        SettingsClient settingsClient= LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSetting);
        //More info :  // https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient


        // new Google API SDK v11 uses getFusedLocationProviderClient(this)
        FusedLocationProviderClient fusedLocationProviderClient= LocationServices.getFusedLocationProviderClient(this);
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback(){

                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        //super.onLocationResult(locationResult);
                        if (locationResult != null) {
                            onLocationChanged(locationResult.getLastLocation());
                        }
                    }

                    @Override
                    public void onLocationAvailability(LocationAvailability locationAvailability) {
                        super.onLocationAvailability(locationAvailability);
                    }
                },
                Looper.myLooper());

    }
    public void onLocationChanged(Location location)
    {
        if(!isDataAcquisitionEnabled) return;

        mLastLocation = location;

        String latitude = Double.toString(location.getLatitude());
        String longitude = Double.toString(location.getLongitude());

        // New location has now been determined
        String msg = "obd.pires.data: Updated Location: " + latitude + "," + longitude;
        Log.i(TAG, msg);
        //tvLocationDetails.setText(msg);
        //toast.setText(msg);
        //toast.show();

        Date currentTime = Calendar.getInstance().getTime();


        if (lastUpdateTimeGPS == null) {
            lastUpdateTimeGPS = new Date();
            lastUpdateTimeGPS.setTime(currentTime.getTime() - 2000 * minMillisBetweenData);
        }

        long diffInMillis = currentTime.getTime() - lastUpdateTimeGPS.getTime();
//        long diffSeconds = TimeUnit.SECONDS.convert(diffInMillis, TimeUnit.MILLISECONDS);

        if (diffInMillis <= minMillisBetweenData) return;
        lastUpdateTimeGPS = currentTime;

        JSONObject jsonObjLocation = new JSONObject();
        try {
            jsonObjLocation.put("timestamp", fmt.format(currentTime));
            jsonObjLocation.put("latitude", latitude);
            jsonObjLocation.put("longitude", longitude);
            jsonObjLocation.put("user_token", userEmail);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        locationAddRequests.put(jsonObjLocation);

        // backup

        JSONArray jsonGPS = new JSONArray();
        jsonGPS.put(latitude);
        jsonGPS.put(longitude);

        String gpsString = jsonGPS.toString();
        Log.d(TAG, "obd.pires.data: Getting GPS data: " + gpsString);
        writeDataToFile("DELETEME_GPS.txt", fmt.format(currentTime) + " " + gpsString);

    }

    private boolean checkPermissions(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            requestPermissions();
            return false;
        }
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                FINE_LOCATION_REQUEST);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // A permissão foi concedida, você pode continuar com as operações Bluetooth.
            } else {
                // A permissão foi negada, trate de acordo.
                Toast.makeText(this, "Problema com permissões", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == FINE_LOCATION_REQUEST) {
            // Received permission result for Location permission.
            Log.i(TAG, "Received response for Location permission request.");

            // Check if the only required permission has been granted
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission has been granted, preview can be displayed
                Log.i(TAG, "Location permission has now been granted. Now call initLocationUpdate");
                initLocationUpdate();
            } else {
//                Snackbar.make(mainLayout, R.string.rational_location_permission,
//                                Snackbar.LENGTH_INDEFINITE)
//                        .setAction(getString(R.string.ok), new View.OnClickListener() {
//                            @Override
//                            public void onClick(View view) {
//                                requestPermissions();
//                            }
//                        }).show();

            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    @Override
    protected void onStop() {
        if (toast != null) {
            toast.cancel();
        }

        mLocationManager.removeUpdates(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mLocationManager.unregisterGnssStatusCallback(
                    mGnssStatusCallback
            );
        }
        super.onStop();
    }
}
