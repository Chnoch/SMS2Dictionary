package ch.chnoch.android.sms2dictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import android.net.Uri;
import android.opengl.Visibility;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.UserDictionary;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.backup.BackupManager;
import android.app.backup.RestoreObserver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SMS2Dictionary extends Activity {

	private final static String TAG = "SMS2Dictionary";

	public static final String SHARED_PREFS_NAME = "sms2dictionary_preferences";
	private static final String  DEVICE_PREFS_NAME = "device_preferences";

	
	private BackupManager mBackupManager;
	private Set<String> mKeywords, mLocalKeywords;
	private Handler mHandler = new Handler();
	private ProgressBar mProgressBar;
	private TextView mInfoText;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start);
		
		mBackupManager = new BackupManager(getBaseContext());

		Button button = (Button) findViewById(R.id.buttonAction);
		mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
		mInfoText = (TextView) findViewById(R.id.textViewInfo);
		

		loadPreferences();
		
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				loadPreferences();
				// Start lengthy operation in a background thread
				new Thread(new Runnable() {
					public void run() {
						// Update the progress bar
						mHandler.post(new Runnable() {
							public void run() {
								mProgressBar.setVisibility(View.VISIBLE);
								mInfoText.setVisibility(View.VISIBLE);
							}
						});
						
						if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)){
							  // THIS PHONE HAS SMS FUNCTIONALITY
								importSentSMSIntoDictionary();
							} else {
							  // NO SMS HERE :(
								importNewWordsFromBackup();
							}
						
						mHandler.post(new Runnable() {
							public void run() {
								mProgressBar.setVisibility(View.GONE);
							}
						});
					}
				}).start();
			}
		});

	}

	@Override
	protected void onStop() {
		super.onStop();

		savePreferences();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_start, menu);
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private void storeWordsInDictionary(Set<String> wordSet) {
		for (String word : wordSet) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				UserDictionary.Words.addWord(this, word, 1, "", null);
			} else {
				UserDictionary.Words.addWord(this, word, 1,
						UserDictionary.Words.LOCALE_TYPE_ALL);
				
			}
		}
	}
	
	private void importNewWordsFromBackup() {
		setInfoText("Importing new words from Backup");
		Set<String> newWords = new HashSet<String>();
		for (String word : mKeywords) {
			if (!mLocalKeywords.contains(word)) {
				newWords.add(word);
			}
		}
		
		setInfoText("Storing words into the dictionary");
		storeWordsInDictionary(newWords);
		setInfoText("Saving preferences and backing up new data to the cloud");
		
		savePreferences();
		setInfoText("Update completed. Added " + newWords.size()
				+ " new words to the dictionary");
	}

	private void importSentSMSIntoDictionary() {

		setInfoText("Parsing text messages");
		Set<String> smsList = getOutboxSms();
		setInfoText("Storing words into the dictionary");
		storeWordsInDictionary(smsList);
		setInfoText("Saving preferences and backing up new data to the cloud");
		savePreferences();
		setInfoText("Update completed. Added " + smsList.size()
				+ " new words to the dictionary");
	}

	private Set<String> getOutboxSms() {
		if (null == getBaseContext()) {
			return new HashSet<String>();
		}

		Uri uriSms = Uri.parse("content://sms/sent");
		Cursor cursor = getBaseContext().getContentResolver().query(uriSms,
				null, null, null, null);
		List<String> outboxSms = cursor2SmsArray(cursor);

		Set<String> newValues = new HashSet<String>();
		for (String sms : outboxSms) {
			for (String word : sms.split(" ")) {
				word = word.replaceAll("[^a-zA-Zäöüéèà]+", "");
				if (!mKeywords.contains(word) && word.length() > 0) {
					mKeywords.add(word);
					newValues.add(word);
				}
			}
		}

		if (!cursor.isClosed()) {
			cursor.close();
		}

		return newValues;
	}

	public List<String> cursor2SmsArray(Cursor cursor) {
		if (null == cursor || 0 == cursor.getCount()) {
			return new ArrayList<String>();
		}

		List<String> messages = new ArrayList<String>();

		try {
			for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor
					.moveToNext()) {
				String smsBody = cursor.getString(cursor
						.getColumnIndexOrThrow("body"));

				messages.add(smsBody);
			}

		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		} finally {
			cursor.close();
		}

		return messages;
	}

	private void setInfoText(final String text) {
		mHandler.post(new Runnable() {
			public void run() {
				mInfoText.setText(text);
			}
		});
	}

	private void loadPreferences() {
		SharedPreferences sharedSettings = getSharedPreferences(SHARED_PREFS_NAME, 0);
		Set<String> tempKeys = sharedSettings.getStringSet("keywords",
				new HashSet<String>());
		
		mKeywords = new HashSet<String>();
		for (String value : tempKeys) {
			mKeywords.add(value);
		}
		
		SharedPreferences privateSettings = getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE);
		tempKeys = privateSettings.getStringSet("keywords", new HashSet<String>());
		
		mLocalKeywords= new HashSet<String>();
		for (String value : tempKeys) {
			mLocalKeywords.add(value);
		}
	}
	
	private void savePreferences() {
		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences sharedSettings = getSharedPreferences(SHARED_PREFS_NAME, 0);
		SharedPreferences.Editor editor = sharedSettings.edit();
		editor.putStringSet("keywords", mKeywords);
		// Commit the edits!
		editor.commit();
		
		for (String word : mKeywords) {
			mLocalKeywords.add(word);
		}
		
		SharedPreferences privateSettings = getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE);
		editor = privateSettings.edit();
		editor.putStringSet("keywords", mLocalKeywords);
		editor.commit();

		// Backup the data to the cloud
		mBackupManager.dataChanged();
	}

}
