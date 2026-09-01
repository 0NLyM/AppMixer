# AppMixer

**Work in progress**

AppMixer is a fork of [VolumeManager](https://github.com/yume-chan/VolumeManager) by [yume-chan](https://github.com/yume-chan), reworked with a **Nothing OS inspired** black/white/red look and aimed at being more customizable and feature-rich over time. See [`NOTICE.md`](NOTICE.md) for a summary of what changed compared to upstream and [`LICENSE`](LICENSE) for licensing (GPLv2, inherited from upstream).

Control each app's volume independently. [Shizuku](https://shizuku.rikka.app/) is used to access privileged APIs.

Requires Android 13.


## Download

Development builds can be produced from [action artifacts](https://github.com/0NLyM/AppMixer/actions/workflows/build.yml?query=branch%3Amain) once CI is configured with a signing keystore.

## Usage

1. Install and enable [Shizuku](https://shizuku.rikka.app/)
2. Launch AppMixer and request Shizuku permission
3. It should automatically enable its accessibility service
4. You can change volume either from
   1. The main interface
   2. Press any volume button and a popup should appear, completely replace the default volume popup
   3. Enable accessibility button and click the button

## Compare to [SoundMaster from ShizuTools](https://github.com/legendsayantan/ShizuTools/wiki/SoundMaster)

This app uses hidden API to directly change each audio stream's volume.

SoundMaster uses MediaProjection API to record audio from each app and apply post-effects.

| Feature                        | AppMixer        | SoundMaster     |
| ------------------------------ | --------------- | --------------- |
| Minimal Android version        | 13              | 10              |
| Control volume of each app     | ✅              | ✅              |
| Set output device for each app | ❌ <sup>1</sup> | ✅              |
| Change left-right balance      | ❌ <sup>2</sup> | ✅              |
| Equalizer (EQ)                 | ❌              | ✅              |
| Control protected apps         | ✅              | ❌ <sup>3</sup> |
| Zero latency added             | ✅              | ❌              |

<sup>1</sup>: There are APIs to do that, but not implemented in this app

<sup>2</sup>: There are other APIs to do that, but not implemented in this app

<sup>3</sup>: Can be worked around by patching the app

## How does it work

1. Use [`AudioManager#getActivePlaybackConfigurations()`](<https://developer.android.com/reference/android/media/AudioManager#getActivePlaybackConfigurations()>) to get list of [`AudioPlaybackConfiguration`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/AudioPlaybackConfiguration.java;drc=e282cc572ef848b1cb8d622c2c4939aac37c3b27).

   Each `AudioPlaybackConfiguration` represents a audio player, like [`AudioTrack`](https://developer.android.com/reference/android/media/AudioTrack) and [`MediaPlayer`](https://developer.android.com/media/platform/mediaplayer)

2. Use [`ActivityManager#getRunningAppProcesses()`](<https://developer.android.com/reference/android/app/ActivityManager#getRunningAppProcesses()>) and [`PackageManager#getApplicationInfo()`](<https://developer.android.com/reference/android/content/pm/PackageManager#getApplicationInfo(java.lang.String,%20android.content.pm.PackageManager.ApplicationInfoFlags)>) to map and group `AudioPlaybackConfiguration#getClientPid()` to apps
3. Use `AudioPlaybackConfiguration#getPlayerProxy()` and [`IPlayer.setVolume()`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/av/media/libaudioclient/aidl/android/media/IPlayer.aidl;l=29;drc=75e48fea431b1de2bf1715eb5c22ba4c794200bd) to update the internal volume multiplier
4. Use [`AudioManager#registerAudioPlaybackCallback()`](<https://developer.android.com/reference/android/media/AudioManager?hl=en#registerAudioPlaybackCallback(android.media.AudioManager.AudioPlaybackCallback,%20android.os.Handler)>) to listen for new `AudioPlaybackConfiguration`s and apply current volume to them.

## Note

This app uses the same API as MIUI's "Adjust media sound in multiple apps". Because this API can only setting volume, not reading, the volume set by one app will not be reflected in the other one.

## Credits

- Original app: [VolumeManager](https://github.com/yume-chan/VolumeManager) by [yume-chan](https://github.com/yume-chan), licensed under GPLv2.
- [Shizuku](https://shizuku.rikka.app/) for privileged API access without root.
