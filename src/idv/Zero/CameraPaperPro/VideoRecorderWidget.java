package idv.Zero.CameraPaperPro;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class VideoRecorderWidget extends AppWidgetProvider {
	private AppWidgetManager mWidgetManager = null;
	private int[] mWidgetIds;
	
	public void onReceive(Context context, Intent intent) {
		mWidgetManager = AppWidgetManager.getInstance(context);
		mWidgetIds = mWidgetManager.getAppWidgetIds(new ComponentName(context, VideoRecorderWidget.class));
		
		for (int i : mWidgetIds)
			mWidgetManager.updateAppWidget(i, doUpdate(context));
	}
	
	public RemoteViews doUpdate(Context context) {
		RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.video_recorder_widget);
		
		Intent bcast = new Intent(CameraPaperService.ACTION_TOGGLE_VIDEO_RECORD);
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, bcast, PendingIntent.FLAG_UPDATE_CURRENT);
		updateViews.setOnClickPendingIntent(R.id.record_button, pi);
		
		return updateViews;
	}
}
