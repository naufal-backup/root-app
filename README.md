# Whatsapp viewonce autosaver

Aplikasi Android (Kotlin + Jetpack Compose, Material3) untuk mengamankan file sekali-lihat
WhatsApp (`.../files/ViewOnce`) sebelum terhapus otomatis — plus browser & galeri media untuk
folder root (SAF maupun superuser).

Repo: https://github.com/naufal-backup/whatsapp-viewonce-autosaver

## Fitur

### 🖼 Tab Media
- Sumber ganda: folder SAF (`OpenDocumentTree` + persist permission) atau path superuser.
- Fetch rekursif (`DocumentFile` / `find` via `su` + `nsenter` ke namespace `system_server`).
- Filter chips Semua / Gambar / Video / Audio + SearchBar dengan saran + debounce.
- Grid 3 kolom paginasi (200 awal + tombol muat lagi), thumbnail 300px, frame video via Coil.
- Preview full-screen: gambar (Coil), video/audio (ExoPlayer Media3).
- Riwayat folder terakhir — tap untuk auto-fetch tanpa pilih ulang.

### 📁 Tab Browser (superuser)
- Jelajah filesystem sebagai root: `/`, `/data/data`, `/data/app`, `/sdcard`, navigasi ↑.
- Cari nama file/folder rekursif (depth ≤ 4, debounce).
- Ketuk file → simpan manual ke folder simpan (mode Manual).
- "Pakai folder ini" → jadikan sumber tab Media.

### 🛡 Tab Jaga (anti-hapus ViewOnce)
- Default sumber: `/data/data/com.whatsapp/files/ViewOnce`, simpan: `/sdcard/ViewOnceSaved`
  (keduanya bisa diketik / dipilih).
- **Mode Otomatis**: foreground service cek tiap 2–10 dtk, salin file baru + `chattr +i`
  aslinya. Lanjut setelah reboot (BootReceiver). Status + log 30 terakhir.
- **Mode Manual**: tanpa copy otomatis — simpan lewat ketuk file / tombol 💾 Simpan di preview.
- **Script permanen**: pasang `zzz_rootapp_watch.sh` ke `/data/adb/service.d` — jalan saat
  boot dan tetap hidup walau app ditutup/force-stop. Ada Stop + Hapus.

## Syarat

- Android 8+ (minSdk 26). Fitur superuser butuh HP rooted (Magisk/KernelSU) + grant root.
- `chattr +i` butuh filesystem yang mendukung; salinan di folder simpan tetap aman bila gagal.

## Build & rilis

- Lokal: Android Studio, JDK 17, SDK API 34 → Run.
- CI: `.github/workflows/android.yml` (push ke `main` → build debug APK → artifact +
  GitHub Release `Whatsapp viewonce autosaver N`).
- Catatan: tiap build CI memakai debug key baru — uninstall dulu bila
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` saat `adb install -r`.

## Struktur kode

```
app/src/main/java/com/tb/rootapp/
  MainActivity.kt     → 3 tab (Media/Browser/Jaga) + grid + preview + player
  RootBrowser.kt      → konten browser (dipakai tab + dialog picker)
  RootHelper.kt       → su via nsenter system_server: ls/find/search/cp/cat/chattr
  MediaRepository.kt  → scan SAF rekursif
  WatchService.kt     → foreground service penjaga (+ BootReceiver)
  WatchScript.kt      → install/start/stop script service.d
  WatchStore.kt       → konfigurasi + riwayat (SharedPreferences)
  WatchLog.kt / ManualSave.kt / MediaItem.kt / AppConfig.kt
```
