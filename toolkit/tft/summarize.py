"""Merangkum keluaran TftLoadReport menjadi satu tabel CSV.

Pakai: python3 toolkit/tft/summarize.py reports/tft > ringkasan.csv
"""
import glob
import os
import sys

d = sys.argv[1] if len(sys.argv) > 1 else "reports/tft"
keys = ["delivery_ratio", "avg_delay", "overhead_ratio", "tft_drops",
        "gini_relay_load", "max_relay_load", "hub_share", "hub_utilization", "gini_among_hubs"]
print("skenario," + ",".join(keys))
for f in sorted(glob.glob(os.path.join(d, "*_TftLoadReport.csv"))):
    vals = {}
    with open(f) as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 2:
                vals[parts[0]] = parts[1]
    name = os.path.basename(f).replace("_TftLoadReport.csv", "")
    print(name + "," + ",".join(vals.get(k, "") for k in keys))
