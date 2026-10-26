#!/usr/bin/env bash
set -x
OUT=ui-out
mkdir -p $OUT
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb shell wm density 320
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb install -r -g widgets/build/outputs/apk/release/widgets-release.apk
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }
find() { python3 .github/ui/find.py "$OUT/$1.xml" "$2" "${3:-exact}"; }

adb shell am start -n com.droidkay.halowidgets/.MainActivity
sleep 15
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -n com.droidkay.halowidgets/.MainActivity
sleep 3
shot 01-app-dark
for i in 1 2 3; do adb shell input swipe 800 2000 800 700 400; sleep 1.5; shot 0$((i+1))-app-dark-scroll; done

# Light and Glass styles
adb shell am force-stop com.droidkay.halowidgets
adb shell am start -n com.droidkay.halowidgets/.MainActivity; sleep 3
dump 10
if P=$(find 10 "Light"); then read cx cy rest <<< "$P"; adb shell input tap $cx $cy; sleep 2; shot 10-light; adb shell input swipe 800 2000 800 700 400; sleep 1.5; shot 11-light-scroll; fi
adb shell input swipe 800 700 800 2000 400; sleep 1
dump 12
if P=$(find 12 "Glass"); then read cx cy rest <<< "$P"; adb shell input tap $cx $cy; sleep 2; shot 12-glass; adb shell input swipe 800 2000 800 700 400; sleep 1.5; shot 13-glass-scroll; fi
adb shell input swipe 800 700 800 2000 400; sleep 1
dump 14
if P=$(find 14 "Dark"); then read cx cy rest <<< "$P"; adb shell input tap $cx $cy; sleep 1; fi

# Pin a few real widgets to the launcher via "Add"
for n in 1 2 3; do
  dump 20
  python3 - "$OUT/20.xml" $n > /tmp/add.txt <<'PY'
import re,sys
data=open(sys.argv[1],encoding='utf-8',errors='ignore').read(); n=int(sys.argv[2])
hits=[]
for m in re.finditer(r'<node [^>]*>', data):
    s=m.group(0)
    if ' text="Add"' in s:
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if b:
            x1,y1,x2,y2=map(int,b.groups()); hits.append(((x1+x2)//2,(y1+y2)//2))
if len(hits)>=n: print(*hits[n-1])
PY
  if [ -s /tmp/add.txt ]; then
    read ax ay < /tmp/add.txt
    adb shell input tap $ax $ay; sleep 2
    dump 21
    shot 2$n-pin-dialog
    if P=$(find 21 "Add to home screen" contains) || P=$(find 21 "ADD AUTOMATICALLY" contains) || P=$(find 21 "Add automatically" contains); then
      read cx cy rest <<< "$P"; adb shell input tap $cx $cy; sleep 2
    fi
  fi
  adb shell am start -n com.droidkay.halowidgets/.MainActivity; sleep 2
done
adb shell input keyevent KEYCODE_HOME; sleep 4
shot 30-home-with-widgets
adb shell input swipe 1500 1300 200 1300 300; sleep 2
shot 31-home-page2
adb shell settings put system user_rotation 1
sleep 5; shot 32-home-landscape
adb logcat -d | grep -iE "halowidgets|FATAL|AndroidRuntime|RemoteViews" | tail -200 > $OUT/99-log.txt
exit 0
