package idv.Zero.CameraPaper;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;

public class IndicatorCircle extends Indicator {
	public int x, y;
	public int color;
	
	public IndicatorCircle(int ix, int iy, int iduration, int icolor) {
		x = ix;
		y = iy;
		duration_ms = iduration;
		color = icolor;
	}

	@Override
	public void draw(Canvas cv) {
		float dT = System.currentTimeMillis() - startTimestamp;
		float progress = dT / duration_ms;
		Paint pCircle = new Paint();
		pCircle.setColor(color);
		pCircle.setStyle(Style.STROKE);
		pCircle.setStrokeWidth(10);
		pCircle.setAlpha(255 - (int)( 255.0 * progress ));
		// Expand to 1/8 screen height
		cv.drawCircle(x, y, (float) (300 * progress), pCircle);
	}
}