"""Ringkasan floor vs round per trace (akhiran folder _mf, _mf_roc, _mr, _mr_roc).
Pemakaian: python toolkit/tft/ringkas_floor_round.py sintetis haggle reality reality_aktif
"""
import glob, statistics as st, sys
import os
B = os.path.join("reports", "tft_seeds") + os.sep
def load(trace, tag, sc):
    pre = "" if trace == "sintetis" else trace + "_"
    out = {}
    for f in glob.glob(f"{B}{pre}interval_replantrue_C20{tag}/seed*/{sc}_TftLoadReport.csv"):
        for l in open(f):
            p = l.rstrip().split("\t")
            if len(p) == 2:
                try: out.setdefault(p[0], []).append(float(p[1]))
                except ValueError: pass
    return out
K = [("delivery_ratio","Delivery (semua)",3),("delivery_ratio_matured","Delivery (matang)",3),("created_matured","Pesan matang",0),("relay_delivery_share","Lewat relay",3),("tft_drops","Dibuang",0),
     ("gini_relay_load","Gini semua",3),("gini_among_hubs_degree","Gini hub (derajat)",3),
     ("gini_among_hubs_contacts","Gini hub (kontak)",3),("max_relay_load","Beban maks.",0),
     ("relayed","Kerja relay",0),("total_transfers","Total transfer",0),("hub_utilization","Utilisasi hub",3)]
rows = [("COOP (tanpa TFT)","_mf","baseline_COOP_NOTFT"),("SELFISH","_mf","baseline_SELFISH_NOTFT"),
        ("GTFT baseline","_mf","baseline_GTFT"),
        ("REACTIVE floor","_mf","hubaware_REACTIVE"),("REACTIVE round","_mr","hubaware_REACTIVE"),
        ("PROACTIVE floor","_mf","hubaware_PROACTIVE"),("PROACTIVE round","_mr","hubaware_PROACTIVE"),
        ("PROACTIVE+replan floor","_mf_roc","hubaware_PROACTIVE"),("PROACTIVE+replan round","_mr_roc","hubaware_PROACTIVE"),
        ("S_ONLY floor","_mf","hubaware_S_ONLY"),("S_ONLY round","_mr","hubaware_S_ONLY"),
        ("K_ONLY floor","_mf","hubaware_K_ONLY"),("K_ONLY round","_mr","hubaware_K_ONLY")]
for trace in sys.argv[1:]:
    pre0 = "" if trace == "sintetis" else trace + "_"
    print(f"\n=== {trace} ===")
    print("Skema | n | " + " | ".join(k[1] for k in K))
    for name, tag, sc in rows:
        d = load(trace, tag, sc)
        if not d: print(name, "| (belum ada)"); continue
        cells = [(f"{st.mean(d[k]):.{dp}f}" if k in d else "-") for k, _, dp in K]
        print(f"{name} | {len(d['delivery_ratio'])} | " + " | ".join(cells))
    # per seed: GTFT vs PROACTIVE+replan round & floor
    print("per seed delivery matang / Gini hub(derajat) / beban maks: GTFT | PRO+replan floor | PRO+replan round")
    for s in range(1, 6):
        def v(tag, sc):
            pre = "" if trace == "sintetis" else trace + "_"
            r = {}
            for l in open(f"{B}{pre}interval_replantrue_C20{tag}/seed{s}/{sc}_TftLoadReport.csv"):
                p = l.rstrip().split("\t")
                if len(p) == 2: r[p[0]] = p[1]
            return f"{float(r['delivery_ratio_matured']):.3f}/{float(r['gini_among_hubs_degree']):.3f}/{float(r['max_relay_load']):.0f}"
        try: print(s, v("_mf","baseline_GTFT"), "|", v("_mf_roc","hubaware_PROACTIVE"), "|", v("_mr_roc","hubaware_PROACTIVE"))
        except FileNotFoundError: print(s, "belum ada")
