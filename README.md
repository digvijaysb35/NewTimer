# NewTimer

A native Android countdown timer app.

## Included
- Hours, minutes, and seconds countdown
- Built-in quick presets plus user-saved custom presets
- System alarm sound and custom audio-file selection
- App-level ring gain control
- Automatic ring stop duration
- AMOLED-friendly black interface
- Foreground notification with a Stop action
- Optional accessibility service to stop the timer/alarm with volume keys

## Volume-key behavior
Enable **NewTimer Volume Key Stop** in Android Accessibility settings. Android and device firmware may prevent volume-key interception while the display is off; this behavior must be verified on the target phone. The app-level gain slider changes software playback gain but cannot exceed the phone's system alarm-volume limit.

## Build
The GitHub Actions workflow builds a debug APK on pushes to main and uploads it as the NewTimer-debug-apk artifact.
