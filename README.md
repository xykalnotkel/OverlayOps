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

| Varian | Link Unduh | Ukuran | Catatan |
|---|---|---|---|
| **Release APK (Disarankan)** | **[OverlayOps-1.2.0-release.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.2.0/OverlayOps-1.2.0-release.apk)** | **1,90 MB** | Signed Keystore Resmi, R8 Minified, Anti-Lag Engine |
| Debug APK (Troubleshooting) | [OverlayOps-1.2.0-debug.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.2.0/OverlayOps-1.2.0-debug.apk) | ~6,0 MB | Logging logcat aktif, unstripped |
| Tag Releases | [GitHub Releases v1.2.0](https://github.com/xykalnotkel/OverlayOps/releases) | — | Semua rilisan & changelog |
| Build Log CI | [GitHub Actions](https://github.com/xykalnotkel/OverlayOps/actions) | — | Build otomatis multi-runner |

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

## 🆕 Yang Baru di v1.2.0

1. **⚡ Optimistic Real-Time UI (0ms Feedback)**:
   - Saat mengubah status di bottom sheet, label dan warna chip di list langsung berganti dalam 0 milidetik seketika tanpa nunggu background task!
   - Otomatis rollback mulus jika eksekusi sistem ditolak oleh ROM.
2. **🚀 Anti-Lag Engine**:
   - `AppsRepository.loadApps`: Pre-fetch `SYSTEM_ALERT_WINDOW` secara batch sekaligus dalam 1 panggilan IPC (mengeliminasi 350+ query binder individual yang bikin freeze).
   - `AppListAdapter`: Dukungan **Partial Payload Diffing (`PAYLOAD_STATUS`)**. Saat status op berubah, hanya chip yang di-update tanpa me-render ulang ikon atau layout row.
3. **✨ Built in XyVerse**:
   - Subtitle header & dialog Tentang OverlayOps kini menampilkan identitas ekosistem resmi XyVerse.
4. **🌐 Landing Website & VirusTotal Audit**:
   - Website unduhan interaktif ala App Ops bertema *Cyber-Obsidian Dark*.
   - Screenshot audit VirusTotal 0/72 Clean dan panduan verifikasi hash.

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
2. Install **OverlayOps Release APK**.
3. Buka OverlayOps → tekan **Minta izin** → Izinkan selalu.
4. Ketuk app untuk detail 19 AppOps, atau ketuk chip status / tahan lama baris untuk mengubah mode overlay.

---

## 🛠️ Build dari Source

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash ./gradlew assembleDebug
```

Rilis ditandatangani otomatis di GitHub Actions lewat secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
