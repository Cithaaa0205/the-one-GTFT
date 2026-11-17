"""Memotong trace Reality (MIT) ke satu jendela waktu dan menggeser waktunya ke 0.

Pemakaian:  python toolkit/tft/cut_reality.py <minggu_awal> <jumlah_minggu> [--aktif] > data/events/Reality_wN.txt

--aktif: hanya node yang punya kontak di jendela itu, ID dipetakan ulang menjadi
0..n-1 (pemetaan dicetak ke stderr). Tanpa opsi ini, ID asli 0-96 dipakai,
termasuk node yang tidak aktif di jendela tersebut.

Alasan memotong: trace penuh 196,5 hari (satu run > 1 jam). Jendela dipilih
dari periode dengan aktivitas kontak tinggi dan stabil (bukan masa libur
minggu 15-16). Kontak yang sudah terbuka sebelum jendela diabaikan; kontak
yang masih terbuka di akhir jendela ditutup oleh akhir simulasi.
"""
import sys

w0, nw = int(sys.argv[1]), int(sys.argv[2])
aktif = "--aktif" in sys.argv
WEEK = 7 * 86400
t0, t1 = w0 * WEEK, (w0 + nw) * WEEK
open_ = set()
src = "data/events/RealityConnectionTraceFinal.txt"
rows = []
for line in open(src):
    p = line.split()
    if len(p) < 5 or p[1] != "CONN":
        continue
    t = float(p[0])
    if t < t0 or t >= t1:
        continue
    a, b = int(p[2]), int(p[3])
    k = (min(a, b), max(a, b))
    if p[4] == "up":
        open_.add(k)
    elif k not in open_:
        continue  # turun tanpa pernah naik di jendela ini
    else:
        open_.discard(k)
    rows.append((t - t0, a, b, p[4]))

ids = sorted({x for r in rows for x in r[1:3]})
remap = {old: new for new, old in enumerate(ids)} if aktif else {x: x for x in ids}
if aktif:
    sys.stderr.write(f"{len(ids)} node aktif; pemetaan ID asli -> ID baru:\n")
    sys.stderr.write(" ".join(f"{o}->{n}" for o, n in remap.items()) + "\n")
for t, a, b, st in rows:
    print(f"{t:.0f} CONN {remap[a]} {remap[b]} {st}")
