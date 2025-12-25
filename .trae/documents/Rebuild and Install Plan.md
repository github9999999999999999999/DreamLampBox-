# Rebuild and Install APK via ADB

To resolve the previous build error (file lock on `R.jar`) and deploy the updated "Landscape Video Player" to your device, I will execute the following steps:

1.  **Stop Gradle Daemons**
    *   Run `.\gradlew --stop` to terminate any stuck Gradle processes holding file locks.

2.  **Clean and Rebuild**
    *   Run `.\gradlew clean assembleDebug` to clear the build cache and compile a fresh APK.

3.  **Install to Device**
    *   Run `adb install -r app\build\outputs\apk\debug\app-debug.apk` to install the app on your connected phone (the `-r` flag ensures it reinstalls/updates the existing app).

Please confirm to proceed with the build and installation.