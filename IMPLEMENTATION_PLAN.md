# Pembaruan Lumi v8

- Panel kalkulator yang ringkas dan keypad berkelompok; tombol 54 dp, warna operator, hasil rata kanan, serta dukungan layar kecil dan keyboard.
- Formatter ekspresi dengan pemisah ribuan titik, desimal koma, validasi tempelan Indonesia, serta mapping kursor/seleksi. Operator setelah hasil melanjutkan perhitungan; angka memulai baru.
- Pertumbuhan memakai format nominal besar tanpa saturasi Long. Tidak ada perubahan saldo, ledger, atau database.
- Grafik batang harian dengan skala 1/2/5, ringkasan periode, kartu tanggal terpilih, ketukan batang, dan slider aksesibel. Privasi menutup grafik beserta statistik.
- Validasi: pengelompokan per operand, pecahan, paste ambigu, batas cursor, skala/hit area grafik; UI kalkulator 320 dp dan grafik 393 dp. Uji seluruh regresi, lint, dan build sebelum publikasi.
- Distribusi: app-debug-v8.apk, README dan checksum otomatis, dokumentasi diperbarui, commit dan push sebagai Asricky ke Asricky/lumi. Identitas instalasi dan sertifikat tetap dipertahankan.

Validasi v8: 156 pengujian lulus tanpa gagal/skip; lint 0 error, 14 peringatan. APK 1.0.8 tetap memakai package dan sertifikat yang sama. Rendering kalkulator 320 dp, grafik 393 dp, tempelan, hasil lanjutan, pembagian nol, navigasi kembali, dan privasi lulus. Belum diuji pada HP fisik.
