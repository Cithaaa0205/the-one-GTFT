"""Uji integrasi ACK di The ONE (bukan uji unit).

Menjalankan skenario 4 node dengan event kontak nyata, lalu memeriksa kapan
setiap node pertama kali memiliki ACK pesan M1 (3 -> 0, sampai sekitar t=20):
  1. kontak 0-1 dan 1-2 dimulai bersamaan di t=500 (urutan event 0-1 dulu);
  2. sama, tetapi urutan event 1-2 dulu;
  3. 1 membawa ACK lalu bertemu 2 belakangan (t=800).
Harapan: ACK berpindah paling jauh satu hop per langkah simulasi.

Jalankan dari folder proyek setelah kompilasi:
  python toolkit/tft/uji_ack_integrasi.py            (Linux/macOS, memakai one.sh)
  python toolkit/tft/uji_ack_integrasi.py --one one.bat
"""
import argparse, os, subprocess, sys

ap = argparse.ArgumentParser()
ap.add_argument("--one", default="bash one.sh")
a = ap.parse_args()

base = os.path.join("toolkit", "tft", "uji_ack")
out = os.path.join("reports", "uji_ack")
cases = [
    ("trace_urut_ab_bc.txt", {"0": None, "1": 500, "2": 501}),
    ("trace_urut_bc_ab.txt", {"0": None, "1": 500, "2": 501}),
    ("trace_carry.txt", {"0": None, "1": 500, "2": 800}),
]
fail = 0
for trace, expect in cases:
    d = os.path.join(out, trace.replace(".txt", ""))
    os.makedirs(d, exist_ok=True)
    cfg = os.path.join(d, "case.cfg")
    with open(cfg, "w") as fh:
        fh.write(f"Events1.filePath = {os.path.join(base, trace)}\n".replace("\\", "/"))
        fh.write(f"Report.reportDir = {d}\n".replace("\\", "/"))
    subprocess.run(f"{a.one} -b 1 {os.path.join(base, 'uji_ack.cfg')} {cfg}", shell=True,
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    first = {}
    for line in open(os.path.join(d, "uji_ack_TftAckProbeReport.csv")):
        p = line.split()
        if len(p) == 3:
            first[p[1]] = float(p[2])
    print(f"== {trace}: pertama kali punya ACK M1 -> {dict(sorted(first.items()))}")
    if "0" not in first or first["0"] > 30:
        print("   GAGAL: tujuan (node 0) belum membuat ACK"); fail += 1
    for node, t in expect.items():
        if t is None:
            continue
        ok = node in first and abs(first[node] - t) < 1e-6
        print(f"   {'ok   ' if ok else 'GAGAL'} node {node}: harapan t={t}, hasil {first.get(node)}")
        fail += 0 if ok else 1
    if "3" in first and first["3"] < 100:
        pass  # node 3 bisa menerima ACK saat masih terhubung dengan 0; bukan bagian uji
print("SEMUA UJI LULUS" if fail == 0 else f"{fail} UJI GAGAL")
sys.exit(1 if fail else 0)
