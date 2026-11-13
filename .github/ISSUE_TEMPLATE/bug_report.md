---
name: Bug report
about: Something on the dial, in the editor or in the monitor did not behave as the README says
title: ''
labels: bug
assignees: ''
---

**Watch and platform**
- Watch model:
- Wear OS version (Settings → System → About):
- MFD-24 version (editor → `ABOUT`):

**What happened**
What you did, what you expected, what the dial or the editor showed instead. A screenshot off
the watch helps (`adb shell screencap -p /sdcard/s.png` then `adb pull /sdcard/s.png`).

**Log, if you can get one**
`adb logcat -d | grep -iE "mfd24|avdesign"` right after it happened. The face logs little on
purpose, so an empty result is itself useful.

**Settings that matter**
Vigilance on or off, position automatic or manual, which sensor slots are on, always-on mode.
