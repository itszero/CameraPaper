package idv.Zero.CameraPaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;

public class IndicatorRectangle extends Indicator {
	public Rect rect;
	public int color;
	
	public IndicatorRectangle(Rect iRect, int iColor, int duration)
	{
		rect = iRect;
		color = iColor;
		duration_ms = duration;
	}
	
	@Override
	public void draw(Canvas cv) {
		float dT = System.currentTimeMillis() - startTimestamp;
		float progress = dT / duration_ms;
		Paint pRect = new Paint();
		pRect.setColor(color);
		pRect.setStyle(Style.FILL);
		pRect.setAlpha(255 - (int)( 255.0 * progress ));
		cv.drawRect(rect, pRect);			
	}
}
