package idv.Zero.CameraPaper;

import android.graphics.Canvas;

/* Indicators (objects on the screen) */
public abstract class Indicator {
	public long startTimestamp = System.currentTimeMillis();
	public long duration_ms;
	
	public abstract void draw(Canvas canvas);
}
