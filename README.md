# 🎬 CinePro

App rakam video cinematic untuk telefon Honor 200. Filter warna dan kesan lensa dipaparkan **live** semasa merakam, dan terus masuk ke dalam fail video.

## Ciri utama

| Bahagian | Isi |
|---|---|
| **WARNA** | Asli, ARRI, Canon, Harcourt (hitam putih), Teal & Oren, Kodak Print (dengan slider kekuatan) |
| **LENSA** | Black Mist, Tiffen Pro-Mist, CineBloom (dengan slider kekuatan) |
| **FILEM** | Letterbox (2.39:1 landskap / 4:5 potret), Film Grain, Halation, Vignette (on/off) |
| **KAMERA** | 24/30 fps, Shutter 1/48, Kunci AE/AF, White Balance, slider Terang |
| **PRESET** | Drama Malam, Iklan Raya, Hitam Putih Harcourt, Blockbuster, Senja Mimpi, serta preset awak sendiri |
| **Audio** | Mic telefon atau mic luar USB-C (dikesan automatik), dengan meter level |
| **Simpan** | Galeri › album **CinePro** (folder `DCIM/CinePro`) |

Rakaman default ialah 1080p 24fps. Antara muka bergaya moden kaca, untuk landskap dan potret.

> Nota: filter ini ialah **gaya yang diilhamkan** oleh rupa ARRI, Canon, Harcourt, Tiffen dan CineBloom. Ia bukan LUT atau produk rasmi jenama tersebut.

---

## 📘 Panduan langkah demi langkah

### Langkah 1: Buat repo GitHub (sekali sahaja)
1. Buka **github.com/new** (log masuk akaun `mohdrozaimi205`).
2. **Repository name:** tulis `CinePro`.
3. Pilih **Public**.
4. **JANGAN** tanda "Add a README file".
5. Tekan butang hijau **Create repository**.
6. Beritahu Claude: **"repo CinePro dah siap"**. Claude akan hantar semua fail ke repo ni.

### Langkah 2: Tunggu APK siap dibina (automatik)
1. Buka **github.com/mohdrozaimi205/CinePro**.
2. Tekan tab **Actions**. Akan ada satu binaan "Bina APK CinePro" sedang berjalan (bulatan kuning).
3. Tunggu lebih kurang 5–8 minit sampai tanda ✅ hijau muncul.
4. Kalau keluar tanda ❌ merah, beritahu Claude: **"tolong debug"**.

### Langkah 3: Muat turun dan pasang di telefon Honor
1. Di telefon, buka **github.com/mohdrozaimi205/CinePro/releases**.
2. Tekan fail **CinePro-v1.0.x.apk** untuk muat turun.
3. Buka fail tu. Kalau telefon minta izin, pergi **Tetapan › benarkan "Pasang app tidak diketahui"** untuk Chrome/Fail.
4. Tekan **Pasang** dan buka **CinePro**.
5. Benarkan izin **Kamera** dan **Mikrofon**.

### Langkah 4: Simpan dalam laptop
- Fail projek: `D:\Projek Android Studio Apps\CinePro`
- Fail APK: `D:\Projek Android Studio Apps\CinePro\APK\`
- Untuk buka dalam Android Studio: **File › Open** › pilih folder `CinePro` › tunggu "Gradle sync" siap.

### Langkah 5: Simpan APK ke Google Drive
- Upload fail APK ke folder **My Drive › Apk Android Studio**.

---

## 🎥 Cara guna CinePro
1. **Pilih rupa:** tekan tab **PRESET** dan pilih contohnya "Drama Malam". Atau atur sendiri di tab **WARNA** dan **LENSA**.
2. **Ketik skrin** untuk fokus pada subjek.
3. Tekan **butang merah** untuk mula rakam. Tekan sekali lagi untuk berhenti.
4. Video disimpan terus dalam **Galeri › CinePro**. Tekan gambar kecil di tepi untuk tonton.
5. **Simpan gabungan sendiri:** tab PRESET › **+ Simpan** › beri nama. Tekan lama pada preset sendiri untuk padam.
6. Tekan tab yang sedang aktif sekali lagi untuk sembunyikan panel.

### Tips cinematic
- Guna **24 fps** dan hidupkan **Shutter 1/48** supaya gerakan nampak lembut macam filem. Bila shutter ON, kecerahan dikawal melalui slider **Terang**.
- Di bawah lampu kalimantang (50Hz) yang berkelip, matikan Shutter 1/48.
- **Black Mist 30–50%** paling natural. CineBloom sesuai untuk suasana mimpi atau malam berlampu.
- Rakam **landskap** untuk letterbox 2.39:1 ala panggung wayang.

---
Dibina dengan Kotlin + CameraX + OpenGL ES. APK dibina automatik oleh GitHub Actions dan diterbitkan ke Releases.
