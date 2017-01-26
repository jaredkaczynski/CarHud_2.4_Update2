package com.carhud.app.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class MirroredLinearLayout extends LinearLayout
{
	private Canvas offscreen;
	Bitmap bitmap;
	Paint p;
	
	 public MirroredLinearLayout(Context context, AttributeSet attrs) 
	 {
		 super(context, attrs);
		 setLayerType(View.LAYER_TYPE_HARDWARE, null);
		 p = new Paint();
	 }

	 @Override
	 protected void onDraw(Canvas canvas) 
	 {
	        if(canvas==offscreen)
	        {
	            super.onDraw(offscreen);
	        }
	        else
	        {
	        	canvas.translate(getWidth(), 0);
	        	canvas.scale(-1, 1);
	        	if (bitmap == null)
	        	{
	        		bitmap=Bitmap.createBitmap(getWidth(), getHeight(), Config.ARGB_8888);
	        		offscreen=new Canvas(bitmap);
	        	}
	            super.draw(offscreen);
	            if (p == null)
	            	p=new Paint();
	            canvas.drawBitmap(bitmap, 0, 0, p);
	            invalidate();
	        }
	 }
}
