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

package com.carhud.app.cobra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.carhud.app.Hud;
import com.carhud.app.Hud.cHandler;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class CobraBluetoothChatService 
{
    // Debugging
    private static final String TAG = "com.carhud.app.cobrabluetoothchatservice";
    private static final boolean D = true;
    public int counter = 1;

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//  UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66"); //ANDROID DEVICE
    private static final UUID MY_UUID_INSECURE =
//          UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
      		UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final cHandler cHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int CONNECTION_FAILED = 4; // connection failed

    public CobraBluetoothChatService(Context context, cHandler handler) 
    {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        cHandler = handler;
    }

    private synchronized void setState(int state)
    {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        cHandler.obtainMessage(Hud.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() 
    {
        return mState;
    }

    public synchronized void connect(BluetoothDevice device, boolean secure) 
    {
        if (D) Log.d(TAG, "connect to: " + device);
        if (mState == STATE_CONNECTING) 
        {
            if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        }
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) 
    {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);
        if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
        if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();
        Message msg = cHandler.obtainMessage(Hud.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Hud.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        cHandler.sendMessage(msg);
        setState(STATE_CONNECTED);
    }

    public synchronized void stop() 
    {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) 
        {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(STATE_NONE);
    }
    public void write(byte[] out) 
    {
        ConnectedThread r;
        synchronized (this) 
        {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }

    private void connectionFailed() 
    {
    	cHandler.obtainMessage(Hud.MESSAGE_STATE_CHANGE, CONNECTION_FAILED, -1).sendToTarget();
    }

    private void connectionLost() 
    {
        cHandler.obtainMessage(Hud.MESSAGE_CONNECTION_LOST, -1).sendToTarget();
    }

    private class ConnectThread extends Thread 
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) 
        {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try 
            {
                if (counter % 2 == 0) 
                {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } 
                else 
                {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            }
            catch (IOException e) 
            {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        @Override
		public void run() 
        {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);
            mAdapter.cancelDiscovery();
            try 
            {
                mmSocket.connect();
            } 
            catch (IOException e) 
            {
                try 
                {
                    mmSocket.close();
                } 
                catch (IOException e2) 
                {
                    Log.e(TAG, "unable to close() " + mSocketType +  " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            synchronized (CobraBluetoothChatService.this) 
            {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() 
        {
            try 
            {
                mmSocket.close();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread 
    {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) 
        {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try 
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
        @Override
		public void run()
        {
        	int[] arrayOfInt = new int[32];
    		while (true)
    		{       	
	        	try
	        	{
        			if (mmInStream.read() == 36)
        			{
        				arrayOfInt[0] = 36;
        				for (int i=1;i<32;i++)
        					arrayOfInt[i] = mmInStream.read();
        				if (arrayOfInt[31] == 141)
        				cHandler.obtainMessage(Hud.MESSAGE_READ, arrayOfInt).sendToTarget();
        			}
        		}
	            catch (IOException e)
	            {
	            	Log.e(TAG, "disconnected", e);
	  				connectionLost();
	  				break;
	            }	        	
    		}
        }        

        public void write(byte[] buffer) 
        {
            try 
            {
                mmOutStream.write(buffer);
                cHandler.obtainMessage(Hud.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "Exception during write", e);
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