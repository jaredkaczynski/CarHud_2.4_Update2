package com.carhud.app.usersetting;

import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.carhud.app.R;


public class UserSetting extends PreferenceActivity
{
	   @Override
	   public void onCreate(Bundle savedInstanceState) 
	   {
	        super.onCreate(savedInstanceState);
	        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
	   }	

	   public void exitThis()
	   {
		   finish();
	   }

	   public static class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
	   {

	        @Override
	        public void onCreate(final Bundle savedInstanceState)
	        {
	            super.onCreate(savedInstanceState);
	            addPreferencesFromResource(R.xml.settings);
	        }
	        
		   @Override
		   public void onResume() 
		   {
			   super.onResume();
			   // Set up a listener whenever a key changes
			   getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
			   
	            PreferenceScreen preferenceScreen = getPreferenceScreen();
	            SharedPreferences sp = preferenceScreen.getSharedPreferences();

	            //HIDE OTHER PREF CATEGORIES
	            PreferenceGroup preferenceGroup;
	            preferenceGroup = (PreferenceGroup) findPreference("standaloneSettings");
	            if (preferenceGroup != null)
	            	preferenceScreen.removePreference(preferenceGroup);
	            preferenceGroup = (PreferenceGroup) findPreference("receiverSettings");
	            if (preferenceGroup != null)
	            	preferenceScreen.removePreference(preferenceGroup);	            
	            preferenceGroup = (PreferenceGroup) findPreference("senderSettings");
	            if (preferenceGroup != null)
	            	preferenceScreen.removePreference(preferenceGroup);
	            
	            //SHOW BASED ON RUN TYPE
	            ListPreference mListPreference = (ListPreference) findPreference("setupType");
		        int val = Integer.parseInt(sp.getString("setupType","Choose the setup type for this device"));
				String[] lArr = getResources().getStringArray(R.array.setupTypeList);
				String[] lvArr = getResources().getStringArray(R.array.setupTypeListValues);
				for(int i = 0; i < lvArr.length; i++)
				{
					if (val == Integer.parseInt(lvArr[i]))
				        mListPreference.setSummary(lArr[i]);							
				}
			    switch (val) 
			    {
				    case 1:
				    	addPreferencesFromResource(R.xml.standalone_settings);
				    	setupStandReceiverSettings();
				        break;
				    case 2:
				    	addPreferencesFromResource(R.xml.receiver_settings);
				    	setupStandReceiverSettings();
				    	break;
				    case 3:
				    	addPreferencesFromResource(R.xml.sender_settings);
				    	setupSenderSettings();
				    	break;
			    }									
		   }
	
		   @Override
		   public void onPause() 
		   {
			   super.onPause();
			   // Set up a listener whenever a key changes
			   getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		   }
	
		   @Override
		   public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) 
		   {
		   		CheckBoxPreference cbp;
			   	ListPreference lp;
			   	String tmp;

			   	if (key.equals("setupType"))
			   	{
		            PreferenceScreen preferenceScreen = getPreferenceScreen();			   		
		            //HIDE OTHER PREF CATEGORIES
		            PreferenceGroup preferenceGroup;
		            preferenceGroup = (PreferenceGroup) findPreference("standaloneSettings");
		            if (preferenceGroup != null)
		            	preferenceScreen.removePreference(preferenceGroup);
		            preferenceGroup = (PreferenceGroup) findPreference("receiverSettings");
		            if (preferenceGroup != null)
		            	preferenceScreen.removePreference(preferenceGroup);	            
		            preferenceGroup = (PreferenceGroup) findPreference("senderSettings");
		            if (preferenceGroup != null)
		            	preferenceScreen.removePreference(preferenceGroup);				    			            
			   		
			   		ListPreference mListPreference = (ListPreference) getPreferenceScreen().findPreference("setupType");
			        int val = Integer.parseInt(sharedPreferences.getString(key,""));
					String[] lArr = getResources().getStringArray(R.array.setupTypeList);
					String[] lvArr = getResources().getStringArray(R.array.setupTypeListValues);
					for(int i = 0; i < lvArr.length; i++)
					{
						if (val == Integer.parseInt(lvArr[i]))
							mListPreference.setSummary(lArr[i]);
					}
				    switch (val) 
				    {
				    case 1:
				    	addPreferencesFromResource(R.xml.standalone_settings);
				    	setupStandReceiverSettings();
				        break;
				    case 2:
				    	addPreferencesFromResource(R.xml.receiver_settings);
				    	setupStandReceiverSettings();
				    	break;
				    case 3:
				    	addPreferencesFromResource(R.xml.sender_settings);
				    	setupSenderSettings();
				    	break;
				    }					
			   	}
			   	//STANDALONE/RECEIVER SETTINGS
			   	else if (key.equals("speedType"))
			   	{
				   	lp = (ListPreference) findPreference("speedType");
				   	tmp = getPreferenceScreen().getSharedPreferences().getString("speedType", "");
					String[] stlArr = getResources().getStringArray(R.array.speedTypeList);
					String[] stlvArr = getResources().getStringArray(R.array.speedTypeListValues);
					for(int i = 0; i < stlvArr.length; i++)
					{
						if (stlvArr[i].equals(tmp))
							lp.setSummary(stlArr[i]);
					}
			   	}
			   	else if (key.equals("mockGPS"))
			   	{
					cbp = (CheckBoxPreference) findPreference("mockGPS");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mockGPS", false) ? "Enabled" : "Disabled");							   		
			   	}
			   	else if (key.equals("showAltitude"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showAltitude");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showAltitude", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showTime"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showTime");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showTime", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showLocalTemp"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showLocalTemp");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showLocalTemp", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showRPM"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showRPM");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showRPM", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showRPMBar"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showRPMBar");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showRPMBar", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showTemp"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showTemp");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showTemp", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("showBat"))
			   	{
					cbp = (CheckBoxPreference) findPreference("showBat");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showBat", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("fullscreen"))
			   	{
					cbp = (CheckBoxPreference) findPreference("fullscreen");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("fullscreen", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("useMetric"))
			   	{
					cbp = (CheckBoxPreference) findPreference("useMetric");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("useMetric", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("useCobra"))
			   	{
			   		cbp = (CheckBoxPreference) findPreference("useCobra");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("useCobra", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("mirror"))
			   	{
					cbp = (CheckBoxPreference) findPreference("mirror");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mirror", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("fullBright"))
			   	{
					cbp = (CheckBoxPreference) findPreference("fullBright");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("fullBright", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("screenOn"))
			   	{
					cbp = (CheckBoxPreference) findPreference("screenOn");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("screenOn", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("obdBtaddress"))
			   	{
			   		showBtAddress("obdBtaddress");
			   	}
			   	else if (key.equals("cobraBtaddress"))
			   	{
			   		showBtAddress("cobraBtaddress");
			   	}

			   	//SENDER SETTINGS
			   	else if (key.equals("interceptNav"))
			   	{
			   		cbp = (CheckBoxPreference) findPreference("interceptNav");
			   		cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("interceptNav", false) ? "Enabled" : "Disabled");
			   	}
			   	else if (key.equals("btaddress"))
			   	{
			   		showBtAddress("btaddress");
			   	}
			   	else if (key.equals("sendGPSdata"))
			   	{
					cbp = (CheckBoxPreference) findPreference("sendGPSdata");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("sendGPSdata", false) ? "Enabled" : "Disabled");
			   	}			   	
			   	else if (key.equals("mockGPSsender"))
			   	{
					cbp = (CheckBoxPreference) findPreference("mockGPSsender");
					cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mockGPSsender", false) ? "Enabled" : "Disabled");			   		
			   	}
		   }
		   
		   public void showBtAddress(String field)
		   {
			   ListPreference lp = (ListPreference) findPreference(field);
			   String mac = getPreferenceScreen().getSharedPreferences().getString(field, "");
			   
			   BluetoothAdapter mBluetoothAdapter = null;
			   	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			   	
			   	if (mBluetoothAdapter.isEnabled() && mBluetoothAdapter != null) 
			   	{
			   		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			   		if (pairedDevices.size() > 0) 
			   		{			   
			   			for (BluetoothDevice device : pairedDevices) 
			   			{
			   				if (mac.equals(device.getAddress()))
			   					mac = device.getName();
			   			}
			   		}
			   	}
			   	if (mac.equals(""))
			   		lp.setSummary("NOT CONFIGURED");
			   	else
			   		lp.setSummary(mac);
		   }		   
		   public void setupStandReceiverSettings()
		   {
			   	CheckBoxPreference cbp;
			   	ListPreference lp;
			   	String tmp;
			   	
			   	//SPEED TYPE
			   	lp = (ListPreference) findPreference("speedType");
			   	tmp = getPreferenceScreen().getSharedPreferences().getString("speedType", "");
				String[] stlArr = getResources().getStringArray(R.array.speedTypeList);
				String[] stlvArr = getResources().getStringArray(R.array.speedTypeListValues);
				for(int i = 0; i < stlvArr.length; i++)
				{
					if (stlvArr[i].equals(tmp))
						lp.setSummary(stlArr[i]);
				}

				//MOCK GPS
				cbp = (CheckBoxPreference) findPreference("mockGPS");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mockGPS", false) ? "Enabled" : "Disabled");				

				//SHOW ALTITUDE
				cbp = (CheckBoxPreference) findPreference("showAltitude");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showAltitude", false) ? "Enabled" : "Disabled");

				//SHOW TIME
				cbp = (CheckBoxPreference) findPreference("showTime");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showTime", false) ? "Enabled" : "Disabled");

				//SHOW LOCAL TEMP
				cbp = (CheckBoxPreference) findPreference("showLocalTemp");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showLocalTemp", false) ? "Enabled" : "Disabled");
				
				//SHOW RPM
				cbp = (CheckBoxPreference) findPreference("showRPM");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showRPM", false) ? "Enabled" : "Disabled");
				
				//SHOW RPM BAR
				cbp = (CheckBoxPreference) findPreference("showRPMBar");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showRPMBar", false) ? "Enabled" : "Disabled");
				
				//SHOW COOLANT TEMP
				cbp = (CheckBoxPreference) findPreference("showTemp");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showTemp", false) ? "Enabled" : "Disabled");
				
				//SHOW BATTERY
				cbp = (CheckBoxPreference) findPreference("showBat");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("showBat", false) ? "Enabled" : "Disabled");
				
				//RUN FULLSCREEN
				cbp = (CheckBoxPreference) findPreference("fullscreen");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("fullscreen", false) ? "Enabled" : "Disabled");

				//USE METRIC
				cbp = (CheckBoxPreference) findPreference("useMetric");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("useMetric", false) ? "Enabled" : "Disabled");

				//USE COBRA
				cbp = (CheckBoxPreference) findPreference("useCobra");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("useCobra", false) ? "Enabled" : "Disabled");

				//USE MIRROR
				cbp = (CheckBoxPreference) findPreference("mirror");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mirror", false) ? "Enabled" : "Disabled");

				//USE FULL BRIGHTNESS
				cbp = (CheckBoxPreference) findPreference("fullBright");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("fullBright", false) ? "Enabled" : "Disabled");

				//USE FORCE SCREEN ON
				cbp = (CheckBoxPreference) findPreference("screenOn");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("screenOn", false) ? "Enabled" : "Disabled");
			   
			   //OBD2, COBRA BT DEVICES
			   BluetoothAdapter mBluetoothAdapter = null;
			   mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			   if (mBluetoothAdapter.isEnabled()) 
			   {
				   Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
				   if (pairedDevices.size() > 0) 
				   {			   
					   String[] pbdevices = new String[pairedDevices.size()];
					   String[] pbdevicesmacs = new String[pairedDevices.size()];
					   int count = 0;
					   for (BluetoothDevice device : pairedDevices) 
					   {
						   pbdevices[count] = device.getName();
						   pbdevicesmacs[count] = device.getAddress();
						   count++;
					   }
					   //OBD2 DEVICE
					   lp = (ListPreference) findPreference("obdBtaddress");
					   lp.setEntries(pbdevices);
					   lp.setEntryValues(pbdevicesmacs);
					   
					   //COBRA DEVICE
					   ListPreference lp2 = (ListPreference) findPreference("cobraBtaddress");
					   lp2.setEntries(pbdevices);
					   lp2.setEntryValues(pbdevicesmacs);
				   }
				   else
				   {
					   CharSequence[] entries = { "No devices found" };
					   CharSequence[] entryValues = { "" };

					   //OBD2 DEVICE
					   lp = (ListPreference) findPreference("obdBtaddress");
					   lp.setEntries(entries);
					   lp.setEntryValues(entryValues);				   

					   //COBRA DEVICE
					   ListPreference lp2 = (ListPreference) findPreference("cobraBtaddress");
					   lp2.setEntries(entries);
					   lp2.setEntryValues(entryValues);				   					  
				   }				   
			   }
			   else
			   {
				   CharSequence[] entries = { "No devices found (is bluetooth on?)" };
				   CharSequence[] entryValues = { "" };

				   //OBD2 DEVICE
				   lp = (ListPreference) findPreference("obdBtaddress");
				   lp.setEntries(entries);
				   lp.setEntryValues(entryValues);				   

				   //COBRA DEVICE
				   ListPreference lp2 = (ListPreference) findPreference("cobraBtaddress");
				   lp2.setEntries(entries);
				   lp2.setEntryValues(entryValues);				   
			   }
			   showBtAddress("obdBtaddress");
			   showBtAddress("cobraBtaddress");
			   
		   }
		   public void setupSenderSettings()
		   {
		    	//SELECT HUD DEVICE
			   	BluetoothAdapter mBluetoothAdapter = null;
			   	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			   	if (mBluetoothAdapter.isEnabled()  && mBluetoothAdapter != null) 
			   	{
			   		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			   		if (pairedDevices.size() > 0) 
			   		{			   
			   			String[] pbdevices = new String[pairedDevices.size()];
			   			String[] pbdevicesmacs = new String[pairedDevices.size()];
			   			int count = 0;
			   			for (BluetoothDevice device : pairedDevices) 
			   			{
			   				pbdevices[count] = device.getName();
			   				pbdevicesmacs[count] = device.getAddress();
			   				count++;
			   			}
			   			ListPreference lp = (ListPreference) findPreference("btaddress");
			   			lp.setEntries(pbdevices);
			   			lp.setEntryValues(pbdevicesmacs);
			   		}
			   		else
			   		{
			   			CharSequence[] entries = { "No devices found" };
			   			CharSequence[] entryValues = { "" };
			   			ListPreference lp = (ListPreference) findPreference("btaddress");
			   			lp.setEntries(entries);
			   			lp.setEntryValues(entryValues);				   
			   		}			   		
			   	}
			   	else
			   	{
			   		CharSequence[] entries = { "No devices found (is bluetooth on?)" };
			   		CharSequence[] entryValues = { "" };
			   		ListPreference lp = (ListPreference) findPreference("btaddress");
			   		lp.setEntries(entries);
			   		lp.setEntryValues(entryValues);
			   	}
			   	showBtAddress("btaddress");
			   	
		    	CheckBoxPreference cbp;

		    	//INTERCEPT NAV
		    	cbp = (CheckBoxPreference) findPreference("interceptNav");
		    	cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("interceptNav", false) ? "Enabled" : "Disabled");

		    	//SEND GPS
				cbp = (CheckBoxPreference) findPreference("sendGPSdata");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("sendGPSdata", false) ? "Enabled" : "Disabled");

		    	//MOCK GPS SENDER
				cbp = (CheckBoxPreference) findPreference("mockGPSsender");
				cbp.setSummary(getPreferenceScreen().getSharedPreferences().getBoolean("mockGPSsender", false) ? "Enabled" : "Disabled");
				
		   }
	   }
}
