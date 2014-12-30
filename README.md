LitePatcherInstaller
====================

Only support for dalvik runtime

Allow java file level override to boot class path.

e.g.
Create an application
with AndroidManifest.xml:

<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
	package="an.override.kk443"
	android:versionCode="2" android:versionName="2.0">
	<uses-sdk android:minSdkVersion="17" />

	<application>
		<meta-data android:name="mbcpDescription" android:value="Patch framework" />
		<meta-data android:name="mbcpTargetJarPath" android:value="/system/framework/framework.jar" />
	</application>

</manifest>

And source contain somthing like
src/android/app/Activity.java
src/com/android/server/power/PowerManagerService.java

Install the apk, enable it in LitePatcherInstaller.
Reboot then the device is running framework with the 2 classes in the apk.
