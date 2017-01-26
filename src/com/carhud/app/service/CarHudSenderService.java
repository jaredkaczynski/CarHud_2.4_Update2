package com.carhud.app.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.carhud.app.CarHudApplication;
import com.carhud.app.Hud;
import com.carhud.app.R;
import com.maxmpz.poweramp.player.PowerampAPI;

public class CarHudSenderService extends Service
{
	public static final String TAG = "com.carhud.app.carhudsenderservice";
	private static final boolean D = true;
	
	private nHandler nHandler = new nHandler(this);
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatServiceSender mChatService = null;
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_DEVICE_NAME = 2;
	public static final int MESSAGE_READ = 3;
    public static final int MESSAGE_WRITE = 4;
	public static final int MESSAGE_CONNECTION_LOST = 6;
	private BroadcastReceiver mediaReceiver;
    String btaddress = "", artist = "", album = "", track = "";
    WakeLock wakeLock;
    final int HELLO_ID = 1;
    private static Hud HUD;
    Timer timer = new Timer();
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	Boolean connectionRestarting = false;
	Boolean sendGPSdata, mockGPSsender;
	private NotificationReceiver nReceiver;
	private LocationManager locationManager;
	private LocationListener locationListener;
	double currentSpeed, currentAltitude, lat, lon;
	String currentTime;
	
	@Override
	public IBinder onBind(Intent intent) 
	{
 		if (D) Log.w(TAG, "onBind()");
		return null;
	}
    
 	@Override
	public void onCreate() 
	{
    	if (D) Log.w(TAG, "onCreate()");
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
		cha.setServiceRunning(true);
	}
 	
	@Override
	public void onStart(Intent intent, int startid)
	{
    	if (D) Log.w(TAG, "onStart()");
		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
		cha.setServiceRunning(true);
		init();
	}
	
	public static void setMainActivity(Hud activity)
	{
		HUD = activity;
	}

	//SEE IF MOCK LOCATION IS ENABLED
	public static boolean isMockSettingsON(Context context) 
	{
		// returns true if mock location enabled, false if not enabled.
		if (Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).equals("0"))
			return false;
		else
			return true;
	}
	 
 	@Override
 	public int onStartCommand(Intent intent, int flags, int startId)
 	{
    	if (D) Log.w(TAG, "onStartCommand()");
		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
		cha.setServiceRunning(true);

 		//CREATE WAKELOCK TO KEEP WORKING WHEN SYSTEM OFF
 		PowerManager mgr = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
 		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
 		wakeLock.acquire();
 		
		//REGISTER MEDIA METADATA RECEIVER
		if (mediaReceiver == null)
		{
			mediaReceiver = new MediaReceiver();
			IntentFilter filter = new IntentFilter();
			filter.addAction("com.android.music.metachanged");
			filter.addAction("com.android.music.playstatechanged");
			filter.addAction("com.android.music.playbackcomplete");
			filter.addAction("com.android.music.queuechanged");
			filter.addAction(PowerampAPI.ACTION_TRACK_CHANGED);
			filter.addAction("android.provider.Telephony.SMS_RECEIVED");
			filter.addAction("android.intent.action.PHONE_STATE");			
			registerReceiver(mediaReceiver, filter);
		}

		//REGISTER NOTIFICATION LISTENER RECEIVER (ONLY WORKS FOR 4.3 AND UP)
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean interceptNav = sharedPrefs.getBoolean("interceptNav", false);
    	if(Build.VERSION.SDK_INT >= 18 && interceptNav)
    	{
	        nReceiver = new NotificationReceiver(this);
	        IntentFilter filter = new IntentFilter();
	        filter.addAction("com.carhud.app.NOTIFICATION_LISTENER_MESSAGE");
	        registerReceiver(nReceiver,filter);
    	}
		sendGPSdata = sharedPrefs.getBoolean("sendGPSdata", false);
		if (sendGPSdata)
		{
			locationManager = (LocationManager) getSystemService (Context.LOCATION_SERVICE);
			mockGPSsender = sharedPrefs.getBoolean("mockGPSsender", false);
			if (mockGPSsender)
			{
				if (isMockSettingsON(this))
				{
					String mocLocationProvider = LocationManager.GPS_PROVIDER;
					locationManager.addTestProvider(mocLocationProvider, false, false, false, false, true, true, true, 0, 5);
					locationManager.setTestProviderEnabled(mocLocationProvider, true);
				}
				else
					HUD.stopServiceMockGPS();
			}	
			
			if ( locationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ))
			{
				if (D) Log.d(TAG, "gps says its on!");
				getGPSdata();
			}
			else
				HUD.stopServiceGPS();
		}

		init(); 		

		return START_STICKY;
 	}
 	
	//START GPS LISTENER
	public void getGPSdata()
	{
		if (D) Log.d(TAG, "getGPSdata()");
		locationListener = new gpsSpeedListener(); 
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
	}
	
	private class gpsSpeedListener implements LocationListener 
	{ 
		@Override 
		public void onLocationChanged(Location location) 
		{ 
			if(location!=null) 
			{ 
				String speed = "0"; 
				String altitude = "0";
				String time = "0";
				
				if(location.hasSpeed())
					speed = "" + (location.getSpeed()*3600)/1000;

				if (location.hasAltitude())
					altitude = "" + location.getAltitude();

				Date d = new Date(location.getTime());			
				SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss a", Locale.US);
				time = df.format(d);

				lat = location.getLatitude();
				lon = location.getLongitude();
				
				String tmp = "GPS~" + speed + "~" + altitude + "~" + time + "~" + lat + "~" + lon + "~";				
                byte[] tmpBA = tmp.getBytes();
                mChatService.write(tmpBA);				
            } 
        } 
        @Override 
        public void onProviderDisabled(String provider) 
        { 
        } 
        @Override 
        public void onProviderEnabled(String provider) 
        { 
        } 
        @Override 
        public void onStatusChanged(String provider, int status, Bundle extras) 
        { 
        } 
	 }	
 		
 	//SEND INFO TO THE ONGOING NOTIFICATION
    private void sendNotification(String msg, Boolean connected)
    {
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	if (cha.getServiceRunning())
    	{
    		NotificationCompat.Builder builder;
    		if (connected)
    		{
		    	builder = new NotificationCompat.Builder(this)  
		    		.setSmallIcon(R.drawable.ic_launcher_connected)  
		    		.setContentTitle("CarHud")  
		    		.setContentText(msg)
		    		.setOngoing(true);
    		}
    		else
    		{
		    	builder = new NotificationCompat.Builder(this)  
			    		.setSmallIcon(R.drawable.ic_launcher)  
			    		.setContentTitle("CarHud")  
			    		.setContentText(msg)
			    		.setOngoing(true);  
    		}
	    	Context appcontext = (getApplicationContext());
			Intent notificationIntent = new Intent(Intent.ACTION_MAIN, null, appcontext, Hud.class)
			.addCategory(Intent.CATEGORY_LAUNCHER);
	    	PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);  
	    	builder.setContentIntent(contentIntent);  
	    	NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);  
	    	manager.notify(HELLO_ID, builder.build());
    	}
    }
    
 	// INITIALIZE THE CONNECTION
 	private void init()
 	{
 		if (D) Log.w(TAG, "init()");

 		//DETERMINE IF BT ADAPTER IS ENABLED AND START THE CONNECTION IF SO
	    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        btaddress = sharedPrefs.getString("btaddress","");	    
	    if (mBluetoothAdapter.isEnabled() && !btaddress.isEmpty()) 
	    {
            if (mChatService == null) 
            	mChatService = new BluetoothChatServiceSender(this, nHandler);            	
	        if (D) Log.w(TAG, "Service Created, BT: " + btaddress);
	        sendNotification(getString(R.string.service_running), false);
    		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(btaddress);
        	mChatService.connect(device, true);
        	connectionRestarting = false;
	    }
	    //OTHERWISE, WAIT 5 SECONDS AND START AGAIN
	    else
	    {
    		sendNotification(getString(R.string.bt_not_available), false);
    		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    		cha.setLastMessage(HUD.getString(R.string.bt_not_available));
    		restartConnection();
	    }
 	}

	//RESTART THE CONNECTION ON A TIMER
	public void restartConnection()
	{
		if (D) Log.w(TAG, "restartConnection()");
		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
		if(cha.getServiceRunning())
		{
			if (!connectionRestarting)
			{
				try
				{
					timer.schedule(new TimerTask() {@Override
					public void run() {init();}}, 5000);
					connectionRestarting = true;
				}
				catch (IllegalStateException e)
				{
					if (D) Log.w(TAG, "restartConnection() timer was cancelled");				
				}
			}
		}
	}

	//BROADCAST RECEIVER MESSAGES FROM BLUETOOTH SERVICE
	private class MediaReceiver extends BroadcastReceiver 
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			if (D) Log.w(TAG, "MediaReceiver onReceive()");
			String action = intent.getAction();
			if(action.substring(0,22).equals("com.maxmpz.audioplayer"))
			{
				if (D) Log.d(TAG, "MediaReceiver received poweramp action: " + action);
                Bundle bundle = intent.getBundleExtra(PowerampAPI.TRACK);
                artist = bundle.getString(PowerampAPI.Track.ARTIST);
                album = bundle.getString(PowerampAPI.Track.ALBUM);
                track = bundle.getString(PowerampAPI.Track.TITLE);
                String tmp = "MEDIA~" + artist + "~" + album + "~" + track + "~\n";
                byte[] tmpBA = tmp.getBytes();
                mChatService.write(tmpBA);
			}
			if(action.equals("com.android.music.metachanged") || action.equals("com.android.music.playstatechanged") || action.equals("com.android.music.playbackcomplete") || action.equals("com.android.music.queuechanged"))
			{
				artist = intent.getStringExtra("artist");
				album = intent.getStringExtra("album");
				track = intent.getStringExtra("track");
  	    	  	String tmp = "MEDIA~" + artist + "~" + album + "~" + track + "~\n";
  	    	  	byte[] tmpBA = tmp.getBytes();
  	    	  	mChatService.write(tmpBA);
			}
		    if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED"))
		    {
                Bundle bundle = intent.getExtras();
                if (bundle != null) 
                {
                    Object[] pdus = (Object[])bundle.get("pdus");
                    for (int i = 0; i < pdus.length; i++) 
                    {
                        SmsMessage SMessage = SmsMessage.createFromPdu((byte[])pdus[i]);
                        String ContactName = SMessage.getOriginatingAddress();
                        Uri lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(ContactName));
                        Cursor c = context.getContentResolver().query(lookupUri, new String[]{ContactsContract.Data.DISPLAY_NAME},null,null,null);
                        try 
                        {
                        	c.moveToFirst();
                        	String displayName = c.getString(0);
                        	ContactName = displayName;
                        }
                        catch (Exception e)
                        {
                        }
                        c.close();
                    	String title = "SMS Message Received From: " + ContactName;
                    	String message = SMessage.getMessageBody().toString(); 
                        String tmp = "SMS~" + title + "~" + message + "~\n";
                        byte[] tmpBA = tmp.getBytes();
                        mChatService.write(tmpBA);
                    }
                }
	        }
		    if(intent.getAction().equals("android.intent.action.PHONE_STATE"))
		    {
		    	String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
		    	if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) 
		        {
		    	    Bundle bundle = intent.getExtras();
		    	    String phoneNumber= bundle.getString("incoming_number");
		    	    if(phoneNumber != null)
		    	    {   
		    	    	String contactName = "";
                        Uri lookupUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                        Cursor c = context.getContentResolver().query(lookupUri, new String[]{ContactsContract.Data.DISPLAY_NAME},null,null,null);
                        try 
                        {
                        	c.moveToFirst();
                        	String displayName = c.getString(0);
                        	contactName = displayName;
                        }
                        catch (Exception e)
                        {
                        }
                        c.close();
		    	    	
		    	    	
                    	String title = "Incoming Call From: " + phoneNumber;
                    	String message;
                    	if (contactName != "")
                    		message = "Contact: " + contactName;
                    	else
                    		message = "Contact: UNKNOWN";
                        String tmp = "CALL~" + title + "~" + message + "~\n";
                        byte[] tmpBA = tmp.getBytes();                    	
                        mChatService.write(tmpBA);
		    	    }
		        }
		    }		    
		}
	};	
	    
    public static class nHandler extends Handler 
    {    	 
        private final CarHudSenderService mService;
        public nHandler(CarHudSenderService service) 
        {
            mService = service;
        }
 
        @Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) 
            {
            	case MESSAGE_STATE_CHANGE:
                	if (D) Log.w(TAG, "Detected state change of " + msg.arg1);

                    switch (msg.arg1) 
                    {
                    	case BluetoothChatServiceSender.CONNECTION_FAILED:
                    		mService.sendNotification(HUD.getString(R.string.connection_failed), false);
                    		HUD.setSenderText(HUD.getString(R.string.connection_failed));
                    		mService.restartConnection();
                    		break;
                    	case BluetoothChatServiceSender.STATE_CONNECTED:
                    		mService.sendNotification(HUD.getString(R.string.connected_to) + mService.btaddress, true);
                    		HUD.setSenderText(HUD.getString(R.string.connected_to) + mService.btaddress);                    		
                    		//SEND MEDIA INFO
                    		if (!mService.artist.isEmpty())
                    		{
                    			String tmp = "MEDIA~" + mService.artist + "~" + mService.album + "~" + mService.track + "~\n";
              	    	  		byte[] tmpBA = tmp.getBytes();
              	    	  		mService.mChatService.write(tmpBA);
                    		}
                    		break;
                    	case BluetoothChatServiceSender.STATE_CONNECTING:
                    		mService.sendNotification(HUD.getString(R.string.connecting), false);
                    		HUD.setSenderText(HUD.getString(R.string.connecting));
							break;
                    	case BluetoothChatServiceSender.STATE_LISTEN:
                    	case BluetoothChatServiceSender.STATE_NONE:
                    		mService.sendNotification(HUD.getString(R.string.not_connected), false);
                    		HUD.setSenderText(HUD.getString(R.string.not_connected));
                    		break;
                    }
                    break;
            	case MESSAGE_CONNECTION_LOST:
            		mService.restartConnection();
            		break;            		
                case MESSAGE_WRITE:
//                	if (D) Log.d(TAG, "message_write()");
                    break;            		
            	case MESSAGE_READ:
//            		if (D) Log.d(TAG, "message_read()");
	                break;
                case MESSAGE_DEVICE_NAME:
                    HUD.setSenderText(HUD.getString(R.string.connected_to) + msg.getData().getString(DEVICE_NAME));
                    break;
            }
        }
    }
	        
	@Override
	public void onDestroy() 
	{	
		if (D) Log.w(TAG, "onDestroy()");
		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
		cha.setServiceRunning(false);
		
		nHandler.removeCallbacksAndMessages(mChatService);
		timer.cancel();

		if (locationManager!= null && locationListener != null)
		{
			locationManager.removeUpdates(locationListener);
			locationManager = null;
		}
		if (mediaReceiver != null)
		{
			unregisterReceiver(mediaReceiver);
			mediaReceiver = null;
		}
		if (nReceiver != null)
		{
			unregisterReceiver(nReceiver);
			nReceiver = null;
		}
	    if (mChatService != null) 
	    {
	    	mChatService.stop();
	    	mChatService = null;
	    }
	    final int HELLO_ID = 1;
		
	    String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);	
		mNotificationManager.cancel(HELLO_ID);

		wakeLock.release();
	    super.onDestroy();
	}

	//RECEIVE NOTIFICATION EVENTS (NAVIGATION) FROM MAPS
    class NotificationReceiver extends BroadcastReceiver
    {
        private final CarHudSenderService mService;
        public NotificationReceiver(CarHudSenderService service) 
        {
            mService = service;
        }
        
        @Override
        public void onReceive(Context context, Intent intent) 
        {
			String tmp = intent.getStringExtra("notification_event");
			if (D) Log.w(TAG, "receive: " + tmp);
  	  		byte[] tmpBA = tmp.getBytes();
  	  		mService.mChatService.write(tmpBA);
        }
    }
}