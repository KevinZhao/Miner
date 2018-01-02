package com.miner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

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
import com.miner.socket.SocketClient;
import com.miner.utils.DateUtil;
import com.miner.utils.NetworkUtils;
import com.miner.utils.PermissionUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener, ActivityCompat.OnRequestPermissionsResultCallback, View.OnClickListener {
    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_CODE = 2;
    private static final int READ_EXTERNAL_STORAGE_CODE = 3;

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
    @BindView(R.id.tv_command)
    TextView tvCommand;
    @BindView(R.id.socketstate)
    TextView socketstate;
    @BindView(R.id.tv_command2)
    TextView tvCommand2;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mPermissionDenied = false;
    private GoogleMap mMap;
    private Handler handler;
    private double lat = 0;
    private double lng = 0;
    private int frequency = 5;//传感器更新频率，单位秒
    private Timer timer;
    private TimerTask task;
    private Timer timer_acc;
    private TimerTask task_acc;
    private PopupWindow popupWindow;
    private LocationManager lm;
    private File file;
    private SensorManager mSensorManager;
    private String bestProvider;
    private Sensor mAccelerometer;
    private Sensor mLightSensor;
    private GPSBean gpsBean = new GPSBean();//GPS
    private GPSBean googleGps = new GPSBean();//GoogleGPS
    private LightBean lightBean = new LightBean();//光线
    private AccelerationBean accelerationBean = new AccelerationBean();//加速度
    private JSONArray jsonArray = new JSONArray();
    private boolean isExists;//是否存在sd卡
    private String filePath;
    private boolean isRecord;//是否在采集中
    private boolean isOpen = false;//抽屉是否在打开状态
    private List<Integer> data;
    private long mCurrentTime;
    private int writeFlag = 0;

    private String host = "192.168.0.118";
    private int port = 7777;
    private SocketClient socketClient;
//    private Socket client;
    //手机型号
    private String systemModel;
    private String Tag = "MapsActivity";
    private String deviceID;
    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        if (NetworkUtils.isNetworkAvailable(MapsActivity.this)) {
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
            initAll();

        } else {
            Toast.makeText(this, "Please connect to the network", Toast.LENGTH_SHORT).show();
        }
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
                            mMap.addMarker(new MarkerOptions()
                                    .position(myLatLng)
                                    .flat(true)//标记平面化
                                    //.rotation(45.0f)//将标记旋转45度
                                    //.icon(BitmapDescriptorFactory.defaultMarker()));
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));
                            //                            mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
                        }
                        Log.i("location", ":" + lat + ";" + lng);
                        break;
                    case 2:
                        tvAccX.setText("AccelerationX：" + accelerationBean.getX());
                        tvAccY.setText("AccelerationY：" + accelerationBean.getY());
                        tvAccZ.setText("AccelerationZ: " + accelerationBean.getZ());
                        break;
                    case 3:
                        String state = (String) msg.obj;
                        socketstate.setText(state);
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

    /**
     * 初始化方法
     */
    private void initAll() {
        // 获取手机型号
        systemModel = Build.MODEL;
        socketstate.setText("");
        initData();
        initListener();
        setWriteReadPermission();
        initGpsAndSensor();
        conn();


    }



    private void initGpsAndSensor() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        deviceID=telephonyManager.getDeviceId();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        isExists = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (isExists) {
            filePath = Environment.getExternalStorageDirectory().getPath() + "/Miner";
            makeRootDirectory(filePath);
        } else {
            Toast.makeText(MapsActivity.this, "No SDCard!", Toast.LENGTH_SHORT).show();
        }
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // 判断GPS是否正常启动
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please open the GPS navigation...", Toast.LENGTH_SHORT).show();
            // 返回开启GPS导航设置界面
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, 0);
            return;
        }

        // 为获取地理位置信息时设置查询条件
        bestProvider = lm.getBestProvider(getCriteria(), true);

        // 获取位置信息
        // 如果不设置查询要求，getLastKnownLocation方法传人的参数为LocationManager.GPS_PROVIDER
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = lm.getLastKnownLocation(bestProvider);
        updateView(location, false);
        // 监听状态
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
        socketClient = new SocketClient(host, port, filePath,onSocketStateListener);
//        client = socketClient.getClient();
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
    };

    /**
     * 初始化数据
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
     * 设置下拉列表
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
     * 初始化监听器
     */
    private void initListener() {
        tvRecord.setOnClickListener(this);
        tvClearMap.setOnClickListener(this);
        tvPop.setOnClickListener(this);
        ivSetting.setOnClickListener(this);
        ipset.setOnClickListener(this);
        tvFrequency.setOnClickListener(this);
        tvCommand.setOnClickListener(this);
        tvCommand2.setOnClickListener(this);
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
     * 更新定时器
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
                    tvLightX.setText("Light: " + xValue);
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
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
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
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
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
                gpsBean.setLongitude(location.getLongitude());
                gpsBean.setLatitude(location.getLatitude());
                gpsBean.setAltitude(location.getAltitude());
                gpsBean.setBearing(location.getBearing());
                gpsBean.setSpeed(location.getSpeed());
            } else {
                //GoogleGPS
                googleGps.setLongitude(location.getLongitude());
                googleGps.setLatitude(location.getLatitude());
                googleGps.setAltitude(location.getAltitude());
                googleGps.setBearing(location.getBearing());
                googleGps.setSpeed(location.getSpeed());
            }
            if (gpsBean.getLongitude() != 0 && gpsBean.getLatitude() != 0) {
                tvGpsLat.setText("Latitude: " + gpsBean.getLatitude());
                tvGpsLng.setText("Longitude: " + gpsBean.getLongitude());
                tvGpsAlt.setText("Altitude: " + gpsBean.getAltitude());
                tvGpsSpeed.setText("Speed: " + gpsBean.getSpeed());
                tvGpsBearing.setText("Heading: " + gpsBean.getBearing());
            } else {
                tvGpsLat.setText("Latitude: " + googleGps.getLatitude());
                tvGpsLng.setText("Longitude: " + googleGps.getLongitude());
                tvGpsAlt.setText("Altitude: " + googleGps.getAltitude());
                tvGpsSpeed.setText("Speed: " + googleGps.getSpeed());
                tvGpsBearing.setText("Heading: " + googleGps.getBearing());
            }


        }

    }

    // 将字符串写入到文本文件中
    public void writeTxtToFile(JSONArray array) {
        String strContent = "No data has been taken for the time being";
        if (isRecord) {
            if (jsonArray.length() >= 2000) {
                writeFlag++;
            } else {
                writeFlag = 0;
            }
        }
        if (writeFlag > 0) {
            file = makeFilePath(filePath, "/" + ms2Date(mCurrentTime) + "-" + writeFlag + ".json");
        } else {
            file = makeFilePath(filePath, "/" + ms2Date(mCurrentTime) + ".json");
        }
        try {
            if (array.length() > 0) {
                strContent = array + "";
            }
            RandomAccessFile raf = null;
            raf = new RandomAccessFile(file, "rwd");
            raf.seek(file.length());
            raf.write(strContent.getBytes());
            raf.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        jsonArray = new JSONArray();


    }

    /**
     * 转换时间格式
     *
     * @param _ms
     * @return
     */
    public static String ms2Date(long _ms) {
        Date date = new Date(_ms);
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(date);
    }


    private JSONArray dealBean() throws JSONException {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("time", DateUtil.getDateTimeFromMillis(System.currentTimeMillis()));
            JSONObject object_acc = new JSONObject();
            object_acc.put("accx", accelerationBean.getX());
            object_acc.put("accy", accelerationBean.getY());
            object_acc.put("accz", accelerationBean.getZ());
            jsonObject.put("acc", object_acc);
            JSONObject object_gps = new JSONObject();
            object_gps.put("longitude", gpsBean.getLongitude());
            object_gps.put("latitude", gpsBean.getLatitude());
            object_gps.put("altitude", gpsBean.getAltitude());
            object_gps.put("speed", gpsBean.getSpeed());
            object_gps.put("bearing", gpsBean.getBearing());
            jsonObject.put("gps", object_gps);
            JSONObject object_google = new JSONObject();
            object_google.put("longitude", googleGps.getLongitude());
            object_google.put("latitude", googleGps.getLatitude());
            object_google.put("altitude", googleGps.getAltitude());
            object_google.put("speed", googleGps.getSpeed());
            object_google.put("bearing", googleGps.getBearing());
//            jsonObject.put("googleGPS", object_google);
            JSONObject object_light = new JSONObject();
            object_light.put("lightx", lightBean.getX());
            jsonObject.put("light", object_light);
            jsonObject.put("deviceID",deviceID);
            jsonObject.put("frequency",frequency);
            jsonArray.put(jsonObject);
            socketClient.sendOrder(String.valueOf(jsonArray));

//            if (jsonArray.length() > 2000) {
//                writeTxtToFile(jsonArray);
//            }
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


    // 生成文件
    private File makeFilePath(String filePath, String fileName) {
        File file = null;
        try {
            file = new File(filePath + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // 生成文件夹
    private static void makeRootDirectory(String filePath) {
        File file = null;
        try {
            file = new File(filePath);
            if (!file.exists()) {
                file.mkdir();
            }
        } catch (Exception e) {
            Log.i("error:", e + "");
        }
    }


    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        if (mMap != null) {
            mMap.clear();
        }
        if (timer != null && task != null) {
            timer.cancel();
            task.cancel();
        }
        if (timer_acc != null && task_acc != null) {
            timer_acc.cancel();
            task_acc.cancel();
        }
        writeFlag = 0;
//        if (jsonArray.length() > 0) {
//            writeTxtToFile(jsonArray);
//        }

        if (socketClient!=null){
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
                    openCamera();
                    if (gpsBean.getLongitude() == 0 && gpsBean.getLatitude() == 0
                            && googleGps.getLongitude() == 0 && googleGps.getLatitude() == 0) {
                        Toast.makeText(MapsActivity.this, "If you have no access to your location information, open your cell phone GPS", Toast.LENGTH_SHORT).show();
                    } else {
                        jsonArray = new JSONArray();
                        mCurrentTime = System.currentTimeMillis();
                        Toast.makeText(MapsActivity.this, "Start recording data", Toast.LENGTH_SHORT).show();
                        //获取数据
                        updateTimer();
                        tvRecord.setText("Stop");
                        tvRecord.setTextColor(Color.RED);
                        isRecord = true;
                    }
                } else {
                    closeCamera();
                    if (timer != null && task != null) {
                        timer.cancel();
                        task.cancel();
                    }
                    writeFlag = 0;
//                    if (jsonArray.length() > 0) {
//                        writeTxtToFile(jsonArray);
//                    }
                    tvRecord.setText("Record");
                    tvRecord.setTextColor(getResources().getColor(R.color.ori_textcolor));
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
            case R.id.tv_command:
                switchDrawlayout();
                getLog();
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
            case R.id.tv_frequency:
                //Frequency
                if (lvFrequency.getVisibility() == View.VISIBLE) {
                    lvFrequency.setVisibility(View.GONE);
                } else {
                    lvFrequency.setVisibility(View.VISIBLE);
                }
                break;
            default:
                break;
        }
    }

    private void getLog() {
        socketClient.sendOrder("0x04");
    }

    private void openCamera() {
        socketClient.sendOrder("0x01");
    }
    private void closeCamera() {
        socketClient.sendOrder("0x03");
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
                final String scommand = cmd + etcommand.getText().toString();
                if (!TextUtils.isEmpty(scommand)) {
                    socketClient.sendOrder(scommand);

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
                    if (socketClient != null) {
                        socketClient.onDestroy();
                    }
                    conn();
                    alertDialog.dismiss();
                } else {
                    Toast.makeText(MapsActivity.this, "Please fill in the information", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

}
