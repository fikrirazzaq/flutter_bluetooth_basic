package com.tablemi.flutter_bluetooth_basic;

public class Constant {
    public static final int abnormal_Disconnection = 0x011; // Abnormal disconnection
    // handler that gets info from Bluetooth service
    public static final String READ_DATA_CNT = "read_data_cnt";
    public static final String READ_BUFFER_ARRAY = "read_buffer_array";
    public static final String ACTION_CONN_STATE = "action_connect_state";
    public static final String TOAST = "action_toast";
    public static final int READ_DATA = 10000;
    public static final int WRITE_DATA = 10001;
    public static final int MESSAGE_TOAST = 10002;
    public static final int CONN_STATE_DISCONNECT = 0x90;
    public static final int CONN_STATE_CONNECTED = CONN_STATE_DISCONNECT << 3;
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remo
    public static final  int MESSAGE_DEVICE_NAME = 4;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String STATE = "state";
    public static final String DEVICE_ID = "id";
}