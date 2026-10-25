#!/usr/bin/env bash
# Drives Halo on the emulator and saves screenshots + logs to ui-out/.
set -x
OUT=ui-out
mkdir -p $OUT
APK=app/build/outputs/apk/fold/release/app-fold-release.apk
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb install -r -g $APK
adb shell cmd package set-home-activity com.droidkay.halo/.MainActivity
adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }
find() { python3 .github/ui/find.py "$OUT/$1.xml" "$2" "${3:-exact}"; }
tap() { adb shell input tap "$1" "$2"; }
home() { adb shell input keyevent KEYCODE_HOME; }

# Long-press at (x,y), drag to (tx,ty) in steps, hold there for $5 s (screenshot), then optionally
# continue to (ux,uy) and release.
drag() {
  local x=$1 y=$2 tx=$3 ty=$4 hold=$5 name=$6 ux=${7:-$3} uy=${8:-$4}
  adb shell input motionevent DOWN $x $y
  sleep 1.2
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    adb shell input motionevent MOVE $((x + (tx - x) * i / 12)) $((y + (ty - y) * i / 12))
  done
  sleep 0.4
  shot "$name-a-holding"
  sleep "$hold"
  shot "$name-b-after-hold"
  for i in 1 2 3 4 5 6; do
    adb shell input motionevent MOVE $((tx + (ux - tx) * i / 6)) $((ty + (uy - ty) * i / 6))
  done
  sleep 0.6
  shot "$name-c-before-drop"
  adb shell input motionevent UP $ux $uy
  sleep 1.5
  shot "$name-d-dropped"
}

# ---------------------------------------------------------------- inner screen (portrait)
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm density 384
home; sleep 3; home; sleep 4
shot 01-inner-home
dump 01-inner-home
W=1812; H=2176

# Pick an app label on the grid and drag its icon to the right edge (next page / new page).
LBL=""
for name in Settings Chrome Clock Camera Contacts Files Calendar Maps Gmail Photos YouTube; do
  if P=$(find 01-inner-home "$name"); then LBL=$name; break; fi
done
echo "dragging $LBL at $P"
if [ -n "$LBL" ]; then
  read cx cy x1 y1 x2 y2 <<< "$P"
  IY=$((y1 - 60))
  drag $cx $IY $((W - 150)) $((H / 3)) 2.5 02-drag-to-edge $((W / 2)) $((H / 3))
  dump 02-after-drag
fi
adb logcat -d -s HaloLayout > $OUT/02-layout-log.txt

# Drag within the page in edit mode (should still be jiggling).
if P2=$(find 02-after-drag "Done"); then echo "edit mode on"; fi
for name in Clock Camera Contacts Files Calendar Maps Photos YouTube Chrome Settings; do
  if [ "$name" != "$LBL" ] && P=$(find 02-after-drag "$name"); then
    read cx cy x1 y1 x2 y2 <<< "$P"
    drag $cx $((y1 - 60)) 300 700 0.5 03-drag-in-page 300 700
    break
  fi
done

# "+" sheet → Apps tab → add the first app that isn't on the home screen.
dump 04-edit
if P=$(find 04-edit "+"); then
  read cx cy rest <<< "$P"; tap $cx $cy; sleep 1.5
  shot 04-plus-sheet; dump 04-plus-sheet
  if P=$(find 04-plus-sheet "Add"); then read cx cy rest <<< "$P"; tap $cx $cy; sleep 1; shot 04-added; fi
  if P=$(find 04-plus-sheet "Done"); then read cx cy rest <<< "$P"; tap $cx $cy; sleep 1; fi
fi

# Settings (⚙ in the edit bar).
dump 05-edit
if P=$(find 05-edit "⚙"); then
  read cx cy rest <<< "$P"; tap $cx $cy; sleep 1.5
  shot 05-settings
  adb shell input swipe 900 1800 900 500 400; sleep 1; shot 05-settings-2
  adb shell input swipe 900 1800 900 500 400; sleep 1; shot 05-settings-3
  adb shell input swipe 900 1800 900 500 400; sleep 1; shot 05-settings-4
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
dump 06-edit
if P=$(find 06-edit "Done"); then read cx cy rest <<< "$P"; tap $cx $cy; sleep 1; fi
home; sleep 1
shot 06-inner-final

# ---------------------------------------------------------------- inner screen (landscape)
adb shell settings put system user_rotation 1
sleep 5
shot 07-inner-landscape

# ---------------------------------------------------------------- cover screen
adb shell settings put system user_rotation 0
adb shell wm size 904x2176
adb shell wm density 420
sleep 5
home; sleep 2
shot 08-cover
dump 08-cover
W=904; H=2176
for name in Clock Camera Contacts Files Calendar Maps Photos YouTube Chrome Settings; do
  if P=$(find 08-cover "$name"); then
    read cx cy x1 y1 x2 y2 <<< "$P"
    drag $cx $((y1 - 60)) $((W - 20)) $((H / 8)) 2.5 09-cover-drag-right-edge 450 $((H / 3))
    break
  fi
done

adb logcat -d -s HaloLayout AndroidRuntime > $OUT/99-log.txt
adb logcat -d | grep -iE "halo|FATAL" | tail -300 > $OUT/99-logcat-halo.txt
exit 0
