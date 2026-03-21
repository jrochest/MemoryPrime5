# Debugging

## Useful ADB Commands

### Filter logcat to only your app's process

```bash
adb logcat --pid=$(adb shell pidof com.jrochest.mp.debug)
```

This filters logcat output to only show logs from the Memory Prime debug build, cutting out all the noise from other system processes and apps.
