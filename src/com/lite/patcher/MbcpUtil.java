package com.lite.patcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemProperties;
import android.util.Log;

public class MbcpUtil {
	// <meta-data android:name="mbcpTargetJarPath" android:value="/system/framework/services.jar" />
	public static final String META_TARGET_JAR_PATH = "mbcpTargetJarPath";
	// <meta-data android:name="mbcpDescription" android:value="test" />
	public static final String META_DESCRIPTION = "mbcpDescription";
	public static final String MBCP_PATH = "/data/mbcp.txt";
	public static final File MBCP_PATH_FILE = new File(MBCP_PATH);
	public static final String MBCP_PROPERTY_NAME = "app.mbcp.enable";
	public static final String MBCP_DISABLE_DEX_DEP = "/data/mbcp_nodexdep";
	public static final String MBCP_DISABLE_APPLY = "/data/mbcp_disable";
	static final String MBCP_APP_PROCESS = "app_process2";
	static final String MBCP_DEXOPT = "dexopt2";
	static final String SCRIPT_BASE = "mbcp/";
	public static String sEnvStatus;
	public static boolean sEnvOk;
	public static boolean sSuOk;

	public static String patch(Context context) {
		String suffix = Build.VERSION.SDK_INT < 17 ? "_sdk16" : "";
		File appProcessFile = ScriptUtil.writeAssetToCacheFile(context,
				SCRIPT_BASE + MBCP_APP_PROCESS + suffix, MBCP_APP_PROCESS);
		if (appProcessFile == null) {
			return "Cannot find asset " + MBCP_APP_PROCESS;
		}
		File dexopt = ScriptUtil.writeAssetToCacheFile(context,
				SCRIPT_BASE + MBCP_DEXOPT + suffix, MBCP_DEXOPT);
		if (dexopt == null) {
			return "Cannot find asset " + MBCP_DEXOPT;
		}
		String result = ScriptUtil.executeScript(context, SCRIPT_BASE + "patch.sh");

		appProcessFile.delete();
		dexopt.delete();

		return result;
	}

	public static String restore(Context context) {
		return ScriptUtil.executeScript(context, SCRIPT_BASE + "restore.sh");
	}

	public synchronized static void testSu() {
		if (ScriptUtil.suCmd("ls").length() < 100) {
			sEnvStatus += "\nError, Cannot use su command";
			return;
		}
		sSuOk = true;
	}

	public synchronized static boolean isEnvReady(Context context) {
		final String keyword = "MBCP";
		String r = ScriptUtil.suCmd("app_process --help");
		boolean aOk = false, dOk = false;
		if (r != null && r.indexOf(keyword) >= 0) {
			sEnvStatus = "app_process OK";
			aOk = true;
		} else {
			sEnvStatus = "Patched app_process not installed";
		}
		r = ScriptUtil.suCmd("dexopt --help");
		if (r != null && r.indexOf(keyword) >= 0) {
			sEnvStatus += "\ndexopt OK";
			dOk = true;
		} else {
			sEnvStatus += "\nPatched dexopt not installed";
		}
		sEnvOk = aOk && dOk;
		if (!sEnvOk) {
			int secure = SystemProperties.getInt("ro.secure", 1);
			if (secure != 0) {
				sEnvStatus += "\nWarning, ro.secure=0, this tool should not work";
			}
		}
		return sEnvOk;
	}

	public static class PatchLineInfo {
		public final String patch;
		public final String target;
		public final boolean enable;

		public PatchLineInfo(String patch, String target, boolean enable) {
			this.patch = patch;
			this.target = target;
			this.enable = enable;
		}

		public PatchLineInfo(String line) {
			enable = line.charAt(0) != '#';
			int sep = line.indexOf(':');
			patch = line.substring(enable ? 0 : 1, sep);
			target = line.substring(sep + 1);
		}
	}

	public static class FPatch {
		public final String packageName;
		public final String apkPath;
		public final String moduleVersion;
		public final String appName;
		public final Drawable icon;
		public final String description;
		public final String targetJar;

		public FPatch(String packageName, String sourceDir, String moduleVersion, String appName, Drawable icon, String description,
				String targetJar) {
			this.packageName = packageName;
			this.apkPath = sourceDir;
			this.moduleVersion = moduleVersion;
			this.appName = appName;
			this.icon = icon;
			this.description = description.trim();
			this.targetJar = targetJar;
		}

		@Override
		public String toString() {
			return String.format("%s [%s]", appName, moduleVersion);
		}
	}

	public static void appendNewPatch(ApplicationInfo app) {
		try {
			PrintWriter patchLines = new PrintWriter(new FileWriter(MBCP_PATH_FILE, true));
			patchLines.print("#");
			patchLines.print(app.sourceDir);
			patchLines.print(":");
			patchLines.println(app.metaData.getString(META_TARGET_JAR_PATH));
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot write " + MBCP_PATH, e);
		}
	}

	public static void updatePatchList(ArrayList<PatchLineInfo> patches) {
		try {
			PrintWriter patchLines = new PrintWriter(MBCP_PATH);
			for (int i = 0; i < patches.size(); i++) {
				PatchLineInfo p = patches.get(i);
				if (!p.enable) {
					patchLines.print('#');					
				}
				patchLines.print(p.patch);
				patchLines.print(':');
				patchLines.println(p.target);
			}
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot write " + MBCP_PATH, e);
		}
	}

	public static void updatePatchListByEnable(HashSet<String> enableSet, FPatch patch, boolean enable) {
		ArrayList<PatchLineInfo> all = getAllPatchStatus();
		try {
			PrintWriter patchLines = new PrintWriter(MBCP_PATH);
			boolean found = false;
			for (int i = 0; i < all.size(); i++) {
				PatchLineInfo p = all.get(i);
				if (!found && p.patch.equals(patch.apkPath)) {
					found = true;
				}
				if (!enableSet.contains(p.patch)) {
					patchLines.print('#');
				}
				patchLines.print(p.patch);
				patchLines.print(':');
				patchLines.println(p.target);
			}
			if (!found) {
				if (!enable) {
					patchLines.print('#');
				}
				patchLines.print(patch.apkPath);
				patchLines.print(':');
				patchLines.println(patch.targetJar);
			}
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot write " + MBCP_PATH, e);
		}
	}

	public static ArrayList<PatchLineInfo> getAllPatchStatus() {
		ArrayList<PatchLineInfo> infos = new ArrayList<PatchLineInfo>();
		try {
			BufferedReader patchLines = new BufferedReader(new FileReader(MBCP_PATH));
			String line;
			while ((line = patchLines.readLine()) != null) {
				infos.add(new PatchLineInfo(line));
			}
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot read " + MBCP_PATH, e);
		}
		return infos;
	}

	public static HashSet<String> getActivePatchPaths() {
		HashSet<String> patches = new HashSet<String>();
		if (!isPatchListFileExist()) {
			Log.i(LitePatcherActivity.TAG, "Not found " + MBCP_PATH);
			return patches;
		}
		try {
			BufferedReader patchLines = new BufferedReader(new FileReader(MBCP_PATH));
			String line;
			while ((line = patchLines.readLine()) != null) {
				if (line.length() < 1 || line.charAt(0) == '#') {
					continue;
				}
				String patch = line.substring(0, line.indexOf(':'));
				patches.add(patch);
			}
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot read " + MBCP_PATH, e);
		}
		return patches;
	}

	public static boolean isPatchListFileExist() {
		return MBCP_PATH_FILE.exists();
	}

	public static void forceReLoadPatchListFile(ArrayList<FPatch> patches) {
		if (patches.isEmpty()) {
			return;
		}
		if (isPatchListFileExist()) {
			return;
		} else {
			Log.i(LitePatcherActivity.TAG, "Creating " + MBCP_PATH);
		}

		try {
			PrintWriter patchLines = new PrintWriter(MBCP_PATH);
			for (int i = 0; i < patches.size(); i++) {
				FPatch p = patches.get(i);
				patchLines.print('#');
				patchLines.print(p.apkPath);
				patchLines.print(':');
				patchLines.println(p.targetJar);
			}
			patchLines.close();
		} catch (IOException e) {
			Log.w(LitePatcherActivity.TAG, "Cannot write " + MBCP_PATH, e);
		}
	}

	public static boolean isPropertyOn() {
		return SystemProperties.getInt(MBCP_PROPERTY_NAME, 0) > 0;
	}

	public static boolean isDexDepDisable() {
		boolean disable = false;
		try {
			char data[] = new char[8];
			FileReader fr = new FileReader(MBCP_DISABLE_DEX_DEP);
			fr.read(data);
			String str = new String(data, 0, data.length);
			disable = str.length() > 0 && str.charAt(0) != '0';
			fr.close();
		} catch (Exception e) {
			Log.w(LitePatcherActivity.TAG, "Cannot read " + MBCP_DISABLE_DEX_DEP, e);
		}
		return disable;
	}
	
	public static void setDexDepDisable(boolean disable) {
		try {
			FileWriter fr = new FileWriter(MBCP_DISABLE_DEX_DEP);
			fr.write(disable ? "1" : "0");
			fr.close();
		} catch (Exception e) {
			Log.w(LitePatcherActivity.TAG, "Cannot write " + MBCP_DISABLE_DEX_DEP, e);
		}
	}
}
