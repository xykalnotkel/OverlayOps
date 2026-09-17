# AppsPerms

> **Catatan v1.3.0:** nama paket berubah `app.overlayops` → `app.appsperms`, jadi Android
> menganggapnya app baru — uninstall versi OverlayOps dulu sebelum memasang AppsPerms.

Aplikasi Android modern & super ringan untuk mengelola **AppOps tersembunyi** — fokus utama op
`SYSTEM_ALERT_WINDOW` (**Display over other apps**), plus 18 op penting lainnya (kamera, mikrofon,
clipboard, lokasi, wakelock, dst).

✨ **Built in XyVerse** oleh [Xykal (@xykalnotkel)](https://github.com/xykalnotkel).  
Terinspirasi dari [App Ops by Rikka](https://appops.rikka.app/), namun dibuat jauh lebih ramping (~1.9 MB),
responsif tanpa jeda (**0ms Optimistic UI**), dan **tanpa root permanen** lewat **Shizuku**.

---

## 🌐 Website Resmi & Audit Keamanan

Kunjungi landing page lengkap OverlayOps:
👉 **[AppsPerms — appsperms.haekal.web.id](https://appsperms.haekal.web.id/)**  
*(Domain cadangan: <https://xykalnotkel.github.io/OverlayOps/> — otomatis dialihkan ke domain utama)*

- **Scan Antivirus VirusTotal**: **0/72 Clean (100% Undetected)**
- **Audit Privasi**: 0 permission internet di `AndroidManifest.xml` (data mustahil keluar dari perangkat).
- **Keystore Resmi**: Ditandatangani RSA 4096-bit resmi XyVerse.

---

## ⬇️ Download

| **Varian v1.4.1** (v1.4.0 tetap tersedia di arsip rilis) | Link Unduh | Ukuran | SHA-256 | Catatan |
|---|---|---|---|---|
| **Release APK (Disarankan)** | **[AppsPerms-1.4.1-release.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.4.1/AppsPerms-1.4.1-release.apk)** | 1,98 MB | *live* → `docs/data/virustotal.json` | Signed keystore resmi, R8 minified |
| Debug APK (Troubleshooting) | [AppsPerms-1.4.1-debug.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.4.1/AppsPerms-1.4.1-debug.apk) | 5,99 MB | *live* → `docs/data/virustotal.json` | Logging logcat aktif, unstripped |
| Arsip v1.3.0 | [GitHub Releases](https://github.com/xykalnotkel/OverlayOps/releases) | 1,87 MB | `f0439e13…38cac` | SHA-256 lengkap di halaman rilis |
| Build Log CI | [GitHub Actions](https://github.com/xykalnotkel/OverlayOps/actions) | — | — | Build + unit test + **scan VirusTotal otomatis** |

> ℹ️ Mulai prosedur rilis v1.4.0, **hash & hasil scan VirusTotal tidak pernah ditulis manual lagi** —
> CI mengisinya otomatis per tag (lihat bagian 🔬 di bawah), jadi tabel ini tidak bisa basi.

> ⚠️ **Penting:** sejak v1.3.0 `applicationId` berubah dari `app.overlayops` menjadi `app.appsperms`.
> Android menganggapnya aplikasi baru — **uninstall versi OverlayOps dulu**, baru pasang AppsPerms
> dan berikan izin Shizuku sekali lagi. Untuk memindahkan konfigurasi: **Backup konfigurasi** di versi lama
> → **Restore** di versi baru.

### 🔐 Fingerprint Sertifikat Resmi

```
SHA-256 : 82:A0:2C:AE:E2:7B:8B:9B:09:E7:00:B8:31:3D:D4:AD:E5:CF:9B:94:6B:01:BE:6C:54:EC:33:98:4A:B0:04:30
SHA-1   : 99:55:67:5C:19:0C:BB:4B:0F:D5:08:94:74:08:D2:B8:92:22:69:FF
MD5     : 86:4C:BF:D8:C3:9B:A3:F3:89:B8:5D:64:C2:A1:B7:D6
Alias   : overlayops (CN=OverlayOps, OU=Release, O=xykalnotkel, C=ID)
```

> ⚠️ **Catatan Penting**: Signature release berbeda dari build debug awal. Sebelum menginstall versi release, **uninstall dulu versi debug lama**, baru pasang versi release dan berikan izin Shizuku sekali lagi.

### ❓ Kenapa Tidak Ada di Google Play Store?

Bukan karena malware ataupun virus (terbukti 0/72 di VirusTotal & tanpa izin internet).
Alasannya:
1. **Keterbatasan modal**: Biaya pendaftaran akun Google Play Console ($25 USD) dan birokrasi verifikasi korporat/identitas yang ketat untuk pengembang independen.
2. **Kebijakan Google Play**: Google semakin memperketat dan membatasi aplikasi yang mengelola izin sistem / Shizuku.
3. Rilis mandiri via GitHub Releases menjaga OverlayOps tetap 100% bebas, tanpa iklan, dan tanpa pelacak.

---

> ⚠️ **Catatan v1.4.0 (repo):** fitur Tuning baru ada di source + build CI, **belum dirilis resmi** —
> tabel di atas masih merujuk v1.3.0 sampai tag `v1.4.0` di-push.


## 🆕 Yang Baru di v1.4.0 — Paket Tweak

Rilis ini mengubah AppsPerms dari "manajer AppOps" menjadi **perlengkapan tuning ringan**,
semuanya lewat shell Shizuku (tanpa root):

1. **🖥 Resolusi & DPI (`wm size` / `wm density`)**
   - Preset 50/65/75/90% yang mempertahankan rasio layar (pembulatan genap) + input manual `WxH`
     dengan validasi (tolak landscape, tolak >2× ukuran fisik, density dibatasi 120–640).
   - **Auto-revert 15 detik**: konfirmasi "Tetap / Kembalikan" berjalan di luar activity, jadi
     walaupun layar berubah ukuran dan activity dibuat ulang, override yang bikin tidak nyaman
     tetap dikembalikan otomatis. Tidak ada skenario layar terkunci.
   - Mode "density ikut otomatis" menjaga UI tetap proporsional saat resolusi diturunkan.
   - Catatan ROM: MIUI/HyperOS menolak perintah `wm` sebelum **Opsi pengembang →
     "USB debugging (Security settings)"** aktif — pesan error ROM ditampilkan apa adanya biar bisa dicopy.

2. **⚡ Animasi global** — satu tap ke 0× / 0.5× / 1× untuk `window_animation_scale`,
   `transition_animation_scale`, `animator_duration_scale` (ketiganya ditulis dalam satu perintah).

3. **🧹 Ringan sekejap** — `am kill-all` (hanya proses *cached*, foreground aman) dan
   `pm trim-caches 750MB` (mekanisme resmi, sistem yang memutuskan cache mana yang boleh dibuang).
   Sengaja **tanpa "booster RAM" palsu** dan tanpa `set-process-limit` (tidak ada di `am` shell AOSP
   dan `service call` antar-versi beda — bukan kompromi yang layak).

4. **👻 Perisai Anti Ghost-Touch** — layanan jendela overlay yang menempelkan **pita penangkap sentuhan**
   di sisi atas/bawah/kiri/kanan (tebal 12–96 dp, mode uji berwarna, peringatan kalau cakupan >30% layar).
   Sentuhan hantu di zona itu ditelan sebelum masuk ke app di bawah (Android 12+ memblokir sentuhan ke
   area tertutup overlay sebagai lapisan kedua).
   - Butuh op `SYSTEM_ALERT_WINDOW` untuk **diri sendiri** — dan karena AppsPerms adalah manajer op itu,
     grant-nya cukup satu tombol (*dogfooding*).
   - Jalur kabur: tahan 1,5 dtk pada pita membuka app, aksi notifikasi **Tahan 60 dtk** / **Matikan**,
     foreground service `specialUse` (Android 14-ready).
   - Jujur di UI: ghost-touch hardware murni (digitizer/charger) hanya diredam, bukan disembuhkan.

5. **🌐 i18n OpCatalog dituntaskan** — janji v1.3.0 ("seluruh teks dipindah ke resource") baru berlaku untuk
   chrome UI; judul + deskripsi 19 op dan header grup masih hardcode Indonesia di `OpCatalog.kt`. Sekarang
   semuanya `R.string.op_*` (ID + EN, 305↔305 string paritas) — user English tidak lagi lihat sheet "Privasi".

6. **⏱️ Riwayat & Undo massal** — tiap penulisan AppOps lewat app ini tercatat (`op_history.log`, 500 entri terakhir).
   Menu → **Riwayat & undo massal**: daftar perubahan + tombol **Undo semua** (tiap app+op kembali ke status
   TERAWAL yang tercatat, paket yang sudah ter-uninstall dilewati), **Salin** (laporan teks), **Hapus**.
   Ketuk satu baris → nama paket terisi di pencarian. Sekalian bug fix: early-return `applyStatus` dulunya
   bisa melewati penulisan op non-overlay kalau status overlay kebetulan sama — sudah diperbaiki.

7. ** Fondasi uji ikut naik** — `WmParser` (parsing `wm size|density`, validasi, preset), `GhostGuard`
   (geometri pita + fraksi cakupan) dan `HistoryCodec` (serialisasi riwayat + rencana undo) adalah fungsi murni;
   **24 unit test baru** (parser `wm`, geometri pita, codec riwayat) → **total 45**, semua hijau di `testDebugUnitTest`
   (parser density ikut tertangkap & diperbaiki oleh test 😄).

8. **🌐 Website: alur unduh & audit live** — tombol APK kini memicu **unduhan native browser** (tanpa redirect
   iframe; halaman Terima Kasih terbuka di tab baru, **tanpa tombol download lagi**). Hash, ukuran & skor
   VirusTotal di index.html/thanks.html dibaca dari **`docs/data/virustotal.json`** yang ditulis ulang CI tiap rilis.
   Situs di-serve **Cloudflare Pages** (`appsperms.haekal.web.id` → project `appsperms`) dan otomatis
   di-deploy ulang oleh step terakhir CI tiap ada perubahan `docs/` — GitHub Pages sudah dimatikan.
   Alur unduh: klik tombol → APK di-download di tab baru (native, tidak bisa gagal karena navigasi halaman)
   → tab utama pindah ke `thanks.html` setelah 600 ms.

### 🔬 Prosedur rilis (VirusTotal otomatis per tag)

```bash
git tag v1.4.0 && git push origin main v1.4.0
```
Lalu CI (`build.yml`) otomatis: unit test → build debug+release signed → **unggah APK release ke VirusTotal
via secret `VT_API_KEY`** → polling sampai selesai → menulis `docs/data/virustotal.json` + regenerate
kartu hasil scan (`docs/images/virustotal-report.png`, dari `docs/tools/make-vt-card.py`) → commit balik ke
`main` (perubahan `docs/**` tidak memicu build ulang) → append blok "Audit VirusTotal" + SHA-256 ke
**catatan rilis** (`body_path`). Tanpa `VT_API_KEY`, scan dilewati dengan warning — hash & ukuran tetap
dipublikasikan. Cara set secret sekali: repo → Settings → Secrets and variables → Actions → `VT_API_KEY`
(kunci gratis: VirusTotal → personal API key).

---

## 🆕 Yang Baru di v1.3.0

1. **🌐 Dua bahasa: Indonesia & English**
   - `values-en/strings.xml` baru + seluruh teks dipindah dari kode ke resource, jadi UI benar-benar bisa diterjemahkan.
   - Pilihan bahasa di menu Pengaturan (Ikuti sistem / Indonesia / English), lengkap dengan `locales_config.xml`
     sehingga di Android 13+ juga muncul di Pengaturan sistem → Aplikasi → AppsPerms → Bahasa.

2. **☰ Menu baru + layar Pengaturan**
   - Menu titik tiga tidak lagi daftar panjang: dikelompokkan jadi **Kelola / Diagnostik / Bantuan & info**
     dengan keterangan per aksi, dan memakai `MenuAction` enum (bukan nomor id) supaya salah peta aksi tidak mungkin.
   - Pengaturan: bahasa, urutan daftar bawaan, konfirmasi mode berisiko, peringatan UID bersama.

3. **🛡 Peringatan yang mencegah app rusak**
   - Konfirmasi + penjelasan sebelum menerapkan mode **Diblokir/Diabaikan** ke app yang memang meminta izin overlay.
   - Deteksi **UID bersama**: AppOps disimpan per-UID, jadi klon/profil kerja ikut berubah — sekarang diberi tahu dulu.

4. **🧪 Unit test + CI lebih rapi**
   - `core/AppOpsParser.kt` dipisah dari `ShizukuBridge` supaya murni dan bisa diuji JVM.
   - 20+ unit test: parser `appops` (format antar-ROM), pemetaan status, filter, dan state koneksi.
   - CI menjalankan unit test sebelum build, artefak dinamai `AppsPerms-*`, dan perubahan `docs/` tidak lagi memicu build APK.

5. **🐛 Perbaikan kecil**
   - Label Android TalkBack untuk tombol ikon, tinggi baris minimum 44–56 dp.
   - Nama kelas jadi `AppsPermsApp`, dan semua label UI konsisten memakai nama **AppsPerms**.

---

## 🔧 Cara Kerja & Shizuku

OverlayOps bekerja dengan hak akses shell Shizuku (`uid 2000` atau `root 0`) untuk memanggil perintah `appops set --uid` dan membaca status secara presisi.

```
┌──────────────┐      perintah appops / binder      ┌──────────────────┐        ┌─────────────────┐
│  OverlayOps  │ ─────────────────────────────────► │  uid 2000 / 0    │ ─────► │  AppOpsService  │
│ (XyVerse App)│         via Shizuku Bridge         │  Shizuku Server  │        │  (system_server)│
└──────────────┘                                    └──────────────────┘        └─────────────────┘
```

Tanpa root, tanpa Magisk, tanpa ADB terus-menerus. Cukup pairing Wireless Debugging di Android 11+.

---

## 🚀 Cara Pakai Cepat

1. Install dan jalankan **Shizuku** (via Wireless Debugging atau Root).
2. Install **AppsPerms Release APK**.
3. Buka AppsPerms → tekan **Minta izin** → Izinkan selalu.
4. Ketuk app untuk detail 19 AppOps, atau ketuk chip status / tahan lama baris untuk mengubah mode overlay.
5. Menu titik tiga di kanan atas: aksi massal, backup/restore, laporan perangkat, dan **Pengaturan** (bahasa, konfirmasi mode berisiko).
6. **Tuning** (menu → *Tuning performa & layar*): preset resolusi/DPI dengan auto-revert 15 dtk, animasi 0×, pembersih proses & cache, dan **perisai anti ghost-touch** (aktifkan tombol "Izinkan lewat Shizuku" sekali — AppsPerms meng-grant op overlay untuk dirinya sendiri).

---

## 🛠️ Build dari Source

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash ./gradlew assembleDebug
```

Rilis ditandatangani otomatis di GitHub Actions lewat secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
