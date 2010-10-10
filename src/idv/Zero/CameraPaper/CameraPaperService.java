package idv.Zero.CameraPaper;

import java.io.File;
import java.io.IOException;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

public class CameraPaperService extends WallpaperService {
	public static String ACTION_TOGGLE_VIDEO_RECORD = "idv.Zero.CameraPaper.action.TOGGLE_VIDEO_RECORD";
	public Engine e;
	
	@Override
	public Engine onCreateEngine() {
		e = new CameraPaperEngine();
		return e;
	}

	public class CameraPaperEngine extends Engine implements Handler.Callback, Camera.PictureCallback, Camera.AutoFocusCallback {
		
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
		private Point screenSize;
		private Bitmap bmpCache;
		private boolean pauseMode = false;

		/* UI */
		private final int SIMUTANEOUS_TAP_THRESHOLD = 400;
		private long lastTouchDownTime;
		private Point lastTouchPoint;
		private boolean cancelNextDoubleTap = false;
		private int simutaneousTouchCount = 0;
		
		/* Settings */
		private Point camPreviewSize;
		private String TAG = "CameraPaperService";		
		
		public void onCreate(SurfaceHolder surfaceHolder)
		{
			super.onCreate(surfaceHolder);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			setTouchEventsEnabled(true);
			
			WindowManager manager = (WindowManager)CameraPaperService.this.getSystemService(Context.WINDOW_SERVICE);
		    Display display = manager.getDefaultDisplay();
		    screenSize = new Point(display.getWidth(), display.getHeight());
		}
		
        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
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
			
			if (visible)
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_VISIBLE, 0));
			else
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
		}
		
		public void onDestroy() {
			super.onDestroy();
			
			handler.removeMessages(MSG_SET_VISBILITY);
			handler.removeMessages(MSG_DRAW_FRAMES);
			handler = null;
			
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
            	doSnap();
				break;
			case MSG_SET_VISBILITY:
				updateVisiblity(msg);
				break;
			case MSG_WAKE_FROM_SHAKE:
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
				handler.sendMessage(handler.obtainMessage(MSG_SET_VISBILITY, ARG_INVISIBLE, 0));
			}
		}
		
		private void updateVisiblity(Message msg) {
			try
			{
				if (msg.arg1 == 0) // INVISIBLE
				{
					try
					{
						unregisterReceiver(mReceiver);
					}
					catch (Exception e) {}
					
					Log.i(TAG, "Stop Preview, Close Camera");
					if (cam != null)
					{
						cam.setPreviewCallback(null);
						cam.stopPreview();
						cam.release();
					}
					cam = null;
				}
				else // VISIBLE
				{
					Log.i(TAG, "Open Camera, Start Preview");

					if (cam == null)
						cam = Camera.open();
					
					setCameraParameters(90, null);
					cam.startPreview();
					
					IntentFilter infilter = new IntentFilter();
					infilter.addAction(ACTION_TOGGLE_VIDEO_RECORD);
					registerReceiver(mReceiver, infilter);
					
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

		/* Take picture */
		private void doSnap() {
			Log.i(TAG, "Snap current image");
			if (cam != null && !pauseMode)
			{
				try {
					handler.sendEmptyMessage(MSG_DO_AUTO_FOCUS);
				}
				catch (Exception e) { e.printStackTrace(); }
			}
		}
		
		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			cam.takePicture(null, null, this);
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

		/* Camera settings helper */
		private void setCameraParameters(int rotation, CamcorderProfile profile) {
		    Camera.Parameters parameters = cam.getParameters();
		    Camera.Size size = parameters.getPreviewSize();
		    Log.i(TAG, "Default preview size: " + size.width + ", " + size.height);
		    camPreviewSize = new Point(size.width, size.height);
		    String previewFormatString = parameters.get("preview-format");
		    parameters.setPreviewFormat(PixelFormat.YCbCr_422_SP);
		    Log.i(TAG, "Default preview format: " + previewFormatString);
		    
		    cam.setDisplayOrientation(rotation);
		    try {
		    	cam.setPreviewDisplay(this.getSurfaceHolder());
		    } catch (Exception ex) {}

		    camPreviewSize = new Point();
		    if (profile == null)
		    {
			    camPreviewSize.x = (screenSize.y >> 3) << 3;
			    camPreviewSize.y = (screenSize.x >> 3) << 3;
		    }
		    else
		    {
		    	camPreviewSize.x = profile.videoFrameWidth;
		    	camPreviewSize.y = profile.videoFrameHeight;
		    	parameters.setPreviewFrameRate(profile.videoFrameRate);
		    }
		    Log.i(TAG, "Setting preview size: " + camPreviewSize.x + ", " + camPreviewSize.y);
		    parameters.setPreviewSize(camPreviewSize.x, camPreviewSize.y);
		    
		    // FIXME: This is a hack to turn the flash off on the Samsung Galaxy.
		    parameters.set("flash-value", 2);

		    // This is the standard setting to turn the flash off that all devices should honor.
		    parameters.set("flash-mode", "off");
		    
		    parameters.setPictureFormat(PixelFormat.JPEG);
		    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		    
		    cam.setParameters(parameters);
		}
		
		/* Video recording support */
		private MediaRecorder videoRecorder = null;
		private ContentValues curVideoContentValues = null;
		private long vrStart = 0;

		private Runnable widgetUpdateRunnable = new Runnable() {
			@Override
			public void run() {
				while (!Thread.interrupted())
				{
					long n = System.currentTimeMillis();
					long d = (n - vrStart) / 1000;
					int m = (int) (d / 60);
					int s = (int) (d % 60);
					String elapsed = String.format("%02d:%02d", m, s);
					setWidgetText(elapsed);
					
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						break;
					}
				}
				
				setWidgetText("00:00");
			}
			
			private void setWidgetText(String txt)
			{
				Context context = getApplicationContext();
				AppWidgetManager mWidgetManager = AppWidgetManager.getInstance(context);
				int[] mWidgetIds = mWidgetManager.getAppWidgetIds(new ComponentName(context, VideoRecorderWidget.class));
				
				for (int i : mWidgetIds)
				{
					RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.video_recorder_widget);
					updateViews.setTextViewText(R.id.text_info, txt);
					mWidgetManager.updateAppWidget(i, updateViews);
				}
			}
		};
		private Thread thdWidgetUpdater = null;
		
		private void doVideoRecording() {
			if (videoRecorder == null)
			{
				CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
				
				videoRecorder = new MediaRecorder();
				cam.stopPreview();
				cam.release();
				cam = Camera.open();
				setCameraParameters(90, profile);
				cam.startPreview();
				cam.unlock();
				videoRecorder.setCamera(cam);
				videoRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				videoRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				videoRecorder.setProfile(profile);

				// create video path
		        long dateTaken = System.currentTimeMillis();
		        String title = Long.toString(dateTaken);
		        String fileName = title + ".3gp";
		        String dirPath = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera/";
		        String filePath = dirPath + "/" + fileName;
		        File cameraDir = new File(dirPath);
		        cameraDir.mkdirs();

		        ContentValues values = new ContentValues(7);
		        values.put(Video.Media.TITLE, title);
		        values.put(Video.Media.DISPLAY_NAME, fileName);
		        values.put(Video.Media.DESCRIPTION, "");
		        values.put(Video.Media.DATE_TAKEN, dateTaken);
		        values.put(Video.Media.MIME_TYPE, "video/3gpp");
		        values.put(Video.Media.DATA, filePath);
		        curVideoContentValues = values;
		        videoRecorder.setOutputFile(filePath);
		        videoRecorder.setPreviewDisplay(this.getSurfaceHolder().getSurface());

		        try {
					videoRecorder.prepare();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				videoRecorder.start();
				
				vrStart = System.currentTimeMillis();
				thdWidgetUpdater = new Thread(widgetUpdateRunnable);
				thdWidgetUpdater.start();
				
				Toast.makeText(CameraPaperService.this, R.string.start_record, Toast.LENGTH_SHORT).show();
				Log.i(TAG, "Start recording");
			}
			else
			{
				videoRecorder.stop();
				videoRecorder.release();
				videoRecorder = null;

				try {
					cam.reconnect();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				cam.stopPreview();
				setCameraParameters(90, null);
				cam.startPreview();
				
				getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, curVideoContentValues);
				Toast.makeText(CameraPaperService.this, R.string.stop_record, Toast.LENGTH_SHORT).show();
				
				thdWidgetUpdater.interrupt();
				thdWidgetUpdater = null;
				
				curVideoContentValues = null;
				Log.i(TAG, "stop recording");
			}
		}
		
		BroadcastReceiver mReceiver = new BroadcastReceiver() {
			
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.i(TAG, "received broadcast: " + intent.getAction());
				if (intent.getAction().equals(ACTION_TOGGLE_VIDEO_RECORD))
					CameraPaperService.CameraPaperEngine.this.doVideoRecording();
			}
			
		};
	}
}
