# Aturan Kerja Sama Repository

1. **File milik satu orang hanya diubah pemiliknya.**
   - Baseline (rekan): `src/routing/ShevadeTftRouter.java`, `settings/baseline/`
   - Hub-aware (Citha): `src/routing/HubAwareTftRouter.java`,
     `src/routing/hubaware/`, `settings/hubaware/`
2. **File bersama diubah lewat pull request dan disetujui berdua.**
   `src/routing/tft/`, `src/report/TftLoadReport.java`, `settings/common/`,
   `toolkit/tft/`.
3. **Jika hub-aware membutuhkan perubahan di baseline** (misalnya fungsi baru
   yang bisa ditimpa), ajukan pull request ke pemilik baseline.
4. **Branch**: `main` (stabil), `baseline-dev`, `hubaware-dev`.
5. **Sebelum mengambil data skripsi, beri tag** (misalnya `v1.0`) dan catat
   tag tersebut di Bab IV.
6. **Uji regresi**: setelah perubahan, jalankan
   `python toolkit/tft/run_seeds.py --seeds 5` dan bandingkan dengan tabel
   "Hasil acuan" di README_TFT.md. Jika berubah, jelaskan alasannya di pesan
   commit.
