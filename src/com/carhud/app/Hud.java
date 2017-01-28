package com.carhud.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsMessage;
import android.text.Html;
import android.text.TextPaint;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.carhud.app.cobra.CobraBluetoothChatService;
import com.carhud.app.service.BluetoothChatServiceReceiver;
import com.carhud.app.service.CarHudSenderService;
import com.carhud.app.service.ObdBluetoothChatService;
import com.carhud.app.usersetting.UserSetting;
import com.carhud.app.views.GaugeLinearLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.maxmpz.poweramp.player.PowerampAPI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class Hud extends ActionBarActivity {
    private static final String TAG = "com.carhud.app.hud";
    private static final boolean D = true;

    private static final int RESULT_SETTINGS = 1;
    private static final int RESULT_ENABLE_GPS = 2;
    private static final int RESULT_ENABLE_BLUETOOTH = 3;

    public static final int MESSAGE_STATE_CHANGE = 4;
    public static final int MESSAGE_DEVICE_NAME = 5;
    public static final int MESSAGE_READ = 6;
    public static final int MESSAGE_WRITE = 7;
    public static final int MESSAGE_TOAST = 8;
    public static final int MESSAGE_CONNECTION_LOST = 9;
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    Button stopButton, startButton, closePopUp;

    private BroadcastReceiver mediaReceiver;
    SharedPreferences sharedPrefs;

    Boolean mirror, serviceStarted, fullBright, screenOn, activityStarted, useCobra, useMetric, popupShown = false, topPopupShown = false;
    Boolean showBat, showTemp, showRPM, showRPMBar, fullscreen, showAltitude, showLocalTemp, showTime, mockGPS;
    Boolean obdRestarting = false, cobraRestarting = false;
    Boolean senderConnected = false, cobraConnected = false, obdConnected = false, navigationShown = false, navigationCloseAlert = false, tempMediaShown = false;
    Boolean senderNeeded = false, cobraNeeded = false, obdNeeded = false, statusHiding = false, statusShown = false;

    int setupType, animationOn, densityModifier, artistStart, albumStart, trackStart, statusHeight, dataColor;
    float artistWidth, albumWidth, trackWidth, screenDensity;
    String speedType, btaddress, obdBtaddress, cobraBtaddress, currentTime;
    double screenWidth, lat, lon;
    double currentSpeed, currentAltitude;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private Handler handler = new Handler();
    private Handler aHandler = new Handler();
    private Handler statusViewHandler = new Handler();
    private Handler mediaViewHandler = new Handler();
    private Runnable messageRun;
    LinkedList<String> obdQueue = new LinkedList<String>();
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatServiceReceiver mChatService = null;
    private CobraBluetoothChatService cChatService = null;
    private ObdBluetoothChatService oChatService = null;
    public mHandler mHandler = new mHandler(this);
    public cHandler cHandler = new cHandler(this);
    public oHandler oHandler = new oHandler(this);
    private PopupWindow pw;
    private PopupWindow tpw;
    backgroundObd backgroundObd = new backgroundObd();
    localTempThread localTempThread = new localTempThread();

    LinkedList<messagePopup> popupQueue = new LinkedList<messagePopup>();

    Timer timer = new Timer();

    private static Bitmap NavIcon = null;

    private AdView adView;
    ActionBar ab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (D) Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);
//		requestWindowFeature(Window.FEATURE_NO_TITLE);

        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        cha.setActivityRunning(true);
        activityStarted = cha.getActivityRunning();

        ab = getSupportActionBar();
        ab.setTitle("");
    }

    //SET DISPLAY AND COLOR
    public void initDisplay() {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);

        //SPEEDOMETER AND MEDIA (CONSTANTS)
        TextView speedText = (TextView) findViewById(R.id.speedText);
        speedText.setTextColor(dataColor);
        TextView artistText = (TextView) findViewById(R.id.artistText);
        artistText.setTextColor(dataColor);
        setColorImage("artist", "artistTitle");
        TextView albumText = (TextView) findViewById(R.id.albumText);
        albumText.setTextColor(dataColor);
        setColorImage("album", "albumTitle");
        TextView trackText = (TextView) findViewById(R.id.trackText);
        trackText.setTextColor(dataColor);
        setColorImage("track", "trackTitle");

        //GLOBAL OPTIONS
        showAltitude = sharedPrefs.getBoolean("showAltitude", false);
        if (showAltitude) {
            LinearLayout altitudelayout = (LinearLayout) findViewById(R.id.altitudelayout);
            altitudelayout.setVisibility(View.VISIBLE);
            TextView altitudeText = (TextView) findViewById(R.id.altitudeText);
            altitudeText.setTextColor(dataColor);
            setColorImage("altitude", "altitudeTitle");
        }
        showTime = sharedPrefs.getBoolean("showTime", false);
        if (showTime) {
            LinearLayout timelayout = (LinearLayout) findViewById(R.id.timelayout);
            timelayout.setVisibility(View.VISIBLE);
            TextView timeText = (TextView) findViewById(R.id.timeText);
            timeText.setTextColor(dataColor);
            setColorImage("time", "timeTitle");
        }
        showLocalTemp = sharedPrefs.getBoolean("showLocalTemp", false);
        if (showLocalTemp) {
            LinearLayout localtemplayout = (LinearLayout) findViewById(R.id.localtemplayout);
            localtemplayout.setVisibility(View.VISIBLE);
            TextView localtempText = (TextView) findViewById(R.id.localtempText);
            localtempText.setTextColor(dataColor);
            setColorImage("temp", "localtempTitle");
        }
        //OBD2 OPTIONS
        speedType = sharedPrefs.getString("speedType", "gps");
        if (speedType.equals("obd")) {
            TextView obdConnectionTitle = (TextView) findViewById(R.id.obdConnectionTitle);
            obdConnectionTitle.setTextColor(dataColor);
            findViewById(R.id.obdConnectionTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.obdConnection).setVisibility(View.VISIBLE);

            showRPM = sharedPrefs.getBoolean("showRPM", true);
            showRPMBar = sharedPrefs.getBoolean("showRPMBar", true);

            if (showRPM || showRPMBar) {
                LinearLayout rpmtextandgaugelayout = (LinearLayout) findViewById(R.id.rpmtextandgaugelayout);
                rpmtextandgaugelayout.setVisibility(View.VISIBLE);
            }

            if (showRPM) {
                LinearLayout rpmtextlabellayout = (LinearLayout) findViewById(R.id.rpmtextlabellayout);
                rpmtextlabellayout.setVisibility(View.VISIBLE);

                TextView rpmText = (TextView) findViewById(R.id.rpmText);
                rpmText.setTextColor(dataColor);
                TextView rpmTextLabel = (TextView) findViewById(R.id.rpmTextLabel);
                rpmTextLabel.setTextColor(dataColor);
            }

            if (showRPMBar) {
                LinearLayout rpmgaugelayout = (LinearLayout) findViewById(R.id.rpmgaugelayout);
                rpmgaugelayout.setVisibility(View.VISIBLE);

                GaugeLinearLayout gauge = (GaugeLinearLayout) findViewById(R.id.gauge);
                gauge.setColor(dataColor);
            }

            showTemp = sharedPrefs.getBoolean("showTemp", true);
            if (showTemp) {
                TableRow coolantlayout = (TableRow) findViewById(R.id.coolantlayout);
                coolantlayout.setVisibility(View.VISIBLE);

                setColorImage("temp", "coolantTitle");
                TextView coolantText = (TextView) findViewById(R.id.coolantText);
                coolantText.setTextColor(dataColor);
            }
        }
        //COBRA OPTIONS
        useCobra = sharedPrefs.getBoolean("useCobra", false);
        if (useCobra) {
            TextView cobraConnectionTitle = (TextView) findViewById(R.id.cobraConnectionTitle);
            cobraConnectionTitle.setTextColor(dataColor);
            findViewById(R.id.cobraConnectionTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.cobraConnection).setVisibility(View.VISIBLE);

            showBat = sharedPrefs.getBoolean("showBat", true);
            if (showBat) {
                LinearLayout batterylayout = (LinearLayout) findViewById(R.id.batterylayout);
                batterylayout.setVisibility(View.VISIBLE);
                TextView batteryText = (TextView) findViewById(R.id.batteryText);
                batteryText.setTextColor(dataColor);
                setColorImage("bat", "batteryTitle");
            }
        }
    }

    // GET APP SETTINGS, RUN APPROPRIATE APP TYPE
    public void startActivity() {
        if (D) Log.d(TAG, "startActivity()");
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setupType = Integer.parseInt(sharedPrefs.getString("setupType", "0"));
        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        cha.setActivityRunning(true);
        switch (setupType) {
            case 1:
                if (D) Log.d(TAG, "Setup type is standalone");
                runStandAlone();
                break;
            case 2: //Receiver
                if (D) Log.d(TAG, "Setup type is receiver");
                runReceiver();
                break;
            case 3: //Sender
                if (D) Log.d(TAG, "Setup type is sender");
                runSender();
                break;
            default: //Undefined
                if (D) Log.d(TAG, "Setup type is undefined");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.not_configured));
        }
    }

    //SIZE TEXTVIEW
    public void sizeTextView(TextView tv, String measureText) {
        //SET FONT TO MATCH WIDTH
        TextPaint textPaint = tv.getPaint();
        float width = textPaint.measureText(measureText);
        while (tv.getMeasuredWidth() - width > 10) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (tv.getTextSize() + 1));
            width = textPaint.measureText(measureText);
        }
        //REDUCE TOP/BOTTOM PADDING TO ACCOUNT FOR FONT PADDING
        int divFactor = (int) Math.round((textPaint.getTextSize() / this.getResources().getDisplayMetrics().density) * .20);
        int additionalPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, divFactor, this.getResources().getDisplayMetrics());
        tv.setPadding(0, 0 - additionalPadding, 0, 0 - additionalPadding - Math.round(additionalPadding * 0.113f));
    }

    //SIZE DISPLAY
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            //ONLY RUN FOR STANDALONE AND RECEIVER
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            setupType = Integer.parseInt(sharedPrefs.getString("setupType", "0"));
            if (setupType != 1 && setupType != 2)
                return;

            //SPEEDOMETER
            TextView speedText = (TextView) findViewById(R.id.speedText);
            sizeTextView(speedText, "00");

            //GET VALUES BASED OFF SPEEDOMETER HEIGHT
            speedText.measure(0, 0);
            int padding = Math.round(speedText.getMeasuredHeight() * 0.085f);
            int mediaFont = Math.round((speedText.getMeasuredHeight() * 0.16f) / this.getResources().getDisplayMetrics().density);
            int smallerFont = Math.round(mediaFont * 0.65f);
            int rpmBarHeight = Math.round((speedText.getMeasuredHeight() * 0.50f) / this.getResources().getDisplayMetrics().density);

            //RPM VIEWS
            LinearLayout rpmtextlabellayout = (LinearLayout) findViewById(R.id.rpmtextlabellayout);
            rpmtextlabellayout.setPadding(0, padding, 0, padding);
            //RPM TEXT
            TextView rpmText = (TextView) findViewById(R.id.rpmText);
            //sizeTextView(rpmText, "7000");
            //RPM LABEL
            TextView rpmTextLabel = (TextView) findViewById(R.id.rpmTextLabel);
            //rpmTextLabel.setTextSize(mediaFont);

            //RPM BAR
            LinearLayout rpmgaugelayout = (LinearLayout) findViewById(R.id.rpmgaugelayout);
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            showRPM = sharedPrefs.getBoolean("showRPM", true);
            //SPECIAL CASE FOR NO RPM TEXT
            if (!showRPM) {
                int rpmPadding = Math.round(speedText.getMeasuredHeight() * 0.05f);
                rpmBarHeight = Math.round((speedText.getMeasuredHeight() * 0.80f) / this.getResources().getDisplayMetrics().density);
                rpmgaugelayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rpmBarHeight));
                rpmgaugelayout.setPadding(0, rpmPadding, 0, rpmPadding);
            } else
                rpmgaugelayout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rpmBarHeight));

            //ARTIST TEXT
            TextView artistText = (TextView) findViewById(R.id.artistText);
            //artistText.setTextSize(mediaFont);
            //ALBUM TEXT
            TextView albumText = (TextView) findViewById(R.id.albumText);
            //albumText.setTextSize(mediaFont);
            //TRACK TEXT
            TextView trackText = (TextView) findViewById(R.id.trackText);
            //trackText.setTextSize(mediaFont);
            //GET VALUES BASED OFF ARTIST HEIGHT
            artistText.measure(0, 0);
            int iconHeight = Math.round(artistText.getMeasuredHeight());
            int padding2 = Math.round(artistText.getMeasuredHeight() * 0.18f);
            //ARTIST ICON
            ImageView artistTitle = (ImageView) findViewById(R.id.artistTitle);
            artistTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            artistTitle.setPadding(padding2, padding2, padding2, padding2);
            //ALBUM ICON
            ImageView albumTitle = (ImageView) findViewById(R.id.albumTitle);
            albumTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            albumTitle.setPadding(padding2, padding2, padding2, padding2);
            //ARTIST ICON
            ImageView trackTitle = (ImageView) findViewById(R.id.trackTitle);
            trackTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            trackTitle.setPadding(padding2, padding2, padding2, padding2);

            //NAVIGATION TEXT
            TextView navigationText = (TextView) findViewById(R.id.navigationText);
            //navigationText.setTextSize(mediaFont);
            //NAVIGATION ICON
            ImageView navigationIcon = (ImageView) findViewById(R.id.navigationIcon);

            //ESTIMATED ARRIVAL
            TextView estArrival = (TextView) findViewById(R.id.estArrival);
            //estArrival.setTextSize(smallerFont);
            //EXTIMATED DISTANCE
            TextView estDistance = (TextView) findViewById(R.id.estDistance);
            //estDistance.setTextSize(smallerFont);

            //ALTITIDE TEXT
            TextView altitudeText = (TextView) findViewById(R.id.altitudeText);
            //altitudeText.setTextSize(mediaFont);
            //TEMP TEXT
            TextView localtempText = (TextView) findViewById(R.id.localtempText);
            //localtempText.setTextSize(mediaFont);
            //TIME TEXT
            TextView timeText = (TextView) findViewById(R.id.timeText);
            //timeText.setTextSize(mediaFont);
            //ALTITUDE ICON
            ImageView altitudeTitle = (ImageView) findViewById(R.id.altitudeTitle);
            altitudeTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            altitudeTitle.setPadding(padding2, padding2, padding2, padding2);
            //TEMP ICON
            ImageView localtempTitle = (ImageView) findViewById(R.id.localtempTitle);
            localtempTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            localtempTitle.setPadding(padding2, padding2, padding2, padding2);
            //TIME ICON
            ImageView timeTitle = (ImageView) findViewById(R.id.timeTitle);
            timeTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            timeTitle.setPadding(padding2, padding2, padding2, padding2);

            //BATTERY TEXT
            TextView batteryText = (TextView) findViewById(R.id.batteryText);
            batteryText.setTextSize(mediaFont);
            //COOLANT TEXT
            TextView coolantText = (TextView) findViewById(R.id.coolantText);
            coolantText.setTextSize(mediaFont);
            //BATTERY ICON
            ImageView batteryTitle = (ImageView) findViewById(R.id.batteryTitle);
            batteryTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            batteryTitle.setPadding(padding2, padding2, padding2, padding2);
            //COOLANT ICON
            ImageView coolantTitle = (ImageView) findViewById(R.id.coolantTitle);
            coolantTitle.setLayoutParams(new TableRow.LayoutParams(iconHeight, iconHeight));
            coolantTitle.setPadding(padding2, padding2, padding2, padding2);
            super.onWindowFocusChanged(hasFocus);
        }
    }

    // STANDALONE TYPE
    public void runStandAlone() {
        if (D) Log.d(TAG, "Running as standalone");

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        //CHECK REQUIREMENTS
        speedType = sharedPrefs.getString("speedType", "gps");
        if (speedType.equals("obd")) {
            obdBtaddress = sharedPrefs.getString("obdBtaddress", "");
            if (obdBtaddress.isEmpty()) {
                if (D) Log.d(TAG, "No obd address");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.not_configured));
                return;
            }
        }
        useCobra = sharedPrefs.getBoolean("useCobra", false);
        if (useCobra) {
            cobraBtaddress = sharedPrefs.getString("cobraBtaddress", "");
            if (cobraBtaddress.isEmpty()) {
                if (D) Log.d(TAG, "No cobra address");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.not_configured));
                return;
            }
        }
        if (speedType.equals("obd") || useCobra) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                if (D) Log.d(TAG, "No bluetooth device");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.bt_not_available));
                return;
            }
        }

        fullBright = sharedPrefs.getBoolean("fullBright", false);
        if (fullBright) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 1.0f;
        }
        screenOn = sharedPrefs.getBoolean("screenOn", false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fullscreen = sharedPrefs.getBoolean("fullscreen", true);
        if (fullscreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        //SEE IF SCREEN NEEDS MIRRORED AND DRAW SCREEN
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mirror = sharedPrefs.getBoolean("mirror", false);
        if (mirror) {
            setContentView(R.layout.hud_display_mirror);
        } else {
            setContentView(R.layout.hud_display);
        }

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;

        initDisplay();

        speedType = sharedPrefs.getString("speedType", "gps");
        showAltitude = sharedPrefs.getBoolean("showAltitude", false);
        showTime = sharedPrefs.getBoolean("showTime", false);
        showLocalTemp = sharedPrefs.getBoolean("showLocalTemp", false);
        //GET SPEED DATA
        if (speedType.equals("gps") || showAltitude || showTime || showLocalTemp)
            getGPSdata();
        if (speedType.equals("obd")) {
            obdNeeded = true;
            startBluetoothOBD2();
        }

        //REGISTER MEDIA METADATA RECEIVER
        if (mediaReceiver == null) {
            mediaReceiver = new MediaReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction("com.android.music.metachanged");
            filter.addAction("com.android.music.playstatechanged");
            filter.addAction("com.android.music.playbackcomplete");
            filter.addAction("com.android.music.queuechanged");
            filter.addAction("android.provider.Telephony.SMS_RECEIVED");
            filter.addAction(PowerampAPI.ACTION_TRACK_CHANGED);
            registerReceiver(mediaReceiver, filter);
        }

        //START COBRA IRADAR
        useCobra = sharedPrefs.getBoolean("useCobra", false);
        if (useCobra) {
            cobraNeeded = true;
            startCobraIradar();
        }
    }

    // RECEIVER TYPE
    public void runReceiver() {
        if (D) Log.d(TAG, "Running as receiver");

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        //CHECK REQUIREMENTS
        speedType = sharedPrefs.getString("speedType", "gps");
        if (speedType.equals("obd")) {
            obdBtaddress = sharedPrefs.getString("obdBtaddress", "");
            if (obdBtaddress.isEmpty()) {
                if (D) Log.d(TAG, "No obd address");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.not_configured));
                return;
            }
        }
        useCobra = sharedPrefs.getBoolean("useCobra", false);
        if (useCobra) {
            cobraBtaddress = sharedPrefs.getString("cobraBtaddress", "");
            if (cobraBtaddress.isEmpty()) {
                if (D) Log.d(TAG, "No cobra address");
                setContentView(R.layout.hud);

                //SHOW AD
				/*adView = new AdView(this);
			    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
			    adView.setAdSize(AdSize.BANNER);
			    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
			    layout.addView(adView);
			    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
			    adView.loadAd(adRequest);*/

                TextView mainText = (TextView) findViewById(R.id.mainText);
                mainText.setText(getString(R.string.not_configured));
                return;
            }
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            if (D) Log.d(TAG, "No bluetooth device");
            setContentView(R.layout.hud);

            //SHOW AD
			/*adView = new AdView(this);
		    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
		    adView.setAdSize(AdSize.BANNER);
		    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
		    layout.addView(adView);
		    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
		    adView.loadAd(adRequest);*/

            TextView mainText = (TextView) findViewById(R.id.mainText);
            mainText.setText(getString(R.string.bt_not_available));
            return;
        }

        fullBright = sharedPrefs.getBoolean("fullBright", false);
        if (fullBright) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 1.0f;
        }
        screenOn = sharedPrefs.getBoolean("screenOn", false);
        if (screenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        fullscreen = sharedPrefs.getBoolean("fullscreen", true);
        if (fullscreen) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        senderNeeded = true;
        BluetoothAdapter.getDefaultAdapter().enable();
        //SEE IF SCREEN NEEDS MIRRORED AND DRAW SCREEN
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mirror = sharedPrefs.getBoolean("mirror", false);
        if (mirror) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            setContentView(R.layout.hud_display_mirror);
        }
        else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setContentView(R.layout.hud_display);
        }

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;

        initDisplay();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, RESULT_ENABLE_BLUETOOTH);
        } else {
            TextView senderConnectionTitle = (TextView) findViewById(R.id.senderConnectionTitle);
            senderConnectionTitle.setTextColor(dataColor);
            findViewById(R.id.senderConnectionTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.senderConnection).setVisibility(View.VISIBLE);
            if (mChatService == null)
                mChatService = new BluetoothChatServiceReceiver(this, mHandler);
            mChatService.start();
        }

        speedType = sharedPrefs.getString("speedType", "gps");
        showAltitude = sharedPrefs.getBoolean("showAltitude", false);
        showTime = sharedPrefs.getBoolean("showTime", false);
        showLocalTemp = sharedPrefs.getBoolean("showLocalTemp", false);
        //GET SPEED DATA
        if ((speedType.equals("gps") || showAltitude || showTime || showLocalTemp) && (!speedType.equals("gpssender")))
            getGPSdata();
        if (speedType.equals("gpssender") && showLocalTemp)
            localTempThread.start();
        if (speedType.equals("obd")) {
            obdNeeded = true;
            startBluetoothOBD2();
        }
        //START COBRA IRADAR
        useCobra = sharedPrefs.getBoolean("useCobra", false);
        if (useCobra) {
            cobraNeeded = true;
            startCobraIradar();
        }
    }

    // SENDER TYPE
    public void runSender() {
        if (D) Log.d(TAG, "Running as sender");

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean skipNotificationWarning = sharedPrefs.getBoolean("skipNotificationWarning", false);
        if (!skipNotificationWarning) {
            final CharSequence[] items = {"Don't show this again"};
            AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
            dlgAlert.setTitle("Be sure notification access is set under security!");
            dlgAlert.setMultiChoiceItems(items, null, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                    if (isChecked) {
                        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean("skipNotificationWarning", true);
                        editor.commit();
                    } else {
                        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean("skipNotificationWarning", false);
                        editor.commit();
                    }
                }
            });
            dlgAlert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            dlgAlert.setCancelable(true);
            dlgAlert.create().show();
        }

        BluetoothAdapter.getDefaultAdapter().enable();
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        btaddress = sharedPrefs.getString("btaddress", "");
        if (btaddress.isEmpty()) {
            setContentView(R.layout.hud);

            //SHOW AD
			/*adView = new AdView(this);
		    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
		    adView.setAdSize(AdSize.BANNER);
		    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
		    layout.addView(adView);
		    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
		    adView.loadAd(adRequest);*/

            TextView mainText = (TextView) findViewById(R.id.mainText);
            mainText.setText(getString(R.string.not_configured));
            return;
        }
        setContentView(R.layout.sender);

        //SHOW AD
		/*adView = new AdView(this);
	    adView.setAdUnitId("ca-app-pub-2765181867665979/8238067247");
	    adView.setAdSize(AdSize.BANNER);
	    LinearLayout layout = (LinearLayout)findViewById(R.id.adLayout);
	    layout.addView(adView);
	    AdRequest adRequest = new AdRequest.Builder().addTestDevice("224F611EDD5189F758EA83EF1E855F1C").build();
	    adView.loadAd(adRequest);*/

        TextView senderText = (TextView) findViewById(R.id.senderText);
        TextView errorText = (TextView) findViewById(R.id.errorText);

        stopButton = (Button) findViewById(R.id.stop_button);
        startButton = (Button) findViewById(R.id.start_button);

        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        serviceStarted = cha.getServiceRunning();
        if (serviceStarted == true) {
            startButton.setBackgroundColor(Color.parseColor("#706168"));
            startButton.setClickable(false);
            stopButton.setBackgroundColor(Color.parseColor("#A6180A"));
            stopButton.setClickable(true);
            CarHudSenderService.setMainActivity(this);
            senderText.setText(cha.getlastMessage());
            errorText.setText(cha.getlastError());
        } else {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter.isEnabled()) {
                startService();
                senderText.setText(getString(R.string.service_starting));
                cha.setLastMessage(getString(R.string.service_starting));
            } else {
                startButton.setBackgroundColor(Color.parseColor("#00F700"));
                startButton.setClickable(true);
                stopButton.setBackgroundColor(Color.parseColor("#706168"));
                stopButton.setClickable(false);
                errorText.setText(getString(R.string.bt_not_available));
                cha.setLastError(getString(R.string.bt_not_available));
            }

        }

        //SERVICE BUTTONS
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CarHudApplication cha = ((CarHudApplication) getApplicationContext());
                cha.setServiceRunning(false);
                stopService(new Intent(cha, CarHudSenderService.class));
                startButton.setBackgroundColor(Color.parseColor("#00F700"));
                startButton.setClickable(true);
                stopButton.setBackgroundColor(Color.parseColor("#706168"));
                stopButton.setClickable(false);
                TextView senderText = (TextView) findViewById(R.id.senderText);
                senderText.setText(getString(R.string.service_stopped));
                cha.setLastMessage(getString(R.string.service_stopped));
            }
        });
        final Button startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter.isEnabled()) {
                    startService();
                    CarHudApplication cha = ((CarHudApplication) getApplicationContext());
                    TextView senderText = (TextView) findViewById(R.id.senderText);
                    senderText.setText(getString(R.string.service_starting));
                    cha.setLastMessage(getString(R.string.service_starting));
                } else {
                    CarHudApplication cha = ((CarHudApplication) getApplicationContext());
                    TextView errorText = (TextView) findViewById(R.id.errorText);
                    errorText.setText(getString(R.string.bt_not_available));
                    cha.setLastError(getString(R.string.bt_not_available));
                }
            }
        });
    }

    //START SERVICE
    public void startService() {
        stopButton = (Button) findViewById(R.id.stop_button);
        startButton = (Button) findViewById(R.id.start_button);

        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        TextView errorText = (TextView) findViewById(R.id.errorText);
        errorText.setText("");
        cha.setLastError("");

        cha.setServiceRunning(true);
        startService(new Intent(cha, CarHudSenderService.class));
        CarHudSenderService.setMainActivity(this);

        startButton.setBackgroundColor(Color.parseColor("#706168"));
        startButton.setClickable(false);
        stopButton.setBackgroundColor(Color.parseColor("#A6180A"));
        stopButton.setClickable(true);
    }

    //STOP SERVICE NO GPS
    public void stopServiceGPS() {
        stopButton = (Button) findViewById(R.id.stop_button);
        startButton = (Button) findViewById(R.id.start_button);

        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        cha.setServiceRunning(false);
        stopService(new Intent(cha, CarHudSenderService.class));

        TextView errorText = (TextView) findViewById(R.id.errorText);
        errorText.setText(R.string.gps_not_available);
        cha.setLastError(getString(R.string.gps_not_available));
        startButton.setBackgroundColor(Color.parseColor("#00F700"));
        startButton.setClickable(true);
        stopButton.setBackgroundColor(Color.parseColor("#706168"));
        stopButton.setClickable(false);
    }

    //STOP SERVICE NO MOCK GPS
    public void stopServiceMockGPS() {
        stopButton = (Button) findViewById(R.id.stop_button);
        startButton = (Button) findViewById(R.id.start_button);

        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        cha.setServiceRunning(false);
        stopService(new Intent(cha, CarHudSenderService.class));

        TextView errorText = (TextView) findViewById(R.id.errorText);
        errorText.setText(R.string.mockgps_not_available);
        cha.setLastError(getString(R.string.mockgps_not_available));
        startButton.setBackgroundColor(Color.parseColor("#00F700"));
        startButton.setClickable(true);
        stopButton.setBackgroundColor(Color.parseColor("#706168"));
        stopButton.setClickable(false);
    }


    public static class mHandler extends Handler {

        private final Hud mActivity;

        public mHandler(Hud activity) {
            mActivity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothChatServiceReceiver.STATE_CONNECTED:
                            mActivity.senderConnected = true;
                            mActivity.setColorImage("connected", "senderConnection");
                            mActivity.checkConnectionsAndHide();
                            break;
                        case BluetoothChatServiceReceiver.STATE_CONNECTING:
                            mActivity.senderConnected = false;
                            mActivity.setColorImage("connecting", "senderConnection");
                            mActivity.checkConnectionsAndHide();
                            break;
                        case BluetoothChatServiceReceiver.STATE_LISTEN:
                        case BluetoothChatServiceReceiver.STATE_NONE:
                            mActivity.senderConnected = false;
                            mActivity.setColorImage("notconnected", "senderConnection");
                            mActivity.checkConnectionsAndHide();
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                    if (D) Log.d(TAG, "message_read()");
                    Log.v("bytesafter", Arrays.toString((byte[]) msg.obj));
                    Log.v("bytesafterb", new String((byte[]) msg.obj));
                    Log.v("bytesafterb", String.valueOf(((byte[]) msg.obj).length));

                    byte[] readBuf = (byte[]) msg.obj;
                    String str = new String(readBuf, 0, msg.arg1);
                    StringTokenizer st = new StringTokenizer(str, "~");
                    String artist = "", album = "", track = "", type = "", ntype = "", ss = "", error = "";

                    if (st.hasMoreElements())
                        type = st.nextElement().toString();
                    if (type.equals("NAVIGATION")) {
                        if (st.hasMoreElements())
                            ntype = st.nextElement().toString();
                        //SHOW NAVIGATION AREA
                        if (ntype.equals("POSTED")) {
                            if (st.hasMoreElements()) {
                                ss = st.nextElement().toString();
                                if (!ss.isEmpty()) {
                                    String[] sarr = ss.split("\n");
                                    String nextAction = sarr[0];
                                    if (sarr.length >= 3) {
                                        String est_arrival = sarr[1];
                                        String est_dist = sarr[2];

                                        mActivity.setArrival(est_arrival);
                                        mActivity.setDistance(est_dist);
                                    }
                                    if (!nextAction.isEmpty())
                                        mActivity.showNavigation(nextAction);
                                    if (st.hasMoreElements()) {
                                        ss = st.nextElement().toString();
                                        if (!ss.isEmpty()) {
                                            Log.v("Stringafter4", ss);
                                            NavIcon = StringToBitMap(ss);
                                            mActivity.showNavigationImage(NavIcon);
                                        }
                                    }
                                }
                            }
                        }
                        //HIDE NAVIGATION AREA
                        else if (ntype.equals("CLEARED")) {
                            mActivity.hideNavigation();
                        }
                    }
                    if (type.equals("MEDIA")) {
                        if (st.hasMoreElements())
                            artist = st.nextElement().toString();
                        if (st.hasMoreElements())
                            album = st.nextElement().toString();
                        if (st.hasMoreElements())
                            track = st.nextElement().toString();
                        mActivity.displayMedia(artist, album, track);
                    }
                    if (type.equals("GPS")) {
                        //SPEED
                        double currentSpeed = 0;
                        if (st.hasMoreElements())
                            currentSpeed = Double.parseDouble(st.nextElement().toString());
                        //ALTITUDE
                        double currentAltitude = 0;
                        if (st.hasMoreElements())
                            currentAltitude = Double.parseDouble(st.nextElement().toString());
                        //TIME
                        String localTime = "";
                        if (st.hasMoreElements())
                            localTime = st.nextElement().toString();
                        //LATLON
                        if (st.hasMoreElements())
                            mActivity.lat = Double.parseDouble(st.nextElement().toString());
                        if (st.hasMoreElements())
                            mActivity.lon = Double.parseDouble(st.nextElement().toString());

                        //SPEED
                        mActivity.speedType = mActivity.sharedPrefs.getString("speedType", "gps");
                        if (mActivity.speedType.equals("gpssender")) {
                            mActivity.useMetric = mActivity.sharedPrefs.getBoolean("useMetric", false);
                            if (!mActivity.useMetric)
                                currentSpeed = currentSpeed * 2.2369;
                            String tmp = String.format(Locale.US, "%.0f", currentSpeed);
                            mActivity.setSpeed(tmp);
                        }

                        //ALTITUDE
                        mActivity.showAltitude = mActivity.sharedPrefs.getBoolean("showAltitude", false);
                        if (mActivity.showAltitude) {
                            mActivity.useMetric = mActivity.sharedPrefs.getBoolean("useMetric", false);
                            if (!mActivity.useMetric)
                                currentAltitude = currentAltitude * 3.2808399;
                            String tmp = String.format(Locale.US, "%.0f", currentAltitude);
                            mActivity.setAltitude(tmp);
                        }

                        //TIME
                        mActivity.showTime = mActivity.sharedPrefs.getBoolean("showTime", false);
                        if (mActivity.showTime)
                            mActivity.setTime(localTime);
                    }
                    if (type.equals("SMS")) {
                        String title = "", message = "";
                        if (st.hasMoreElements())
                            title = st.nextElement().toString();
                        if (st.hasMoreElements())
                            message = st.nextElement().toString();
                        messagePopup mp = new messagePopup();
                        mp.setData(title, message, 8, false);
                        mActivity.showPopupTimedQueue(mp);
                    }
                    if (type.equals("CALL")) {
                        String title = "", message = "";
                        if (st.hasMoreElements())
                            title = st.nextElement().toString();
                        if (st.hasMoreElements())
                            message = st.nextElement().toString();
                        messagePopup mp = new messagePopup();
                        mp.setData(title, message, 8, true);
                        mActivity.showPopupTimedQueue(mp);
                    }
                    break;
                case MESSAGE_DEVICE_NAME:
                    //mActivity.makeToast(mActivity.getString(R.string.connected_to) + msg.getData().getString(DEVICE_NAME));
                    break;
                case MESSAGE_CONNECTION_LOST:
                    //mActivity.makeToast("CONNECTION LOST");
                    if (mActivity.activityStarted)
                        mActivity.mChatService.start();
                    break;
            }
        }
    }

    /**
     * @param encodedString
     * @return bitmap (from given string)
     */
    public static Bitmap StringToBitMap(String encodedString){
        try {
            byte [] encodeByte= Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            Log.v("StringtoBMP", String.valueOf(bitmap==null));
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }

    // To animate view slide out from top to bottom
    public void slideToBottom(View view) {
        /*statusHeight = view.getHeight();
        TranslateAnimation animate = new TranslateAnimation(0, 0, 0, statusHeight);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.GONE);*/
    }

    // To animate view slide out from bottom to top
    public void slideToTop(View view) {
        /*TranslateAnimation animate = new TranslateAnimation(0, 0, statusHeight, 0);
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.VISIBLE);*/
    }

    //SEE IF CONNECTIONS ARE MADE AND HIDE STATUS BAR IF ALL IS GOOD
    public void checkConnectionsAndHide() {
        Boolean allConnected = true;
        if (senderNeeded && !senderConnected)
            allConnected = false;
        if (cobraNeeded && !cobraConnected)
            allConnected = false;
        if (obdNeeded && !obdConnected)
            allConnected = false;
        if (allConnected) {
            if (!statusHiding) {
                statusHiding = true;
                statusViewHandler.postDelayed(hideStatus, 5000);
            }
        } else {
            if (statusHiding) {
                statusViewHandler.removeCallbacks(hideStatus);
                statusHiding = false;
            }
            if (!statusShown) {
                slideToTop(findViewById(R.id.statusBar));
                statusShown = true;
            }
        }
    }

    //HIDE STATUS BAR RUNNABLE
    Runnable hideStatus = new Runnable() {
        @Override
        public void run() {
            slideToBottom(findViewById(R.id.statusBar));
            statusShown = false;
            statusHiding = false;
        }
    };

    //SET ARRIVAL TIME
    public void setArrival(String arrival) {
        TextView estArrival = (TextView) findViewById(R.id.estArrival);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
        estArrival.setTextColor(dataColor);
        estArrival.setText(arrival);
    }

    //SET DISTANCE
    public void setDistance(String distance) {
        TextView estDistance = (TextView) findViewById(R.id.estDistance);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
        estDistance.setTextColor(dataColor);
        estDistance.setText(distance);
    }

    //HIDE MEDIA RUNNABLE
    Runnable hideMedia = new Runnable() {
        @Override
        public void run() {
            //SHOW BAT AND TEMP AGAIN IF NEEDED
            sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean uc = sharedPrefs.getBoolean("useCobra", false);
            if (uc) {
                findViewById(R.id.batteryTitle).setVisibility(View.VISIBLE);
                findViewById(R.id.batteryText).setVisibility(View.VISIBLE);
            }
            String st = sharedPrefs.getString("speedType", "gps");
            if (st.equals("obd")) {
                findViewById(R.id.coolantTitle).setVisibility(View.VISIBLE);
                findViewById(R.id.coolantText).setVisibility(View.VISIBLE);
            }
            slideToBottom(findViewById(R.id.mediaotherlayout));
            slideToTop(findViewById(R.id.navigationlayout));
            tempMediaShown = false;
        }
    };

    //QUICK SHOW MEDIA WHEN NAVIGATION IS SHOWN AND NOT IN A CLOSE ALERT
    public void quickShowMedia() {
        if (navigationShown && !navigationCloseAlert) {
            mediaViewHandler.removeCallbacks(hideMedia);
            if (!tempMediaShown) {
                tempMediaShown = true;

                //ONLY SHOW MEDIA, NOT BATTERY OR TEMP
                findViewById(R.id.batteryTitle).setVisibility(View.GONE);
                findViewById(R.id.batteryText).setVisibility(View.GONE);
                findViewById(R.id.coolantTitle).setVisibility(View.GONE);
                findViewById(R.id.coolantText).setVisibility(View.GONE);

                slideToBottom(findViewById(R.id.navigationlayout));
                slideToTop(findViewById(R.id.mediaotherlayout));
            }
            mediaViewHandler.postDelayed(hideMedia, 3000);
        }
    }

    //IF NAVIGATION CLOSE ALERT AND TEMP MEDIA IS SHOWN, QUICKLY GO BACK TO NAVIGATION
    public void immediateSwitchNavigation() {
        mediaViewHandler.removeCallbacks(hideMedia);
        //SHOW BAT AND TEMP AGAIN IF NEEDED
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean uc = sharedPrefs.getBoolean("useCobra", false);
        if (uc) {
            findViewById(R.id.batteryTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.batteryText).setVisibility(View.VISIBLE);
        }
        String st = sharedPrefs.getString("speedType", "gps");
        if (st.equals("obd")) {
            findViewById(R.id.coolantTitle).setVisibility(View.VISIBLE);
            findViewById(R.id.coolantText).setVisibility(View.VISIBLE);
        }
        slideToBottom(findViewById(R.id.mediaotherlayout));
        slideToTop(findViewById(R.id.navigationlayout));
        tempMediaShown = false;
    }

    //SHOW NAVIGATION
    public void showNavigationImage(Bitmap image) {

        ImageView iv = (ImageView) findViewById(R.id.navigationIcon);
        //iv.setVisibility(View.GONE);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
//		navigationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        //Log.v("bmp", String.valueOf(bmp.getByteCount()));
        iv.setImageBitmap(image);
        iv.setBackgroundColor(0xFF000000);
        iv.setColorFilter(dataColor);
        iv.setVisibility(View.VISIBLE);
//			navigationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        if (navigationCloseAlert) {
            iv.setColorFilter(0xFF000000);
            iv.setBackgroundColor(dataColor);
        }


        if (!navigationShown)
            slideToTop(findViewById(R.id.navigationlayout));
        navigationShown = true;
    }

    //SHOW NAVIGATION
    public void showNavigation(String ss) {
        if (!navigationShown)
            slideToBottom(findViewById(R.id.mediaotherlayout));

        ImageView iv = (ImageView) findViewById(R.id.navigationIcon);
        //iv.setVisibility(View.GONE);

        TextView navigationText = (TextView) findViewById(R.id.navigationText);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
//		navigationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);

        navigationText.setTextColor(dataColor);
        navigationText.setText(ss);

        navigationCloseAlert = false;
        if (ss.startsWith("0.1mi") || ss.startsWith("500ft") || ss.startsWith("450ft") || ss.startsWith("400ft") || ss.startsWith("350ft") || ss.startsWith("300ft") || ss.startsWith("250ft") || ss.startsWith("200ft") || ss.startsWith("150ft") || ss.startsWith("100ft") || ss.startsWith("50ft") || ss.startsWith("0ft")) {
            navigationCloseAlert = true;
            if (tempMediaShown)
                immediateSwitchNavigation();
        }

		/*int imageId = -1;

		if (ss.contains("Image"))
			imageId = R.drawable.depart;
		else if (ss.contains("Turn left"))
		{
			if (ss.contains("ramp"))
				imageId = R.drawable.ramp_left;
			else
				imageId = R.drawable.left;
		}
		else if (ss.contains("Turn right"))
		{
			if (ss.contains("ramp"))
				imageId = R.drawable.ramp_right;
			else
				imageId = R.drawable.right;
		}
		else if (ss.contains("Continue onto") || ss.contains("Pass by"))
			imageId = R.drawable.straight;
		else if (ss.contains("At the traffic circle") || ss.contains("At the roundabout"))
		{
			if (ss.contains("take the 1st") || ss.contains("take the first"))
				imageId = R.drawable.roundabout_1;
			else if (ss.contains("take the 2nd") || ss.contains("take the second"))
				imageId = R.drawable.roundabout_2;
			else if (ss.contains("take the 3rd") || ss.contains("take the third"))
				imageId = R.drawable.roundabout_3;
			else if (ss.contains("take the 4th") || ss.contains("take the fourth") || ss.contains("continue straight"))
				imageId = R.drawable.roundabout_4;
			else if (ss.contains("take the 5th") || ss.contains("take the fifth"))
				imageId = R.drawable.roundabout_5;
			else if (ss.contains("take the 6th") || ss.contains("take the sixth"))
				imageId = R.drawable.roundabout_6;
			else if (ss.contains("take the 7th") || ss.contains("take the seventh"))
				imageId = R.drawable.roundabout_7;
			else
				imageId = R.drawable.roundabout;
		}
		else if (ss.contains("Exit the traffic circle") || ss.contains("Exit the roundabout"))
			imageId = R.drawable.roundabout_exit;
		else if (ss.contains("traffic circle") || ss.contains("roundabout"))
			imageId = R.drawable.roundabout;
		else if (ss.contains("Merge"))
			imageId = R.drawable.merge;
		else if (ss.contains("Keep left"))
			imageId = R.drawable.fork_left;
		else if (ss.contains("Keep right"))
			imageId = R.drawable.fork_right;
		else if (ss.contains("Exit onto") || ss.contains("Take exit") || ( ss.contains("Take the") && (ss.contains("exit") || ss.contains("ramp"))))
		{
			if (ss.contains("left"))
				imageId = R.drawable.ramp_left;
			else
				imageId = R.drawable.ramp_right;
		}
		else if (ss.contains("Slight left"))
			imageId = R.drawable.slight_left;
		else if (ss.contains("Slight right"))
			imageId = R.drawable.slight_right;
		else if (ss.contains("Sharp left"))
			imageId = R.drawable.sharp_left;
		else if (ss.contains("Sharp right"))
			imageId = R.drawable.sharp_right;
		else if (ss.contains("U-turn"))
			imageId = R.drawable.uturn;
		else if (!ss.contains("Rerouting") && !ss.contains("Re-routing"))
			imageId = R.drawable.arrive;


            //Log.v("bmp", String.valueOf(bmp.getByteCount()));
            iv.setImageResource(imageId);
            iv.setBackgroundColor(0xFF000000);
            iv.setColorFilter(dataColor);
            iv.setVisibility(View.VISIBLE);*/
//			navigationText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            if (navigationCloseAlert) {
                iv.setColorFilter(0xFF000000);
                iv.setBackgroundColor(dataColor);
            }


        if (!navigationShown)
            slideToTop(findViewById(R.id.navigationlayout));
        navigationShown = true;
    }

    public void storeImageBytes(byte[] image){
        Log.v("ImageSize", String.valueOf(image.length));
        Bitmap bmp = null;
        if (image != null) {
            bmp = BitmapFactory.decodeByteArray(image, 0, image.length);
            Log.v("bmp", String.valueOf(bmp.getByteCount()));
            NavIcon = bmp;
        }
    }

    //HIDE NAVIGATION
    public void hideNavigation() {
        /*if (navigationShown) {
            slideToBottom(findViewById(R.id.navigationlayout));
            slideToTop(findViewById(R.id.mediaotherlayout));
        }
        navigationShown = false;*/
    }

    //SET SENDER TEXT
    public void setSenderText(String msg) {
        TextView senderText = (TextView) findViewById(R.id.senderText);
        if (senderText != null) {
            try {
                senderText.setText(msg);
                CarHudApplication cha = ((CarHudApplication) getApplicationContext());
                cha.setLastMessage(msg);
            } catch (Exception e) {
                if (D) Log.w(TAG, "Exception writing to senderText, msg was: " + msg);
            }
        }
    }

    // TOAST DISPLAY
    public void makeToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    //DISPLAY MEDIA INFO
    public void displayMedia(String artist, String album, String track) {
        if (D) Log.w(TAG, "displayMedia " + artist + album + track);
        CarHudApplication cha = ((CarHudApplication) getApplicationContext());
        String lastArtistAlbumTrack = cha.getlastArtistAlbumTrack();
        if (lastArtistAlbumTrack == null)
            lastArtistAlbumTrack = "";
        String newArtistAlbumTrack = artist + album + track;

        if (D) Log.w(TAG, "last = " + lastArtistAlbumTrack + ", new = " + newArtistAlbumTrack);
        //DONT UPDATE THE UI IF THE DATA HASN'T CHANGED
        if (!lastArtistAlbumTrack.equals(newArtistAlbumTrack)) {
            cha.setLastArtistAlbumTrack(newArtistAlbumTrack);
            TextView artistTextView = (TextView) findViewById(R.id.artistText);
            TextView albumTextView = (TextView) findViewById(R.id.albumText);
            TextView trackTextView = (TextView) findViewById(R.id.trackText);

            artistTextView.clearAnimation();
            albumTextView.clearAnimation();
            trackTextView.clearAnimation();

            artistTextView.setText(artist);
            albumTextView.setText(album);
            trackTextView.setText(track);


            int[] location = new int[2];
            artistTextView.getLocationOnScreen(location);
            artistStart = location[0];
            albumTextView.getLocationOnScreen(location);
            albumStart = location[0];
            trackTextView.getLocationOnScreen(location);
            trackStart = location[0];

            artistTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            artistWidth = artistTextView.getMeasuredWidth();
            albumTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            albumWidth = albumTextView.getMeasuredWidth();
            trackTextView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            trackWidth = trackTextView.getMeasuredWidth();

            if (artistWidth + artistStart > screenWidth) {
                animationOn = 1;
                animateTextView(artistWidth, artistStart, artistTextView);
            } else if (albumWidth + albumStart > screenWidth) {
                animationOn = 2;
                animateTextView(albumWidth, albumStart, albumTextView);
            } else if (trackWidth + trackStart > screenWidth) {
                animationOn = 3;
                animateTextView(trackWidth, trackStart, trackTextView);
            }

            if (navigationShown)
                quickShowMedia();
        }
    }

    //CREATE TICKER TEXT FOR TEXTVIEW
    public void animateTextView(float width, float start, TextView tv) {
        int scrollBy = (int) Math.round(screenWidth - start - width - 50);
        int time = Math.abs(scrollBy) * 10;
        Animation mAnimation = new TranslateAnimation(0, scrollBy, 0, 0);
        mAnimation.setDuration(time);
        mAnimation.setStartOffset(1000);
        mAnimation.setAnimationListener(al);
        tv.setAnimation(mAnimation);
    }

    //KEEP TRACK OF ANIMATIONS
    AnimationListener al = new AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        // at the end of the animation, start new activity
        @Override
        public void onAnimationEnd(Animation animation) {
            TextView artistTextView = (TextView) findViewById(R.id.artistText);
            TextView albumTextView = (TextView) findViewById(R.id.albumText);
            TextView trackTextView = (TextView) findViewById(R.id.trackText);
            artistTextView.clearAnimation();
            albumTextView.clearAnimation();
            trackTextView.clearAnimation();
            switch (animationOn) {
                case 1:
                    if (albumWidth + albumStart > screenWidth) {
                        animationOn = 2;
                        animateTextView(albumWidth, albumStart, albumTextView);
                    } else if (trackWidth + trackStart > screenWidth) {
                        animationOn = 3;
                        animateTextView(trackWidth, trackStart, trackTextView);
                    } else if (artistWidth + artistStart > screenWidth) {
                        animationOn = 1;
                        animateTextView(artistWidth, artistStart, artistTextView);
                    }
                    break;
                case 2:
                    if (trackWidth + trackStart > screenWidth) {
                        animationOn = 3;
                        animateTextView(trackWidth, trackStart, trackTextView);
                    } else if (artistWidth + artistStart > screenWidth) {
                        animationOn = 1;
                        animateTextView(artistWidth, artistStart, artistTextView);
                    } else if (albumWidth + albumStart > screenWidth) {
                        animationOn = 2;
                        animateTextView(albumWidth, albumStart, albumTextView);
                    }
                    break;
                case 3:
                    if (artistWidth + artistStart > screenWidth) {
                        animationOn = 1;
                        animateTextView(artistWidth, artistStart, artistTextView);
                    } else if (albumWidth + albumStart > screenWidth) {
                        animationOn = 2;
                        animateTextView(albumWidth, albumStart, albumTextView);
                    } else if (trackWidth + trackStart > screenWidth) {
                        animationOn = 3;
                        animateTextView(trackWidth, trackStart, trackTextView);
                    }
                    break;
            }
        }
    };

    //SEE IF MOCK LOCATION IS ENABLED
    public static boolean isMockSettingsON(Context context) {
        // returns true if mock location enabled, false if not enabled.
        if (Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).equals("0"))
            return false;
        else
            return true;
    }

    //START GPS LISTENER
    public void getGPSdata() {
        if (D) Log.d(TAG, "getGPSdata()");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mockGPS = sharedPrefs.getBoolean("mockGPS", false);
        if (mockGPS) {
            if (isMockSettingsON(this)) {
                String mocLocationProvider = LocationManager.GPS_PROVIDER;
                locationManager.addTestProvider(mocLocationProvider, false, false, false, false, true, true, true, 0, 5);
                locationManager.setTestProviderEnabled(mocLocationProvider, true);
            } else
                Toast.makeText(this, getString(R.string.mockgps_not_available), Toast.LENGTH_LONG).show();
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            currentSpeed = 0;
            currentAltitude = 0;
            currentTime = "";
            locationListener = new gpsSpeedListener();
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            showLocalTemp = sharedPrefs.getBoolean("showLocalTemp", false);
			if (showLocalTemp)
				localTempThread.start();
		}
		else
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(getString(R.string.gpsoff));
			builder.setMessage(getString(R.string.enablegps));
			builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() 
			{
				@Override
				public void onClick(DialogInterface dialogInterface, int i) 
				{
					// Show location settings when the user acknowledges the alert dialog
					Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivity(intent);
			    }
			});
			Dialog alertDialog = builder.create();
			alertDialog.setCanceledOnTouchOutside(false);
			alertDialog.show();			
		}
	}
	
	private class gpsSpeedListener implements LocationListener 
	{ 
		@Override 
		public void onLocationChanged(Location location) 
		{ 
			if(location!=null) 
			{ 
				if(location.hasSpeed())
				{ 
		    		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		    		speedType = sharedPrefs.getString("speedType","gps");
		    		if (speedType.equals("gps"))
		    		{
			    		useMetric = sharedPrefs.getBoolean("useMetric", false);
			    		if (useMetric)
			    			currentSpeed = (location.getSpeed()*3600)/1000;
			    		else
			    			currentSpeed = location.getSpeed()*2.2369;
						String str = String.format(Locale.US,"%.0f",currentSpeed); 
						setSpeed(str);
		    		}
                } 
				if (location.hasAltitude())
				{
					showAltitude = sharedPrefs.getBoolean("showAltitude", false);
					if (showAltitude)
					{
			    		useMetric = sharedPrefs.getBoolean("useMetric", false);
			    		if (useMetric)
			    			currentAltitude = location.getAltitude();
			    		else
			    			currentAltitude = location.getAltitude()*3.2808399;
						String str = String.format(Locale.US,"%.0f",currentAltitude); 
						setAltitude(str);
					}
				}
				showTime = sharedPrefs.getBoolean("showTime", false);
				if (showTime)
				{
					Date d = new Date(location.getTime());
					SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss a", Locale.US);
					currentTime = df.format(d);
					setTime(currentTime);
				}
				showLocalTemp = sharedPrefs.getBoolean("showLocalTemp", false);
				if (showLocalTemp)
				{
					lat = location.getLatitude();
					lon = location.getLongitude();
				}
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
    	
	//LOCAL TEMP PROCESSING THREAD
	public class localTempThread implements Runnable
	{
		Thread backgroundThread;
		
		public void start()
		{
			if( backgroundThread == null ) 
			{
				backgroundThread = new Thread( this );
				backgroundThread.start();
		    }
		}
	    public void stop() 
	    {
	        if( backgroundThread != null ) 
	           backgroundThread.interrupt();
	    }
    	@Override
		public void run()
    	{
    		try
    		{
	    		String st;
				String queryString;
				HttpClient httpclient;
				HttpGet httpget;
				HttpResponse response;
				HttpEntity entity;
				InputStream in;
				
				while (!backgroundThread.isInterrupted())
				{    		
		    		try
		    		{
		    			if (lat != 0 || lon != 0)
		    			{
			    			queryString = "http://forecast.weather.gov/MapClick.php?lat=" + lat + "&lon=" + lon;
			    			queryString = queryString.replace(" ","%20"); 
			    			try
			    			{
				    	    	httpclient = new DefaultHttpClient();
				    	    	httpget = new HttpGet(queryString);
				    	    	response = httpclient.execute(httpget);
				    	    	entity = response.getEntity();
				    	    	if (entity != null)
				    	    	{
				    	    		in = entity.getContent();
				    	    		st = convertStreamToString(in);
				    	    		in.close();
					    			useMetric = sharedPrefs.getBoolean("useMetric", false);
					    			if (useMetric)
					    			{
						    			st = st.substring(st.indexOf("myforecast-current-sm"));
						    			st = st.substring(23);
						    			st = st.substring(0,st.indexOf("&")) + " C";
					    			}
					    			else
					    			{
					    				st = st.substring(st.indexOf("myforecast-current-lrg"));
						    			st = st.substring(24);
						    			st = st.substring(0,st.indexOf("&")) + " F";
					    			}
					    			threadMsg(st);
				    	    	}
			    			}
			    			catch (Exception e)
			    			{
			    				threadMsg("Error");
			    			}
		    			}
		    			try
		    			{
		    				Thread.sleep(30000);
		    			}
		    			catch (InterruptedException e)
		    			{
		    			}
		    		}
		   	    	catch (Exception e)
		   	    	{
		   	    	}
		    	}
				if (D) Log.d(TAG, "localTempThread Stopping");				
    		}
    		finally
    		{
    			backgroundThread = null;
    		}
    	}
    	
        private void threadMsg(String msg) 
        {
            if (!msg.equals(null) && !msg.equals("")) 
            {
                Message msgObj = handler.obtainMessage();
                Bundle b = new Bundle();
                b.putString("message", msg);
                msgObj.setData(b);
                mIncomingHandler.sendMessage(msgObj);
            }
        }
        private Handler mIncomingHandler = new Handler(new IncomingHandlerCallback());
	}
	
	//HANDLER FOR TEMP THREAD
	class IncomingHandlerCallback implements Handler.Callback
	{
	    @Override
	    public boolean handleMessage(Message message) 
	    {
	    	String aResponse = message.getData().getString("message");
	    	if ( aResponse != null) 
	    	{
	    		setTemp(aResponse);
            }
	        return true;
	    }
	}

	//CONVERT HTTP INPUT TO STRING
    private static String convertStreamToString(InputStream is) 
    {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();

    String line = null;
    try 
    {
        while ((line = reader.readLine()) != null) 
        {
            sb.append(line + "\n");
        }
    } 
    catch (IOException e) 
    {
        e.printStackTrace();
    } 
    finally 
    {
        try 
        {
            is.close();
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }
    return sb.toString();
}
	
	//SET SPEED
	public void setSpeed(String speed)
	{
		TextView speedTextView = (TextView) findViewById(R.id.speedText);
        speedTextView.setText(speed);		
	}

	//SET ALTITUDE
	public void setAltitude(String altitude)
	{
		useMetric = sharedPrefs.getBoolean("useMetric", false);
		if (useMetric) altitude = altitude + " m";
		else altitude = altitude + " ft";
		TextView altitudeTextView = (TextView) findViewById(R.id.altitudeText);
        altitudeTextView.setText(altitude);		
	}

	//SET TIME
	public void setTime(String time)
	{
		TextView timeTextView = (TextView) findViewById(R.id.timeText);
        timeTextView.setText(time);		
	}

	//SET TEMP
	public void setTemp(String temp)
	{
		TextView localtempTextView = (TextView) findViewById(R.id.localtempText);
        localtempTextView.setText(temp);		
	}
	
	//SET RPM
	public void setRPM(String rpm)
	{
		TextView rpmTextView = (TextView) findViewById(R.id.rpmText);
        rpmTextView.setText(rpm);		
		GaugeLinearLayout gauge = (GaugeLinearLayout) findViewById(R.id.gauge);
		gauge.setRpm(Float.parseFloat(rpm));
	}

	//SET COOLANT
	public void setCoolant(String coolant)
	{
		TextView coolantTextView = (TextView) findViewById(R.id.coolantText);
        coolantTextView.setText(coolant);		
	}
	
	//ON TOUCH EVENT, SHOW ACTION BAR
	public boolean onTouchEvent(MotionEvent event) 
	{
	    if (event.getAction() == MotionEvent.ACTION_DOWN) 
	    {
	    	getSupportActionBar().show();
	    	hideActionBarDelayed(aHandler);
	    }
	    return super.onTouchEvent(event);
	}
	
	//HIDE ACTION BAR
	public void hideActionBarDelayed(Handler handler) 
	{
		aHandler.removeCallbacks(hideActionBar);
	    handler.postDelayed(hideActionBar, 8000);
	}
	
	//HIDE ACTION BAR RUNNABLE
    Runnable hideActionBar = new Runnable() 
    { 
         @Override
		public void run() 
         {
        	getSupportActionBar().hide();
         } 
    };
	
	
	// CREATE MENU
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		if (D) Log.d(TAG, "Creating options menu");
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu, menu);
	    return super.onCreateOptionsMenu(menu);		
	}
		
	//SHOW SETTINGS
	public void showSettings()
	{
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		Intent settingsIntent;
   		settingsIntent = new Intent(this, UserSetting.class);
        startActivityForResult(settingsIntent, RESULT_SETTINGS);		
	}
	
	//SETTINGS MENU LOADER
	@Override
    public boolean onOptionsItemSelected(MenuItem item) 
	{
        switch (item.getItemId()) 
        {
	        case R.id.menu_settings:
	        	if (D) Log.d(TAG, "Settings was selected");
	        	sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	        	Boolean skipLegacyWarning = sharedPrefs.getBoolean("skipLegacyWarning", false);
	        	if(Build.VERSION.SDK_INT <= 16 && !skipLegacyWarning)
	        	{
	        		CharSequence[] items = {"Don't show this again"};
	        		boolean[] itemsSel = {false};
	        		AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
	        		dlgAlert.setTitle("Android < 4.2 bluetooth bug, please allow 30 seconds to load");
	        		dlgAlert.setMultiChoiceItems(items, itemsSel, new DialogInterface.OnMultiChoiceClickListener()
	        		{
	        			@Override
	                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) 
	        			{
	                        if (isChecked) 
	                        {
	            	    		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	            	    		SharedPreferences.Editor editor = sharedPrefs.edit();
	            	    		editor.putBoolean("skipLegacyWarning", true);
	            	    		editor.commit();
	                        } 
	                        else 
	                        {
	            	    		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	            	    		SharedPreferences.Editor editor = sharedPrefs.edit();
	            	    		editor.putBoolean("skipLegacyWarning", false);
	            	    		editor.commit();
	                        }
	        			}
	        		}
	        		).setPositiveButton("OK", new DialogInterface.OnClickListener()
	        		{
	        			@Override
						public void onClick(DialogInterface dialog, int which)
	        			{
	        				showSettings();
        					dialog.dismiss();
	        			}
	        		});	        		
	        		dlgAlert.setCancelable(true);
	        		dlgAlert.create().show();
	        	}
	        	else
	        		showSettings();
	            break;
	        case R.id.menu_exit:
	        	if (D) Log.d(TAG, "Exit was selected");
				CarHudApplication cha = ((CarHudApplication)getApplicationContext());
				serviceStarted = cha.getServiceRunning();
				if (serviceStarted)
					stopService(new Intent(cha, CarHudSenderService.class));
				cha.setActivityRunning(false);	        	
	        	finish();
        }
        return true;
    }
	
	//ACTIVITY RESULT LISTENER
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		if (D) Log.d(TAG, "Activity result");
		super.onActivityResult(requestCode, resultCode, data);
	    switch (requestCode) 
	    {
	    	case RESULT_SETTINGS:
	    		if (D) Log.d(TAG, "Activity returned from settings, restarting");
	    		finish();
	    		try
	    		{
	    			Thread.sleep(2000);
	    		}
	    		catch (InterruptedException e)
	    		{
	    			
	    		}
	    		startActivity(getIntent());
	    		break;
	    	case RESULT_ENABLE_GPS:
	    		if (D) Log.d(TAG, "Activity returned from gps on request");
	    		finish();
	    		try
	    		{
	    			Thread.sleep(2000);
	    		}
	    		catch (InterruptedException e)
	    		{
	    			
	    		}
	    		startActivity(getIntent());
	    		break;
	    	case RESULT_ENABLE_BLUETOOTH:
	    		if (D) Log.d(TAG, "Activity returned from bluetooth on request");
	    		finish();
	    		try
	    		{
	    			Thread.sleep(2000);
	    		}
	    		catch (InterruptedException e)
	    		{
	    			
	    		}
	    		startActivity(getIntent());
	    		break;
	    }
	}
	
	//BROADCAST RECEIVER FOR STANDALONE
	private class MediaReceiver extends BroadcastReceiver 
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			String action = intent.getAction();
			if(action.substring(0,22).equals("com.maxmpz.audioplayer"))
			{
				if (D) Log.d(TAG, "MediaReceiver received poweramp action: " + action);
                Bundle bundle = intent.getBundleExtra(PowerampAPI.TRACK);
				displayMedia(bundle.getString(PowerampAPI.Track.ARTIST), bundle.getString(PowerampAPI.Track.ALBUM), bundle.getString(PowerampAPI.Track.TITLE));
			}
			if(action.equals("com.android.music.metachanged") || action.equals("com.android.music.playstatechanged") || action.equals("com.android.music.playbackcomplete") || action.equals("com.android.music.queuechanged"))
			{
				if (D) Log.d(TAG, "MediaReceiver received media changed action: " + action);
				displayMedia(intent.getStringExtra("artist"), intent.getStringExtra("album"), intent.getStringExtra("track"));
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
                    	messagePopup mp = new messagePopup();
                    	mp.setData(title, message, 8, false);
                    	showPopupTimedQueue(mp);
                    }
                }
	        }
		}
	};

	public void showPopupTimedQueue(messagePopup mp)
	{
		//SEE IF ITS PRIORITY AND NEEDS TO BREAK IN TO VIEW
		if (mp.getPriority() == true && popupShown)
		{
			//PUSH CURRENT DISPLAYED MESSAGE BACK ONTO QUEUE
			String title = ((TextView)pw.getContentView().findViewById(R.id.popup_title)).getText().toString();
			String message = ((TextView)pw.getContentView().findViewById(R.id.popup_message)).getText().toString();
        	messagePopup tmpMp = new messagePopup();
        	tmpMp.setData(title, message, 8, false);
        	popupQueue.addFirst(tmpMp);
        	//STOP MESSAGE HANDLER
        	handler.removeCallbacks(messageRun);
        	//CLEAR THE POPUP WINDOW
        	pw.dismiss();
        	//PUT NEW MESSAGE ON THE FRONT OF THE QUEUE AND START THE DISPLAY
        	popupQueue.addFirst(mp);
        	showPopupTimed();
		}
		else
		{
			//PUT ON THE QUEUE
			popupQueue.addLast(mp);
			//IF POPUP NOT ALREADY SHOWN, DISPLAY
			if (!popupShown)
				showPopupTimed();
		}
	}
	
	public void showPopupTimed()
	{
		try 
		{
			messagePopup mp = popupQueue.pollFirst();
			sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
			dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
			mirror = sharedPrefs.getBoolean("mirror", false);
			View PopUpView;
			if (mirror.equals(true))
				PopUpView = getLayoutInflater().inflate(R.layout.popup_layout_mirror, null);
			else
				PopUpView = getLayoutInflater().inflate(R.layout.popup_layout, null);
	
			pw = new PopupWindow(PopUpView, LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT, true);
			pw.setAnimationStyle(android.R.style.Animation_Dialog);
			
			LayerDrawable ld = (LayerDrawable) pw.getContentView().getBackground();
			ld.findDrawableByLayerId(R.id.backgroundColor).setColorFilter(dataColor, Mode.SRC_ATOP);
			pw.getContentView().setBackground(ld);

			((TextView)pw.getContentView().findViewById(R.id.popup_title)).setTextColor(dataColor);
			((TextView)pw.getContentView().findViewById(R.id.popup_title)).setText(mp.getTitle());
			((TextView)pw.getContentView().findViewById(R.id.popup_message)).setTextColor(dataColor);
			((TextView)pw.getContentView().findViewById(R.id.popup_message)).setText(mp.getMessage());
			pw.showAtLocation(PopUpView, Gravity.CENTER, 0, 0);
			popupShown = true;
	            
	            
			handler=new Handler();
			messageRun = new Runnable()
			{
				@Override
				public void run()
				{
					pw.dismiss();
					popupShown = false;
					if (popupQueue.size() > 0)
						showPopupTimed();
				}	
			};						
			handler.postDelayed(messageRun,(mp.getTime() * 1000));
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	//CLEAR SET AND CLEAR POPUP
	public void clearTopPopup()
	{
		if(topPopupShown)
			tpw.dismiss();
		topPopupShown = false;
	}
	//SHOW SET AND CLEAR POPUP
	public void showTopPopup(messagePopup mp)
	{
		try 
		{
			//JUST UPDATE THE POPUP
			if (topPopupShown)
			{
				String toAdd = "";
				for(int i = 0; i < Integer.parseInt(mp.getTitle()); i++)
					toAdd+= "&#9632;";
				((TextView)tpw.getContentView().findViewById(R.id.top_popup_message)).setText(Html.fromHtml(toAdd) + mp.getMessage() + Html.fromHtml(toAdd));
				
			}
			//SHOW THE POPUP
			else
			{
				sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
				dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
				mirror = sharedPrefs.getBoolean("mirror", false);
				View PopUpView;
				if (mirror.equals(true))
					PopUpView = getLayoutInflater().inflate(R.layout.top_popup_layout_mirror, null);
				else
					PopUpView = getLayoutInflater().inflate(R.layout.top_popup_layout, null);
		
				tpw = new PopupWindow(PopUpView, LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT, true);
				tpw.setAnimationStyle(android.R.style.Animation_Dialog);
				
				((TextView)tpw.getContentView().findViewById(R.id.top_popup_message)).setBackgroundColor(dataColor);				
				tpw.showAtLocation(PopUpView, Gravity.TOP, 0, 0);
				String toAdd = "";
				for(int i = 0; i < Integer.parseInt(mp.getTitle()); i++)
					toAdd+= "&#9632;";
				((TextView)tpw.getContentView().findViewById(R.id.top_popup_message)).setText(Html.fromHtml(toAdd) + mp.getMessage() + Html.fromHtml(toAdd));
			}
			topPopupShown = true;
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	
	@Override
	public void onStart() 
	{
		if (D) Log.d(TAG, "onStart()");
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	cha.setActivityRunning(true);
    	activityStarted = cha.getActivityRunning();
    	hideActionBarDelayed(aHandler);
		super.onStart();
	}

	@Override
	public void onResume() 
	{
		if (D) Log.d(TAG, "onResume()");
		CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	cha.setActivityRunning(true);
    	activityStarted = cha.getActivityRunning();
		serviceStarted = cha.getServiceRunning();
	    if (!serviceStarted)
	    	startActivity();
	    else
	    	runSender();
	    super.onResume();
	}

	@Override
	public void onPause() 
	{
		if (D) Log.d(TAG, "onPause()");
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	cha.setActivityRunning(false);
    	activityStarted = cha.getActivityRunning();		

    	cHandler.removeCallbacksAndMessages(cChatService);
    	mHandler.removeCallbacksAndMessages(mChatService);
    	oHandler.removeCallbacksAndMessages(oChatService);
    	handler.removeCallbacks(messageRun);
		aHandler.removeCallbacks(hideActionBar);
    	statusViewHandler.removeCallbacks(hideStatus);
    	mediaViewHandler.removeCallbacks(hideMedia);
		timer.cancel();    	
		popupQueue.clear();
		clearTopPopup();

		if (locationManager!= null && locationListener != null)
		{
			locationManager.removeUpdates(locationListener);
			locationManager = null;
		}
    	backgroundObd.stop();
    	localTempThread.stop();
		if (mediaReceiver != null)
		{
			unregisterReceiver(mediaReceiver);
			mediaReceiver = null;
		}
		
	    if (mChatService != null) 
	    {
	    	mChatService.stop();
	    	mChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }
	    }
	    if (cChatService != null) 
	    {
	    	cChatService.stop();
	    	cChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
	    if (oChatService != null) 
	    {
	    	oChatService.stop();
	    	oChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
	    super.onPause();
	}

	@Override
	public void onStop() 
	{
		if (D) Log.d(TAG, "onStop()");
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	cha.setActivityRunning(false);
    	activityStarted = cha.getActivityRunning();
    	
    	cHandler.removeCallbacksAndMessages(cChatService);
    	mHandler.removeCallbacksAndMessages(mChatService);
    	oHandler.removeCallbacksAndMessages(oChatService);
    	handler.removeCallbacks(messageRun);
		aHandler.removeCallbacks(hideActionBar);
    	statusViewHandler.removeCallbacks(hideStatus);
    	mediaViewHandler.removeCallbacks(hideMedia);    	
		timer.cancel();   
		popupQueue.clear();
		clearTopPopup();
    	
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
		
	    if (mChatService != null) 
	    {
	    	mChatService.stop();
	    	mChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }
	    }
	    if (cChatService != null) 
	    {
	    	cChatService.stop();
	    	cChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
	    if (oChatService != null) 
	    {
	    	oChatService.stop();
	    	oChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
    	backgroundObd.stop();
    	localTempThread.stop();
	    
	    super.onStop();
	}

	@Override
	public void onDestroy() 
	{
		if (D) Log.d(TAG, "onDestroy()");
    	CarHudApplication cha = ((CarHudApplication)getApplicationContext());
    	cha.setActivityRunning(false);
    	activityStarted = cha.getActivityRunning();
    	
    	cHandler.removeCallbacksAndMessages(cChatService);
    	mHandler.removeCallbacksAndMessages(mChatService);
    	oHandler.removeCallbacksAndMessages(oChatService);
    	handler.removeCallbacks(messageRun);
    	aHandler.removeCallbacks(hideActionBar);
    	statusViewHandler.removeCallbacks(hideStatus);
    	mediaViewHandler.removeCallbacks(hideMedia);    	
		timer.cancel();   
		popupQueue.clear();
		clearTopPopup();
    	
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
	    if (mChatService != null) 
	    {
	    	mChatService.stop();
	    	mChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }
	    }
	    if (cChatService != null) 
	    {
	    	cChatService.stop();
	    	cChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
	    if (oChatService != null) 
	    {
	    	oChatService.stop();
	    	oChatService = null;
	    	//FIX FOR OLD BLUETOOTH STACK CRASH ON CLOSING MULTIPLE BLUETOOTH SOCKETS
	    	if(Build.VERSION.SDK_INT <= 16)
	    	{ try { Thread.sleep(10000);}catch (InterruptedException e){} }	    	
	    }
    	backgroundObd.stop();
    	localTempThread.stop();
	    
		serviceStarted = cha.getServiceRunning();
		cha.setActivityRunning(false);
	    if (serviceStarted)
	    {
	    	if (D) Log.d(TAG, "Stopping service!");
	    	stopService(new Intent(cha, CarHudSenderService.class));
	    	cha.setServiceRunning(false);
	    }
	    super.onDestroy();
	}
		
	//OBD PROCESSING THREAD
	public class backgroundObd implements Runnable
	{
		Thread backgroundThread;
		
		public void start()
		{
			if( backgroundThread == null ) 
			{
				backgroundThread = new Thread( this );
				backgroundThread.start();
		    }
		}
	    public void stop() 
	    {
	        if( backgroundThread != null ) 
	           backgroundThread.interrupt();
	    }
    	@Override
		public void run()
    	{
    		try
    		{
        		obdQueue.clear();
    			obdQueue.add("ATZ");
    			obdQueue.add("ATSP0");
    			obdQueue.add("0100");
    			obdQueue.add("ATAT1");
    			obdQueue.add("ATE0");
    			obdQueue.add("ATL0");
    			obdQueue.add("ATS0");
    			obdQueue.add("010C1");
    			obdQueue.add("010D1");
    			obdQueue.add("01051");    
    			try
    			{
    				Thread.sleep(2000);
    			}
    			catch (InterruptedException e)
    			{
    			}
    			
				String cmd;
				while (!backgroundThread.isInterrupted())
				{
					if (!obdQueue.isEmpty())
						cmd = obdQueue.pollFirst();
					else
					{
						cmd = "010C1";
						obdQueue.add("010D1");
						obdQueue.add("01051");    				
					}
		    		try
		    		{
		    			cmd+= "\r";
		    			if (oChatService != null)
		    				oChatService.write(cmd.getBytes());
		    		}
		    		catch (Exception e)
		    		{
			            e.printStackTrace();    			
		    		}
				}
				if (D) Log.d(TAG, "backgroundObd Stopping");
    		}
    		finally
    		{
    			backgroundThread = null;
    		}
    	}
	}
		
    public void startObdCommands()
    {
		try
		{
			backgroundObd.start();
		}
		catch (IllegalThreadStateException e)
		{
			if (D) Log.w(TAG, "processobd already running");	
		}
    }
    
    //START COBRA IRADAR
	public void startCobraIradar()
	{
		if (D) Log.w(TAG, "startCobraIradar()");
        // GET LOCAL BLUETOOTH ADAPTER
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        cobraBtaddress = sharedPrefs.getString("cobraBtaddress","");
        if (mBluetoothAdapter == null) 
        {
            Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) 
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, RESULT_ENABLE_BLUETOOTH);
        } 
        else 
        {
            if (cChatService == null)
                cChatService = new CobraBluetoothChatService(this, cHandler);
        	BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(cobraBtaddress);
        	cChatService.connect(device, true);
        	cobraRestarting = false;
        }
	}
	
	//RESTART THE CONNECTION ON A TIMER
	public void restartCobraConnection()
	{
		if (D) Log.w(TAG, "restartCobraConnection()");
		if (!cobraRestarting)
		try
		{
            setColorImage("notconnected","cobraConnection");
			timer.schedule(new TimerTask() {@Override
			public void run() {startCobraIradar();}}, 5000);
			cobraRestarting = true;
		}
		catch (IllegalStateException e)
		{
			if (D) Log.w(TAG, "restartConnection() timer was cancelled");	
		}
	}
	
	//START BLUETOOTH OBD2
	public void startBluetoothOBD2()
	{
		if (D) Log.w(TAG, "startBluetootOBD2()");
        // GET LOCAL BLUETOOTH ADAPTER
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        obdBtaddress = sharedPrefs.getString("obdBtaddress","");
        if (mBluetoothAdapter == null) 
        {
            Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) 
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, RESULT_ENABLE_BLUETOOTH);
        } 
        else 
        {
            if (oChatService == null)
                oChatService = new ObdBluetoothChatService(this, oHandler);
        	BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(obdBtaddress);
        	oChatService.connect(device, true);
        	obdRestarting = false;
        }
    }
	
	//RESTART THE CONNECTION ON A TIMER
	public void restartObdConnection()
	{
		if (D) Log.w(TAG, "restartObdConnection()");
		if (!obdRestarting)
		{
			try
			{
				backgroundObd.stop();
				obdQueue.clear();
				setColorImage("notconnected","obdConnection");
				timer.schedule(new TimerTask() {@Override
				public void run() {startBluetoothOBD2();}}, 5000);
				obdRestarting = true;				
			}
			catch (IllegalStateException e)
			{
				if (D) Log.w(TAG, "restartConnection() timer was cancelled");
			}
		}
	}
	
	//COBRA CHECKSUM
    private static int calCheckSum(int[] paramArrayOfInt)
    {
    	int i = 0;
    	for (int j = 1; ; j++)
    	{
    		if (j >= 30)
    			return i;
    		i ^= paramArrayOfInt[j];
    	}
    }
    
    //SET BATTERY LEVEL
    public void setBatteryLevel(String bat)
    {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
		TextView batText = (TextView) findViewById(R.id.batteryText);
		batText.setTextColor(dataColor);
		batText.setText(bat + " V");
    }

    //SET ICON
    public void setColorImage(String drawable, String imageView)
    {
		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		dataColor = sharedPrefs.getInt("dataColor", 0xFF33B5E5);
		
		ImageView iv = (ImageView) findViewById(getResources().getIdentifier(imageView, "id", getPackageName()));
		iv.setImageDrawable(getResources().getDrawable(getResources().getIdentifier(drawable, "drawable", getPackageName())));
		iv.setColorFilter(dataColor);
    }
    
    //COBRA HANDLER
    public static class cHandler extends Handler 
    {
    	 
        private final Hud mActivity;
        public cHandler(Hud activity) 
        {
            mActivity = activity;
        }
 
        @Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) 
            {
            	case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) 
                    {
	                	case CobraBluetoothChatService.CONNECTION_FAILED:
                    		mActivity.cobraConnected = false;                    			                		
                            mActivity.restartCobraConnection();
                    		mActivity.checkConnectionsAndHide();                            
	                		break;
                    	case CobraBluetoothChatService.STATE_CONNECTED:
                    		mActivity.cobraConnected = true;                    			                		                    		
                            mActivity.setColorImage("connected","cobraConnection");                    		
                    		mActivity.checkConnectionsAndHide();                            
            				break;
                    	case CobraBluetoothChatService.STATE_CONNECTING:
                    		mActivity.cobraConnected = false;                    			                		                    		
                            mActivity.setColorImage("connecting","cobraConnection");
                    		mActivity.checkConnectionsAndHide();                            
            				break;
                    	case CobraBluetoothChatService.STATE_LISTEN:
                    	case CobraBluetoothChatService.STATE_NONE:
                    		mActivity.cobraConnected = false;                    			                		                    		
                            mActivity.setColorImage("notconnected","cobraConnection");
                    		mActivity.checkConnectionsAndHide();                            
                    		break;
                    }
            		break;
                case MESSAGE_WRITE:
                    break;            		
            	case MESSAGE_READ:
            		if (D) Log.d(TAG, "cobra message_read()");
            		String title = "";
            		String message = "";
            		int[] packet = (int[]) msg.obj;
            		if (packet[30] == calCheckSum(packet))
            		{
            			//BATTERY LEVEL
	            		String bat = "" + ((100 * (packet[11] - 48) + 10 * (packet[12] - 48) + (packet[13] - 48)) / 10.0D);
	            		mActivity.setBatteryLevel(bat);
	            		//SHOW ALERT IF RADAR ALERT IS SET
	            		if (packet[4] == 65)
	            		{
	            			switch (packet[5])
	            			{
		            			case 88:
		            				message+= "X Band";
		            				break;
		            			case 75:
		            				message+= "K Band";
		            				break;
		            			case 65:
		            				message+= "Ka Band";
		            				break;
		            			case 80:
		            				message+= "POP";
		            				break;
		            			case 86:
		            				message+= "VG2";
		            				break;
		            			case 69:
		            				message+= "Emergency Vehicle";
		            				break;
		            			case 79:
		            				message+= "Road Construction";
		            				break;
		            			case 82:
		            				message+= "Railroad";
		            				break;
		            			case 66:
		            			case 71:
		            			case 73:
		            			case 74:
		            			case 76:
		            			case 81:
		            				message+= "Laser";
		            				break;
		            			case 87:
		            				message+= "Battery Alert";
		            				break;
	            			}
	            			switch (packet[6])
	            			{
	            				case 49:
	            					title = "1";
	            					break;
		            			case 50:
		            				title = "2";
		            				break;
		            			case 51:
		            				title = "3";
		            				break;
		            			case 52:
		            				title = "4";
		            				break;
		            			case 53:
		            				title = "5";
		            				break;
	            			}
	                    	messagePopup mp = new messagePopup();
	                    	mp.setData(title, message, 1, true);
	            			mActivity.showTopPopup(mp);
	            		}
	            		else
	            		{
	            			if (mActivity.topPopupShown == true)
	            				mActivity.clearTopPopup();
	            		}
            		}
	                break;
                case MESSAGE_DEVICE_NAME:
                    //mActivity.makeToast(mActivity.getString(R.string.connected_to) + msg.getData().getString(DEVICE_NAME));
                    break;
            	case MESSAGE_CONNECTION_LOST:
            		mActivity.restartCobraConnection();
            		break;            		                    
            }
        }
    }	
    public static class oHandler extends Handler 
    {
    	 
        private final Hud mActivity;
        public oHandler(Hud activity) 
        {
            mActivity = activity;
        }
 
        @SuppressLint("DefaultLocale")
		@Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) 
            {
            	case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) 
                    {
	                	case ObdBluetoothChatService.CONNECTION_FAILED:
                    		mActivity.obdConnected = false;                    			                		
                            mActivity.restartObdConnection();
                    		mActivity.checkConnectionsAndHide();                            
	                		break;
                    	case ObdBluetoothChatService.STATE_CONNECTED:
                    		mActivity.obdConnected = true;
                            mActivity.setColorImage("connected","obdConnection");
                            mActivity.startObdCommands();
                    		mActivity.checkConnectionsAndHide();                            
                            break;
                    	case ObdBluetoothChatService.STATE_CONNECTING:
                    		mActivity.obdConnected = false;                    		
                    		mActivity.setColorImage("connecting","obdConnection");
                    		mActivity.checkConnectionsAndHide();                            
                    		break;
                    	case ObdBluetoothChatService.STATE_LISTEN:
                    	case ObdBluetoothChatService.STATE_NONE:
                    		mActivity.obdConnected = false;                    		                    		
                    		mActivity.setColorImage("notconnected","obdConnection");
                    		mActivity.checkConnectionsAndHide();                                                		
                    		break;
                    }
            		break;
                case MESSAGE_WRITE:
                    break;            		
            	case MESSAGE_READ:
            		if (D) Log.d(TAG, "obd message_read()");
	                byte[] readBuf = (byte[]) msg.obj;
                	String str = new String(readBuf, 0, msg.arg1);
                	StringTokenizer st = new StringTokenizer(str, "~");
                	String val = "", type = "";

                	if (st.hasMoreElements())
                		type = st.nextElement().toString();
                	if (type.equals("NODATA"))
                	{
                		if (D) Log.d(TAG, "NODATA");
                	}
                	else
                	{
        	    		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity.getApplicationContext());
    		    		boolean useMetric = sharedPrefs.getBoolean("useMetric", false);
                		if (type.equals("SPEED"))
                		{
 		                	if (st.hasMoreElements())
 		                	{
		                		val = st.nextElement().toString();
		                		float val2 = Float.valueOf(val);
		                		if (useMetric)
		                			val = String.format("%.0f", val2);
		                		else
		                			val = String.format("%.0f", val2 * 0.621371192);
	                			mActivity.setSpeed(val);
 		                	}
	                	}
                		else if (type.equals("RPM"))
	                	{
		                	if (st.hasMoreElements())
		                	{
		                		val = st.nextElement().toString();
		                		float val2 = Float.valueOf(val);
		                		//ROUND TO NEAREST 50
		                		val2 = Float.valueOf(Math.round(val2 / 50) * 50);
		                		mActivity.setRPM(String.format("%.0f",val2));
		                	}
	                	}
                		else if (type.equals("COOLANT"))
                		{
 		                	if (st.hasMoreElements())
 		                	{
		                		val = st.nextElement().toString();
		                		float val2 = Float.valueOf(val);
		                		if (useMetric)
		                			val = String.format("%.0f", val2) + " C";
		                		else
		                			val = String.format("%.0f", val2 * 1.8f + 32) + " F";
	                			mActivity.setCoolant(val);
 		                	}
                		}
                	}
                  break;
                case MESSAGE_DEVICE_NAME:
                    //mActivity.makeToast(mActivity.getString(R.string.connected_to) + msg.getData().getString(DEVICE_NAME));
                    break;
            	case MESSAGE_CONNECTION_LOST:
            		if (D) Log.d(TAG, "connection lost()");
            			mActivity.restartObdConnection();
        			break;
            }
        }
    }	    
}
