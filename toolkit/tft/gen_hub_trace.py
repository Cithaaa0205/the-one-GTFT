"""Membangkitkan trace kontak sintetis sederhana dengan dua hub.

Node 0 = hub utama (H1), node 1 = hub kedua (H2), node 2..N-1 = perifer.
Format keluaran mengikuti StandardEventsReader ONE: "waktu CONN a b up/down".

Contoh: python3 gen_hub_trace.py 12 43200 1 > data/events/tft_hub_trace.txt
"""
import random
import sys

n = int(sys.argv[1]) if len(sys.argv) > 1 else 12
end = float(sys.argv[2]) if len(sys.argv) > 2 else 43200
seed = int(sys.argv[3]) if len(sys.argv) > 3 else 1
rng = random.Random(seed)

# rata-rata waktu antarkontak (detik) per jenis pasangan; None = tidak pernah bertemu
def mean_gap(a, b):
    a, b = min(a, b), max(a, b)
    if a == 0 and b == 1:
        return 900
    if a == 0:
        return 600      # H1 sangat populer
    if a == 1:
        return 1500     # H2 cukup populer
    if b == a + 1:
        return 3600     # sebagian kecil perifer saling bertemu
    return None

events = []
for a in range(n):
    for b in range(a + 1, n):
        g = mean_gap(a, b)
        if g is None:
            continue
        t = rng.expovariate(1.0 / g)
        while t < end:
            dur = rng.uniform(30, 120)
            events.append((round(t, 1), a, b, "up"))
            events.append((round(min(t + dur, end), 1), a, b, "down"))
            t += dur + rng.expovariate(1.0 / g)

events.sort(key=lambda e: (e[0], 0 if e[3] == "down" else 1))
for t, a, b, s in events:
    print(f"{t} CONN {a} {b} {s}")
