# 自定义节拍器音色集与多轨道

本分支在上游 [toc2](https://github.com/thetwom/toc2) 基础上增加音色集切换与多轨道叠加，音符、三连音、场景等原有逻辑不变。

## 用法

主界面标题栏新增一个波形图标按钮，位于静音按钮左侧（横屏同样显示）。点击弹出音色集列表，选中立即生效，播放过程中也能切换，切换后不需要停止播放。选择写入默认 SharedPreferences（键 `soundset`），重启后保留。

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


## 多轨道

最多 8 条轨道同时播放，用于叠出鼓组。

- **加号按钮**：上半区添加音符，下半区添加轨道。按钮下半区颜色更深，用来区分两个区域。
- **短按轨道**：切换当前编辑的轨道，音符区显示该轨的音符。
- **长按轨道**：打开轨道菜单，里面只有轨道音量滑条、静音开关、✔ 和垃圾桶。✔ 结束编辑（音量修改实时生效），垃圾桶删除该轨。
- **循环方式**：每条轨独立循环，各自回到第一个音符，不同长度的轨道会形成错位（polymeter）。共享 BPM 与音符时长。
- **显示**：音符区纵向排列所有轨道，一轨时占满，两轨及以上时每行占视口一半，其余轨道上下拖动查看。
- **新轨道**初始为空，不发声，需要自己加音符。
- **上限**：第 9 条轨道会被拒绝并提示。最后一轨不能删除。

### 存储

场景格式升级到 `#2`：`#2 <轨道数> (<音量> <静音> <音符数> (<id> <音量> <时长>)*)*`。
旧场景（`#0`/`#1`，单轨）读入时作为一条轨道，音量 1.0。
旧版 App 无法读取 `#2` 场景，会判定为无效。

## 采样来源

全部采样由 `tools/synth_sounds.py` 程序化合成（numpy），没有引入任何第三方采样文件。

- 输出 44.1 kHz 与 48 kHz 两版，单声道 16 bit WAV，标准 44 字节头，符合 `AudioDecoder.waveToPCM` 的硬编码偏移。
- 重新生成：`python3 tools/synth_sounds.py`
- Ableton Live 与 iReal Pro 的默认节拍器声音是商业软件自带的采样，本仓库里对应的是听感接近的合成音，不是原厂采样文件。

## 与上游的差异

| 文件 | 改动 |
|---|---|
| `metronomeproperties/SoundSet.kt` | 新增。音色集定义、当前音色集管理与持久化 |
| `metronomeproperties/Track.kt` | 新增。轨道模型、v2 场景格式的序列化与反序列化 |
| `audio/AudioMixer.kt` | 每轨独立推进与入队，混音时按轨道音量叠加 |
| `services/PlayerService.kt`、`PlayerServiceConnection.kt` | 播放内容由单轨列表改为轨道列表 |
| `viewmodels/MetronomeViewModel.kt` | 轨道增删、选择、音量与静音，音符操作跨轨按 uid 查找 |
| `views/NoteView.kt` | 多轨分行绘制、纵向拖动滚动、短按与长按轨道手势 |
| `views/SoundChooser.kt` | 轨道菜单（音量、静音、✔、删除）、加号上下分区 |
| `metronomeproperties/NoteList.kt` | `availableNotes` 由 `val` 改为可变，原数组改名 `defaultNotes` |
| `audio/AudioMixer.kt` | 采样缓存改为可失效，加入采样版本号；播放线程检测到版本变化后换采样 |
| `players/SingleNotePlayer.kt` | `reload()`：切换音色后清掉 SoundPool 里的旧采样 |
| `fragments/MetronomeAndScenesFragment.kt` | 处理工具栏音色按钮 |
| `views/NoteView.kt`、`views/SoundChooser.kt` | 切换音色后重算音符图标（SoundChooser 自己监听音色变化） |
| `res/menu/metronome_and_scenes.xml` | 工具栏新增音色按钮，位于静音按钮左侧，横竖屏共用 |
| `dialogs/SoundSetDialog.kt` | 新增。音色集选择对话框 |
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
