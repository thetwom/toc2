# Metronome

[中文](README.md) | English

A fork of [thetwom/toc2](https://github.com/thetwom/toc2) (GPL-3.0) with switchable metronome
sounds, up to 8 independently looping tracks, and 16 note durations including thirty second
notes and septuplets.

| Item | Content |
|---|---|
| Upstream | [thetwom/toc2](https://github.com/thetwom/toc2), later upstream versions moved to [codeberg.org/thetwom/metronome](https://codeberg.org/thetwom/metronome) |
| Branch | `feature/custom-sound-sets` |
| Version | 4.15.4 (versionCode 67) |
| Package name | `de.moekadu.metronome.custom` |
| Min / target / compile SDK | 23 / 36 / 37 |

The package name differs from the upstream `de.moekadu.metronome`, so it installs next to the
original. This branch is not distributed on F-Droid or Google Play, and the store badges and
links in the upstream README do not apply here.

## Differences:

1. **Sound sets.** 7 sound sets with 8 slots each, switched in the toolbar.
2. **Multiple tracks.** Swipe right from the main screen into the track page, up to 8 tracks,
   each with its own notes, volume and mute, looping independently on a shared BPM and
   duration grid.
3. **Durations.** Sixteenth, eighth, quarter and thirty second bases, each with straight,
   triplet, quintuplet and septuplet timing, 16 combinations in total.

## Sound sets

A waveform icon sits left of the mute button in the top right toolbar, shown in both
orientations. It opens the sound set list; selecting one takes effect immediately, switching
during playback does not break the beat, and the choice is written to the `soundset`
preference, so it survives a restart.

| Sound set | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| Default | kick drum | snare | rim | woodblock | claves | hi-hat | mute | wood low |
| Ableton Live click | 1568 | 1046 | 880 | 1319 | 2093 | 698 | mute | 784 |
| 808 kit | kick | snare | closed hat | cowbell | clap | rim | mute | tom |
| iReal Pro click | 1180 | 900 | 720 | 1520 | shaker | 560 | mute | 1000 |
| DnB kit | kick | snare | closed hat | open hat | rim | shaker | mute | tom |
| Techno kit | kick | snare | closed hat | open hat | clap | rim | mute | ride |
| Rock kit | kick | snare | closed hat | open hat | tom | ride | mute | crash |

The default note names follow the system language. The `English note names` setting forces
English and takes effect immediately.

All samples are synthesised programmatically by [`tools/synth_sounds.py`](tools/synth_sounds.py),
44.1 kHz and 48 kHz versions, mono 16 bit WAV, 44 byte header, 100 files in
`app/src/main/res/raw`. No third-party samples. The Ableton Live and iReal Pro entries are
synthetic clicks that sound close to the commercial originals.
Every sound set has the same number of slots, so note ids stored in scenes stay valid after
switching sound sets.

## Multiple tracks

The page order is metronome, tracks, scenes. Entering the track page locks the whole screen to
portrait, 8 rows divide the height evenly, and the page does not scroll.

Each row, left to right: index, note preview thumbnail, track volume, mute, focus, delete.
Focus is single selection, defaulting to the first track; the selected row is highlighted as a
whole and the main screen switches to that track immediately. Delete is unavailable while only
one track is left. During playback the row of the sounding track is highlighted briefly.

Playback rules:

* Each track loops independently, returning to its own first note, on a shared BPM and
  duration grid. Tracks with different note counts drift apart and only realign on the least
  common multiple.
* A track created during playback aligns to the shared time grid.
* Note that mixing sums the samples directly.
Focus exists only in memory; after a restart it returns to the first track.

## Note durations

The editor offers 16 durations: sixteenth, eighth, quarter and thirty second bases, each with
straight, triplet, quintuplet and septuplet timing. A septuplet occupies the time of 4 ordinary
notes of the same kind, for example 7 sixteenth septuplets fill exactly one quarter note.

Note lengths are computed on a fractional frame clock, and all tracks share one grid. Scenes
that only use the original durations keep the previous per note rounding, so their timing is
identical to older versions. On narrow screens the duration and tuplet button groups each wrap
onto their own line.

## Scenes and storage

A scene stores BPM and the whole track list, in the format `#2`:

```
#2 <numTracks> (<volume> <mute> <numNotes> (<id> <volume> <duration>)*)*
```

Single track scenes written by upstream versions (`#0`, `#1`) load as one track with volume
1.0. Scenes containing thirty second notes or septuplets need this version or a later one;
older versions reject them as invalid.

## Building

Requires JDK 17 or newer (Temurin 21 was used here), Android SDK Platform 37, Build-Tools
36.0.0, Gradle 9.6.1.

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug          # or use a local Gradle 9.6.1: gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Regenerating the samples requires numpy:

```bash
python3 tools/synth_sounds.py    # writes to app/src/main/res/raw/
```

## Installing over an existing build and migrating

This package is signed with a locally generated debug key and cannot update an installed build
with a different signature. To keep old scenes, save them first, then export them through the
share action of the old version, uninstall, install this package and import them. Because the
package name differs, the upstream original can also coexist with this one.

## Known behaviour

| Behaviour | Cause |
|---|---|
| Tracks with different note counts sound offset | because each track loops independently |
| Sound becomes muddy with many tracks | because the samples are summed directly, lower the volume in that case |
| A brief flicker when entering the track page in landscape | the track page locks portrait, which triggers a view rebuild |

## Changed files

The file by file list is in [README-sound-sets.md](README-sound-sets.md). The main additions are
`metronomeproperties/SoundSet.kt`, `metronomeproperties/Track.kt`, `audio/TrackNoteGrid.kt`,
`fragments/TrackFragment.kt`, `views/TrackPreviewView.kt`, `dialogs/SoundSetDialog.kt`; the
rewritten ones are `audio/AudioMixer.kt`, `viewmodels/MetronomeViewModel.kt`,
`metronomeproperties/NoteDuration.kt` and the note views.

## License

GPL-3.0
