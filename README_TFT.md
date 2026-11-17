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

## Mode control plane

- **distributed (default, sesuai paper).** Setiap node punya tabel link state
  sendiri (EWMA, link baru diumumkan setelah satu siklus OFF lengkap) yang
  ditukar saat kontak. Tujuan membuat ACK berisi rute yang dilalui pesan, lalu
  ACK disebar secara store-carry-forward: hanya antara dua node yang sedang
  berkontak, paling jauh satu hop per langkah simulasi. ACK yang baru diterima
  baru diteruskan pada langkah berikutnya, baik lewat pertukaran saat kontak
  dimulai maupun lewat kontak yang sedang berlangsung (aturan waktu yang sama). Setiap node menghitung kredit TFT dari ACK yang
  sudah sampai kepadanya (`NodeAckState`); kredit dari ACK yang diterima pada
  interval t-1 dipakai pada interval t. Derajat dan beban relay (khusus
  hub-aware) ikut menyebar bersama tabel link state.
- **ideal (cek internal saja).** `settings/common/control_ideal.cfg`. Semua
  informasi diketahui semua node pada batas interval tanpa keterlambatan. Tidak
  sesuai paper; dipakai untuk uji regresi dan mengukur pengaruh keterlambatan.

Setting terkait (namespace `TftControlPlane`): `linkStateMode`
(`distributed`/`ideal`), `ewmaAlpha` (0,8), `ackTtl` (7200 detik),
`enforceWindow` (`previous` = kredit dari ACK interval t-1, sesuai proposal;
`current` = kredit dari ACK yang sudah diterima pada interval berjalan, sesuai
bunyi aturan penolakan di Bagian V paper).

## Hasil acuan (trace sintetis, 5 seed, beban per interval, C = 20, mode distributed)

Dipakai sebagai uji regresi: setelah mengubah kode, hasil skenario yang sama
harus tetap sama kecuali perubahannya memang disengaja.

| Skema | Delivery | Dibuang | Gini semua | Gini antar-hub | Beban maks. | ACK dikirim |
|---|---|---|---|---|---|---|
| baseline_COOP_NOTFT | 0,970 | 0 | 0,883 | 0,386 | 1260 | 15429 |
| baseline_SELFISH_NOTFT | 0,162 | 0 | 0,000 | 0,000 | 0 | 2570 |
| baseline_GTFT | 0,834 | 148 | 0,782 | 0,219 | 886 | 13283 |
| hubaware_REACTIVE | 0,726 | 308 | 0,768 | 0,176 | 724 | 11557 |
| hubaware_PROACTIVE | 0,722 | 345 | 0,803 | 0,163 | 684 | 11498 |
| hubaware_PROACTIVE + replan saat kontak | 0,885 | 87 | 0,803 | 0,180 | 862 | 14099 |
| hubaware_S_ONLY | 0,957 | 18 | 0,878 | 0,361 | 1205 | 15228 |
| hubaware_K_ONLY | 0,555 | 291 | 0,625 | 0,157 | 474 | 8843 |

Dengan `enforceWindow = current` hasilnya hampir sama (GTFT 0,835; PROACTIVE
dengan replan saat kontak 0,884).

Dengan `ShevadeTftRouter.repFactor = 3`: GTFT 0,789 (beban maks. 968, Gini
antar-hub 0,173); PROACTIVE + replan saat kontak 0,843 (beban maks. 1178, Gini
antar-hub 0,167).

Mode ideal (cek internal), untuk perbandingan: GTFT 0,841; REACTIVE 0,722;
PROACTIVE 0,928. Laporan mode ideal identik dengan kode sebelum ACK distributed
ditambahkan (kecuali baris baru `control_mode` dan `ack_transmissions`).

### Replan saat kontak (modifikasi, bukan bagian paper)

Di mode distributed, source hanya tahu beban dan derajat relay dari tabel yang
ia terima lewat kontak, jadi infonya bisa basi. PROACTIVE lalu menghitung
epsilon berbeda dari yang ditegakkan relay dan banyak pesan ditolak. Setting
`HubAwareTftRouter.replanOnContact = true`
(`settings/hubaware/replan_on_contact.cfg`) membuat source langsung memakai tabel
baru itu untuk merencanakan ulang pesan miliknya. Kuota TFTCap yang sudah
dipesan pesan yang masih menunggu dilepas dulu agar tidak terhitung dua kali.
Paper hanya menjalankan Fig. 4 di awal interval, jadi ini termasuk modifikasi
hub-aware. GTFT baseline tidak memakainya.

## Penyederhanaan yang masih ada (TODO)

Baseline:
1. Contrition belum diimplementasikan.
2. (Selesai di mode distributed) Prediksi link memakai EWMA alpha = 0,8.
3. Kapasitas link dan trafik latar belakang belum masuk TFTCap.
4. (Sebagian selesai) Link state dan ACK kini distributed. Pertukaran tabel
   dan ACK dianggap selalu muat di setiap kontak (tidak memakai bandwidth atau
   buffer data); jumlah ACK yang dikirim dicatat sebagai `ack_transmissions`.
   Tanda tangan rute (HIBC) belum dimodelkan: ACK dianggap selalu lolos
   verifikasi.
5. TFTCap untuk link selain hop pertama memakai perkiraan pemakaian source sendiri.
6. Pesan dikirim langsung bila tujuan sedang terhubung.

Hub-aware: tidak ada perubahan di luar fungsi epsilon.
