# 自定义节拍器音色集与多轨道

本分支在上游 [toc2](https://github.com/thetwom/toc2) 基础上增加音色集切换与多轨道叠加，音符、三连音、场景等原有逻辑不变。

## 用法

主界面标题栏新增一个波形图标按钮，位于静音按钮左侧（横屏同样显示）。点击弹出音色集列表，选中立即生效，播放过程中也能切换，切换后不需要停止播放。选择写入默认 SharedPreferences（键 `soundset`），重启后保留。

音色集内的鼓件按拍位分配：每个音色集提供 7 个槽位，槽位对应原有的音符音色选择器（底部网格与音符编辑界面）。想把 kick / snare / hat 分给不同拍，就用原有方式给每个音符选不同槽位。

| 音色集 | 槽位 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Default | kick drum | snare | rim | woodblock | claves | hi-hat | mute | wood low |
| Ableton Live click | 1568 | 1046 | 880 | 1319 | 2093 | 698 | mute | 784 |
| 808 kit | kick | snare | closed hat | cowbell | clap | rim | mute | tom |
| iReal Pro click | 1180 | 900 | 720 | 1520 | shaker | 560 | mute | 1000 |
| DnB kit | kick | snare | closed hat | open hat | rim | shaker | mute | tom |
| Techno kit | kick | snare | closed hat | open hat | clap | rim | mute | ride |
| Rock kit | kick | snare | closed hat | open hat | tom | ride | mute | crash |

每套 8 个槽位，第 7 槽固定为静音，第 8 槽是新增音色。音色选择网格 2x4 正好八格，每格右下角标注该音符的名字（click 类显示频率，中文界面下鼓件显示中文名）。

槽位 6 固定为静音，沿用原版 mute 采样。所有音色集槽位数量一致，因此旧场景里保存的 note id 不会失效。


## 多轨道

多轨管理放在单独的页面：右滑顺序为 节拍器 → 轨道 → 场景。节拍器页右上角的按钮仍然直达场景页，轨道页右上角也有进入场景页的入口。

**主界面保持原版样式**，只显示当前焦点轨道的音符，加号只添加音符，没有任何多轨控件、没有纵向滚动。

轨道页：

- 页面进入时锁定竖屏，离开恢复设备方向，其余页面不受影响
- 8 行等分屏高，不滚动，轨道从上往下填充，空行留白
- 每行从左到右：序号、该轨音符的缩略预览、音量滑条、静音、焦点、删除
- 焦点单选，默认第一条，选中行整行高亮；主界面立即切到该轨
- 新建轨道按钮紧跟最后一条轨道下方，满 8 条后消失
- 删除图标在只剩一条轨道时不可用
- 播放时正在发声的轨道整行短暂高亮

行为：

- 每轨独立循环，各自回到第一个音符，共享 BPM 与音符时长
- 新建的轨道为空，不发声，加音符后才有内容
- 初始为一条轨道，沿用原版的默认音符
- 焦点选择保存在内存里，重启后回到第一条轨道

### 存储

场景格式为 `#2`：`#2 <轨道数> (<音量> <静音> <音符数> (<id> <音量> <时长>)*)*`。
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
| `fragments/TrackFragment.kt` | 新增。轨道页，8 行列表、新建、焦点、静音、音量、删除 |
| `views/TrackPreviewView.kt` | 新增。轨道行里的音符缩略预览 |
| `fragments/ViewPagerAdapter.kt` | 页面由两页改为三页：节拍器、轨道、场景 |
| `res/layout/fragment_tracks.xml`、`res/layout/track_row.xml` | 新增。轨道页与行布局 |
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
