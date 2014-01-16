package com.lite.patcher;

import java.util.ArrayList;

import libcore.io.Libcore;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.lite.patcher.MbcpUtil.FPatch;

public class LitePatcherActivity extends Activity {
	public static final String TAG = "LPatch";

	Context mContext;
	ConfirmDailog mConfirmDialog;
	InfoDailog mInfoDialog;
	LoadingDialog mLoadingDialog;
	ListView mPatchList;
	java.util.Set<String> mEnabledPatches;
	PatchesAdapter mPatchesAdapter;
	BroadcastReceiver mPackageChangeReceiver;
	boolean mIsInited;
	boolean mIsEnvReady;
	boolean mIsResumed;
	boolean mIsPendingRefresh;
	boolean mForceReload;

	Runnable mRefreshStatus = new Runnable() {
		@Override
		public void run() {
			mIsEnvReady = MbcpUtil.isEnvReady(mContext);
			initStatus();
			initButton();
		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		mContext = this;
		mConfirmDialog = new ConfirmDailog();
		mInfoDialog = new InfoDailog();
		mLoadingDialog = new LoadingDialog();
		registerPackageChangeReceiver();

		runTask(new Runnable() {
			@Override
			public void run() {
				ScriptUtil.initSuMode();
				mIsEnvReady = MbcpUtil.isEnvReady(mContext);
				MbcpUtil.testSu();
			}
		}, new Runnable() {
			@Override
			public void run() {
				initStatus();
				initButton();
				initListView();
				mIsInited = true;
			}
		});
	}

	private void initStatus() {
		TextView tv = (TextView) findViewById(R.id.status);
		tv.setText(MbcpUtil.sEnvStatus);

		CheckBox cb = (CheckBox) findViewById(R.id.cb_no_dex_dep);
		cb.setEnabled(mIsEnvReady);
		if (mIsEnvReady) {
			cb.setChecked(MbcpUtil.isDexDepDisable());
			if (!mIsInited) {
				cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton b, boolean checked) {
						MbcpUtil.setDexDepDisable(checked);
					}
				});
			}
		}
	}

	private void initButton() {
		Button b = (Button) findViewById(R.id.btn_patch);
		Button restoreBtn = (Button) findViewById(R.id.btn_restore);
		if (MbcpUtil.sEnvOk) {
			b.setEnabled(false);
			restoreBtn.setEnabled(true);
			b.setText(getString(R.string.btn_patched));
		} else {
			b.setEnabled(MbcpUtil.sSuOk);
			restoreBtn.setEnabled(false);
			b.setText(getString(R.string.btn_patch));
		}
		if (mIsInited) {
			return;
		}
		b.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mConfirmDialog.show(getFragmentManager(), getString(R.string.confirm_patch), new Runnable() {
					@Override
					public void run() {
						runTask(new Runnable() {
							@Override
							public void run() {
								showToast(MbcpUtil.patch(mContext), true);
								//showToast(ScriptUtil.suCmd("ls"), true);
							}
						}, mRefreshStatus);
					}
				});
			}
		});

		restoreBtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mConfirmDialog.show(getFragmentManager(), getString(R.string.confirm_restore), new Runnable() {
					@Override
					public void run() {
						runTask(new Runnable() {
							@Override
							public void run() {
								showToast(MbcpUtil.restore(mContext), true);
							}
						}, mRefreshStatus);
					}
				});
			}
		});

		b = (Button) findViewById(R.id.btn_restart_system);
		b.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mConfirmDialog.show(getFragmentManager(), getString(R.string.confirm_restart), new Runnable() {
					@Override
					public void run() {
						ScriptUtil.softReboot(mContext);
					}
				});
			}
		});

		b = (Button) findViewById(R.id.btn_reboot);
		b.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mConfirmDialog.show(getFragmentManager(), getString(R.string.confirm_reboot), new Runnable() {
					@Override
					public void run() {
						ScriptUtil.reboot(mContext);
					}
				});
			}
		});
	}

	private void initListView() {
		mPatchesAdapter = new PatchesAdapter(mContext);
		mPatchList = (ListView) findViewById(R.id.patch_list);

		TextView emptyView = new TextView(mContext);
		LinearLayout.LayoutParams lparam = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT);
		lparam.weight = 1;
		emptyView.setLayoutParams(lparam);
		emptyView.setText(getString(R.string.empty));
		emptyView.setVisibility(View.GONE);
		((ViewGroup) mPatchList.getParent()).addView(emptyView, 2);
		mPatchList.setEmptyView(emptyView);

		mPatchList.setFastScrollEnabled(true);
		mPatchList.setDivider(new ColorDrawable(0xff777777));
		mPatchList.setDividerHeight(1);
		mPatchList.setAdapter(mPatchesAdapter);
		mPatchList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				FPatch p = mPatchesAdapter.getItem(position);
				mInfoDialog.show(getFragmentManager(), p.packageName, "apkPath=" + p.apkPath + "\ntargetJar=" + p.targetJar);
			}
		});
		
		mPatchList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				FPatch p = mPatchesAdapter.getItem(position);
				Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
	            intent.setData(android.net.Uri.fromParts("package", p.packageName, null));
	            startActivity(intent);
				return true;
			}
		});

		refreshList();

//		mPatchesAdapter.sort(new java.util.Comparator<FPatch>() {
//			@Override
//			public int compare(FPatch lhs, FPatch rhs) {
//				return lhs.appName.compareTo(rhs.appName);
//			}
//		});
	}

	private void refreshList() {
		mPatchesAdapter.clear();
		final ArrayList<FPatch> patches = new ArrayList<FPatch>();
		runTask(new Runnable() {
			@Override
			public void run() {
				mEnabledPatches = java.util.Collections.synchronizedSet(MbcpUtil.getActivePatchPaths());
				PackageManager pm = mContext.getPackageManager();
				for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_META_DATA)) {
					ApplicationInfo app = pkg.applicationInfo;
					if (app.metaData == null || !app.metaData.containsKey(MbcpUtil.META_TARGET_JAR_PATH)) {
						continue;
					}
					String description = app.metaData.getString(MbcpUtil.META_DESCRIPTION, "");
					if (description.length() == 0) {
						try {
							int resId = app.metaData.getInt(MbcpUtil.META_DESCRIPTION, 0);
							if (resId != 0) {
								description = pm.getResourcesForApplication(app).getString(resId);
							}
						} catch (Exception e) {}
					}
					FPatch p = new FPatch(pkg.packageName, pkg.applicationInfo.sourceDir,
							pkg.versionName, pm.getApplicationLabel(app).toString(),
							pm.getApplicationIcon(app), description, app.metaData.getString(MbcpUtil.META_TARGET_JAR_PATH));
					if (mEnabledPatches.contains(pkg.applicationInfo.sourceDir)) {
						p.enable = true;
					}
					patches.add(p);
				}
				if (mForceReload) {
					MbcpUtil.deleteMbcpFile();
					mForceReload = false;
				}
				if (!patches.isEmpty()) {
					MbcpUtil.forceReLoadPatchListFile(patches);
				}
			}
		}, new Runnable() {
			@Override
			public void run() {
				mPatchesAdapter.addAll(patches);
			}
		});
	}

	void registerPackageChangeReceiver() {
		if (mPackageChangeReceiver != null) {
			return;
		}
		IntentFilter iFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		iFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
		iFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		iFilter.addDataScheme("package");
		registerReceiver(mPackageChangeReceiver = new BroadcastReceiver() {
			long mLastReFreshTime;
			@Override
			public void onReceive(Context context, Intent intent) {
				if (mIsResumed) {
					long now = System.currentTimeMillis();
					if (mLastReFreshTime + 3000 < now) {
						refreshList();
					}
					mLastReFreshTime = now;
				} else {
					mIsPendingRefresh = true;
				}
				
			}
		}, iFilter);
	}

	void unregisterPackageChangeReceiver() {
		if (mPackageChangeReceiver == null) {
			return;
		}
		unregisterReceiver(mPackageChangeReceiver);
		mPackageChangeReceiver = null;
	}

	void showToast(final String msg) {
		showToast(msg, false);
	}

	void showToast(final String msg, final boolean longDuration) {
		final int d = longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
		if (Thread.currentThread() != android.os.Looper.getMainLooper().getThread()) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(mContext, msg, d).show();
				}
			});
		} else {
			Toast.makeText(mContext, msg, d).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mIsResumed = true;
		if (mIsPendingRefresh) {
			refreshList();
			mIsPendingRefresh = false;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mIsResumed = false;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterPackageChangeReceiver();
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 0, 0, getString(R.string.mi_usage));
		menu.add(0, 1, 0, getString(R.string.mi_show_bcp));
		menu.add(0, 2, 0, getString(R.string.mi_show_mbcp_config));
		menu.add(0, 3, 0, getString(R.string.mi_refresh_list));
		menu.add(0, 4, 0, "Version 0.1");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == 0) {
			mInfoDialog.show(getFragmentManager(), "Usage", "AndroidManifest example:\n"
					+ "<application>\n"
					+ "  <meta-data android:name=\"mbcpDescription\" android:value=\"My patch\" />\n"
					+ "  <meta-data android:name=\"mbcpTargetJarPath\" android:value=\"/system/framework/services.jar\" />\n"
					+ "</application>");
		} else if (item.getItemId() == 1) {
			mInfoDialog.show(getFragmentManager(), "BOOTCLASSPATH", Libcore.os.getenv("BOOTCLASSPATH"));
		} else if (item.getItemId() == 2) {
			mInfoDialog.show(getFragmentManager(), "MBCP Config", MbcpUtil.getMbcpFileContent());
		} else if (item.getItemId() == 3) {
			mForceReload = true;
			refreshList();
		}
		return super.onOptionsItemSelected(item);
	}

	static class ListViewItemHolder {
		CheckBox checkbox;
		ImageView icon;
		TextView description;
	}

	private class PatchesAdapter extends ArrayAdapter<FPatch> {

		public PatchesAdapter(Context context) {
			super(context, R.layout.list_item_patch, R.id.text);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = super.getView(position, convertView, parent);

			if (convertView == null) {
				ListViewItemHolder vh = new ListViewItemHolder();
				vh.checkbox = (CheckBox) view.findViewById(R.id.checkbox);
				vh.icon = (ImageView) view.findViewById(R.id.icon);
				vh.description = (TextView) view.findViewById(R.id.description);
				view.setTag(vh);

				vh.checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						FPatch patch = (FPatch) buttonView.getTag();
						boolean changed = mEnabledPatches.contains(patch.apkPath) ^ isChecked;
						if (changed) {
							if (isChecked) {
								mEnabledPatches.add(patch.apkPath);
							} else {
								mEnabledPatches.remove(patch.apkPath);
							}
							MbcpUtil.updatePatchListByEnable(mEnabledPatches, patch, isChecked);
						}
					}
				});
			}

			ListViewItemHolder vh = (ListViewItemHolder) view.getTag();
			FPatch item = getItem(position);
			vh.checkbox.setTag(item);
			vh.icon.setImageDrawable(item.icon);

			if (item.description.length() > 0) {
				vh.description.setText(item.description);
				vh.description.setTextColor(0xffaaccaa);
			} else {
				vh.description.setText(item.packageName);
				vh.description.setTextColor(0xffbb6622);
			}
			vh.checkbox.setChecked(mEnabledPatches.contains(item.apkPath));

			return view;
		}
	}

	void runTask(final Runnable task, final Runnable onFinish) {
		new AsyncTask<Void, Integer, Void>() {
			@Override
			protected void onPreExecute() {
				mLoadingDialog.show(getFragmentManager());
			}
			
			@Override
			protected Void doInBackground(Void... params) {
				task.run();
				return null;
			}
			
			@Override
			protected void onPostExecute(Void result) {
				if (onFinish != null) {
					onFinish.run();
				}
				mLoadingDialog.dismissAllowingStateLoss();
			}
		
		}.execute();
	}

	public static class ConfirmDailog extends DialogFragment {
		AlertDialog mAlertDialog;
		Runnable mAction;
		String mMessage;

		public ConfirmDailog() {
		}

		public void show(FragmentManager fm, String message, Runnable action) {
			if (mAlertDialog != null) {
				mAlertDialog.setMessage(message);
			}
			mMessage = message;
			mAction = action;
			show(fm, "ConfirmDailog");
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mAlertDialog = new AlertDialog.Builder(getActivity())
		    .setTitle(getString(R.string.confirm_title)).setMessage(mMessage)
		    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		        	mAction.run();
		        }
		    }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {
		        }
		    }).create();
		}
	}

	public static class InfoDailog extends DialogFragment {
		AlertDialog mAlertDialog;
		Runnable mAction;
		String mMessage;
		String mTitle;

		public InfoDailog() {
		}

		public void show(FragmentManager fm, String title, String message) {
			if (mAlertDialog != null) {
				mAlertDialog.setTitle(title);
				mAlertDialog.setMessage(message);
			}
			mTitle = title;
			mMessage = message;
			show(fm, "InfoDailog");
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mAlertDialog = new AlertDialog.Builder(getActivity()).setTitle(mTitle).setMessage(mMessage).create();
		}
	}

	public static class LoadingDialog extends DialogFragment {
		public LoadingDialog() {
		}

		public synchronized void show(FragmentManager fm) {
			if (isAdded()) {
				return;
			}
			show(fm, "LoadingDialog");
		}

		@Override
		public synchronized Dialog onCreateDialog(Bundle savedInstanceState) {
			ProgressDialog pd = new ProgressDialog(getActivity());
			pd.setCanceledOnTouchOutside(false);
			String loading = getString(R.string.loading);
			pd.setTitle(loading);
			pd.setMessage(loading + "...........");
			return pd;
		}
	}
}
