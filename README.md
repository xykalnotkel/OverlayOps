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

| Varian | Link Unduh | Ukuran | SHA-256 | Catatan |
|---|---|---|---|---|
| **Release APK (Disarankan)** | **[AppsPerms-1.3.0-release.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.3.0/AppsPerms-1.3.0-release.apk)** | **1,87 MB** | `f0439e13…38cac` | Signed keystore resmi, R8 minified |
| Debug APK (Troubleshooting) | [AppsPerms-1.3.0-debug.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.3.0/AppsPerms-1.3.0-debug.apk) | ~5,9 MB | `35d3cf58…d1d74` | Logging logcat aktif, unstripped |
| Tag Releases | [GitHub Releases](https://github.com/xykalnotkel/OverlayOps/releases) | — | — | Semua rilisan & changelog |
| Build Log CI | [GitHub Actions](https://github.com/xykalnotkel/OverlayOps/actions) | — | — | Build + unit test otomatis |

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

---

## 🛠️ Build dari Source

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash ./gradlew assembleDebug
```

Rilis ditandatangani otomatis di GitHub Actions lewat secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
