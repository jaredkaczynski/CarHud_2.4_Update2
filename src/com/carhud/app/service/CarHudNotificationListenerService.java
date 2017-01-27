package com.carhud.app.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.carhud.app.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jp.yokomark.remoteview.reader.RemoteViewsInfo;
import jp.yokomark.remoteview.reader.RemoteViewsReader;
import jp.yokomark.remoteview.reader.action.BitmapReflectionAction;
import jp.yokomark.remoteview.reader.action.RemoteViewsAction;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CarHudNotificationListenerService extends NotificationListenerService
{
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
	@Override
    public void onNotificationPosted(StatusBarNotification sbn) 
    {
        Log.v("Notification",sbn.getPackageName());
    	if (sbn.getPackageName().equals("com.google.android.apps.maps"))
    	{
            Bitmap bmp = null;
            String ss = null;
            RemoteViews remoteViews = sbn.getNotification().contentView;
            RemoteViewsInfo info = RemoteViewsReader.read(this, sbn.getNotification().contentView);
            for (RemoteViewsAction action : info.getActions()) {
                if (!(action instanceof BitmapReflectionAction))
                    continue;
                BitmapReflectionAction concrete = (BitmapReflectionAction)action;
                bmp = concrete.getBitmap();
                //SaveImage(bmp);

            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Notification temp = sbn.getNotification();
                Log.v("Notification",temp.toString());
                Log.v("Notification",temp.extras.toString());
                if(temp.bigContentView != null){
                    Log.v("Notification1",temp.bigContentView.toString());
                    // Use reflection to examine the m_actions member of the given RemoteViews object.
                    // It's not pretty, but it works.
                    RemoteViews views = temp.bigContentView;
                    List<String> text = new ArrayList<>();
                    try
                    {
                        Field field = views.getClass().getDeclaredField("mActions");
                        field.setAccessible(true);

                        @SuppressWarnings("unchecked")
                        ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field.get(views);
                        Log.v("actions",actions.toString());
                        // Find the setText() and setTime() reflection actions
                        for (Parcelable p : actions)
                        {
                            Parcel parcel = Parcel.obtain();
                            p.writeToParcel(parcel, 0);
                            parcel.setDataPosition(0);

                            // The tag tells which type of action it is (2 is ReflectionAction, from the source)
                            int tag = parcel.readInt();
                            Log.v("Tag Value", String.valueOf(tag));
                            if (tag != 2 && tag != 12) continue;

                            // View ID
                            parcel.readInt();

                            String methodName = parcel.readString();
                            if (methodName == null) continue;
                                // Save strings
                            else if (methodName.equals("setText"))
                            {
                                // Parameter type (10 = Character Sequence)
                                parcel.readInt();

                                // Store the actual string
                                String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString();
                                Log.v("reflect1",t);
                                text.add(t);
                            }
                            else if (methodName.equals("setString"))
                            {
                                // Parameter type (10 = Character Sequence)
                                parcel.readInt();

                                // Store the actual string
                                String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString();
                                Log.v("reflect2",t);
                                text.add(t);
                            }
                            else if (methodName.equals("setImageBitmap"))
                            {
                                // Parameter type (10 = Character Sequence)
                                /*parcel.setDataPosition(0);
                                parcel.toString();

                                // Store the actual string
                                Bitmap t = Bitmap.CREATOR.createFromParcel(parcel);*/
                                Log.v("reflect2", parcel.toString());
                            }

                            // Save times. Comment this section out if the notification time isn't important
                            else if (methodName.equals("setTime"))
                            {
                                // Parameter type (5 = Long)
                                parcel.readInt();

                                String t = new SimpleDateFormat("h:mm a").format(new Date(parcel.readLong()));
                                text.add(t);
                            } else {
                                Log.v("Method",methodName);
                            }

                            parcel.recycle();
                        }
                    }

                    // It's not usually good style to do this, but then again, neither is the use of reflection...
                    catch (Exception e)
                    {
                        Log.e("NotificationClassifier", e.toString());
                    }
                    Log.v("Notification1",text.toString());
                    ss = text.get(2);
                }
                if(temp.contentView != null){
                    Log.v("Notification1",temp.contentView.toString());
                    // Use reflection to examine the m_actions member of the given RemoteViews object.
                    // It's not pretty, but it works.
                    RemoteViews views = temp.contentView;
                    List<String> text = new ArrayList<>();
                    try
                    {
                        Field field = views.getClass().getDeclaredField("mActions");
                        field.setAccessible(true);

                        @SuppressWarnings("unchecked")
                        ArrayList<Parcelable> actions = (ArrayList<Parcelable>) field.get(views);
                        Log.v("actions",actions.toString());
                        // Find the setText() and setTime() reflection actions
                        for (Parcelable p : actions)
                        {
                            Parcel parcel = Parcel.obtain();
                            p.writeToParcel(parcel, 0);
                            parcel.setDataPosition(0);

                            // The tag tells which type of action it is (2 is ReflectionAction, from the source)
                            int tag = parcel.readInt();
                            Log.v("Tag Value", String.valueOf(tag));
                            if (tag != 2 && tag != 12) continue;

                            // View ID
                            parcel.readInt();

                            String methodName = parcel.readString();
                            if (methodName == null) continue;
                                // Save strings
                            else if (methodName.equals("setText"))
                            {
                                // Parameter type (10 = Character Sequence)
                                parcel.readInt();

                                // Store the actual string
                                String t = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel).toString();
                                Log.v("reflect1",t);
                                text.add(t);
                            }
                            // Save times. Comment this section out if the notification time isn't important
                            else if (methodName.equals("setTime"))
                            {
                                // Parameter type (5 = Long)
                                parcel.readInt();

                                String t = new SimpleDateFormat("h:mm a").format(new Date(parcel.readLong()));
                                text.add(t);
                            } else {
                                Log.v("Method",methodName);
                            }

                            parcel.recycle();
                        }
                    }

                    // It's not usually good style to do this, but then again, neither is the use of reflection...
                    catch (Exception e)
                    {
                        Log.e("NotificationClassifier", e.toString());
                    }
                    Log.v("Notification1",text.toString());
                    //Split on -
                    String[] BigData = text.get(1).split("-");
                    //Arrival time, Destination
                    ss  += "\n" + BigData[1] + "\n" + BigData[0];
                }
            }

            Intent i = new  Intent("com.carhud.app.NOTIFICATION_LISTENER_MESSAGE");
//    		i.putExtra("notification_event","NAVIGATION~" + sbn.getId() + "~POSTED~" + ss + "~");
    		i.putExtra("notification_event","NAVIGATION~POSTED~" + ss + "~");
            //Convert to byte array
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();

            //i.putExtra("Image",byteArray);
    		sendBroadcast(i);
    	}
    }

    private static void SaveImage(Bitmap finalBitmap) {

        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        File myDir = new File(root + "/saved_images");
        myDir.mkdirs();

        String fname = "Image1-"+ "test" +".jpg";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
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