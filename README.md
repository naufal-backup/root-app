# Root App — Android media dari folder root pilihan

Aplikasi Android (Kotlin + Compose) untuk pilih folder root via SAF lalu fetch semua media rekursif.

Fitur:
- Pilih Folder Root (`OpenDocumentTree` + persist permission)
- Fetch rekursif `DocumentFile`, filter image/video/audio
- Filter chips: Semua / Gambar / Video / Audio + search nama file
- Counter: `filtered/total • 🖼 img • 🎬 vid • 🎵 aud`
- Preview: tap item -> dialog full-screen
  - Gambar: Coil full
  - Video/Audio: ExoPlayer (Media3) dengan controller play/pause/seek

Build lokal:
1. Install Android Studio (JDK 17 + SDK API 34)
2. Buka folder `root-app`, sync Gradle, Run (minSdk 26)

Build via GitHub Actions:
- Workflow: `.github/workflows/android.yml`
- Push repo ini ke GitHub (root repo = folder `root-app`), Action otomatis jalan di push ke `main/master`, PR, atau manual `workflow_dispatch`
- Steps: JDK 17 + Android SDK 34 + Gradle 8.7 + `gradle assembleDebug`
- Hasil: artifact `app-debug` (`app/build/outputs/apk/debug/app-debug.apk`)
- Tidak butuh `gradlew` committed, pakai Gradle system 8.7
