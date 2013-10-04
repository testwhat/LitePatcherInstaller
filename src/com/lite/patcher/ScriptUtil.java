package com.lite.patcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.content.Context;
import android.util.Log;

public class ScriptUtil {
	public static String execAsset(Context context, String fileName) {
		File file = new File(context.getCacheDir(), fileName);
		if (!file.exists()) {
			file = writeAssetToCacheFile(context, fileName);
			if (file == null) {
				return "Could not find asset " + fileName;
			}
		}
		file.setReadable(true, false);
		file.setExecutable(true, false);
		return suCmd(file.getAbsolutePath());
	}

	public static String softReboot(Context context) {
		return execAsset(context, "soft_reboot.sh");
	}

	public static String reboot(Context context) {
		return execAsset(context, "reboot");
	}

	public static String executeScript(Context context, String name) {
		Log.i(LitePatcherActivity.TAG, "executeScript " + name);
		File scriptFile = writeAssetToCacheFile(context, name);
		if (scriptFile == null) {
			return "Could not find asset \"" + name + "\"";
		}
		File busybox = writeAssetToCacheFile(context, "busybox");
		if (busybox == null) {
			scriptFile.delete();
			return "Could not find asset \"busybox\"";
		}
		scriptFile.setReadable(true, false);
		scriptFile.setExecutable(true, false);

		busybox.setReadable(true, false);
		busybox.setExecutable(true, false);

		try {
			return suCmd(scriptFile.getAbsolutePath() + " " + scriptFile.getParent() + " " + android.os.Process.myUid() + " 2>&1");
		} finally {
			scriptFile.delete();
			busybox.delete();
		}
	}

	static String su = new File("/system/xbin/su").exists() ? "/system/xbin/su" : "su";
	static int SU_MODE = 0;

	public static synchronized String suCmd(String cmd) {
		return SU_MODE == 0 ? cmd(su, cmd) : cmd("sh", su + " -c " + cmd);
	}

	public static String cmd(String exec, String cmd) {
		StringBuilder sb = new StringBuilder(256);
		try {
			ProcessBuilder pb = new ProcessBuilder(exec);
			java.lang.Process process = pb.start();
			InputStream in = process.getInputStream();
			PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())), true);
			out.println(cmd);
			out.println("exit");

			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line).append("\n");
			}

			br.close();
		} catch (Exception e) {
			e.printStackTrace();
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			return sw.toString();
		}
		return sb.toString();
	}

	public static File writeAssetToCacheFile(Context context, String name) {
		String dest = name;
		int fs = dest.indexOf('/');
		if (fs > 0) {
			dest = name.substring(fs);
		}
		return writeAssetToCacheFile(context, name, dest);
	}

	public static File writeAssetToCacheFile(Context context, String assetName, String fileName) {
		File file = null;
		try {
			file = new File(context.getCacheDir(), fileName);
			if (file.exists()) {
				return file;
			}
			InputStream in = context.getAssets().open(assetName);
			FileOutputStream out = new FileOutputStream(file);
			
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) > 0){
				out.write(buffer, 0, len);
			}
			in.close();
			out.close();
			
			return file;
		} catch (IOException e) {
			e.printStackTrace();
			if (file != null) {
				file.delete();
			}
			return null;
		}
	}
}
