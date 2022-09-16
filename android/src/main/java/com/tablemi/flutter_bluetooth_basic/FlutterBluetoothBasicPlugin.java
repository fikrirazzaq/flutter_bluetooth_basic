package com.tablemi.flutter_bluetooth_basic;

import android.Manifest;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

/**
 * FlutterBluetoothBasicPlugin
 */
public class FlutterBluetoothBasicPlugin implements FlutterPlugin, MethodCallHandler,
        RequestPermissionsResultListener, ActivityAware {
    private static final String TAG = "BluetoothBasicPlugin";
    private final int id = 0;
    private static final String NAMESPACE = "flutter_bluetooth_basic";
    private MethodChannel channel;
    private EventChannel stateChannel;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private FlutterPluginBinding pluginBinding;
    private ActivityPluginBinding activityBinding;
    private Context context;
    /**
     * Member object for the chat services
     */
    private BluetoothService mService = null;

    private static final int REQUEST_FINE_LOCATION_PERMISSIONS = 1451;
    private static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 1452;
    private static final int REQUEST_CONNECT_PERMISSIONS = 1453;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1001;
    private static final int REQUEST_DEVICES = 1002;

    private MethodCall pendingCall;
    private Result pendingResult;
    private Map<String, Object> pendingArgs;

    private final Object tearDownLock = new Object();

    //private final Map<Integer, OperationOnPermission> operationsOnPermission = new HashMap<>();


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine");
        pluginBinding = flutterPluginBinding;
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), NAMESPACE + "/methods");
        channel.setMethodCallHandler(this);
        this.stateChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), NAMESPACE + "/state");
        stateChannel.setStreamHandler(stateStreamHandler);
        this.context = (Application) pluginBinding.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.mBluetoothManager = this.context.getSystemService(BluetoothManager.class);
        } else
            this.mBluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mService == null) {
            mService = new BluetoothService(mBluetoothAdapter, mHandler);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        Log.d(TAG, "onDetachedFromEngine");
        pluginBinding = null;
        context = null;
        channel.setMethodCallHandler(null);
        channel = null;
        stateChannel.setStreamHandler(null);
        stateChannel = null;
        mBluetoothAdapter = null;
        mBluetoothManager = null;
    }


    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Log.d(TAG, "onAttachedToActivity");
        activityBinding = binding;
        binding.addActivityResultListener(
                (requestCode, resultCode, data) -> {
                    if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
                        if (pendingResult != null) {
                            pendingResult.success(resultCode != 0);
                        }
                        return true;
                    }
                    return false;
                }
        );
        activityBinding.addRequestPermissionsResultListener(this);
        if (mService == null) {
            mService = new BluetoothService(mBluetoothAdapter, mHandler);
        }
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        else {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mService.getState() == Constant.STATE_NONE) {
                // Start the Bluetooth chat services
                mService.start();
            }
        }
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges");
        onDetachedFromActivity();
        if (mService != null) {
            mService.stop();
        }
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        Log.d(TAG, "onReattachedToActivityForConfigChanges");
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity");
        activityBinding.removeRequestPermissionsResultListener(this);
        activityBinding = null;
        if (mService != null) {
            mService.stop();
        }
    }


    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constant.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, msg.getData().getString(Constant.DEVICE_NAME));
                    break;
                case Constant.MESSAGE_TOAST:
                    Log.d(TAG, msg.getData().getString(Constant.TOAST));
                    break;
                case Constant.STATE_CONNECTED:
                    pendingResult.success(true);
                    break;
                case Constant.CONN_STATE_DISCONNECT:
                    pendingResult.success(false);
                    break;
            }
        }
    };


    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (mBluetoothAdapter == null && !"isAvailable".equals(call.method)) {
            result.error("bluetooth_unavailable", "Bluetooth is unavailable", null);
            return;
        }

        final Map<String, Object> args = call.arguments();

        switch (call.method) {
            case "state":
                state(result);
                break;
            case "isAvailable":
                result.success(mBluetoothAdapter != null);
                break;
            case "isOn":
            case "isEnabled":
                result.success(mBluetoothAdapter.isEnabled());
                break;
            case "isConnected":
                result.success(mService.getState() == Constant.STATE_CONNECTED);
                break;
            case "requestEnable":
                if (!mBluetoothAdapter.isEnabled()) {
                    pendingResult = result;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                            ActivityCompat.requestPermissions(activityBinding.getActivity(), new String[]{
                                            Manifest.permission.BLUETOOTH_CONNECT,},
                                    REQUEST_ENABLE_BLUETOOTH);
                            pendingCall = call;
                            pendingResult = result;
                            break;
                        }

                    }
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    ActivityCompat.startActivityForResult(activityBinding.getActivity(), intent, REQUEST_ENABLE_BLUETOOTH, null);
                } else
                    result.success(true);
                break;

            case "requestDisable":
                if (mBluetoothAdapter.isEnabled()) {
                    mBluetoothAdapter.disable();
                    result.success(true);
                } else {
                    result.success(false);
                }
                break;
            case "startScan": {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(activityBinding.getActivity(), new String[]{
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.ACCESS_FINE_LOCATION,},
                                REQUEST_FINE_LOCATION_PERMISSIONS);
                        pendingCall = call;
                        pendingResult = result;
                        break;
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(activityBinding.getActivity(),
                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_COARSE_LOCATION_PERMISSIONS);
                        pendingCall = call;
                        pendingResult = result;
                        break;
                    }
                }
                startScan(call, result);
                break;
            }

            case "getBondedDevices":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(activityBinding.getActivity(), new String[]{
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.ACCESS_FINE_LOCATION,},
                                REQUEST_DEVICES);
                        pendingResult = result;
                        break;
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activityBinding.getActivity(),
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                        ActivityCompat.requestPermissions(activityBinding.getActivity(),
                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_DEVICES);
                        pendingResult = result;
                        break;
                    }
                }
                getDevices(result);
                break;

            case "stopScan":
                stopScan();
                result.success(null);
                break;
            case "connect":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            activityBinding.getActivity(),
                            new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                            REQUEST_CONNECT_PERMISSIONS);
                    pendingResult = result;
                    pendingArgs = args;
                    break;
                }
                connect(result, args);
                break;
            case "disconnect":
                result.success(disconnect());
                break;
            case "destroy":
                result.success(destroy());
                break;
            case "writeData":
                writeData(result, args);
                break;
            case "printReceipt":
                print(result, args);
                break;
            default:
                result.notImplemented();
                break;
        }

    }

    protected byte[] convertVectorByteToBytes(Vector<Byte> data) {
        byte[] sendData = new byte[data.size()];
        if (data.size() > 0) {
            for (int i = 0; i < data.size(); ++i) {
                sendData[i] = (Byte) data.get(i);
            }
        }

        return sendData;
    }

    @SuppressWarnings("unchecked")
    private void print(Result result, Map<String, Object> args) {
        if (args.containsKey("config") && args.containsKey("data")) {
            final Map<String, Object> config = (Map<String, Object>) args.get("config");
            final List<Map<String, Object>> list = (List<Map<String, Object>>) args.get("data");
            if (list != null) {

                // Check that we're actually connected before trying anything
                if (mService.getState() != Constant.STATE_CONNECTED) {
                    result.error("1000", "please connect to device" + mService.getState(), null);
                    return;
                }
                try {
                    Vector<Byte> vectorData = PrintContent.mapToReceipt(config, list);
                    mService.write(convertVectorByteToBytes(vectorData));

                } catch (Exception ex) {
                    result.error("write_error", ex.getMessage(), exceptionToString(ex));
                }
            }
        } else {
            result.error("please add config or data", "", null);
        }

    }


    @SuppressWarnings("unchecked")
    private void writeData(Result result, Map<String, Object> args) {
        if (args.containsKey("bytes")) {
            final ArrayList<Integer> bytes = (ArrayList<Integer>) args.get("bytes");
            // Check that we're actually connected before trying anything
            try {
                Vector<Byte> vectorData = new Vector<>();
                for (int i = 0; i < (bytes != null ? bytes.size() : 0); ++i) {
                    Integer val = bytes.get(i);
                    vectorData.add(Byte.valueOf(Integer.toString(val > 127 ? val - 256 : val)));
                }
                mService.write(convertVectorByteToBytes(vectorData));
            } catch (Exception ex) {
                result.error("write_error", ex.getMessage(), exceptionToString(ex));
            }
        } else {
            result.error("bytes_empty", "Bytes param is empty", null);
        }
    }

    private void connect(Result result, Map<String, Object> args) {
        if (args.containsKey("address")) {
            String address = (String) args.get("address");
            // disconnect();
            try {
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mService.connect(device);
                Thread.sleep(3000);
                result.success(true);
            } catch (Exception ex) {
                result.error("connect_error", ex.getMessage(), exceptionToString(ex));
            }
        } else {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }

    }


    static private boolean checkIsDeviceConnected(BluetoothDevice device) {
        try {
            java.lang.reflect.Method method;
            method = device.getClass().getMethod("isConnected");
            return (boolean) (Boolean) method.invoke(device);
        } catch (Exception ex) {
            return false;
        }
    }

    private void getDevices(Result result) {
        List<Map<String, Object>> devices = new ArrayList<>();
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            Map<String, Object> ret = new HashMap<>();
            ret.put("address", device.getAddress());
            ret.put("name", device.getName());
            ret.put("type", device.getType());
            ret.put("isConnected", checkIsDeviceConnected(device));
            devices.add(ret);
        }

        result.success(devices);
    }

    private void state(Result result) {
        try {
            switch (mBluetoothAdapter.getState()) {
                case BluetoothAdapter.STATE_OFF:
                    result.success(BluetoothAdapter.STATE_OFF);
                    break;
                case BluetoothAdapter.STATE_ON:
                    result.success(BluetoothAdapter.STATE_ON);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    result.success(BluetoothAdapter.STATE_TURNING_OFF);
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    result.success(BluetoothAdapter.STATE_TURNING_ON);
                    break;
                default:
                    result.success(0);
                    break;
            }
        } catch (SecurityException e) {
            result.error("invalid_argument", "Argument 'address' not found", null);
        }

    }

    private void startScan(MethodCall call, Result result) {
        Log.d(TAG, "start scan ");

        try {
            startScan();
            result.success(null);
        } catch (Exception e) {
            result.error("startScan", e.getMessage(), null);
        }
    }

    private void invokeMethodUIThread(final BluetoothDevice device) {
        final Map<String, Object> ret = new HashMap<>();
        ret.put("address", device.getAddress());
        ret.put("name", device.getName());
        ret.put("type", device.getType());
        ret.put("isConnected", checkIsDeviceConnected(device));

        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (tearDownLock) {
                //Could already be teared down at this moment
                if (channel != null) {
                    channel.invokeMethod("ScanResult", ret);
                } else {
                    Log.w(TAG, "Tried to call " + "ScanResult" + " on closed channel");
                }
            }
        });
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device != null && device.getName() != null) {
                invokeMethodUIThread(device);
            }
        }
    };

    private void startScan() throws IllegalStateException {
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null)
            throw new IllegalStateException("getBluetoothLeScanner() is null. Is the Adapter on?");

        // 0:lowPower 1:balanced 2:lowLatency -1:opportunistic
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(null, settings, mScanCallback);
    }

    private void stopScan() {
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (scanner != null) scanner.stopScan(mScanCallback);
    }

    static private String exceptionToString(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }


    /**
     * Reconnect to recycle the last connected object to avoid memory leaks
     */
    private boolean disconnect() {
        if (mService != null) {
            mService.stop();
        }
        return true;
    }

    private boolean destroy() {
        if (mService != null) {
            mService.stop();
        }
        return true;
    }


    @Override
    public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_FINE_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(pendingCall, pendingResult);
            } else {
                pendingResult.error("no_permissions", "this plugin requires location permissions for scanning", null);
                pendingResult = null;
            }
            return true;
        } else if (requestCode == REQUEST_COARSE_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(pendingCall, pendingResult);
            } else {
                pendingResult.error("no_permissions", "this plugin requires location permissions for scanning", null);
                pendingResult = null;
            }
            return true;
        } else if (requestCode == REQUEST_CONNECT_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connect(pendingResult, pendingArgs);
            } else {
                pendingResult.error("no_permissions", "this plugin requires  permissions for connecting", null);
                pendingResult = null;
            }
            return true;
        } else if (requestCode == REQUEST_DEVICES) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getDevices(pendingResult);
            } else {
                pendingResult.error("no_permissions", "this plugin requires  permissions for getting devices", null);
                pendingResult = null;
            }
            return true;
        } else if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ActivityCompat.startActivityForResult(activityBinding.getActivity(), intent, REQUEST_ENABLE_BLUETOOTH, null);
            } else {
                pendingResult.error("no_permissions", "this plugin requires  permissions for enable bluetooth", null);
                pendingResult = null;
            }
            return true;
        }
        return false;
    }

    private final StreamHandler stateStreamHandler = new StreamHandler() {
        private EventSink sink;

        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                Log.d(TAG, "stateStreamHandler, current action: " + action);

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    if (mService != null) {
                        mService.stop();
                    }
                    sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    sink.success(1);
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    sink.success(0);
                }
            }
        };

        @Override
        public void onListen(Object o, EventSink eventSink) {
            sink = eventSink;
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            context.registerReceiver(mReceiver, filter);
        }

        @Override
        public void onCancel(Object o) {
            sink = null;
            context.unregisterReceiver(mReceiver);
        }
    };


}
