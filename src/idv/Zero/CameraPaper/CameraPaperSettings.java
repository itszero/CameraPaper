package idv.Zero.CameraPaper;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class CameraPaperSettings extends PreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
	}
}
