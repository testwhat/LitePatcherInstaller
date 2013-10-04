package com.lite.patcher;

import java.util.ArrayList;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.lite.patcher.MbcpUtil.PatchLineInfo;

public class MbcpChangeReceiver extends BroadcastReceiver {
	static final int NOTIFY_NEW_PATCH = 81000;
	
	@Override
	public void onReceive(final Context context, final Intent intent) {
		Intent sIntent = new Intent("com.lite.patcher.PACKAGE_CHANGE");
		sIntent.putExtra("intent", intent);
		context.startService(sIntent);
	}
	
	public static class PatchChangeService extends IntentService {

		public PatchChangeService() {
			super(PatchChangeService.class.getSimpleName());
		}

		@Override
		protected void onHandleIntent(Intent intent) {
			intent = intent.getParcelableExtra("intent");
			Uri uri = intent.getData();
			String packageName = (uri != null) ? uri.getSchemeSpecificPart() : null;
			if (packageName == null) {
				return;
			}

			boolean isRemoved = intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED);
			boolean isRepacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
			if (isRemoved && !isRepacing) {
				// Remove the package
				ArrayList<PatchLineInfo> allPatches = MbcpUtil.getAllPatchStatus();
				MbcpUtil.PatchLineInfo remove = null;
				for (int i = allPatches.size() - 1; i >= 0; i--) {
					String patch = allPatches.get(i).patch;
					int dashPos = patch.indexOf(packageName) + packageName.length();
					if (patch.charAt(dashPos) == '-') {
						remove = allPatches.remove(i);
						Log.i(LitePatcherActivity.TAG, "Remove " + remove.patch);
						break;
					}
				}
				if (remove != null) {
					MbcpUtil.updatePatchList(allPatches);
				}
				return;
			}

			String appName;
			ApplicationInfo app;
			try {
				PackageManager pm = getPackageManager();
				app = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
				if (app.metaData == null || !app.metaData.containsKey(MbcpUtil.META_TARGET_JAR_PATH)) {
					return;
				}
				appName = pm.getApplicationLabel(app).toString();
			} catch (NameNotFoundException e) {
				Log.w(LitePatcherActivity.TAG, "Package not found", e);
				return;
			}

			boolean replaced = false;
			ArrayList<PatchLineInfo> all = MbcpUtil.getAllPatchStatus();
			for (int i = 0; i < all.size(); i++) {
				PatchLineInfo pi = all.get(i);
				String newP = app.sourceDir.substring(0, app.sourceDir.length() - 6);
				String oriP = pi.patch.substring(0, pi.patch.length() - 6);
				if (newP.equals(oriP)) {
					// Replace existed
					replaced = true;
					all.set(i, new PatchLineInfo(app.sourceDir, 
							app.metaData.getString(MbcpUtil.META_TARGET_JAR_PATH),
							pi.enable));
					MbcpUtil.updatePatchList(all);
					Toast.makeText(this, getString(R.string.app_name) + ": "
							+ app.packageName + " has updated", Toast.LENGTH_SHORT).show();
					break;
				}
			}
			if (!replaced) {
				// Install new
				MbcpUtil.appendNewPatch(app);
			}
			showNotification(this, packageName, appName, replaced ? getString(R.string.notify_patch_updated)
					: getString(R.string.notify_patch_added));
		}
	}

	private static void showNotification(Context context, String packageName, String appName, String msg) {
		Intent startInstaller = new Intent(context, LitePatcherActivity.class);
		startInstaller.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		Notification notification = new Notification.Builder(context)
			.setContentTitle(msg).setTicker(msg)
			.setContentText(appName).setAutoCancel(true)
			.setContentIntent(PendingIntent.getActivity(context, 0, startInstaller, PendingIntent.FLAG_UPDATE_CURRENT))
			.setSmallIcon(android.R.drawable.ic_dialog_info).build();

		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(packageName, NOTIFY_NEW_PATCH, notification);
	}
	
}
