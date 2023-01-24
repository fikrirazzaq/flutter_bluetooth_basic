package com.tablemi.flutter_bluetooth_basic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "FLUTTER_BLUETOOTH";
    private final Handler mHandler;
    private int mState;

    // Member fields
    private final BluetoothAdapter mAdapter;
    public ConnectThread mConnectThread;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param adapter BluetoothAdapter instance
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothService(BluetoothAdapter adapter, Handler handler) {
        mAdapter = adapter;
        mState = Constant.STATE_NONE;
        mHandler = handler;
    }


    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) throws IOException {
        Log.d(TAG, "connect to: " + device);


        // Cancel any thread attempting to make a connection
        if (mState == Constant.STATE_CONNECTED) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        UUID PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(PRINTER_UUID);
        Log.d(TAG, "Get a BluetoothSocket");

        if (mAdapter.isDiscovering()) {
            mAdapter.cancelDiscovery();
        }

        // Make a connection to the BluetoothSocket
        // This is a blocking call and will only return on a
        // successful connection or an exception
        socket.connect();
        if (socket == null) {
            throw new IOException("socket connection not established");
        }
        // Get the input and output streams; using temp objects because
        // member streams are final.
        Log.d(TAG, "Get input and output streams;");

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(socket);
        mConnectThread.start();
    }


    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");
        mState = Constant.STATE_NONE;
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectThread#write(byte[])
     */
    public void write(byte[] out) throws InterruptedException {
        // Create temporary object
        ConnectThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != Constant.STATE_CONNECTED) return;
            r = mConnectThread;
        }
        r.join();
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constant.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constant.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        mState = Constant.STATE_NONE;
        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;


        public ConnectThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
                Log.e(TAG, "Error occurred when creating output stream", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = Constant.STATE_CONNECTED;

        }


        public void run() {
            Log.i(TAG, "BEGIN mConnectThread Socket");
            // Make sure output stream is closed
            if (mmOutStream != null) {
                try {
                    mmOutStream.close();
                }
                catch (Exception ignored) {connectionFailed();}
            }

            // mmInStream sure input stream is closed
            if (mmInStream != null) {
                try {
                    mmInStream.close();
                }
                catch (Exception ignored) {connectionFailed();}
            }

        }


        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                if (mmSocket != null && mmSocket.isConnected())
                    Log.d(TAG, " Write Data");
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(Constant.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constant.TOAST,
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }

        public void cancel() {
            Log.d(TAG, " shut down the connection");
            // Flush output buffers befoce closing
            try {
                if (mmOutStream != null) {
                    mmOutStream.flush();
                    mmOutStream.close();
                }
                if (mmInStream != null)
                    mmInStream.close();
            } catch (Exception e) {
                Log.e(TAG, "mmOutStream flush error", e);
            }
            // Close the connection socket
            if (mmSocket != null) {
                try {
                    Log.d(TAG, "close Socket");
                    Thread.sleep(111);
                    mmSocket.close();
                } catch (Exception e) {
                    Log.e(TAG, "Could not close the connect socket", e);
                }
            }
        }
    }

}
