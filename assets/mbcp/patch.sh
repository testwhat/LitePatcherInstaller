#!/system/bin/sh
export PATH=/system/bin:$PATH

PWD=$1
if [ ! -d $PWD ]; then
	echo Not valid $PWD!
	exit 1
fi

BUSYBOX=$PWD/busybox

BRACKET="$BUSYBOX ["
CP="$BUSYBOX cp"
CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MKDIR="$BUSYBOX mkdir"
MOUNT="$BUSYBOX mount"
RM="$BUSYBOX rm"
TOUCH="$BUSYBOX touch"

MUSER=$2
if $BRACKET -z "$MUSER" ]; then
	echo This script needs the user id!
	exit 1
fi

AppProcess=$PWD/app_process2
DexOpt=$PWD/dexopt2

if $BRACKET ! -f $AppProcess -o ! -f $DexOpt ]; then
	echo Files for update not found!
	pwd
	exit 1
fi

echo Mounting /system writable...
$MOUNT -o remount,rw /system

if $BRACKET -f /system/bin/app_process.bak ]; then
	echo Backup of app_process executable exists already at /system/bin/app_process.bak
else
    $CP -a /system/bin/app_process /system/bin/app_process.bak || exit 1
    echo Created backup of app_process executable at /system/bin/app_process.bak
fi

if $BRACKET -f /system/bin/dexopt.bak ]; then
	echo Backup of app_process executable exists already at /system/bin/dexopt.bak
else
    $CP -a /system/bin/dexopt /system/bin/dexopt.bak || exit 1
    echo Created backup of dexopt executable at /system/bin/dexopt.bak
fi

echo Copying app_process...
$CP $AppProcess /system/bin/app_process || exit 1
$CHMOD 755 /system/bin/app_process || exit 1
$CHOWN root:shell /system/bin/app_process || exit 1

echo Copying dexopt...
$CP $DexOpt /system/bin/dexopt || exit 1
$CHMOD 755 /system/bin/dexopt || exit 1
$CHOWN root:shell /system/bin/dexopt || exit 1

echo Touching config...
$TOUCH /data/mbcp.txt /data/mbcp_nodexdep || exit 1
$CHMOD 644 /data/mbcp.txt /data/mbcp_nodexdep || exit 1
$CHOWN $MUSER:shell /data/mbcp.txt /data/mbcp_nodexdep || exit 1

echo
echo Done! Changes will become active on reboot.
exit 0
