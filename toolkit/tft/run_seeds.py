"""Menjalankan baseline (3 skema) dan hub-aware (4 varian) untuk beberapa seed,
lalu merangkum rata-rata dan simpangan baku.

Setiap seed memakai trace sintetis dan urutan pesan yang berbeda.
Pakai (dari folder proyek):
  Linux/Mac : python3 toolkit/tft/run_seeds.py --seeds 5
  Windows   : python toolkit\\tft\\run_seeds.py --seeds 5 --one one.bat
Opsi: --load interval|buffer  --replan true|false  --capacity 20  --only baseline|hubaware
"""
import argparse
import glob
import os
import statistics
import subprocess
import sys

ap = argparse.ArgumentParser()
ap.add_argument("--seeds", type=int, default=5)
ap.add_argument("--load", default="interval", choices=["interval", "buffer"])
ap.add_argument("--replan", default="true", choices=["true", "false"])
ap.add_argument("--capacity", default="20")
ap.add_argument("--one", default="bash one.sh")
ap.add_argument("--only", default="all", choices=["all", "baseline", "hubaware"])
a = ap.parse_args()

COMMON = os.path.join("settings", "common", "tft_common.cfg")
FAMILIES = {
    "baseline": (os.path.join("settings", "baseline", "baseline.cfg"), 3),
    "hubaware": (os.path.join("settings", "hubaware", "hubaware.cfg"), 4),
}

tag = f"{a.load}_replan{a.replan}_C{a.capacity}"
outdir = os.path.join("reports", "tft_seeds", tag)
os.makedirs(outdir, exist_ok=True)

for seed in range(1, a.seeds + 1):
    trace = os.path.join("data", "events", f"tft_hub_trace_s{seed}.txt")
    with open(trace, "w") as fh:
        subprocess.run([sys.executable, os.path.join("toolkit", "tft", "gen_hub_trace.py"),
                        "12", "43200", str(seed)], stdout=fh, check=True)
    seed_dir = os.path.join(outdir, f"seed{seed}")
    os.makedirs(seed_dir, exist_ok=True)
    cfg = os.path.join(seed_dir, "seed.cfg")
    with open(cfg, "w") as fh:
        fh.write(f"Events1.filePath = {trace}\n".replace("\\", "/"))
        fh.write(f"Events2.prefix = M{seed}_\n")
        fh.write(f"TftControlPlane.loadMetric = {a.load}\n")
        fh.write(f"TftControlPlane.relayCapacity = {a.capacity}\n")
        fh.write(f"ShevadeTftRouter.replanEachInterval = {a.replan}\n")
        fh.write(f"Report.reportDir = {seed_dir}\n".replace("\\", "/"))
    for fam, (fcfg, n) in FAMILIES.items():
        if a.only not in ("all", fam):
            continue
        cmd = f"{a.one} -b {n} {COMMON} {fcfg} {cfg}"
        subprocess.run(cmd, shell=True, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

keys = ["delivery_ratio", "avg_delay", "tft_drops", "gini_relay_load",
        "gini_among_hubs", "max_relay_load", "hub_share"]
order = ["baseline_COOP_NOTFT", "baseline_SELFISH_NOTFT", "baseline_GTFT",
         "hubaware_REACTIVE", "hubaware_PROACTIVE", "hubaware_S_ONLY", "hubaware_K_ONLY"]
summary = os.path.join(outdir, "ringkasan.csv")
with open(summary, "w") as out:
    out.write("skema," + ",".join(keys) + ",jumlah_seed\n")
    for sc in order:
        vals = {k: [] for k in keys}
        files = glob.glob(os.path.join(outdir, "seed*", f"{sc}_TftLoadReport.csv"))
        for f in files:
            for line in open(f):
                p = line.rstrip("\r\n").split("\t")
                if len(p) == 2 and p[0] in vals:
                    vals[p[0]].append(float(p[1]))
        if not files:
            continue
        cells = [f"{statistics.mean(v):.3f}±{statistics.pstdev(v):.3f}" if v else "-"
                 for v in (vals[k] for k in keys)]
        out.write(sc + "," + ",".join(cells) + f",{len(files)}\n")
print(open(summary).read())
print("Ringkasan disimpan di", summary)
