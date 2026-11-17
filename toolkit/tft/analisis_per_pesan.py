"""Analisis per pesan Haggle: apakah pesan yang sampai lewat relay pada COOP juga
sampai lewat kontak langsung pada SELFISH (seed sama => ID pesan sama)?

1. Jalankan COOP dan SELFISH dengan laporan per pesan, untuk seed 1-5:
     python toolkit/tft/analisis_per_pesan.py --jalankan --one "bash one.sh"   (Windows: --one one.bat)
2. Atau, bila laporan sudah ada di reports/haggle_per_pesan/seed*/, cukup:
     python toolkit/tft/analisis_per_pesan.py
Hanya pesan matang yang dihitung (dibuat <= endTime - TTL).
"""
import argparse, os, statistics as st, subprocess

ap = argparse.ArgumentParser()
ap.add_argument("--jalankan", action="store_true")
ap.add_argument("--one", default="bash one.sh")
ap.add_argument("--seeds", type=int, default=5)
a = ap.parse_args()
END, TTL = 274883, 1440 * 60
base = os.path.join("reports", "haggle_per_pesan")

if a.jalankan:
    for s in range(1, a.seeds + 1):
        d = os.path.join(base, f"seed{s}")
        os.makedirs(d, exist_ok=True)
        cfg = os.path.join(d, "seed.cfg")
        with open(cfg, "w") as fh:
            fh.write(f"Events2.prefix = M{s}_\nReport.reportDir = {d}\n".replace("\\", "/"))
            fh.write("ShevadeTftRouter.scheme = [COOP_NOTFT; SELFISH_NOTFT]\n")
            fh.write("Report.nrofReports = 3\nReport.report2 = DeliveredMessagesReport\n")
            fh.write("Report.report3 = CreatedMessagesReport\n")
        subprocess.run(f"{a.one} -b 2 settings/common/tft_common.cfg settings/baseline/baseline.cfg "
                       f"settings/common/trace_haggle.cfg {cfg}", shell=True, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def rows(f):
    for l in open(f):
        if not l.startswith("#") and l.strip():
            yield l.split()

tot = dict(m=0, coop=0, self=0, relay=0, also=0, self_only=0); gains = []
for s in range(1, a.seeds + 1):
    p = os.path.join(base, f"seed{s}", "baseline_")
    cr = {r[1]: float(r[0]) for r in rows(p + "COOP_NOTFT_CreatedMessagesReport.csv")}
    cr2 = {r[1] for r in rows(p + "SELFISH_NOTFT_CreatedMessagesReport.csv")}
    assert set(cr) == cr2, "ID pesan berbeda antar skema"
    mat = {k for k, t in cr.items() if t + TTL <= END}
    def deliv(f):
        d = {}
        for r in rows(f):
            if r[1] in mat and r[1] not in d:
                d[r[1]] = (float(r[0]), int(r[3]))
        return d
    co, se = deliv(p + "COOP_NOTFT_DeliveredMessagesReport.csv"), deliv(p + "SELFISH_NOTFT_DeliveredMessagesReport.csv")
    relay = [k for k, (t, h) in co.items() if h > 1]
    also = [k for k in relay if k in se]
    gains += [se[k][0] - co[k][0] for k in also]
    for k, v in dict(m=len(mat), coop=len(co), self=len(se), relay=len(relay), also=len(also),
                     self_only=len(set(se) - set(co))).items():
        tot[k] += v
    print(f"seed {s}: matang {len(mat)}; COOP {len(co)/len(mat):.3f}; SELFISH {len(se)/len(mat):.3f}; "
          f"COOP lewat relay {len(relay)}, juga sampai di SELFISH {len(also)} ({len(also)/max(1, len(relay)):.1%})")
print(f"\nTotal: pesan matang {tot['m']}; COOP {tot['coop']/tot['m']:.3f}; SELFISH {tot['self']/tot['m']:.3f}")
print(f"COOP lewat relay {tot['relay']}: juga sampai di SELFISH {tot['also']} ({tot['also']/tot['relay']:.1%}); "
      f"hanya sampai berkat relay {tot['relay']-tot['also']} ({1-tot['also']/tot['relay']:.1%})")
print(f"Selisih waktu sampai (SELFISH - COOP) untuk pesan lewat relay yang juga sampai di SELFISH: "
      f"rata-rata {st.mean(gains)/3600:.2f} jam, median {st.median(gains)/3600:.2f} jam; "
      f"COOP lebih lambat pada {sum(1 for x in gains if x < 0)} dari {len(gains)} pesan")
print(f"Sampai di SELFISH tetapi tidak di COOP: {tot['self_only']}")
