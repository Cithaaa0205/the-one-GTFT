# Penelitian Payung: Incentive-Aware Routing Shevade dkk. + Hub-Aware TFT

Satu repository untuk dua skripsi:

| Bagian | Pemilik | Isi |
|---|---|---|
| **Baseline** | Rekan | Replikasi incentive-aware routing Shevade dkk. (2008) di jaringan manusia |
| **Hub-aware** | Citha | Generosity adaptif `epsilon = floor(epsBase * S * K)` di atas baseline |
| **Bersama** | Berdua | Control plane, laporan metrik, parameter skenario |

Router hub-aware **mewarisi** router baseline dan hanya mengganti dua fungsi
epsilon. Semua perbaikan baseline (misalnya contrition) otomatis ikut terpakai,
dan perbandingan dijamin adil karena satu-satunya perbedaan adalah epsilon.

## Struktur

```
src/routing/
  ShevadeTftRouter.java        BASELINE   source routing, Chebyshev, Fig. 4, aturan TFT
  HubAwareTftRouter.java       HUB-AWARE  extends ShevadeTftRouter, menimpa epsilon
  tft/TftControlPlane.java     BERSAMA    link state, kredit ACK, derajat, beban relay
  hubaware/HubAwareFactors.java HUB-AWARE faktor S, K, dan epsilon adaptif
src/report/
  TftLoadReport.java           BERSAMA    semua metrik evaluasi
settings/
  common/tft_common.cfg        BERSAMA    skenario, trace, interval, pesan, laporan
  common/trace_haggle.cfg      BERSAMA    override trace manusia Haggle
  baseline/baseline.cfg        BASELINE   COOP_NOTFT, SELFISH_NOTFT, GTFT
  hubaware/hubaware.cfg        HUB-AWARE  REACTIVE, PROACTIVE, S_ONLY, K_ONLY
toolkit/tft/
  gen_hub_trace.py             trace sintetis dua hub
  run_seeds.py                 jalankan kedua bagian untuk banyak seed + ringkasan
  summarize.py                 rangkum satu folder laporan
data/events/tft_hub_trace.txt  trace sintetis seed 1
```

## Titik perluasan baseline

```java
// ShevadeTftRouter (baseline): konstan
protected int epsilonAtSource(int relayB, int upstreamA) { return epsBase; }
protected int epsilonAtRelay(int relayB, int upstreamA)  { return epsBase; }

// HubAwareTftRouter (Citha): adaptif
@Override protected int epsilonAtSource(int b, int a) {
    return variant == REACTIVE ? super.epsilonAtSource(b, a)
                               : factors.epsilon(epsBase, b, a);
}
@Override protected int epsilonAtRelay(int b, int a) {
    return factors.epsilon(epsBase, b, a);
}
```

## Cara menjalankan

Windows (dari folder proyek):
```
compile.bat
one.bat -b 3 settings\common\tft_common.cfg settings\baseline\baseline.cfg
one.bat -b 4 settings\common\tft_common.cfg settings\hubaware\hubaware.cfg
python toolkit\tft\summarize.py reports\tft
```

Banyak seed sekaligus (kedua bagian, 5 seed):
```
python toolkit\tft\run_seeds.py --seeds 5 --one one.bat
```
Opsi: `--load interval|buffer`, `--replan true|false`, `--capacity 20`,
`--only baseline|hubaware`. Ringkasan tersimpan di
`reports\tft_seeds\<konfigurasi>\ringkasan.csv`.

Trace manusia Haggle: tambahkan `settings\common\trace_haggle.cfg` di urutan
terakhir perintah `one.bat`.

## Parameter

| Setting | Pemilik | Arti |
|---|---|---|
| `TftControlPlane.interval` | bersama | panjang interval TFT (detik) |
| `TftControlPlane.degreeWindow` | bersama | jendela derajat kontak (detik) |
| `TftControlPlane.loadMetric` | bersama | `interval` (paket relay per interval) atau `buffer` |
| `TftControlPlane.relayCapacity` | bersama | kapasitas relay C |
| `ShevadeTftRouter.scheme` | baseline | `COOP_NOTFT`, `SELFISH_NOTFT`, `GTFT` |
| `ShevadeTftRouter.epsBase` | baseline | epsilon konstan = epsilon dasar hub-aware |
| `ShevadeTftRouter.repFactor` | baseline | jumlah salinan per pesan |
| `ShevadeTftRouter.maxHops` | baseline | batas hop jalur kandidat (3) |
| `ShevadeTftRouter.deadline` | baseline | deadline Chebyshev; -1 = TTL |
| `ShevadeTftRouter.replanEachInterval` | baseline | Fig. 4 diulang tiap interval |
| `HubAwareTftRouter.variant` | hub-aware | `REACTIVE`, `PROACTIVE`, `S_ONLY`, `K_ONLY` |
| `HubAwareTftRouter.sMax` | hub-aware | batas atas faktor strata |
| `TftLoadReport.hubFraction` | bersama | porsi node yang dianggap hub untuk evaluasi |

## Metrik (TftLoadReport)

`delivery_ratio`, `avg_delay`, `overhead_ratio`, `tft_drops`,
`gini_relay_load` (semua node), `gini_among_hubs`, `max_relay_load`,
`hub_share`, `hub_utilization`, serta per node: `unique_contacts`,
`relay_load`, `planned_by_sources`, `avg_eps_checked`, `tft_denials`.

## Hasil acuan (trace sintetis, 5 seed, beban per interval, C = 20)

Dipakai sebagai uji regresi: setelah mengubah kode, hasil skenario yang sama
harus tetap sama kecuali perubahannya memang disengaja.

| Skema | Delivery | Dibuang | Gini semua | Gini antar-hub | Beban maks. |
|---|---|---|---|---|---|
| baseline_COOP_NOTFT | 0,974 | 0 | 0,900 | 0,447 | 1338 |
| baseline_SELFISH_NOTFT | 0,162 | 0 | 0,000 | 0,000 | 0 |
| baseline_GTFT | 0,841 | 155 | 0,785 | 0,232 | 911 |
| hubaware_REACTIVE | 0,722 | 329 | 0,768 | 0,187 | 735 |
| hubaware_PROACTIVE | 0,928 | 38 | 0,826 | 0,185 | 912 |
| hubaware_S_ONLY | 0,968 | 8 | 0,892 | 0,415 | 1288 |
| hubaware_K_ONLY | 0,580 | 272 | 0,603 | 0,156 | 495 |

## Penyederhanaan yang masih ada (TODO)

Baseline:
1. Contrition belum diimplementasikan.
2. Prediksi link memakai statistik kumulatif, belum EWMA (paper: alpha = 0,8).
3. Kapasitas link dan trafik latar belakang belum masuk TFTCap.
4. Control plane ideal: link state, ACK, dan beban relay sampai ke semua node
   pada batas interval tanpa biaya; overhead control plane belum dihitung.
5. TFTCap untuk link selain hop pertama memakai perkiraan pemakaian source sendiri.
6. Pesan dikirim langsung bila tujuan sedang terhubung.

Hub-aware: tidak ada perubahan di luar fungsi epsilon.
