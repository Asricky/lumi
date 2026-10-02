<div align="center">
  <img src="docs/brand/lumi-icon.png" width="128" alt="Ikon Lumi" />
  <h1>Lumi</h1>
  <p><strong>Kenali uangmu. Rencanakan hari esok.</strong></p>
  <p>Catatan keuangan pribadi dari notifikasi bank dan dompet digital.<br />Diproses di perangkat, bisa digunakan offline, tanpa login rekening.</p>
  <p>
    <a href="#download-aplikasi">Download APK</a> ·
    <a href="docs/USER_GUIDE.md">Panduan penggunaan</a> ·
    <a href="CHANGELOG.md">Riwayat pembaruan</a>
  </p>
</div>

---

<!-- LATEST_RELEASE_START -->
## Download aplikasi

[**Download Lumi - app-debug-v9.apk**](https://github.com/Asricky/lumi/raw/refs/heads/main/app/build/outputs/apk/debug/app-debug-v9.apk)

**v9 / 1.0.9** &nbsp; | &nbsp; Android 10+ &nbsp; | &nbsp; [Semua versi](app/build/outputs/apk/debug)

### Yang baru

- Beranda dan Rencana menampilkan **Sisa budget hari ini**, budget awal, pemakaian, serta cadangan transportasi terpisah. Konfirmasi saldo tetap menghitung belanja sebelum konfirmasi sebagai pemakaian hari ini tanpa memotong saldo dua kali.
- Transportasi dijelaskan sebagai hari perjalanan × tarif, dikurangi bagian yang sudah dibayar hari ini. Top-up dompet sendiri bukan pengeluaran; pembayaran perjalanan memakai cadangan transportasi.
- Catatan tersedia pada rekening, investasi, aset lain, dan utang. Aset manual dapat dihapus permanen; menghapus rekening dari aset aktif tetap menjaga riwayat transaksi. Migrasi database mempertahankan data lama.
- Kolom nominal nol menjadi placeholder sehingga angka baru tidak tersambung dengan nol bawaan. Kalkulator memakai hasil yang lebih jelas tanpa tulisan “Tekan =”.
- Transfer masuk SeaBank dengan format “kamu menerima transfer saldo senilai Rp647.500” terbaca sebagai pemasukan. Pesan lama yang gagal bisa dibaca ulang dari Tinjauan dengan konfirmasi; pesan sebelum koreksi saldo tidak dimasukkan ulang.
- Lumi tidak lagi bingung terus karena tinjauan lama saat budget aktif masih aman. Jumlah catatan yang perlu diperiksa tetap terlihat di Beranda.
- Validasi: 175 pengujian lulus tanpa gagal/skip; lint 0 error, 14 peringatan. Migrasi Room v1–v5 ke v6, pemrosesan SeaBank, konfirmasi saldo, dan alur catatan/hapus aset diuji. Render layar kecil diperiksa; belum diuji pada HP fisik.

<details>
<summary>Lokasi file &amp; verifikasi unduhan</summary>

File: `app/build/outputs/apk/debug/app-debug-v9.apk`

SHA-256:

```text
3EF595786566F7A80BDD7922EDD1438B05F1C44BD920E5214243FC90B2FC62AE
```

</details>
<!-- LATEST_RELEASE_END -->

## Uangmu, lebih mudah dipahami

| Fitur | Yang bisa kamu lakukan |
| --- | --- |
| **Catatan otomatis** | Membaca notifikasi BCA mobile/myBCA, SeaBank, ShopeePay, dan GoPay, dengan pemeriksaan duplikat. |
| **Tinjauan praktis** | Baca pesan, coba baca ulang format yang diperbaiki, pilih beberapa catatan, lalu hapus sekaligus. |
| **Laporan bulanan PDF** | Preview laporan berdesain, evaluasi Lumi, rekomendasi bulan berikutnya, lalu download ke lokasi pilihanmu. |
| **Kalender keuangan** | Lihat pemasukan, pengeluaran, selisih harian, dan tren pengeluaran per bulan. |
| **Rencana sampai gajian** | Pisahkan kebutuhan wajib dan belanja; saran sementara tetap tersedia saat rencana belum diperbarui. |
| **Aset & kalkulator** | Koreksi saldo, tambah catatan aset, hapus aset, dan simulasikan rencana uang. |
| **Temani hari bersama Lumi** | Sembilan ekspresi mengikuti kondisi keuangan, dengan tema terang/gelap dan pilihan privasi nominal. |

## Mulai dalam beberapa langkah

1. Unduh APK terbaru di atas, lalu pasang pada **Android 10 atau lebih baru**.
2. Isi rekening dan saldo awal sesuai keadaan sebenarnya.
3. Buka **Pengaturan → Pemantauan notifikasi**, lalu berikan akses notifikasi.
4. Pastikan notifikasi transaksi aplikasi bank aktif. Tinjau catatan yang belum dikenali sebelum menggunakannya sebagai acuan.

**Sudah memakai versi sebelumnya?** Pasang sebagai pembaruan tanpa uninstall. Nama aplikasi menjadi **Lumi**, dengan identitas instalasi dan sertifikat yang tetap sama agar data tersimpan.

## Buat laporan bulanan

Buka **Lainnya → Laporan bulanan** atau **Kalender → Laporan bulanan & PDF**, pilih bulan, lalu **Buat preview PDF**. Gunakan pengatur perbesaran dan tombol halaman untuk membaca. Tekan **Simpan / download PDF** dan pilih folder, misalnya Download.

Nominal disamarkan secara default. Centang **Sertakan nominal dalam PDF** bila ingin laporan lengkap. Preview dan file unduhan sama; bulan berjalan ditandai sementara. Laporan mencakup catatan yang tersimpan di Lumi, bukan rekening koran bank.

## Privasi dan kendali

Data finansial diproses lokal. Lumi tidak meminta kata sandi bank, memindahkan uang, atau membeli investasi. Nominal bisa disembunyikan, dan kunci biometrik tersedia.

Saldo adalah estimasi dari saldo awal dan catatan yang diterima. Rekomendasi budget tidak mengubah anggaranmu otomatis. Android dapat membatasi pemantauan latar belakang; setelah **Paksa berhenti**, buka aplikasi lagi. [Panduan pemantauan, cadangan, dan pemecahan masalah →](docs/USER_GUIDE.md)

## Pengembangan

Kotlin · Jetpack Compose · Room · DataStore · WorkManager

```powershell
# Uji, periksa lint, buat APK bernomor berikutnya, dan perbarui README
.\build-update.ps1 -Offline
```

Setiap rilis menyertakan catatan di `CHANGELOG.md`. Skrip memperbarui **tautan download terbaru, nomor versi, dan checksum** secara otomatis setelah build berhasil. APK tersedia di `app/build/outputs/apk/debug/` dengan urutan `app-debug-v1.apk`, `app-debug-v2.apk`, dan seterusnya.

| Dokumentasi | Isi |
| --- | --- |
| [Panduan penggunaan](docs/USER_GUIDE.md) | Instalasi, izin, perhitungan, privasi, cadangan, dan build |
| [PRD](PRD.md) | Perilaku produk dan aturan finansial |
| [DESIGN](DESIGN.md) | Identitas Lumi serta konsistensi tampilan |
| [CHANGELOG](CHANGELOG.md) | Catatan setiap versi |

Pengujian otomatis mencakup parser, ledger, migrasi, navigasi, pemantauan, dan rendering UI. Hasil rilis terbaru tercantum di changelog; pengujian otomatis tidak menggantikan pemeriksaan pada HP fisik.
