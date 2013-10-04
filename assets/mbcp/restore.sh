#!/system/bin/sh
export PATH=/system/bin:$PATH

PWD=$1
if [ ! -d $PWD ]; then
	echo Not valid $PWD!
	exit 1
fi

BUSYBOX=$PWD/busybox

BRACKET="$BUSYBOX ["
CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MOUNT="$BUSYBOX mount"
MV="$BUSYBOX mv"
RM="$BUSYBOX rm"

echo Mounting /system writable...
$MOUNT -o remount,rw /system

if $BRACKET -f /system/bin/app_process.bak ]; then
	echo Restoring backup from /system/bin/app_process.bak...
	$MV /system/bin/app_process.bak /system/bin/app_process || exit 1
	$CHMOD 755 /system/bin/app_process || exit 1
	$CHOWN root:shell /system/bin/app_process || exit 1
else
    echo No backup found at /system/bin/app_process.bak
fi

if $BRACKET -f /system/bin/dexopt.bak ]; then
	echo Restoring backup from /system/bin/dexopt.bak...
	$MV /system/bin/dexopt.bak /system/bin/dexopt || exit 1
	$CHMOD 755 /system/bin/dexopt || exit 1
	$CHOWN root:shell /system/bin/dexopt || exit 1
else
    echo No backup found at /system/bin/dexopt.bak
fi

echo
echo Done! Changes will become active on reboot.
exit 0
