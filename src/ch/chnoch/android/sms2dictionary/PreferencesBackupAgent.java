package ch.chnoch.android.sms2dictionary;


import java.io.IOException;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;


public class PreferencesBackupAgent extends BackupAgentHelper {
	// A key to uniquely identify the set of backup data
	static final String PREFS_BACKUP_KEY = "prefs";

	// Allocate a helper and add it to the backup agent
	@Override
	public void onCreate() {
		SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
				this, SMS2Dictionary.SHARED_PREFS_NAME);
		addHelper(PREFS_BACKUP_KEY, helper);
	}
	
}