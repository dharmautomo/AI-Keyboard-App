# Troubleshooting Guide - AI Keyboard App

## Masalah Umum dan Solusinya

### 1. Aplikasi Terus Berhenti (Crash) Saat Dijalankan

#### **Penyebab: AI Provider URL Kosong**

- **Gejala:** Aplikasi crash saat mencoba menggunakan fitur AI
- **Solusi:**
  - Pastikan `KB_RELAY_BASE_URL` diset dengan benar di `app/build.gradle`
  - Untuk development, gunakan dummy URL seperti `"https://httpbin.org/stream/1"`
  - Untuk production, set URL relay yang valid

#### **Penyebab: BuildConfig Feature Disabled**

- **Gejala:** Error "defaultConfig contains custom BuildConfig fields, but the feature is disabled"
- **Solusi:**
  - Pastikan `android.buildFeatures.buildConfig true` ada di `build.gradle`
  - Atau gunakan dummy URL yang sudah dikonfigurasi

#### **Penyebab: Native Library Gagal Dimuat**

- **Gejala:** `UnsatisfiedLinkError` di logcat
- **Solusi:**
  - Pastikan NDK terinstall dengan benar di Android Studio
  - Clean dan rebuild project
  - Periksa versi NDK di `app/build.gradle` (ndkVersion)

#### **Penyebab: Memory/Resource Issues**

- **Gejala:** `OutOfMemoryError` atau crash tanpa error spesifik
- **Solusi:**
  - Restart Android Studio
  - Clean project dan invalidate caches
  - Pastikan device/emulator memiliki memory yang cukup

### 2. Fitur AI Tidak Berfungsi

#### **Penyebab: URL Relay Tidak Valid**

- **Solusi:**
  - Periksa `KB_RELAY_BASE_URL` di BuildConfig
  - Test URL dengan browser atau Postman
  - Pastikan relay server berjalan dan accessible

#### **Penyebab: Dummy URL Mode**

- **Gejala:** AI features memberikan mock response
- **Solusi:**
  - Ini normal untuk development mode
  - Ganti URL dengan relay server yang valid untuk production
  - Mock response membantu testing tanpa crash

#### **Penyebab: Network Issues**

- **Solusi:**
  - Periksa permission internet di AndroidManifest.xml
  - Test koneksi internet device/emulator
  - Periksa firewall atau proxy settings

### 3. Keyboard Tidak Muncul

#### **Penyebab: Setup Belum Selesai**

- **Solusi:**
  - Jalankan SetupWizardActivity
  - Aktifkan keyboard di Settings > System > Languages & input
  - Pilih keyboard sebagai default input method

#### **Penyebab: Permission Issues**

- **Solusi:**
  - Pastikan semua permission sudah diberikan
  - Restart device setelah install
  - Periksa AndroidManifest.xml untuk permission yang diperlukan

### 4. Build Errors

#### **Penyebab: Gradle Sync Issues**

- **Solusi:**
  - Sync project dengan Gradle files
  - Update Gradle wrapper
  - Periksa versi Android Gradle Plugin

#### **Penyebab: NDK Build Issues**

- **Solusi:**
  - Install NDK yang sesuai dengan versi di build.gradle
  - Periksa path NDK di Android Studio settings
  - Clean dan rebuild project

## Langkah Debug yang Disarankan

### 1. Periksa Logcat

```bash
# Filter logcat untuk error yang relevan
adb logcat | grep -E "(LatinIME|AiProvider|JniUtils|SetupWizard)"
```

### 2. Test Build Variants

```bash
# Build project
./gradlew assembleDebug
```

### 3. Test Native Library

```bash
# Periksa apakah native library berhasil dimuat
adb logcat | grep "JniUtils"
```

### 4. Test AI Features

```bash
# Periksa AI provider logs
adb logcat | grep "AiProvider"
```

## Konfigurasi Development

### 1. Dummy URL Mode (Development)

- AI features enabled dengan mock responses
- Tidak ada crash karena network issues
- Berguna untuk testing UI dan flow

### 2. Production Mode

- AI features menggunakan relay server yang valid
- Real AI responses
- Proper error handling

## Environment Variables

### 1. Development

```bash
# Gunakan dummy URL di build.gradle
buildConfigField "String", "KB_RELAY_BASE_URL", '"https://httpbin.org/stream/1"'
```

### 2. Production

```bash
# Set URL relay yang valid
export KB_RELAY_BASE_URL="https://your-prod-relay.com"
```

## Dependencies yang Diperlukan

### 1. Android Studio

- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0
- NDK (Side by side) 25.1.8937393
- CMake 3.22.1

### 2. Gradle

- Gradle 8.5
- Android Gradle Plugin 8.1.4
- Kotlin 1.9.10

## Support

Jika masalah masih berlanjut:

1. Periksa logcat untuk error spesifik
2. Pastikan semua dependencies terinstall
3. Test dengan device/emulator yang berbeda
4. Buat issue di repository dengan detail error yang lengkap
