package com.carhud.app.service;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class CarHudNotificationListenerService extends NotificationListenerService
{
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) 
    {
    	if (sbn.getPackageName().equals("com.google.android.apps.maps"))
    	{
    		String ss = sbn.getNotification().extras.get("android.text").toString();
    		Intent i = new  Intent("com.carhud.app.NOTIFICATION_LISTENER_MESSAGE");
//    		i.putExtra("notification_event","NAVIGATION~" + sbn.getId() + "~POSTED~" + ss + "~");
    		i.putExtra("notification_event","NAVIGATION~POSTED~" + ss + "~");    		
    		sendBroadcast(i);
    	}
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) 
    {
    	if (sbn.getPackageName().equals("com.google.android.apps.maps"))
    	{    	
    		Intent i = new  Intent("com.carhud.app.NOTIFICATION_LISTENER_MESSAGE");
//    		i.putExtra("notification_event", "NAVIGATION~" + sbn.getId() + "~CLEARED~");
    		i.putExtra("notification_event", "NAVIGATION~CLEARED~");    		
    		sendBroadcast(i);
    	}
    }
}