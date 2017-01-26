package com.carhud.app.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Intent;
import android.os.Build;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CarHudNotificationListenerServiceAccessibility extends AccessibilityService
{

    protected void onServiceConnected() {
        Log.v("Tortuga", "AccessibilityService Connected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.v("Tortuga","FML");
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Log.v("Tortuga","Recieved event");
            Parcelable data = event.getParcelableData();
            if (data instanceof Notification) {
                Log.v("Tortuga","Recieved notification");
                Notification notification = (Notification) data;
                Log.v("Tortuga","ticker: " + notification.tickerText);
                Log.v("Tortuga","icon: " + notification.icon);
                Log.v("Tortuga", "notification: "+ event.getText());
            }
            Intent i = new  Intent("com.carhud.app.NOTIFICATION_LISTENER_MESSAGE");
//    		i.putExtra("notification_event","NAVIGATION~" + sbn.getId() + "~POSTED~" + ss + "~");
            i.putExtra("notification_event","NAVIGATION~POSTED~" + event.getParcelableData().toString() + "~");
            sendBroadcast(i);
        }
    }

    @Override
    public void onInterrupt() {

    }
}