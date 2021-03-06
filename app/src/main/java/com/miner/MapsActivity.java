package com.miner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.fr3ts0n.androbd.plugin.mgr.PluginManager;
import com.fr3ts0n.ecu.EcuDataItem;
import com.fr3ts0n.ecu.EcuDataPv;
import com.fr3ts0n.ecu.prot.obd.ElmProt;
import com.fr3ts0n.ecu.prot.obd.ObdProt;
import com.fr3ts0n.pvs.IndexedProcessVar;
import com.fr3ts0n.pvs.PvChangeEvent;
import com.fr3ts0n.pvs.PvChangeListener;
import com.fr3ts0n.pvs.PvList;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.miner.adapter.TimeListAdapter;
import com.miner.bean.AccelerationBean;
import com.miner.bean.GPSBean;
import com.miner.bean.LightBean;
import com.miner.listener.OnSocketStateListener;
import com.miner.obd.BtCommService;
import com.miner.obd.CommService;
import com.miner.socket.SocketClient;
import com.miner.utils.DateUtil;
import com.miner.utils.LogTools;
import com.miner.utils.PermissionUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.miner.SettingsActivity.ELM_TIMING_SELECT;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener, ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener, LocationListener, GpsStatus.Listener, PvChangeListener, PropertyChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {


    private Timer logTimer = new Timer();

    /**
     * operating modes
     */
    public enum MODE {
        OFFLINE,//< OFFLINE mode
        ONLINE,    //< ONLINE mode
    }

    /**
     * Preselection types
     */
    public enum PRESELECT {
        LAST_DEV_ADDRESS,
        LAST_ECU_ADDRESS,
        LAST_SERVICE
    }


    /**
     * dialog builder
     */
    private static AlertDialog.Builder dlgBuilder;

    /**
     * app preferences ...
     */
    protected static SharedPreferences prefs;
    /**
     * Member object for the BT comm services
     */
    private static CommService mCommService = null;
    /**
     * Local Bluetooth adapter
     */
    private static BluetoothAdapter mBluetoothAdapter = null;
    /**
     * Name of the connected BT device
     */
    private static String mConnectedDeviceName = null;

    /**
     * current OBD service
     */
    private int obdService = ElmProt.OBD_SVC_NONE;
    /**
     * current operating mode
     */
    private MODE mode = MODE.OFFLINE;

    /**
     * empty string set as default parameter
     */
    static final Set<String> emptyStringSet = new HashSet<String>();
    /**
     * initial state of bluetooth adapter
     */
    private static boolean initialBtStateEnabled = false;


    /**
     * internal Intent request codes
     */
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_CODE = 2;
    private static final int READ_EXTERNAL_STORAGE_CODE = 3;


    private static final int DISPLAY_UPDATE_TIME = 200;
    public static final String KEEP_SCREEN_ON = "keep_screen_on";
    public static final String ELM_CUSTOM_INIT_CMDS = "elm_custom_init_cmds";
    /**
     * Key names for preferences
     */
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String PREF_USE_LAST = "USE_LAST_SETTINGS";
    public static final String ELM_ADAPTIVE_TIMING = ELM_TIMING_SELECT;
    public static final String ELM_RESET_ON_NRC = "elm_reset_on_nrc";
    public static final String MEASURE_SYSTEM = "measure_system";
    public static final String PREF_DATA_DISABLE_MAX = "data_disable_max";

    /**
     * Message types sent from the BluetoothChatService Handler
     */
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_FILE_READ = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_DATA_ITEMS_CHANGED = 6;
    public static final int MESSAGE_UPDATE_VIEW = 7;
    public static final int MESSAGE_OBD_STATE_CHANGED = 8;
    public static final int MESSAGE_OBD_NUMCODES = 9;
    public static final int MESSAGE_OBD_ECUS = 10;
    public static final int MESSAGE_OBD_NRC = 11;

    /**
     * Timer for display updates
     */
    private static Timer updateTimer = new Timer();

    @BindView(R.id.cb_traffic)
    CheckBox cbTraffic;
    @BindView(R.id.tv_pop)
    TextView tvPop;
    @BindView(R.id.tv_gps_lat)
    TextView tvGpsLat;
    @BindView(R.id.tv_gps_lng)
    TextView tvGpsLng;
    @BindView(R.id.tv_gps_alt)
    TextView tvGpsAlt;
    @BindView(R.id.tv_gps_speed)
    TextView tvGpsSpeed;
    @BindView(R.id.tv_gps_bearing)
    TextView tvGpsBearing;
    @BindView(R.id.tv_acc_x)
    TextView tvAccX;
    @BindView(R.id.tv_acc_y)
    TextView tvAccY;
    @BindView(R.id.tv_acc_z)
    TextView tvAccZ;
    @BindView(R.id.tv_light_x)
    TextView tvLightX;
    @BindView(R.id.tv_record)
    TextView tvRecord;
    @BindView(R.id.tv_clear_map)
    TextView tvClearMap;
    @BindView(R.id.dw_layout)
    DrawerLayout dwLayout;
    @BindView(R.id.iv_setting)
    ImageView ivSetting;
    @BindView(R.id.tv_ip)
    TextView ipset;
    @BindView(R.id.tv_frequency)
    TextView tvFrequency;
    @BindView(R.id.lv_frequency)
    ListView lvFrequency;
    @BindView(R.id.socketstate)
    TextView socketstate;
    @BindView(R.id.tv_command2)
    TextView tvCommand2;
    @BindView(R.id.probabilty)
    TextView probabilty;
    @BindView(R.id.cameraFrequency)
    TextView cameraFrequency;
    @BindView(R.id.config)
    TextView config;
    @BindView(R.id.whitebalance)
    TextView whitebalance;
    @BindView(R.id.imagesize)
    TextView imagesize;
    @BindView(R.id.tv_obd)
    TextView tvObdprotocols;
    @BindView(R.id.obdStatus)
    TextView obdStatus;

    @BindView(R.id.obdLongitude)
    TextView obdLongitude;
    @BindView(R.id.obdLatitude)
    TextView obdLatitude;
    @BindView(R.id.obdAltitude)
    TextView obdAltitude;
    @BindView(R.id.rpm)
    TextView rpm;
    @BindView(R.id.speed)
    TextView speed;
    @BindView(R.id.exposure)
    TextView exposure;
    @BindView(R.id.logLevel)
    TextView logLevel;
    @BindView(R.id.killProgram)
    TextView killProgram;
    @BindView(R.id.dbTableNumber)
    TextView dbTableNumber;
    @BindView(R.id.envsensorFrequency)
    TextView envsensorFrequency;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mPermissionDenied = false;
    private GoogleMap mMap;
    private Handler handler;
    private double lat = 0;
    private double lng = 0;
    private int frequency = 1;//传感器更新频率，单位秒
    private Timer timer;
    private TimerTask task;
    private Timer timer_acc;
    private TimerTask task_acc;
    private PopupWindow popupWindow;
    private LocationManager lm;
    private SensorManager mSensorManager;
    private String bestProvider;
    private Sensor mAccelerometer;
    private Sensor mLightSensor;
    private GPSBean gpsBean = new GPSBean();//GPS
    private GPSBean googleGps = new GPSBean();//GoogleGPS
    private LightBean lightBean = new LightBean();//Light
    private AccelerationBean accelerationBean = new AccelerationBean();//acc
    private JSONArray jsonArray = new JSONArray();
    private boolean isExists;// sd card exist
    private String filePath;
    private boolean isRecord;//is it record
    private boolean isOpen = false;//Whether the drawer is in the open state
    private List<Integer> data;

    private String host = "192.168.111.103";
    private int port = 7777;
    private SocketClient socketClient;
    //mobile phone model
    private String systemModel;
    private String deviceID;
    private boolean ishow;//Whether the side sidebar modifies the frequency of the camera
    private long mCurrentTime;
    private Location mLastLocation;

    private double obdlon = 0;
    private double obdlat = 0;
    private double obdalt = 0;

    private PvChangeEvent pvEvent = null;

    @Override
    protected void onResume() {
        super.onResume();

    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        initAll();

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1:
                        try {
                            if (isExists) {
                                dealBean();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        if (lat > 0 && lng > 0) {
                            LatLng myLatLng = new LatLng(lat, lng);
                            if (mMap != null) {
                                mMap.addMarker(new MarkerOptions()
                                        .position(myLatLng)
                                        .flat(true)//Marking planarization
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));
                            }
                        }
                        Log.i("location", ":" + lat + ";" + lng);
                        break;
                    case 2:
                        tvAccX.setText("m_AccX：" + keep3decimal(accelerationBean.getX()));
                        tvAccY.setText("m_AccY：" + keep3decimal(accelerationBean.getY()));
                        tvAccZ.setText("m_AccZ: " + keep3decimal(accelerationBean.getZ()));
                        break;
                    case 3:
                        String state = (String) msg.obj;
                        socketstate.setText("TX2 Status:" + state);
                        break;
                    case 4:
                        String dbTableNum = (String) msg.obj;
                        dbTableNumber.setText("Recognized Objects:" + dbTableNum);
                        break;
                    default:
                        break;
                }
            }
        };
        if (null != mSensorManager) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(sensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            mSensorManager.registerListener(sensorEventListener, mLightSensor, SensorManager.SENSOR_DELAY_UI);
        }
        updateAaccTimer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        logTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                String s = MapsActivity.this.getExternalFilesDir("miner").getAbsolutePath();
                File saveFile = new File(s);
                File[] files = saveFile.listFiles();
                List<Long> num = new ArrayList<>();
                if (files.length > 9) {
                    for (File file : files) {
                        String s1 = file.getName().split("\\.")[0];
                        num.add(Long.parseLong(s1));
                    }
                    Collections.sort(num);
                    int k = 0;
                    for (int i = num.size() - 1; i >= 0; i--) {
                        if (k > 9) {
                            File sf = new File(s, num.get(i) + ".log");
                            sf.delete();
                        }
                        k++;
                    }

                }
            }
        }, 0, 5000);
    }

    /**
     * init
     */
    private void initAll() {
        dlgBuilder = new AlertDialog.Builder(this);
        // get preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // register for later changes
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, null);
        setDataListeners();
        // automate elm status display
        CommService.elm.addPropertyChangeListener(this);
        switch (CommService.medium) {
            case BLUETOOTH:
                // Get local Bluetooth adapter
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                // If BT is not on, request that it be enabled.
                if (mBluetoothAdapter != null) {
                    // remember initial bluetooth state
                    initialBtStateEnabled = mBluetoothAdapter.isEnabled();
                    if (!initialBtStateEnabled) {
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    }
                }
                break;
        }

        systemModel = Build.MODEL;
        initData();
        initListener();
        setWriteReadPermission();
        initGpsAndSensor();
        conn();
    }


    private void initGpsAndSensor() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        deviceID = telephonyManager.getDeviceId();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        isExists = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (isExists) {
            filePath = Environment.getExternalStorageDirectory().getPath() + "/Miner";
        } else {
            Toast.makeText(MapsActivity.this, "No SDCard!", Toast.LENGTH_SHORT).show();
        }
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Determine whether GPS is normally started
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please open the GPS navigation...", Toast.LENGTH_SHORT).show();
            // Return to open the GPS navigation settings interface
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, 0);
            return;
        }

        // Setting up query conditions for obtaining geographic location information
        bestProvider = lm.getBestProvider(getCriteria(), true);

        // get location information
        // 如果不设置查询要求，getLastKnownLocation方法传入的参数为LocationManager.GPS_PROVIDER
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Location location = lm.getLastKnownLocation(bestProvider);
        updateView(location, false);
        // Listening state
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        lm.addGpsStatusListener(gpsListener);
        // 绑定监听，有4个参数
        // 参数1，设备：有GPS_PROVIDER和NETWORK_PROVIDER两种
        // 参数2，位置信息更新周期，单位毫秒
        // 参数3，位置变化最小距离：当位置距离变化超过此值时，将更新位置信息
        // 参数4，监听
        // 备注：参数2和3，如果参数3不为0，则以参数3为准；参数3为0，则通过时间来定时更新；两者为0，则随时刷新

        // 1秒更新一次，或最小位移变化超过1米更新一次；
        // 注意：此处更新准确度非常低，推荐在service里面启动一个Thread，在run中sleep(10000);然后执行handler.sendMessage(),更新位置
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
    }

    private void conn() {
        socketClient = new SocketClient(host, port, filePath, onSocketStateListener);
    }

    OnSocketStateListener onSocketStateListener = new OnSocketStateListener() {
        @Override
        public void socketstate(final String state) {
            Log.e(TAG, "socketstate: " + state);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 3;
                    message.obj = state;
                    handler.sendMessage(message);
                }
            });
        }

        @Override
        public void dbTableNumer(final String num) {
            Log.e(TAG, "dbTableNumber: " + num);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Message message = new Message();
                    message.what = 4;
                    message.obj = num;
                    handler.sendMessage(message);
                }
            });
        }
    };

    /**
     * init
     */
    private void initData() {
        data = new ArrayList<>();
        data.add(1);
        data.add(5);
        data.add(10);
        data.add(20);
        data.add(30);
        data.add(60);
    }

    /**
     * set spinner
     */
    private void initSpinner() {
        ListView listView = new ListView(this);
        TimeListAdapter adapter = new TimeListAdapter(this, data);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                tvPop.setText(getResources().getString(R.string.frequency));
                frequency = data.get(i);
                popupWindow.dismiss();
                if (isRecord) {
                    updateTimer();
                }
            }
        });
        popupWindow = new PopupWindow(listView, tvPop.getWidth(), ActionBar.LayoutParams.WRAP_CONTENT, true);
        // 取得popup窗口的背景图片
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.button_fliter_down);
        popupWindow.setBackgroundDrawable(drawable);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                // 关闭popup窗口
                popupWindow.dismiss();
            }
        });

    }

    /**
     * init listener
     */
    private void initListener() {
        tvRecord.setOnClickListener(this);
        tvClearMap.setOnClickListener(this);
        tvPop.setOnClickListener(this);
        ivSetting.setOnClickListener(this);
        ipset.setOnClickListener(this);
        tvFrequency.setOnClickListener(this);
        tvCommand2.setOnClickListener(this);
        config.setOnClickListener(this);
        probabilty.setOnClickListener(this);
        exposure.setOnClickListener(this);
        whitebalance.setOnClickListener(this);
        cameraFrequency.setOnClickListener(this);
        envsensorFrequency.setOnClickListener(this);
        logLevel.setOnClickListener(this);
        imagesize.setOnClickListener(this);
        killProgram.setOnClickListener(this);
        tvObdprotocols.setOnClickListener(this);
        TimeListAdapter adapter = new TimeListAdapter(this, data);
        lvFrequency.setAdapter(adapter);
        lvFrequency.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                frequency = data.get(i);
                if (isRecord) {
                    updateTimer();
                }
                switchDrawlayout();
            }
        });
        dwLayout.setDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                drawerView.setClickable(true);
                isOpen = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                isOpen = false;
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        enableMyLocation();
        Location mylocation = mMap.getMyLocation();
        if (mylocation != null && mylocation.getLatitude() > 0 && mylocation.getLongitude() > 0) {
            LatLng myLatLng = new LatLng(mylocation.getLatitude(), mylocation.getLongitude());
            mMap.addMarker(new MarkerOptions().position(myLatLng).title("Marker in MyLocation"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(myLatLng));
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 15f));
        }
        updateTraffic();
        mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
            @Override
            public void onMyLocationChange(Location location) {
                updateView(location, true);
            }
        });

    }

    /**
     * 设置6.0以上读写权限
     */
    private void setWriteReadPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(MapsActivity.this, WRITE_EXTERNAL_STORAGE_CODE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, false);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(MapsActivity.this, READ_EXTERNAL_STORAGE_CODE,
                    Manifest.permission.READ_EXTERNAL_STORAGE, false);
        }
    }


    /**
     * 路况点击事件
     */
    public void onTrafficToggled(View view) {
        updateTraffic();
    }

    private void updateTraffic() {
        if (!checkReady()) {
            return;
        }
        mMap.setTrafficEnabled(cbTraffic.isChecked());
    }

    private boolean checkReady() {
        if (mMap == null) {
            return false;
        }
        return true;
    }

    /**
     * timer
     */
    private void updateTimer() {
        if (timer != null && task != null) {
            timer.cancel();
            task.cancel();
        }
        timer = new Timer();
        task = new TimerTask() {

            @Override
            public void run() {
                handler.sendEmptyMessage(1);
            }
        };
        timer.schedule(task, 0, frequency * 1000);

    }

    private void updateAaccTimer() {
        if (timer_acc != null && timer_acc != null) {
            timer_acc.cancel();
            task_acc.cancel();
        }
        timer_acc = new Timer();
        task_acc = new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(2);
            }
        };
        timer_acc.schedule(task_acc, 0, 1000);
    }


    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(MapsActivity.this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, false);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Enable the my location layer if the permission has been granted.
                enableMyLocation();
            } else {
                // Display the missing permission error dialog when the fragments resume.
                mPermissionDenied = true;
            }
        } else if (requestCode == WRITE_EXTERNAL_STORAGE_CODE || requestCode == READ_EXTERNAL_STORAGE_CODE) {
            if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) || PermissionUtils.isPermissionGranted(permissions, grantResults,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                setWriteReadPermission();
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    /**
     * 传感器监听
     */
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {


            float xValue = sensorEvent.values[0];// Acceleration minus Gx on the x-axis
            float yValue = sensorEvent.values[1];//Acceleration minus Gy on the y-axis
            float zValue = sensorEvent.values[2];//Acceleration minus Gz on the z-axis


            switch (sensorEvent.sensor.getType()) {
                //                加速度传感器
                case Sensor.TYPE_ACCELEROMETER:
                    accelerationBean.setX(xValue);
                    accelerationBean.setY(yValue);
                    accelerationBean.setZ(zValue);
                    break;
                //                    光线传感器
                case Sensor.TYPE_LIGHT:
                    lightBean.setX(xValue);
                    tvLightX.setText("m_Light: " + xValue);
                    break;
                //                    温度传感器
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    break;
                //                    湿度传感器
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    break;
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    /**
     * 位置监听
     */
    private LocationListener locationListener = new LocationListener() {

        /**
         * 位置信息变化时触发
         */
        public void onLocationChanged(Location location) {
            updateView(location, false);
            Log.i(TAG, "时间：" + location.getTime());
            Log.i(TAG, "经度：" + location.getLongitude());
            Log.i(TAG, "纬度：" + location.getLatitude());
            Log.i(TAG, "海拔：" + location.getAltitude());
        }

        /**
         * GPS状态变化时触发
         */
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status) {
                // GPS状态为可见时
                case LocationProvider.AVAILABLE:
                    Log.i(TAG, "当前GPS状态为可见状态");
                    break;
                // GPS状态为服务区外时
                case LocationProvider.OUT_OF_SERVICE:
                    Log.i(TAG, "当前GPS状态为服务区外状态");
                    break;
                // GPS状态为暂停服务时
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.i(TAG, "当前GPS状态为暂停服务状态");
                    break;
            }
        }

        /**
         * GPS开启时触发
         */
        public void onProviderEnabled(String provider) {
            if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Location location = lm.getLastKnownLocation(provider);
            updateView(location, false);
        }

        /**
         * GPS禁用时触发
         */
        public void onProviderDisabled(String provider) {
            updateView(null, false);
        }

    };

    private String TAG = MapsActivity.class.getSimpleName();
    // 状态监听
    GpsStatus.Listener gpsListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
            switch (event) {
                // 第一次定位
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    Log.i(TAG, "第一次定位");
                    break;
                // 卫星状态改变
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    // 获取当前状态
                    if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    GpsStatus gpsStatus = lm.getGpsStatus(null);
                    // 获取卫星颗数的默认最大值
                    int maxSatellites = gpsStatus.getMaxSatellites();
                    // 创建一个迭代器保存所有卫星
                    Iterator<GpsSatellite> iters = gpsStatus.getSatellites()
                            .iterator();
                    int count = 0;
                    while (iters.hasNext() && count <= maxSatellites) {
                        GpsSatellite s = iters.next();
                        count++;
                    }
                    break;
                // 定位启动
                case GpsStatus.GPS_EVENT_STARTED:
                    Log.i(TAG, "定位启动");
                    break;
                // 定位结束
                case GpsStatus.GPS_EVENT_STOPPED:
                    Log.i(TAG, "定位结束");
                    break;
            }
        }

        ;
    };


    /**
     * 实时更新文本内容
     *
     * @param location
     */
    private void updateView(Location location, boolean isGoogle) {
        if (location != null) {
            lat = location.getLatitude();
            lng = location.getLongitude();
            if (!isGoogle) {
                //手机自带GPS信息
                gpsBean.setLongitude(keep3decimal(location.getLongitude()));
                gpsBean.setLatitude(keep3decimal(location.getLatitude()));
                gpsBean.setAltitude(keep3decimal(location.getAltitude()));
                gpsBean.setBearing(keep3decimal(location.getBearing()));
                gpsBean.setSpeed(location.getSpeed());
            } else {
                //GoogleGPS
                googleGps.setLongitude(keep3decimal(location.getLongitude()));
                googleGps.setLatitude(keep3decimal(location.getLatitude()));
                googleGps.setAltitude(keep3decimal(location.getAltitude()));
                googleGps.setBearing(keep3decimal(location.getBearing()));
                googleGps.setSpeed(location.getSpeed());
            }
            if (gpsBean.getLongitude() != 0 && gpsBean.getLatitude() != 0) {
                tvGpsLat.setText("m_Latitude: " + gpsBean.getLatitude());
                tvGpsLng.setText("m_Longitude: " + gpsBean.getLongitude());
                tvGpsAlt.setText("m_Altitude: " + gpsBean.getAltitude());
                if (mode == MODE.ONLINE) {
                    obdlon = gpsBean.getLongitude();
                    obdlat = gpsBean.getLatitude();
                    obdalt = gpsBean.getLongitude();
                }

                tvGpsSpeed.setText("m_Speed: " + gpsBean.getSpeed());
                tvGpsBearing.setText("m_Heading: " + gpsBean.getBearing());
            } else {
                tvGpsLat.setText("m_Latitude: " + googleGps.getLatitude());
                tvGpsLng.setText("m_Longitude: " + googleGps.getLongitude());
                tvGpsAlt.setText("m_Altitude: " + googleGps.getAltitude());
                tvGpsSpeed.setText("m_Speed: " + googleGps.getSpeed());
                tvGpsBearing.setText("m_Heading: " + googleGps.getBearing());
            }


        }

    }

    public double keep3decimal(double f) {
        DecimalFormat df = new DecimalFormat("#.000");
        return Double.parseDouble(df.format(f));
    }

    private JSONArray dealBean() throws JSONException {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("time", DateUtil.getDateTimeFromMillis(System.currentTimeMillis()));
            jsonObject.put("accx", accelerationBean.getX());
            jsonObject.put("accy", accelerationBean.getY());
            jsonObject.put("accz", accelerationBean.getZ());
            jsonObject.put("longitude", gpsBean.getLongitude());
            jsonObject.put("latitude", gpsBean.getLatitude());
            jsonObject.put("altitude", gpsBean.getAltitude());
            jsonObject.put("speed", gpsBean.getSpeed());
            jsonObject.put("bearing", gpsBean.getBearing());
            jsonObject.put("lightx", lightBean.getX());
            jsonObject.put("deviceID", deviceID);
            jsonObject.put("frequency", frequency);
            scorder(String.valueOf(jsonObject));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray;
    }

    /**
     * 返回查询条件
     *
     * @return
     */
    private Criteria getCriteria() {
        Criteria criteria = new Criteria();
        // 设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // 设置是否要求速度
        criteria.setSpeedRequired(true);
        // 设置是否允许运营商收费
        criteria.setCostAllowed(true);
        // 设置是否需要方位信息
        criteria.setBearingRequired(true);
        // 设置是否需要海拔信息
        criteria.setAltitudeRequired(true);
        // 设置对电源的需求
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        return criteria;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            if (isRecord) {
                scorder("0x03");
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean secureConnection = false;
        switch (requestCode) {
            // device is connected
            case REQUEST_CONNECT_DEVICE_SECURE:
                secureConnection = true;
                // no break here ...
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When BtDeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras().getString(
                            BtDeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // save reported address as last setting
                    prefs.edit().putString(PRESELECT.LAST_DEV_ADDRESS.toString(), address).apply();
                    connectBtDevice(address, secureConnection);
                } else {
                    setMode(MODE.OFFLINE);
                }
                break;

            // bluetooth enabled
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Start online mode
                    setMode(MODE.ONLINE);
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        try {
            // Reduce ELM power consumption by setting it to sleep
            CommService.elm.goToSleep();
            // wait until message is out ...
            Thread.sleep(100, 0);
        } catch (InterruptedException e) {
            // do nothing
        }

		/* don't listen to ELM data changes any more */
        removeDataListeners();
        // don't listen to ELM property changes any more
        CommService.elm.removePropertyChangeListener(this);

        // stop demo service if it was started
        setMode(MODE.OFFLINE);

        // stop communication service
        if (mCommService != null) mCommService.stop();

        // if bluetooth adapter was switched OFF before ...
        if (mBluetoothAdapter != null && !initialBtStateEnabled) {
            // ... turn it OFF again
            mBluetoothAdapter.disable();
        }
        super.onDestroy();
        if (mMap != null) {
            mMap.clear();
        }
        if (logTimer != null) {
            logTimer.cancel();
        }
        if (timer != null && task != null) {
            timer.cancel();
            task.cancel();
        }
        if (timer_acc != null && task_acc != null) {
            timer_acc.cancel();
            task_acc.cancel();
        }

        if (isRecord) {
            scorder("0x03");
        }
        if (socketClient != null) {
            socketClient.onDestroy();
        }
        handler.removeCallbacksAndMessages(null);
        stopGetData();


    }

    /**
     * 停止采集数据
     */
    private void stopGetData() {
        if (lm != null) {
            lm.removeUpdates(locationListener);
        }
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(sensorEventListener);
        }
    }

    /**
     * 抽屉打开/关闭
     *
     * @param
     */
    public void switchDrawlayout() {
        if (isOpen) {
            dwLayout.closeDrawer(Gravity.LEFT);
        } else {
            dwLayout.openDrawer(Gravity.LEFT);
        }
        isOpen = !isOpen;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.tv_record:
                if (!isRecord) {
                    if (gpsBean.getLongitude() == 0 && gpsBean.getLatitude() == 0
                            && googleGps.getLongitude() == 0 && googleGps.getLatitude() == 0) {
                        Toast.makeText(MapsActivity.this, "If you have no access to your location information, open your cell phone GPS", Toast.LENGTH_SHORT).show();
                    } else {
                        jsonArray = new JSONArray();
                        mCurrentTime = System.currentTimeMillis();
                        openCamera();
                        openenvsensor();
                        //获取数据
                        updateTimer();
                        setMode(MODE.ONLINE);
                        tvRecord.setText("Stop");
                        tvRecord.setTextColor(Color.RED);
                        isRecord = true;
                    }
                } else {
                    if (timer != null && task != null) {
                        timer.cancel();
                        task.cancel();
                    }
                    closeenvsensor();
                    closeCamera();
                    tvRecord.setText("Record");
                    tvRecord.setTextColor(getResources().getColor(R.color.ori_textcolor));
                    if (mCommService != null) mCommService.stop();
                    setMode(MODE.OFFLINE);
                    isRecord = false;
                }

                break;
            case R.id.tv_clear_map:
                //清空map
                if (mMap != null) {
                    mMap.clear();
                }
                break;
            case R.id.tv_pop:
                //下拉列表
                initSpinner();
                if (popupWindow != null && !popupWindow.isShowing()) {
                    popupWindow.showAsDropDown(tvPop, 0, 5);
                }
                break;
            case R.id.iv_setting:
                //Open the Drawer
                switchDrawlayout();
                break;
            case R.id.tv_command2:
                switchDrawlayout();
                cmddialog("0x02:");
                break;
            case R.id.tv_ip:
                switchDrawlayout();
                //dialog for ip configuration
                ipdialog();
                break;
            case R.id.config:
                isDisplay();
                break;
            case R.id.probabilty:
                switchDrawlayout();
                setProbabilityDialog();
                isDisplay();
                break;
            case R.id.exposure:
                switchDrawlayout();
                exposureDialog();
                isDisplay();
                break;
            case R.id.logLevel:
                switchDrawlayout();
                logLevelDialog();
                isDisplay();
                break;
            case R.id.imagesize:
                switchDrawlayout();
                ImageSizeDialog();
                isDisplay();
                break;
            case R.id.killProgram:
                switchDrawlayout();
                scorder("0x09");
                isDisplay();
                break;
            case R.id.whitebalance:
                switchDrawlayout();
                whitebalanceDialog();
                isDisplay();
                break;
            case R.id.cameraFrequency:
                switchDrawlayout();
                setCameraFrequencyDialog();
                isDisplay();
                break;
            case R.id.envsensorFrequency:
                switchDrawlayout();
                cmddialog("0x05:check_sensor_state_interval_seconds:");
                isDisplay();
                break;
            case R.id.tv_frequency:
                //Frequency
                if (lvFrequency.getVisibility() == View.VISIBLE) {
                    lvFrequency.setVisibility(View.GONE);
                } else {
                    lvFrequency.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.tv_obd:
                switchDrawlayout();
                Intent settingsIntent = new Intent(MapsActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            default:
                break;
        }
    }

    private void isDisplay() {
        if (!ishow) {
            probabilty.setVisibility(View.VISIBLE);
            exposure.setVisibility(View.VISIBLE);
            cameraFrequency.setVisibility(View.VISIBLE);
            envsensorFrequency.setVisibility(View.VISIBLE);
            logLevel.setVisibility(View.VISIBLE);
            imagesize.setVisibility(View.VISIBLE);
            killProgram.setVisibility(View.VISIBLE);
            whitebalance.setVisibility(View.VISIBLE);
            ishow = true;
        } else {
            probabilty.setVisibility(View.GONE);
            exposure.setVisibility(View.GONE);
            cameraFrequency.setVisibility(View.GONE);
            envsensorFrequency.setVisibility(View.GONE);
            logLevel.setVisibility(View.GONE);
            imagesize.setVisibility(View.GONE);
            killProgram.setVisibility(View.GONE);
            whitebalance.setVisibility(View.GONE);
            ishow = false;
        }
    }

    private void setProbabilityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                scorder("0x05:probability:" + modes[which]);

            }
        });
        builder.show();
    }

    private void setCameraFrequencyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"1秒1张", "1秒2张", "1秒4张"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    scorder("0x05:time_frequency:" + 1000);
                } else if (which == 1) {
                    scorder("0x05:time_frequency:" + 500);
                } else if (which == 2) {
                    scorder("0x05:time_frequency:" + 250);
                }

            }
        });
        builder.show();
    }

    private void logLevelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"0", "1", "2", "3", "4", "5"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                scorder("0x05:level:" + which);
            }
        });
        builder.show();
    }

    private void ImageSizeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"640*480", "1280*720"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (0 == which) {
                    scorder("0x05:imagesize:" + 2);
                } else if (1 == which) {
                    scorder("0x05:imagesize:" + 3);
                }

            }
        });
        builder.show();
    }

    private void exposureDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"off", "on", "OnAutoFlash", "OnAlwaysFlash", "OnFlashRedEye"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                scorder("0x05:autoexposure:" + (which + 1));
            }
        });
        builder.show();
    }

    private void whitebalanceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        //    指定下拉列表的显示数据
        final String[] modes = {"off", "auto", "incandescent", "fluorescent", "warm-fluorescent", "daylight", "cloudy-daylight", "twilight", "shade"};
        //    设置一个下拉的列表选择项
        builder.setItems(modes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                scorder("0x05:whitebalance:" + which);
            }
        });
        builder.show();
    }

    private void getLog() {
        scorder("0x04");
    }

    private void openCamera() {
        scorder("0x01");
    }

    private void closeCamera() {
        scorder("0x03");
    }

    //打开环境传感器
    private void openenvsensor() {
        scorder("0x06");
    }

    //关闭环境传感器
    private void closeenvsensor() {
        scorder("0x07");
    }

    private void scorder(String order) {
        if (socketClient != null) {
            socketClient.sendOrder(order);
        }
    }

    private void cmddialog(final String cmd) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        View view = LayoutInflater.from(MapsActivity.this).inflate(R.layout.layout_cmdcommand, null);
        builder.setView(view);
        final EditText etcommand = view.findViewById(R.id.cmdcommand);
        TextView confirm = view.findViewById(R.id.cmd_confirm);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String etstring = etcommand.getText().toString();
                String scommand = cmd + etstring;
                if (!TextUtils.isEmpty(scommand)) {
                    if (cmd.contains("0x05:time_frequency:")) {
                        Integer integer = Integer.valueOf(etstring);
                        int i = (1000 / integer);
                        scommand = cmd + i;
                    }
                    scorder(scommand);
                    alertDialog.dismiss();
                }
            }
        });
    }

    private void ipdialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        View view = LayoutInflater.from(MapsActivity.this).inflate(R.layout.layout_ipset, null);
        builder.setView(view);
        builder.setCancelable(false);
        final EditText ethost = view.findViewById(R.id.host);
        final EditText etport = view.findViewById(R.id.port);
        String trim = ethost.getText().toString().trim();
        ethost.setText(host);
        etport.setText(port + "");
        TextView cancle = view.findViewById(R.id.cancel);
        TextView confirm = view.findViewById(R.id.confirm);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        cancle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String shost = ethost.getText().toString();
                String sport = etport.getText().toString();
                if (!TextUtils.isEmpty(shost) && !TextUtils.isEmpty(sport)) {
                    host = shost;
                    port = Integer.valueOf(sport);
                    reConnect();
                    alertDialog.dismiss();
                } else {
                    Toast.makeText(MapsActivity.this, "Please fill in the information", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    private void reConnect() {
        if (socketClient != null) {
            socketClient.onDestroy();
            conn();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                Toast.makeText(this, getString(R.string.status_gps_started), Toast.LENGTH_SHORT).show();
                break;
            case GpsStatus.GPS_EVENT_STOPPED:
                Toast.makeText(this, getString(R.string.status_gps_stopped), Toast.LENGTH_SHORT).show();
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                Toast.makeText(this, getString(R.string.status_gps_fix), Toast.LENGTH_SHORT).show();
                break;
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                break;
        }
    }

    /**
     * set listeners for data structure changes
     */
    private void setDataListeners() {
        // add pv change listeners to trigger model updates
        ObdProt.PidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.VidPvs.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
        ObdProt.tCodes.addPvChangeListener(this,
                PvChangeEvent.PV_ADDED
                        | PvChangeEvent.PV_CLEARED
        );
    }

    /**
     * set listeners for data structure changes
     */
    private void removeDataListeners() {
        // remove pv change listeners
        ObdProt.PidPvs.removePvChangeListener(this);
        ObdProt.VidPvs.removePvChangeListener(this);
        ObdProt.tCodes.removePvChangeListener(this);
    }

    MODE previousMode;

    /**
     * set new operating mode
     *
     * @param mode new mode
     */
    public void setMode(MODE mode) {
        // if this is a mode change, or file reload ...
        if (mode != this.mode) {
            switch (mode) {
                case OFFLINE:
                    break;

                case ONLINE:
                    switch (CommService.medium) {
                        case BLUETOOTH:
                            // if pre-settings shall be used ...
                            String address = prefs.getString(PRESELECT.LAST_DEV_ADDRESS.toString(), null);
                            if (istRestoreWanted(PRESELECT.LAST_DEV_ADDRESS)
                                    && address != null) {
                                // ... connect with previously connected device
                                connectBtDevice(address, prefs.getBoolean("bt_secure_connection", false));
                            } else {
                                // ... otherwise launch the BtDeviceListActivity to see devices and do scan
                                Intent serverIntent = new Intent(this, BtDeviceListActivity.class);
                                startActivityForResult(serverIntent,
                                        prefs.getBoolean("bt_secure_connection", false)
                                                ? REQUEST_CONNECT_DEVICE_SECURE
                                                : REQUEST_CONNECT_DEVICE_INSECURE);
                            }
                            break;
                    }
                    break;

            }
            // remember previous mode
            previousMode = this.mode;
            // set new mode
            this.mode = mode;
            setStatus(mode.toString());
        }
    }

    /**
     * Check if restore of specified preselection is wanted from settings
     *
     * @param preselect specified preselect
     * @return flag if preselection shall be restored
     */
    boolean istRestoreWanted(PRESELECT preselect) {
        return prefs.getStringSet(PREF_USE_LAST, emptyStringSet).contains(preselect.toString());
    }

    private void connectBtDevice(String address, boolean secure) {
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mCommService = new BtCommService(this, mHandler);
        mCommService.connect(device, secure);
    }

    /**
     * Handle bluetooth connection established ...
     */
    private void onConnect() {
        mode = MODE.ONLINE;
        setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
        // send RESET to Elm adapter
        CommService.elm.reset();
        setObdService(ObdProt.OBD_SVC_DATA, "OBD Data");
    }

    /**
     * Handle bluetooth connection lost ...
     */
    private void onDisconnect() {
        // handle further initialisations
        setMode(MODE.OFFLINE);
    }

    private void setStatus(String s) {
        obdStatus.setText("OBDII Status:" + s);
    }

    /**
     * Activate desired OBD service
     *
     * @param newObdService OBD service ID to be activated
     */
    public void setObdService(int newObdService, CharSequence menuTitle) {
        // remember this as current OBD service
        obdService = newObdService;


        // set protocol service
        CommService.elm.setService(newObdService, true);

        // remember this as last selected service
        if (newObdService > ObdProt.OBD_SVC_NONE)
            prefs.edit().putInt(PRESELECT.LAST_SERVICE.toString(), newObdService).apply();
    }


    /**
     * Prompt for selection of a single ECU from list of available ECUs
     *
     * @param ecuAdresses List of available ECUs
     */
    protected void selectEcu(final Set<Integer> ecuAdresses) {
        // if more than one ECUs available ...
        if (ecuAdresses.size() > 1) {
            int preferredAddress = prefs.getInt(PRESELECT.LAST_ECU_ADDRESS.toString(), 0);
            // check if last preferred address matches any of the reported addresses
            if (istRestoreWanted(PRESELECT.LAST_ECU_ADDRESS)
                    && ecuAdresses.contains(preferredAddress)) {
                // set addrerss
                CommService.elm.setEcuAddress(preferredAddress);
            } else {
                // NO match with preference -> allow selection

                // .. allow selection of single ECU address ...
                final CharSequence[] entries = new CharSequence[ecuAdresses.size()];
                // create list of entries
                int i = 0;
                for (Integer addr : ecuAdresses) {
                    entries[i++] = String.format("0x%X", addr);
                }
                // show dialog ...
                dlgBuilder
                        .setTitle(R.string.select_ecu_addr)
                        .setItems(entries, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int address = Integer.parseInt(entries[which].toString().substring(2), 16);
                                // set address
                                CommService.elm.setEcuAddress(address);
                                // set this as preference (preference change will trigger ELM command)
                                prefs.edit().putInt(PRESELECT.LAST_ECU_ADDRESS.toString(), address).apply();
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // keep main display on?
        if (key == null || KEEP_SCREEN_ON.equals(key)) {
            getWindow().addFlags(prefs.getBoolean(KEEP_SCREEN_ON, false)
                    ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    : 0);
        }

        // set default comm medium
        if (key == null || SettingsActivity.KEY_COMM_MEDIUM.equals(key))
            CommService.medium =
                    CommService.MEDIUM.values()[
                            getPrefsInt(SettingsActivity.KEY_COMM_MEDIUM, 0)];

        // enable/disable ELM adaptive timing
        if (key == null || ELM_ADAPTIVE_TIMING.equals(key))
            CommService.elm.mAdaptiveTiming.setMode(
                    ElmProt.AdaptTimingMode.valueOf(
                            prefs.getString(ELM_ADAPTIVE_TIMING,
                                    ElmProt.AdaptTimingMode.OFF.toString())));

        // set protocol flag to initiate immediate reset on NRC reception
        if (key == null || ELM_RESET_ON_NRC.equals(key))
            CommService.elm.setResetOnNrc(prefs.getBoolean(ELM_RESET_ON_NRC, false));

        // set custom ELM init commands
        if (key == null || ELM_CUSTOM_INIT_CMDS.equals(key)) {
            String value = prefs.getString(ELM_CUSTOM_INIT_CMDS, null);
            if (value != null && value.length() > 0)
                CommService.elm.setCustomInitCommands(value.split("\n"));
        }

        // ELM timeout
        if (key == null || SettingsActivity.ELM_MIN_TIMEOUT.equals(key))
            CommService.elm.mAdaptiveTiming.setElmTimeoutMin(
                    getPrefsInt(SettingsActivity.ELM_MIN_TIMEOUT,
                            CommService.elm.mAdaptiveTiming.getElmTimeoutMin()));

        // ... measurement system
        if (key == null || MEASURE_SYSTEM.equals(key))
            setConversionSystem(getPrefsInt(MEASURE_SYSTEM, EcuDataItem.SYSTEM_METRIC));

        // ... preferred protocol
        if (key == null || SettingsActivity.KEY_PROT_SELECT.equals(key))
            ElmProt.setPreferredProtocol(getPrefsInt(SettingsActivity.KEY_PROT_SELECT, 0));

        // set disabled ELM commands
        if (key == null || SettingsActivity.ELM_CMD_DISABLE.equals(key)) {
            ElmProt.disableCommands(prefs.getStringSet(SettingsActivity.ELM_CMD_DISABLE, null));
        }


        // Max. data disabling debounce counter
        if (key == null || PREF_DATA_DISABLE_MAX.equals(key))
            EcuDataItem.MAX_ERROR_COUNT = getPrefsInt(PREF_DATA_DISABLE_MAX, 3);
    }

    /**
     * set mesaurement conversion system to metric/imperial
     *
     * @param cnvId ID for metric/imperial conversion
     */
    void setConversionSystem(int cnvId) {
        if (EcuDataItem.cnvSystem != cnvId) {
            // set coversion system
            EcuDataItem.cnvSystem = cnvId;
        }
    }

    private int getPrefsInt(String key, int defaultValue) {
        int result = defaultValue;

        try {
            result = Integer.valueOf(prefs.getString(key, String.valueOf(defaultValue)));
        } catch (Exception ex) {
        }

        return result;
    }

    @Override
    public void pvChanged(PvChangeEvent event) {
        Message msg = mHandler.obtainMessage(MapsActivity.MESSAGE_DATA_ITEMS_CHANGED);
        if (!event.isChildEvent()) {
            msg.obj = event;
            mHandler.sendMessage(msg);
        }
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        /* handle protocol status changes */
        if (ElmProt.PROP_STATUS.equals(evt.getPropertyName())) {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_STATE_CHANGED);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        } else if (ElmProt.PROP_NUM_CODES.equals(evt.getPropertyName())) {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_NUMCODES);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        } else if (ElmProt.PROP_ECU_ADDRESS.equals(evt.getPropertyName())) {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_ECUS);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        } else if (ObdProt.PROP_NRC.equals(evt.getPropertyName())) {
            // forward property change to the UI Activity
            Message msg = mHandler.obtainMessage(MESSAGE_OBD_NRC);
            msg.obj = evt;
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Timer Task to cyclically update data screen
     */
    private transient final TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            /* forward message to update the view */
            Message msg = mHandler.obtainMessage(MapsActivity.MESSAGE_UPDATE_VIEW);
            mHandler.sendMessage(msg);

        }
    };

    private boolean isChangeFileName = true;
    private String logFileName;
    private File logFile;
//    private int isFirst = 1;
    /**
     * Handle message requests
     */
    private transient final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                PropertyChangeEvent evt;

                // log trace message for received handler notification event

                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        // log trace message for received handler notification event
                        switch ((CommService.STATE) msg.obj) {
                            case CONNECTED:
                                onConnect();
                                break;

                            case CONNECTING:
                                setStatus(getString(R.string.title_connecting));
                                break;

                            default:
                                onDisconnect();
                                break;
                        }
                        break;

                    // data has been read - finish up
                    case MESSAGE_FILE_READ:
                        // set listeners for data structure changes
                        setDataListeners();
                        setObdService(CommService.elm.getService(), getString(R.string.saved_data));
                        break;

                    case MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                        Toast.makeText(getApplicationContext(),
                                getString(R.string.connected_to) + mConnectedDeviceName,
                                Toast.LENGTH_SHORT).show();
                        break;

                    case MESSAGE_TOAST:
                        Toast.makeText(getApplicationContext(),
                                msg.getData().getString(TOAST),
                                Toast.LENGTH_SHORT).show();
                        break;

                    case MESSAGE_DATA_ITEMS_CHANGED:
                        pvEvent = (PvChangeEvent) msg.obj;
                        switch (pvEvent.getType()) {
                            case PvChangeEvent.PV_ADDED:
                                updateTimer.schedule(updateTask, 0, DISPLAY_UPDATE_TIME);
                                break;
                        }

                    case MESSAGE_UPDATE_VIEW:
                        String oSpeed = "0.0", oRpm = "0";
                        try {
                            if (pvEvent != null) {
                                if (pvEvent.getSource() == ObdProt.PidPvs) {
                                    // Check if last data selection shall be restored
                                    PvList pvList = (PvList) pvEvent.getSource();
                                    if (pvList != null && pvList.size() > 0) {
                                        Iterator<EcuDataPv> it = pvList.values().iterator();
                                        long ms = System.currentTimeMillis();
                                        if (isChangeFileName) {
                                            logFileName = LogTools.ms2Date(ms) + ".log";
                                            logFile = LogTools.createFile(MapsActivity.this, "miner", logFileName);
                                            isChangeFileName = false;
                                        } else {
                                            if (LogTools.getFileSize(logFile) > 5.0) {
                                                isChangeFileName = true;
                                            } else {
                                                while (it.hasNext()) {
                                                    EcuDataPv pv = it.next();
                                                    if (pv != null) {
                                                        String content = LogTools.ms2Date(ms) + ":" + pv.get(EcuDataPv.FID_DESCRIPT).toString() + ":" + pv.get(EcuDataPv.FID_VALUE) + pv.get(EcuDataPv.FID_UNITS) + "\r\n";
                                                        LogTools.write2File(logFile, content);
                                                    }
                                                }
                                            }
                                        }

//                                        if (isFirst == 1) {
//                                            logFileName = LogTools.ms2Date(ms) + ".csv";
//                                            logFile = LogTools.createFile(MapsActivity.this, "minercsv", logFileName);
//                                            String c = "MAX,DESCRIPTION,MIN,VALUE,BIT_OFS,OFS,FMT,CNV_ID,PID,MENMONIC,UNITS\n";
//                                            LogTools.write2File(logFile, c);
//                                            while (it.hasNext()) {
//                                                EcuDataPv pv = it.next();
//                                                if (pv != null) {
//                                                    String content = pv.get(EcuDataPv.FID_MAX).toString() + ","+pv.get(EcuDataPv.FID_DESCRIPT).toString() + ","+pv.get(EcuDataPv.FID_MIN).toString() + ","+pv.get(EcuDataPv.FID_VALUE).toString() + ","+pv.get(EcuDataPv.FID_BIT_OFS).toString() + ","+pv.get(EcuDataPv.FID_OFS).toString() + ","+pv.get(EcuDataPv.FID_FORMAT).toString() + ","+pv.get(EcuDataPv.FID_CNVID).toString() + ","+pv.get(EcuDataPv.FID_PID).toString() + ","+pv.get(EcuDataPv.FID_MNEMONIC).toString() + ","+pv.get(EcuDataPv.FID_UNITS).toString() + "\n" ;
//                                                    LogTools.write2File(logFile, content);
//                                                }
//                                            }
//                                        }
//                                        isFirst+=1;
                                        while (it.hasNext()) {
                                            EcuDataPv pv = it.next();
                                            if (pv != null) {
                                                if (pv.get(EcuDataPv.FID_DESCRIPT).toString().equals("Engine RPM") &&
                                                        pv.get(EcuDataPv.FID_UNITS).toString().equals("/min")) {
                                                    oRpm = pv.get(EcuDataPv.FID_VALUE) + "";
                                                }
                                                if (pv.get(EcuDataPv.FID_DESCRIPT).toString().equals("Vehicle Speed") &&
                                                        pv.get(EcuDataPv.FID_UNITS).toString().equals("km/h")) {
                                                    oSpeed = pv.get(EcuDataPv.FID_VALUE) + "";
                                                }
                                            }
                                        }
                                        JSONObject object = new JSONObject();
                                        try {
                                            obdLongitude.setText("o_Longitude:" + obdlon);
                                            obdLatitude.setText("o_Latitude:" + obdlat);
                                            obdAltitude.setText("o_Altitude:" + obdalt);
                                            speed.setText("o_Speed:" + oSpeed);
                                            rpm.setText("o_RPM:" + oRpm);
                                            object.put("timestamp", DateUtil.getDateTimeFromMillis(System.currentTimeMillis()));
                                            object.put("longitude", String.valueOf(obdlon));
                                            object.put("latitude", String.valueOf(obdlat));
                                            object.put("altitude", String.valueOf(obdalt));
                                            object.put("speed", oSpeed);
                                            scorder("0x08:" + object);
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }

                            // set up data update timer
                        } catch (Exception ignored) {
                            Log.e("error:", "Error adding PV", ignored);
                        }

                        break;
                    // handle state change in OBD protocol
                    case MESSAGE_OBD_STATE_CHANGED:
                        evt = (PropertyChangeEvent) msg.obj;
                        ElmProt.STAT state = (ElmProt.STAT) evt.getNewValue();
                        /* Show ELM status only in ONLINE mode */
                        setStatus(getResources().getStringArray(R.array.elmcomm_states)[state.ordinal()]);
                        // if last selection shall be restored ...
                        if (istRestoreWanted(PRESELECT.LAST_SERVICE)) {
                            if (state == ElmProt.STAT.ECU_DETECTED) {
                                setObdService(prefs.getInt(PRESELECT.LAST_SERVICE.toString(), 0), null);
                            }
                        }
                        break;

                    // handle change in number of fault codes
                    case MESSAGE_OBD_NUMCODES:
                        evt = (PropertyChangeEvent) msg.obj;
                        //                        setNumCodes((Integer) evt.getNewValue());
                        break;

                    // handle ECU detection event
                    case MESSAGE_OBD_ECUS:
                        evt = (PropertyChangeEvent) msg.obj;
                        selectEcu((Set<Integer>) evt.getNewValue());
                        break;

                    // handle negative result code from OBD protocol
                    case MESSAGE_OBD_NRC:
                        // reset OBD mode to prevent infinite error loop
                        setObdService(ObdProt.OBD_SVC_NONE, getText(R.string.obd_error));
                        // show error dialog ...
                        evt = (PropertyChangeEvent) msg.obj;
                        String nrcMessage = (String) evt.getNewValue();
                        dlgBuilder
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setTitle(R.string.obd_error)
                                .setMessage(nrcMessage)
                                .setPositiveButton(null, null)
                                .show();
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }
    };


}
