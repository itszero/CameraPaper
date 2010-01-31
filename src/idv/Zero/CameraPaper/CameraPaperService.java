	package idv.Zero.CameraPaper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.Toast;

public class CameraPaperService extends WallpaperService {
	
	private static enum CaptureMethod { CAPTURE_SNAP_PREVIEW, CAPTURE_SNAP_JPEG_CB }
	
	static {
		System.loadLibrary("camera-paper");
	}

	@Override
	public Engine onCreateEngine() {
		Engine e = new CameraPaperEngine();
		return e;
	}

	public class CameraPaperEngine extends Engine implements Handler.Callback, Camera.PreviewCallback, Camera.PictureCallback, Camera.AutoFocusCallback {
		
		/* Message handling */
		private Handler handler = new Handler(this);
		public final static int MSG_SET_VISBILITY = 0x1;
		public final static int ARG_VISIBLE = 0x1;
		public final static int ARG_INVISIBLE = 0x0;
		public final static int MSG_DRAW_FRAMES = 0x2;
		public final static int MSG_UI_DOUBLE_TAP = 0x3;
		public final static int MSG_UI_TRIPLE_TAP = 0x4;
		public final static int MSG_WAKE_FROM_SHAKE = 0x5;
		public final static int MSG_DO_AUTO_FOCUS = 0x6;
		
		/* Camera */
		private Camera cam;
		private ByteBuffer previewBuffer;
		private Point screenSize;
		private Bitmap bmpCache;
		private boolean darkMode = false;
		private boolean pauseMode = false;

		/* UI */
		private final int SIMUTANEOUS_TAP_THRESHOLD = 400;
		private long lastTouchDownTime, firstDarkDetection = 0;
		private Point lastTouchPoint;
		private boolean cancelNextDoubleTap = false;
		private int simutaneousTouchCount = 0;
		private SensorManager sm = (SensorManager)CameraPaperService.this.getSystemService(Context.SENSOR_SERVICE);
		private SensorShakeEventListener sensorShakeEventListener;
		private ArrayList<Indicator> lstIndicators = new ArrayList<Indicator>();
		
		/* Settings */
		private Point camPreviewSize;
		private boolean useTapCircleIndicator;
		private CaptureMethod camCaptureMethod;
		
		private String TAG = "CameraPaperService";		
		
		public void onCreate(SurfaceHolder surfaceHolder)
		{
			super.onCreate(surfaceHolder);
			setTouchEventsEnabled(true);
			decoderThread.start();
			
			WindowManager manager = (WindowManager)CameraPaperService.this.getSystemService(Context.WINDOW_SERVICE);
		    Display display = manager.getDefaultDisplay();
		    screenSize = new Point(display.getWidth(), display.getHeight());
		}
		
        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
            	if (useTapCircleIndicator)
            	{
            		Random rnd = new Random();
            		lstIndicators.add(new IndicatorCircle((int)event.getX(), (int)event.getY(), 2000, Color.argb(255, 255 - rnd.nextInt(125), 255 - rnd.nextInt(125), 255 - rnd.nextInt(125))));
            	}

				if (lastTouchPoint == null) lastTouchPoint = new Point((int)event.getX(), (int)event.getY());
				if ( (event.getX() - lastTouchPoint.x) > screenSize.x / 15 ||
				        (event.getY() - lastTouchPoint.y) > screenSize.y / 15 )
					simutaneousTouchCount = 0;
				lastTouchPoint = new Point((int)event.getX(), (int)event.getY());
				
				if (System.currentTimeMillis() - lastTouchDownTime < SIMUTANEOUS_TAP_THRESHOLD)
            		simutaneousTouchCount++;
            	else
					simutaneousTouchCount = 1;
				
				/**
				 * Double Tap message is being delayed, so if we do detected triple tap,
				 * we can send Triple Tap message ahead and cancel next double tap message.
				 */
            	if (simutaneousTouchCount == 2)
            		handler.sendMessageDelayed(handler.obtainMessage(MSG_UI_DOUBLE_TAP, (int)event.getX(), (int)event.getY()), 500);
            	else if (simutaneousTouchCount == 3)
            	{
            		cancelNextDoubleTap = true;
            		handler.sendEmptyMessage(MSG_UI_TRIPLE_TAP);          		
            	}
            	lastTouchDownTime = System.currentTimeMillis();
            }
            super.onTouchEvent(event);
        }
		
		public void onVisibilityChanged(boolean visible)
		{
			super.onVisibilityChanged(visible);
			handler.removeMessages(MSG_SET_VISBILITY);
			reloadSettings();
			
			if (visible)
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_VISIBLE, 0));
			else
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
		}
		
		public void onDestroy() {
			super.onDestroy();
			decoderThread.interrupt();
			
			deregisterShakeDetector();
			sm = null;
			
			handler.removeMessages(MSG_SET_VISBILITY);
			handler.removeMessages(MSG_DRAW_FRAMES);
			handler = null;
			
			lstIndicators.clear();
			lstIndicators = null;
			
			if (bmpCache != null)
			{
				bmpCache.recycle();
				bmpCache = null;
			}
		}
		
		/* Message handling methods */
		@Override
		public boolean handleMessage(Message msg) {
			switch(msg.what)
			{
			case MSG_UI_DOUBLE_TAP:
				if (cancelNextDoubleTap)
					cancelNextDoubleTap = false;
				else
					handleDoubleTap(msg);
				break;
			case MSG_UI_TRIPLE_TAP:
            	if (bmpCache == null) break;
            	doSnap();
            	lstIndicators.add(new IndicatorRectangle(new Rect(0, 0, screenSize.x, screenSize.y), Color.WHITE, 500));
				break;
			case MSG_DRAW_FRAMES:
				drawFrame();
				break;
			case MSG_SET_VISBILITY:
				updateVisiblity(msg);
				break;
			case MSG_WAKE_FROM_SHAKE:
				deregisterShakeDetector();
				resetDarkModeDetector();
				if (!pauseMode)
					handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_VISIBLE, 0));
				break;
			case MSG_DO_AUTO_FOCUS:
				handler.removeMessages(MSG_DO_AUTO_FOCUS);
				if (cam != null)
					cam.autoFocus(this);
				break;
			}
			return false;
		}

		private void handleDoubleTap(Message msg) {
			if (pauseMode)
			{
				pauseMode = false;
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_VISIBLE, 0));
			}
			else
			{
				pauseMode = true;
				resetDarkModeDetector();
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
			}
		}
		
		private void updateVisiblity(Message msg) {
			try
			{
				if (msg.arg1 == 0) // INVISIBLE
				{
					Log.i(TAG, "Stop Preview, Close Camera");
					if (cam != null)
					{
						cam.setPreviewCallback(null);
						cam.stopPreview();
						cam.release();
					}
					cam = null;
					
					if (decoderThread.isAlive() && !decoderThread.isInterrupted())
						decoderThread.interrupt();
					
					previewBuffer.clear();
				}
				else // VISIBLE
				{
					Log.i(TAG, "Open Camera, Start Preview");
					firstDarkDetection = 0;
					
					if (cam == null)
						cam = Camera.open();
					
					setCameraParameters();
					cam.startPreview();
					
					if (decoderThread.isAlive() && !decoderThread.isInterrupted())
						decoderThread.interrupt();
					
					decoderThread = new Thread(decoderRunnable);
					decoderThread.start();
					handler.sendEmptyMessage(MSG_DRAW_FRAMES);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				Log.i(TAG, "Failed when start/stop camera preview, retry in 3 secs.");
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
				handler.sendMessageDelayed(handler.obtainMessage(msg.what, msg.arg1, msg.arg2), 3000);
			}
		}

		/* Camera preview delegate callback */
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (waitingNextPreviewFrame)
			{
				previewBuffer.rewind();
				previewBuffer.put(data);
				waitingNextPreviewFrame = false;
			}
		}

		/* Take picture */
		private void doSnap() {
			Log.i(TAG, "Snap current image");
			if (cam != null && !pauseMode && !darkMode)
			{
				try {
					handler.sendEmptyMessage(MSG_DO_AUTO_FOCUS);
				}
				catch (Exception e) { e.printStackTrace(); }
			}
		}
		
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			if (camCaptureMethod == CaptureMethod.CAPTURE_SNAP_JPEG_CB)
				cam.takePicture(null, null, this);
			else
			{
				long currentTime = System.currentTimeMillis();
				String name = currentTime + "-campaper.jpg";
				
				if (MediaStore.Images.Media.insertImage(getContentResolver(), bmpCache, name, name) != null)
					Toast.makeText(CameraPaperService.this, R.string.image_saved, Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(CameraPaperService.this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
				
				cam.startPreview();
			}
		}
		
		/* Camera picture delegate callback */
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			long currentTime = System.currentTimeMillis();
			String name = currentTime + "-campaper.jpg";
			
			Bitmap bmpSnap = BitmapFactory.decodeByteArray(data, 0, data.length);
			
			if (MediaStore.Images.Media.insertImage(getContentResolver(), bmpSnap, name, name) != null)
				Toast.makeText(CameraPaperService.this, R.string.image_saved, Toast.LENGTH_SHORT).show();
			else
				Toast.makeText(CameraPaperService.this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
			
			cam.startPreview();
		}
		
		@SuppressWarnings("unchecked")
		private void drawFrame() {
			handler.removeMessages(MSG_DRAW_FRAMES);
			
			try
			{
				Canvas cv = this.getSurfaceHolder().lockCanvas();
				if (cv == null) return;
				
				cv.drawColor(Color.argb(255, 10, 10, 10));
				if (!darkMode)
				{
					if (bmpCache != null)
					{
						Rect src = new Rect(0, 0, bmpCache.getWidth(), bmpCache.getHeight());
						Rect dst = new Rect(0, 0, screenSize.x, screenSize.y);
						cv.drawBitmap(bmpCache, src, dst, null);
					}
					
					if (pauseMode)
						cv.drawColor(Color.argb(125, 0, 0, 0));
				}
				
				ArrayList<Indicator> lstIndicatorsCopy = (ArrayList<Indicator>) lstIndicators.clone();
				long currentTime = System.currentTimeMillis();
				for(Indicator id : lstIndicatorsCopy)
				{
					float dT = currentTime - id.startTimestamp;
					if (dT > id.duration_ms)
					{
						lstIndicators.remove(id);
						continue;
					}
					
					id.draw(cv);
				}
				
				this.getSurfaceHolder().unlockCanvasAndPost(cv);
			}
			catch(Exception e) { e.printStackTrace(); }

			handler.sendEmptyMessage(MSG_DRAW_FRAMES);
		}

		/* Helpers */
		private void resetDarkModeDetector() {
			darkMode = false;
			firstDarkDetection = 0;
			deregisterShakeDetector();
		}


		private void registerShakeDetector() {
			deregisterShakeDetector();
			sensorShakeEventListener = new SensorShakeEventListener(handler);
			sm.registerListener(sensorShakeEventListener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
		}

		private void deregisterShakeDetector() {
			if (sm != null && sensorShakeEventListener != null)
				sm.unregisterListener(sensorShakeEventListener);
		}

		/* Decoder thread */
		private boolean waitingNextPreviewFrame = true;
		private Runnable decoderRunnable = new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, "DecodeThread START");
				while(!Thread.currentThread().isInterrupted())
				{
					if (previewBuffer == null) continue;
					
					if (pauseMode || darkMode) continue;
					if (waitingNextPreviewFrame) continue;

					waitingNextPreviewFrame = false;
					int[] out = decodeYUVAndRotate(previewBuffer, camPreviewSize.x, camPreviewSize.y);
					bmpCache = Bitmap.createBitmap(out, camPreviewSize.y, camPreviewSize.x, Bitmap.Config.ARGB_8888);
					waitingNextPreviewFrame = true;
										
					// I use the last element of out indicate it's a dark image or not.
					if (out[camPreviewSize.y * camPreviewSize.x] == 1 && !pauseMode && !darkMode)
					{
						// If we detect DARK MODE, stop camera preview.
						// and restart if user starts to move the phone.
						if (firstDarkDetection == 0)
							firstDarkDetection = System.currentTimeMillis();
						
						if (System.currentTimeMillis() - firstDarkDetection > 5000)
						{
							darkMode = true;
							handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
							if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) // Provide fallback for phones without G-sensor
							{
								Log.i(TAG, "Enter dark mode, device does not support G-sensor. Stop preview for 5 secs.");
								handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_VISIBLE, 0));
							}
							else
							{
								Log.i(TAG, "Enter dark mode, register G-sensor for waking up camera");
								registerShakeDetector();
							}
						}
					}
					else
						firstDarkDetection = 0;
				} // while(true)
				Log.i(TAG, "DecodeThread STOP");
			} // public void run
		};
		private Thread decoderThread = new Thread(decoderRunnable);
		
		/* Camera settings helper */
		private void setCameraParameters() {
		    Camera.Parameters parameters = cam.getParameters();
		    Camera.Size size = parameters.getPreviewSize();
		    Log.i(TAG, "Default preview size: " + size.width + ", " + size.height);
		    camPreviewSize = new Point(size.width, size.height);
		    String previewFormatString = parameters.get("preview-format");
		    parameters.setPreviewFormat(PixelFormat.YCbCr_422_SP);
		    Log.i(TAG, "Default preview format: " + previewFormatString);

		    camPreviewSize = new Point();
		    camPreviewSize.x = (screenSize.y >> 3) << 3;
		    camPreviewSize.y = (screenSize.x >> 3) << 3;
		    Log.i(TAG, "Setting preview size: " + camPreviewSize.x + ", " + camPreviewSize.y);
		    parameters.setPreviewSize(camPreviewSize.x, camPreviewSize.y);
		    if (previewBuffer != null)
		    {
		    	previewBuffer.clear();
		    	previewBuffer = null;
		    }
		    previewBuffer = ByteBuffer.allocateDirect(camPreviewSize.x * camPreviewSize.y * 3 / 2);
		    
		    // FIXME: This is a hack to turn the flash off on the Samsung Galaxy.
		    parameters.set("flash-value", 2);

		    // This is the standard setting to turn the flash off that all devices should honor.
		    parameters.set("flash-mode", "off");
		    
		    parameters.setPictureFormat(PixelFormat.JPEG);
		    parameters.setRotation(90);
		    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		    
		    cam.setParameters(parameters);
		    cam.setPreviewCallback(this);
		}

		/* YUV decoding */
		
		// decode Y, U, and V values on the YUV 420 buffer described as YCbCr_422_SP by Android 
		// David Manpearl 081201 (ported to C code by Zero)
		private int[] decodeYUVAndRotate(ByteBuffer fg, int width, int height) throws NullPointerException, IllegalArgumentException { 
	        final int sz = width * height; 
	        if(fg == null) throw new NullPointerException("buffer 'fg' is null"); 
	        if(fg.capacity() < sz * 3 / 2) throw new IllegalArgumentException("buffer 'fg' size " + fg.capacity() + " < minimum " + sz * 3/ 2); 
	        
	        return nativeDecodeYUVAndRotate(fg, width, height);
		}
		
		private native int[] nativeDecodeYUVAndRotate(ByteBuffer fg, int width, int height);
		
		private void reloadSettings() {
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CameraPaperService.this);
			useTapCircleIndicator = prefs.getBoolean("use_tap_circle", true);
			
			if (prefs.getString("capture_method", "1").equals("1"))
				camCaptureMethod = CaptureMethod.CAPTURE_SNAP_PREVIEW;
			else
				camCaptureMethod = CaptureMethod.CAPTURE_SNAP_JPEG_CB;			
		}
	}	
}
