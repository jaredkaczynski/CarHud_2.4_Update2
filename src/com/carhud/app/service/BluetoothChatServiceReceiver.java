/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.carhud.app.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.carhud.app.Hud;
import com.carhud.app.Hud.mHandler;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothChatServiceReceiver {
    // Debugging
    private static final String TAG = "com.carhud.app.bluetoothchatservice";
    private static final boolean D = true;

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final mHandler mHandler;
    private AcceptThread mSecureAcceptThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    public BluetoothChatServiceReceiver(Context context, mHandler handler) 
    {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state)
    {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Hud.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() 
    {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() 
    {
        if (D) Log.d(TAG, "start");

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) 
    {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);


        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Hud.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Hud.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() 
    {
        if (D) Log.d(TAG, "stop");
        if (mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) 
        {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() 
    {
        // Send a failure message back to the Activity
    	mHandler.obtainMessage(Hud.MESSAGE_CONNECTION_LOST, -1).sendToTarget();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread 
    {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) 
        {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure":"Insecure";
            // Create a new listening server socket
            try 
            {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                MY_UUID_SECURE);
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        @Override
		public void run() 
        {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);
            BluetoothSocket socket = null;
            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) 
            {
                try 
                {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } 
                catch (IOException e) 
                {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) 
                {
                    synchronized (BluetoothChatServiceReceiver.this) 
                    {
                        switch (mState) 
                        {
	                        case STATE_LISTEN:
	                            connected(socket, socket.getRemoteDevice(), mSocketType);
	                            break;
	                        case STATE_NONE:
	                        case STATE_CONNECTED:
	                            // Either not ready or already connected. Terminate new socket.
	                            try 
	                            {
	                                socket.close();
	                            } 
	                            catch (IOException e) 
	                            {
	                                Log.e(TAG, "Could not close unwanted socket", e);
	                            }
	                            break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }

        public void cancel() 
        {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try 
            {
                mmServerSocket.close();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread 
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) 
        {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            // Get the BluetoothSocket input and output streams
            try 
            {
                tmpIn = socket.getInputStream();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
        }

        @Override
		public void run() 
        {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] readBuf;
            byte[] buffer = new byte[1024];
            int bytes = 0;
            ByteArrayOutputStream outputStream;
            int nbytes = 0;
            // Keep listening to the InputStream while connected
            while (true) {
                int length = 0;
                outputStream = new ByteArrayOutputStream();
                do {
                    try {
                        buffer = new byte[990];
                        // Read from the InputStream
                        nbytes = mmInStream.read(buffer);
                        Log.v(TAG, "read nbytes: " + nbytes);

                        // Send the obtained bytes to the UI Activity
                    } catch (IOException e) {
                        Log.v(TAG, "disconnected", e);
                        connectionLost();
                        // Start the service over to restart listening mode
//                    BluetoothChatServiceReceiver.this.start();
                        break;
                    }
                    try {
                        outputStream.write(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } while (nbytes == 990);
                readBuf = outputStream.toByteArray();
                Log.v(TAG, "total bytes: " + readBuf.length);
                mHandler.obtainMessage(Hud.MESSAGE_READ, readBuf.length, -1, readBuf).sendToTarget();
            }
        }

        public void cancel() 
        {
            try 
            {
                mmSocket.close();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
