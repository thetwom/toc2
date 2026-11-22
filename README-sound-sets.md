# 自定义节拍器音色集

本分支在上游 [toc2](https://github.com/thetwom/toc2) 基础上增加音色集切换，其余操作逻辑不变。

## 用法

主界面左上角新增一个波形图标按钮。点击弹出音色集列表，选中立即生效，播放过程中也能切换，切换后不需要停止播放。选择写入默认 SharedPreferences（键 `soundset`），重启后保留。

音色集内的鼓件按拍位分配：每个音色集提供 7 个槽位，槽位对应原有的音符音色选择器（底部网格与音符编辑界面）。想把 kick / snare / hat 分给不同拍，就用原有方式给每个音符选不同槽位。

| 音色集 | 槽位 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|
| Default | kick drum | snare | sticks | woodblock | claves | hi-hat | mute |
| Ableton Live click | click 1568 Hz | click 1046 Hz | click 880 Hz | click 1319 Hz | click 2093 Hz | click 698 Hz | mute |
| 808 kit | kick | snare | closed hat | cowbell | clap | rim | mute |
| iReal Pro click | 木 click 1180 Hz | 900 Hz | 720 Hz | rim 1520 Hz | shaker | 560 Hz | mute |
| DnB kit | kick | snare | closed hat | open hat | rimshot | shaker/ride | mute |
| Techno kit | 909 kick | 909 snare | closed hat | open hat | clap | rim | mute |
| Rock kit | kick | snare | closed hat | open hat | tom | ride | mute |

槽位 6 固定为静音，沿用原版 mute 采样。所有音色集槽位数量一致，因此旧场景里保存的 note id 不会失效。

## 采样来源

全部采样由 `tools/synth_sounds.py` 程序化合成（numpy），没有引入任何第三方采样文件。

- 输出 44.1 kHz 与 48 kHz 两版，单声道 16 bit WAV，标准 44 字节头，符合 `AudioDecoder.waveToPCM` 的硬编码偏移。
- 重新生成：`python3 tools/synth_sounds.py`
- Ableton Live 与 iReal Pro 的默认节拍器声音是商业软件自带的采样，本仓库里对应的是听感接近的合成音，不是原厂采样文件。

## 与上游的差异

| 文件 | 改动 |
|---|---|
| `metronomeproperties/SoundSet.kt` | 新增。音色集定义、当前音色集管理与持久化 |
| `metronomeproperties/NoteList.kt` | `availableNotes` 由 `val` 改为可变，原数组改名 `defaultNotes` |
| `audio/AudioMixer.kt` | 采样缓存改为可失效，加入采样版本号；播放线程检测到版本变化后换采样 |
| `players/SingleNotePlayer.kt` | `reload()`：切换音色后清掉 SoundPool 里的旧采样 |
| `fragments/MetronomeFragment.kt` | 音色按钮与音色集选择对话框 |
| `views/NoteView.kt`、`views/SoundChooser.kt` | 切换音色后重算音符图标 |
| `res/layout/fragment_metronome.xml` | 左上角音色按钮；场景标题起点改到该按钮右侧 |
| `res/values/strings.xml` | 音色集名称与鼓件名称 |
| `res/drawable/ic_sound_set.xml` | 新增按钮图标 |
| `res/raw/*.wav` | 72 个合成采样（6 套 × 6 槽 × 2 采样率） |
| `app/build.gradle.kts` | `applicationId` 改为 `de.moekadu.metronome.custom`，可与原版共存 |
| `tools/synth_sounds.py` | 新增。采样生成脚本 |

## 构建

```bash
export JAVA_HOME=<jdk21>
export ANDROID_HOME=<android-sdk>   # 需要 platform 37 与 build-tools 37.0.0
gradle assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

许可证与上游一致（GPL-3.0）。
