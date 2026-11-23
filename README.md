# 节拍器

中文 | [English](README.en.md)

基于 [thetwom/toc2](https://github.com/thetwom/toc2)（GPL-3.0）的分支，增加了可切换的节拍器音色、最多 8 条独立循环的轨道，包含三十二分音符与七连音在内的 16 种音符时值。

| 项目 | 内容 |
|---|---|
| 上游 | [thetwom/toc2](https://github.com/thetwom/toc2)，上游后续版本迁至 [codeberg.org/thetwom/metronome](https://codeberg.org/thetwom/metronome) |
| 分支 | `feature/custom-sound-sets` |
| 版本 | 4.15.4（versionCode 67） |
| 包名 | `de.moekadu.metronome.custom` |
| 最低 / 目标 / 编译 SDK | 23 / 36 / 37 |

包名与上游 `de.moekadu.metronome` 不同，因此可与原版同时安装。本分支不在 F-Droid 与 Google Play 分发，上游 README 里的商店徽章与链接对这里不适用。

## 差异：

1. **音色集**。7 套音色，每套 8 个槽位，在工具栏切换。
2. **多轨道**。主界面右滑进入轨道页，最多 8 条轨道，各有自己的音符、音量与静音，在共享的 BPM 与时值网格上独立循环。
3. **时值**。十六分、八分、四分、三十二分四种基值，各自搭配无连音、三连音、五连音、七连音，一共 16 种组合。

## 音色集

右上角工具栏静音按钮左侧有一个波形图标，横竖屏都显示。点开是音色集列表，选中立即生效，播放中切换不打断节奏，选择写入 `soundset` 偏好项，可以在重启后保留。

| 音色集 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Default | 底鼓 | 军鼓 | 鼓框 | 高音木鱼 | 木鱼 | 闭镲 | 静音 | 低音木鱼 |
| Ableton Live click | 1568 | 1046 | 880 | 1319 | 2093 | 698 | 静音 | 784 |
| 808 kit | 底鼓 | 军鼓 | 闭镲 | 牛铃 | 拍手 | 鼓框 | 静音 | 桶鼓 |
| iReal Pro click | 1180 | 900 | 720 | 1520 | 沙锤 | 560 | 静音 | 1000 |
| DnB kit | 底鼓 | 军鼓 | 闭镲 | 开镲 | 鼓框 | 沙锤 | 静音 | 桶鼓 |
| Techno kit | 底鼓 | 军鼓 | 闭镲 | 开镲 | 拍手 | 鼓框 | 静音 | 叮叮镲 |
| Rock kit | 底鼓 | 军鼓 | 闭镲 | 开镲 | 桶鼓 | 叮叮镲 | 静音 | 碎音镲 |

默认音色名跟随系统语言。设置里的「音色名用英文」可强制显示英文，立即生效。

所有采样由 [`tools/synth_sounds.py`](tools/synth_sounds.py) 程序化合成，44.1 kHz 与 48 kHz 两版，单声道 16 bit WAV，44 字节头，共 100 个文件在 `app/src/main/res/raw`。不含第三方采样。Ableton Live 与 iReal Pro 两项是听感接近商业原声的合成 click。
各音色集槽位数量一致，因此场景里保存的音符 id 在切换音色集后依然有效。

## 多轨道

页面顺序为节拍器、轨道、场景。进入轨道页时整屏锁定竖屏，8 行等分屏高，该页面不允许滚动。

每行从左到右依次为：序号、音符缩略预览、轨道音量、静音、焦点、删除。焦点单选，默认第一条，选中行整行高亮，主界面立即切到该轨。只剩一条轨道时删除不可用。播放时正在发声的轨道整行短暂高亮。

播放规则：

* 各轨独立循环，各自回到自己的第一个音符，共享 BPM 与时值网格。音符数不同的轨道会错开，只在最小公倍数处重合。
* 播放中新建的轨道对齐到共享时间网格。
* 注意混音是采样直接相加。
焦点只存在于内存，重启焦点后回到第一条轨道。

## 音符时值

编辑界面提供 16 种时值：十六分、八分、四分、三十二分四种基值，各配无连音、三连音、五连音、七连音。七连音占用 4 个同类普通音的时间，例如 7 个十六分七连音正好填满一个四分音符。

音符长度按分数帧时钟计算，所有轨道共用一条网格。只使用原有那几种时值的场景保留原来的按音符取整方式，时序与旧版完全一致。窄屏下时值与连音两组按钮各自换行。

## 场景与存储

场景保存 BPM 与整套轨道列表，格式为 `#2`：

```
#2 <轨道数> (<音量> <静音> <音符数> (<id> <音量> <时值>)*)*
```

上游版本写出的单轨场景（`#0`、`#1`）读入为一条轨道，音量 1.0。含三十二分音符或七连音的场景需要本版本或之后的版本，旧版会判为无效。

## 构建

需要 JDK 17 及以上（本次用 Temurin 21）、Android SDK Platform 37、Build-Tools 36.0.0、Gradle 9.6.1。

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug          # 或使用本地 Gradle 9.6.1：gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

重新生成采样需要 numpy：

```bash
python3 tools/synth_sounds.py    # 输出到 app/src/main/res/raw/
```

## 覆盖安装与迁移

本包的签名来自本地生成的 debug 密钥，无法覆盖签名不同的已装版本。保留旧场景的做法是先保存，再用旧版的分享功能导出，卸载后安装本包并导入。由于包名不同，上游原版也可以与本包共存。

## 已知行为

| 现象 | 原因 |
|---|---|
| 轨道音符数不同时听感错位 | 因为每轨道会独立循环 |
| 轨道多时声音发闷 | 因为采样直接相加，此时应该降低音量 |
| 横屏进入轨道页会闪一下 | 轨道页面锁定竖屏，因此会触发界面重建 |

## 改动文件

逐文件的清单见 [README-sound-sets.md](README-sound-sets.md)。主要新增为 `metronomeproperties/SoundSet.kt`、`metronomeproperties/Track.kt`、`audio/TrackNoteGrid.kt`、`fragments/TrackFragment.kt`、`views/TrackPreviewView.kt`、`dialogs/SoundSetDialog.kt`，重写的有 `audio/AudioMixer.kt`、`viewmodels/MetronomeViewModel.kt`、`metronomeproperties/NoteDuration.kt` 以及各音符视图。

## 许可证

GPL-3.0