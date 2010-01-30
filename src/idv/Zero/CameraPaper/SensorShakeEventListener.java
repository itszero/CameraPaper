package idv.Zero.CameraPaper;

import idv.Zero.CameraPaper.CameraPaperService.CameraPaperEngine;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;

class SensorShakeEventListener implements SensorEventListener {
	private float lastX = 0, lastY = 0, lastZ = 0;
	private long timestamp = 0;
	private Handler handler;
	private final int MOTION_DETECT_THRESHOLD = 300;
	
	public SensorShakeEventListener(Handler _handler)
	{
		handler = _handler;
	}
	
	public void setHandler(Handler _handler)
	{
		handler = _handler;
	}
			
	@Override
	public void onSensorChanged(SensorEvent event) {
		if (lastX == 0 && lastY == 0 && lastZ == 0 && timestamp == 0)
		{
			lastX = event.values[0];
			lastY = event.values[1];
			lastZ = event.values[2];
			timestamp = event.timestamp;
			return;
		}
				
		float dX = event.values[0] - lastX;
		float dY = event.values[1] - lastY;
		float dZ = event.values[2] - lastZ;
		float dT = event.timestamp - timestamp;
				
		// wait until > 100ms
		if (dT < 100 * 1000000) return;
			
		lastX = event.values[0];
		lastY = event.values[1];
		lastZ = event.values[2];
				
		float speed = (float) ((dX + dY + dZ) / dT * 10000 * 10000 * 10000);
		if (speed > MOTION_DETECT_THRESHOLD && speed < MOTION_DETECT_THRESHOLD * 10 && handler != null)
			handler.sendMessage(handler.obtainMessage(CameraPaperEngine.MSG_WAKE_FROM_SHAKE, CameraPaperEngine.ARG_VISIBLE, 0));
	}
			
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}