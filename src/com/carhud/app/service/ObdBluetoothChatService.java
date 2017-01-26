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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.carhud.app.Hud;
import com.carhud.app.Hud.oHandler;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class ObdBluetoothChatService 
{
    // Debugging
    private static final String TAG = "com.carhud.app.obdbluetoothchatservice";
    private static final boolean D = true;

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE = 
    		UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//    UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66"); //ANDROID DEVICE

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final oHandler oHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private ArrayList<Integer> arrBuffer = null;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int CONNECTION_FAILED = 4; // connection failed

    public ObdBluetoothChatService(Context context, oHandler handler) 
    {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        oHandler = handler;
    }

    private synchronized void setState(int state)
    {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        oHandler.obtainMessage(Hud.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
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
        Message msg = oHandler.obtainMessage(Hud.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Hud.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        oHandler.sendMessage(msg);

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
    	oHandler.obtainMessage(Hud.MESSAGE_STATE_CHANGE, CONNECTION_FAILED, -1).sendToTarget();
    }
    private void connectionLost() 
    {
        oHandler.obtainMessage(Hud.MESSAGE_CONNECTION_LOST, Hud.MESSAGE_CONNECTION_LOST, -1).sendToTarget();
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
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
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
                    Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }
            synchronized (ObdBluetoothChatService.this) 
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
        public void write(byte[] buffer) 
        {
            String out = new String(buffer);
            try 
            {
                mmOutStream.write(buffer);
                mmOutStream.flush();
            } 
            catch (IOException e) 
            {
                Log.e(TAG, "Exception during write", e);
                connectionLost();
        		return;
            }
            //SKIP RESULT FOR ATZ SINCE IT IS SOMETIMES BLANK
            if (out.equals("ATZ\r"))
            {
            	//WAIT 5 SECONDS FOR ELM TO CONTINUE
            	try
            	{
            		Thread.sleep(5000);
            	}
                catch (InterruptedException e)
                {
                	Log.e(TAG, "Sleep exception", e);
                }            	
            	try
            	{
            		mmOutStream.flush();
            	}
                catch (IOException e)
                {
                	Log.e(TAG, "Flush out exception", e);
                }            	
            	return;
            }
            else
            {
            	arrBuffer = new ArrayList<Integer>();
            	String rawData = "";
                byte b = 0;
                int bytes;    
                StringBuilder res = new StringBuilder();
                // read until '>' arrives
                try
                {
                	long giveup = System.currentTimeMillis() + 15000;
                	boolean timeout = false;
                	boolean found = false;
                	while (!timeout && !found)
                	{
                    	int available = mmInStream.available();
                		if (available > 0)
                		{
                			if ((char) (b = (byte) mmInStream.read()) != '>')
                			{
                				if ((char) b != ' ' && (char) b != '\n' && (char) b != '\r')
                					res.append((char) b);
                			}
                			else
                			{
                				found = true;
                    			rawData = res.toString().trim();
                				break;
                			}
                		}
                		Thread.sleep(1);
                		long timenow = System.currentTimeMillis();
                		if (timenow > giveup)
                		{
                			timeout = true;
                			rawData = res.toString().trim();
                			rawData+= "-TIMEOUT";
                		}
                	}
                }
                catch (IOException e)
                {
                	Log.e(TAG, "Read exception", e);    
                	connectionLost();
                }
                catch (InterruptedException e)
                {
                	Log.e(TAG, "Exception between write and read", e);
                }            	

	            byte[] data = rawData.getBytes();
	            bytes = data.length;	    	            	

	            if (rawData.contains("SEARCHING") || rawData.contains("DATA") || rawData.contains("OK") || rawData.contains("ELM327") || rawData.contains("TIMEOUT"))
	            {
	            	return;
	            }
	            else
	            {
		            //FILL ARRAY BUFFER
	                arrBuffer = new ArrayList<Integer>();	            	
		            int begin = 0;
		            int end = 2;
		            try
		            {
			            while (end <= rawData.length()) 
			            {
			            	String temp = "0x" + rawData.substring(begin, end);
			            	arrBuffer.add(Integer.decode(temp));
			            	begin = end;
			            	end += 2;
			            }
		            }
		            //BAD DATA
		            catch (NumberFormatException e)
		            {
		            	return;
		            }
		            
	            	int test1 = arrBuffer.get(0); //VERIFY THE DATA
	            	int test2 = arrBuffer.get(1); 
	            	if (test1 == 65) //(BYTE 1 IS "41")
	            	{
			            if (out.contains("010C")) //RPM
			            {
			            	if (test2 == 12)
			            	{
					    		int rpma = arrBuffer.get(2);
					    		int rpmb = arrBuffer.get(3);
					    		String rpm = "RPM~" + ((rpma * 256 + rpmb) / 4) + "~";
					    		data = rpm.getBytes();
					    		bytes = data.length;	            			        		
					    		oHandler.obtainMessage(Hud.MESSAGE_READ, bytes, -1, data).sendToTarget();
			            	}
			            }
			            else if (out.contains("010D")) //SPEED
			            {
			            	if (test2 == 13)
			            	{
				            	int metricSpeed = arrBuffer.get(2);
				            	String kph = "SPEED~" + metricSpeed + "~";
				            	data = kph.getBytes();
				            	bytes = data.length;	            			        		
				            	oHandler.obtainMessage(Hud.MESSAGE_READ, bytes, -1, data).sendToTarget();
			            	}
			            }
			            else if (out.contains("0105")) //COOLANT
				        {
			            	if (test2 == 5)
			            	{
				            	int temp = arrBuffer.get(2) - 40;
				            	String temp_c = "COOLANT~" + temp + "~";
				            	data = temp_c.getBytes();
				            	bytes = data.length;	            			        		
				            	oHandler.obtainMessage(Hud.MESSAGE_READ, bytes, -1, data).sendToTarget();
			            	}
			            }
	            	}
	            }
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
