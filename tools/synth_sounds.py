#!/usr/bin/env python3
"""生成 toc2 fork 所需的节拍器音色采样。

输出: app/src/main/res/raw/{setId}_{slot}{sr}_wav.wav  (mono, pcm_s16le, 44 字节头)
AudioDecoder.waveToPCM 硬编码跳过 44 字节头，故必须保证标准 44 字节头。
"""
import os
import numpy as np
import wave

_HERE = os.path.dirname(os.path.abspath(__file__))


def _find_raw_dir():
    for rel in ("../app/src/main/res/raw", "../toc2/app/src/main/res/raw"):
        candidate = os.path.normpath(os.path.join(_HERE, rel))
        if os.path.isdir(os.path.dirname(candidate)):
            return candidate
    return os.path.normpath(os.path.join(_HERE, "..", "app", "src", "main", "res", "raw"))


OUT = _find_raw_dir()
SRATES = (44100, 48000)


def env_exp(n, tau, sr, attack=0.0005):
    t = np.arange(n) / sr
    e = np.exp(-t / tau)
    a = int(max(1, attack * sr))
    if a < n:
        e[:a] *= np.linspace(0.0, 1.0, a)
    return e


def osc_sine(freq, dur, sr, phase0=0.0):
    n = int(dur * sr)
    t = np.arange(n) / sr
    return np.sin(2 * np.pi * freq * t + phase0), n


def sweep_sine(f0, f1, dur, sr, tau, curve=0.25):
    n = int(dur * sr)
    t = np.arange(n) / sr
    f = f1 + (f0 - f1) * np.exp(-t / curve)
    ph = 2 * np.pi * np.cumsum(f) / sr
    return np.sin(ph) * env_exp(n, tau, sr, attack=0.001)


def noise(n, seed):
    rng = np.random.default_rng(seed)
    return rng.standard_normal(n)


def onepole_lp(x, cutoff, sr):
    a = np.exp(-2 * np.pi * cutoff / sr)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def biquad_bp(x, f0, q, sr):
    w0 = 2 * np.pi * f0 / sr
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = alpha, 0.0, -alpha
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, xi
        y2, y1 = y1, yi
        y[i] = yi
    return y


def biquad_hp(x, f0, q, sr):
    w0 = 2 * np.pi * f0 / sr
    alpha = np.sin(w0) / (2 * q)
    b0, b1, b2 = (1 + np.cos(w0)) / 2, -(1 + np.cos(w0)), (1 + np.cos(w0)) / 2
    a0, a1, a2 = 1 + alpha, -2 * np.cos(w0), 1 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, xi
        y2, y1 = y1, yi
        y[i] = yi
    return y


def pad_to(x, n):
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def finish(x, sr, dur, peak=0.95):
    """统一长度、加尾部淡出、归一化。"""
    n = int(dur * sr)
    x = pad_to(np.asarray(x, dtype=np.float64), n)
    fade = int(min(len(x), 0.003 * sr))
    if fade > 1:
        x[-fade:] *= np.linspace(1.0, 0.0, fade)
    m = np.max(np.abs(x))
    if m > 1e-9:
        x = x / m * peak
    return x


# ---------------- 音色集合 ----------------

def click_tone(freq, dur, sr, tau, noise_amt, seed, peak=0.95):
    s, n = osc_sine(freq, dur, sr)
    s = s * env_exp(n, tau, sr, attack=0.0002)
    nz = noise(n, seed) * env_exp(n, tau * 0.35, sr, attack=0.0001) * noise_amt
    return finish(s + nz, sr, dur, peak)


def ableton(slot, sr):
    """Ableton Live 风格默认节拍器: 极短纯音脉冲。"""
    cfg = [(1568, 0.030, 0.006, 0.05),
           (1046, 0.026, 0.005, 0.05),
           (880, 0.024, 0.005, 0.05),
           (1319, 0.026, 0.005, 0.05),
           (2093, 0.020, 0.004, 0.06),
           (698, 0.028, 0.006, 0.05),
           (784, 0.027, 0.0055, 0.05)]
    f, dur, tau, nz = cfg[slot]
    return click_tone(f, dur, sr, tau, nz, 100 + slot)


def m808(slot, sr):
    if slot == 0:   # kick
        d = 0.35
        s = sweep_sine(120, 42, d, sr, tau=0.12, curve=0.030)
        ck = noise(int(0.006 * sr), 11) * env_exp(int(0.006 * sr), 0.0012, sr)
        s[:len(ck)] += ck * 0.25
        return finish(s, sr, d)
    if slot == 1:   # snare
        d = 0.18
        tone = sweep_sine(240, 170, d, sr, tau=0.045, curve=0.02) * 0.55
        nz = biquad_bp(noise(int(d * sr), 12), 1800, 0.8, sr) * env_exp(int(d * sr), 0.075, sr)
        return finish(tone + nz * 1.1, sr, d)
    if slot == 2:   # closed hat
        d = 0.055
        nz = biquad_hp(noise(int(d * sr), 13), 7000, 0.9, sr) * env_exp(int(d * sr), 0.010, sr)
        return finish(nz, sr, d, peak=0.75)
    if slot == 3:   # cowbell (808 经典双方波)
        d = 0.16
        n = int(d * sr)
        t = np.arange(n) / sr
        a = np.sign(np.sin(2 * np.pi * 540 * t)) * 0.5
        b = np.sign(np.sin(2 * np.pi * 800 * t)) * 0.5
        s = biquad_bp(a + b, 900, 1.2, sr) * env_exp(n, 0.045, sr, attack=0.0005)
        return finish(s, sr, d)
    if slot == 4:   # clap
        d = 0.22
        n = int(d * sr)
        nz = biquad_bp(noise(n, 14), 1500, 1.0, sr)
        e = np.zeros(n)
        for k, off in enumerate((0.0, 0.011, 0.022, 0.033)):
            i0 = int(off * sr)
            seg = int(0.012 * sr)
            if i0 < n:
                e[i0:i0 + seg] += env_exp(min(seg, n - i0), 0.006 if k < 3 else 0.05, sr)
        return finish(nz * np.minimum(e, 1.0), sr, d, peak=0.8)
    if slot == 5:   # rim / click
        d = 0.05
        s = sweep_sine(2400, 900, d, sr, tau=0.006, curve=0.004)
        return finish(s, sr, d, peak=0.8)
    # slot 6: tom
    d = 0.30
    return finish(sweep_sine(190, 95, d, sr, tau=0.10, curve=0.05), sr, d)


def ireal(slot, sr):
    """iReal Pro 风格: 木质感短 click。"""
    cfg = [(1180, 0.030, 0.0085, 0.55, 0.95),
           (900, 0.028, 0.0075, 0.55, 0.85),
           (720, 0.026, 0.0068, 0.55, 0.80),
           (1520, 0.020, 0.0045, 0.45, 0.75),
           (0, 0.040, 0.0140, 0.85, 0.55),
           (560, 0.030, 0.0060, 0.50, 0.70),
           (1000, 0.028, 0.0070, 0.55, 0.85)]
    f, dur, tau, nz_amt, peak = cfg[slot]
    n = int(dur * sr)
    if f == 0:  # shaker
        nz = biquad_bp(noise(n, 200 + slot), 5200, 0.9, sr) * env_exp(n, tau, sr, attack=0.001)
        return finish(nz, sr, dur, peak=peak)
    tone = np.sin(2 * np.pi * f * np.arange(n) / sr) * env_exp(n, tau, sr, attack=0.0003)
    tone += 0.4 * np.sin(2 * np.pi * f * 2.4 * np.arange(n) / sr) * env_exp(n, tau * 0.5, sr, attack=0.0003)
    nz = biquad_bp(noise(n, 200 + slot), f * 2.2, 1.2, sr) * env_exp(n, tau * 0.4, sr, attack=0.0002) * nz_amt
    return finish(tone + nz, sr, dur, peak=peak)


def dnb(slot, sr):
    if slot == 0:   # kick: 紧、低频
        d = 0.30
        s = sweep_sine(150, 48, d, sr, tau=0.10, curve=0.022)
        ck = biquad_hp(noise(int(0.010 * sr), 31), 2500, 0.7, sr) * env_exp(int(0.010 * sr), 0.002, sr) * 0.3
        s[:len(ck)] += ck
        return finish(s, sr, d)
    if slot == 1:   # snare: 明亮 breakbeat snare
        d = 0.20
        n = int(d * sr)
        tone = sweep_sine(330, 190, d, sr, tau=0.035, curve=0.015) * 0.5
        nz = biquad_bp(noise(n, 32), 2600, 0.7, sr) * env_exp(n, 0.085, sr)
        nz2 = biquad_bp(noise(n, 33), 700, 0.9, sr) * env_exp(n, 0.05, sr) * 0.5
        return finish(tone + nz + nz2, sr, d)
    if slot == 2:   # closed hat: 短脆
        d = 0.045
        nz = biquad_hp(noise(int(d * sr), 34), 8500, 0.8, sr) * env_exp(int(d * sr), 0.008, sr)
        return finish(nz, sr, d, peak=0.7)
    if slot == 3:   # open hat
        d = 0.18
        nz = biquad_hp(noise(int(d * sr), 35), 7500, 0.7, sr) * env_exp(int(d * sr), 0.055, sr)
        return finish(nz, sr, d, peak=0.7)
    if slot == 4:   # rimshot
        d = 0.055
        n = int(d * sr)
        s = np.sin(2 * np.pi * 1750 * np.arange(n) / sr) * env_exp(n, 0.0045, sr) * 0.7
        s += biquad_bp(noise(n, 36), 3000, 1.4, sr) * env_exp(n, 0.004, sr) * 0.6
        return finish(s, sr, d, peak=0.8)
    if slot == 5:   # shaker/ride
        d = 0.22
        n = int(d * sr)
        nz = biquad_hp(noise(n, 37), 5200, 0.8, sr) * env_exp(n, 0.07, sr, attack=0.004)
        return finish(nz, sr, d, peak=0.6)
    # slot 6: tom
    d = 0.26
    return finish(sweep_sine(230, 120, d, sr, tau=0.085, curve=0.045), sr, d)


def techno(slot, sr):
    if slot == 0:   # 909 kick: punchy
        d = 0.32
        s = sweep_sine(180, 50, d, sr, tau=0.11, curve=0.018)
        ck = noise(int(0.004 * sr), 41) * env_exp(int(0.004 * sr), 0.0009, sr) * 0.4
        s[:len(ck)] += ck
        return finish(s, sr, d)
    if slot == 1:   # 909 snare
        d = 0.17
        n = int(d * sr)
        tone = sweep_sine(260, 185, d, sr, tau=0.030, curve=0.012) * 0.45
        nz = biquad_bp(noise(n, 42), 2100, 0.9, sr) * env_exp(n, 0.065, sr)
        return finish(tone + nz, sr, d)
    if slot == 2:   # closed hat
        d = 0.042
        nz = biquad_hp(noise(int(d * sr), 43), 9000, 0.9, sr) * env_exp(int(d * sr), 0.007, sr)
        return finish(nz, sr, d, peak=0.7)
    if slot == 3:   # open hat
        d = 0.20
        nz = biquad_hp(noise(int(d * sr), 44), 8000, 0.8, sr) * env_exp(int(d * sr), 0.06, sr)
        return finish(nz, sr, d, peak=0.7)
    if slot == 4:   # clap
        d = 0.20
        n = int(d * sr)
        nz = biquad_bp(noise(n, 45), 1400, 1.1, sr)
        e = np.zeros(n)
        for k, off in enumerate((0.0, 0.010, 0.020, 0.030)):
            i0 = int(off * sr)
            seg = int(0.010 * sr)
            if i0 < n:
                e[i0:i0 + seg] += env_exp(min(seg, n - i0), 0.005 if k < 3 else 0.045, sr)
        return finish(nz * np.minimum(e, 1.0), sr, d, peak=0.8)
    if slot == 5:   # rim
        d = 0.048
        n = int(d * sr)
        s = np.sin(2 * np.pi * 1400 * np.arange(n) / sr) * env_exp(n, 0.004, sr) * 0.7
        s += biquad_bp(noise(n, 46), 2600, 1.5, sr) * env_exp(n, 0.004, sr) * 0.5
        return finish(s, sr, d, peak=0.8)
    # slot 6: ride
    d = 0.30
    n = int(d * sr)
    nz = biquad_hp(noise(n, 47), 5000, 0.7, sr) * env_exp(n, 0.11, sr, attack=0.003)
    return finish(nz, sr, d, peak=0.55)


def rock(slot, sr):
    if slot == 0:   # kick: 更有中频 click
        d = 0.34
        s = sweep_sine(130, 58, d, sr, tau=0.13, curve=0.030)
        ck = biquad_bp(noise(int(0.012 * sr), 51), 1800, 0.8, sr) * env_exp(int(0.012 * sr), 0.0025, sr) * 0.45
        s[:len(ck)] += ck
        return finish(s, sr, d)
    if slot == 1:   # snare: 带尾音
        d = 0.26
        n = int(d * sr)
        tone = sweep_sine(300, 200, d, sr, tau=0.040, curve=0.016) * 0.55
        nz = biquad_bp(noise(n, 52), 2400, 0.8, sr) * env_exp(n, 0.095, sr)
        nz += biquad_bp(noise(n, 53), 500, 1.0, sr) * env_exp(n, 0.06, sr) * 0.35
        return finish(tone + nz, sr, d)
    if slot == 2:   # closed hat
        d = 0.050
        nz = biquad_hp(noise(int(d * sr), 54), 7500, 0.8, sr) * env_exp(int(d * sr), 0.009, sr)
        return finish(nz, sr, d, peak=0.65)
    if slot == 3:   # open hat
        d = 0.24
        nz = biquad_hp(noise(int(d * sr), 55), 6800, 0.75, sr) * env_exp(int(d * sr), 0.075, sr)
        return finish(nz, sr, d, peak=0.65)
    if slot == 4:   # tom
        d = 0.28
        s = sweep_sine(200, 110, d, sr, tau=0.10, curve=0.05)
        return finish(s, sr, d)
    if slot == 5:   # ride
        d = 0.28
        n = int(d * sr)
        nz = biquad_hp(noise(n, 56), 4800, 0.7, sr) * env_exp(n, 0.10, sr, attack=0.003)
        return finish(nz, sr, d, peak=0.55)
    # slot 6: crash
    d = 0.42
    n = int(d * sr)
    nz = biquad_hp(noise(n, 57), 4200, 0.6, sr) * env_exp(n, 0.16, sr, attack=0.002)
    return finish(nz, sr, d, peak=0.5)


SETS = {
    "ableton": ableton,
    "s808": m808,
    "ireal": ireal,
    "dnb": dnb,
    "techno": techno,
    "rock": rock,
}


def write_wav(path, samples, sr):
    data = np.clip(samples, -1.0, 1.0)
    ints = (data * 32767.0).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(ints.tobytes())
    # 校验头长度为 44 字节 (waveToPCM 会硬跳 44)
    with open(path, "rb") as f:
        raw = f.read(64)
    assert raw[:4] == b"RIFF" and raw[8:12] == b"WAVE", path
    assert raw[12:16] == b"fmt " and raw[36:40] == b"data", f"{path}: 非 44 字节头"


def default_extra(sr):
    """Extra note of the default sound set: low wood block, matching the built in wood block."""
    d = 0.075
    n = int(d * sr)
    f = 700.0
    t = np.arange(n) / sr
    tone = np.sin(2 * np.pi * f * t) * env_exp(n, 0.012, sr, attack=0.0004)
    tone += 0.35 * np.sin(2 * np.pi * f * 2.1 * t) * env_exp(n, 0.008, sr, attack=0.0003)
    nz = biquad_bp(noise(n, 900), f * 2.4, 1.2, sr) * env_exp(n, 0.005, sr, attack=0.0002) * 0.5
    return finish(tone + nz, sr, d, peak=0.9)


def main():
    os.makedirs(OUT, exist_ok=True)
    count = 0
    for sr in SRATES:
        tag = 44 if sr == 44100 else 48
        write_wav(os.path.join(OUT, f"woodblock_low_{tag}_wav.wav"), default_extra(sr), sr)
        count += 1
    for set_id, fn in SETS.items():
        for sr in SRATES:
            tag = 44 if sr == 44100 else 48
            for slot in range(7):
                x = fn(slot, sr)
                path = os.path.join(OUT, f"{set_id}_{slot}_{tag}_wav.wav")
                write_wav(path, x, sr)
                count += 1
    print(f"generated {count} files in {OUT}")


if __name__ == "__main__":
    main()
