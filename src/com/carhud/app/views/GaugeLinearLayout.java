package com.carhud.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.carhud.app.CarHudApplication;

public class GaugeLinearLayout extends LinearLayout
{
	CarHudApplication cha;
	float rpm;
	Paint p;
	int dataColor;
	
	 public GaugeLinearLayout(Context context, AttributeSet attrs) 
	 {
		 super(context, attrs);
		 setLayerType(View.LAYER_TYPE_HARDWARE, null);
		 if (!isInEditMode()) {
			 cha = ((CarHudApplication) context.getApplicationContext());
		 }
		 rpm = 0;
		 dataColor = 0xFF33B5E5;

		 p = new Paint();
	 }

	 public void setRpm(float newRpm)
	 {
		rpm = newRpm;
		invalidate();
	 }
	 public void setColor(int newDataColor)
	 {
		 dataColor = newDataColor;
	 }

	 @Override
	 protected void onDraw(Canvas canvas) 
	 {
		 double barLength = canvas.getWidth() - 60;
		 double spaces = barLength / 7000;
		 int newLength = (int) Math.round(spaces * rpm); 
		 p.setColor(dataColor);
		 canvas.drawRect(30, 0, newLength + 30, canvas.getHeight(), p);
		 super.onDraw(canvas); 
		 invalidate();
	 }
}
