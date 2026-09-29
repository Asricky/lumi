# Changelog

## 1.0.8 — 2026-09-29

- Kalkulator diperbarui dengan panel perhitungan/hasil, keypad yang lebih rapi, serta format Indonesia: titik ribuan dan koma desimal pada input, hasil, dan simulasi pertumbuhan.
- Tempel nominal seperti `1.500.000,50`, edit pada posisi kursor, hapus pilihan, atau lanjutkan operasi dari hasil. Format tempelan ambigu ditolak dengan pesan yang jelas; kalkulator tidak mengubah saldo.
- Grafik pengeluaran Kalender memakai batang harian, skala otomatis, total periode, rata-rata, dan rincian tanggal terpilih. Ketuk batang atau gunakan pemilih tanggal yang aksesibel.
- Mode privasi menyembunyikan seluruh angka/grafik; hari mendatang tidak ditampilkan sebagai pengeluaran aktual.
- Validasi: 156 pengujian lulus; lint tanpa error (14 peringatan). Tampilan diuji melalui rendering native pada lebar 320/393 dp; belum diuji pada HP fisik.

## 1.0.7 — 2026-09-26

- Notifikasi transaksi, budget, dan pengingat menampilkan logo Lumi berwarna, aksen teal, serta judul yang jelas. Privasi layar kunci tetap dijaga.
- Saran belanja sementara tersedia di Beranda, Rencana, dan Saran Lumi saat rencana belum ada atau berakhir. Perhitungan memakai saldo rekening tercatat, menahan cadangan, dan tidak menambahkan perkiraan gaji.
- Menu **Laporan bulanan** di Lainnya dan Kalender: preview PDF, perbesar halaman, navigasi halaman, dan simpan file melalui pemilih lokasi Android.
- PDF A4 berisi ringkasan masuk/keluar, kategori, kalender, evaluasi budget, rekomendasi bulan berikutnya, dan daftar transaksi. Lumi memberi teguran berdasarkan pelampauan budget; data belum ditinjau ditandai sementara.
- Preview dan unduhan memakai snapshot PDF yang sama. Nominal disamarkan secara default dan bisa disertakan secara eksplisit.
- Validasi: 151 pengujian lulus; lint 0 error, 14 peringatan. Tata letak A4 dan navigasi diuji secara otomatis; preview PDF serta pemilih lokasi simpan belum diverifikasi pada HP fisik.

## 1.0.6 — 2026-09-25

- Nama aplikasi, launcher, notifikasi, dan ekspor menjadi **Lumi**; data dan kompatibilitas pembaruan tetap dijaga.
- Pesan notifikasi langsung terlihat pada tinjauan, termasuk format yang belum dikenali. Pesan panjang bisa diperluas; mode privasi menyamarkan digit.
- Pilih beberapa catatan atau **Pilih semua**, lalu **Hapus terpilih** dengan satu konfirmasi. Penghapusan atomik menjaga saldo dan histori.
- README baru lebih ringkas, dengan tombol unduh utama, catatan versi, dan panduan terpisah. Repositori memakai nama **Asricky/lumi**.
- Validasi: 141 pengujian lulus; lint tanpa error (13 peringatan). APK tetap memakai identitas dan sertifikat pembaruan yang sama. Belum diuji pada HP fisik.


## 1.0.5 — 2026-09-24

- Sembilan cover dan ikon Lumi dari Lumi-app-cover.png, mengikuti transaksi aktual, budget, dan pencapaian; alias launcher lama tetap tersedia saat upgrade.
- Simpan & konfirmasi atau abaikan duplikat langsung kembali ke Beranda; Food & Drink selalu di urutan pertama. Catatan tanpa rekening dapat ditinjau dengan memilih rekening aktif.
- Kalender mendapat kartu Masuk/Keluar/Selisih, grafik Tren Pengeluaran per bulan dengan pemilih tanggal, serta rekomendasi budget tanpa mengubah target tersimpan.
- Rencana sampai gajian diringkas menjadi kartu saldo, hari tersisa, safe to spend, budget, pemakaian, penyangga, dan proyeksi saldo. Saran Lumi memakai satu insight utama dan maksimal dua pendukung.
- Perlu ditinjau memiliki aksi Tinjau/Hapus dengan konfirmasi. Aset dan rekening bisa diedit, diarsipkan, serta dipulihkan tanpa menghapus histori.
- Pemulihan listener saat disconnect, startup, boot, dan update; percobaan terbatas dengan jeda bertahap. Pengaturan menampilkan status koneksi, waktu notifikasi terakhir, dan aktivitas parser.
- Deduplikasi lintas BCA mobile/myBCA memakai bukti referensi atau identitas kejadian. Kemiripan yang belum pasti ditahan tanpa ledger tambahan; transaksi berbeda tetap dicatat.
- Migrasi Room v5 menambah status arsip aset tanpa menghapus saldo, transaksi, ledger, atau rencana. README dan tautan APK terbaru tetap diperbarui otomatis setiap rilis.


## 1.0.4 — 2026-09-20

- Tampilan teal/mint, sapaan nama dari profil, sembilan ekspresi Lumi, dan ikon launcher baru dari referensi Lumi.
- Tab menyimpan posisi/pilihan tanpa fade dan tanpa memulihkan formulir lama; CTA memakai chevron yang konsisten.
- Nominal tersembunyi memakai `********` pada saldo, aset, arus kas, transaksi, target, dan hasil simulasi.
- Kalender menampilkan arus kas harian bertanda, misalnya +100K atau −1.25M, terpisah dari hasil terhadap budget.
- Konfirmasi transaksi Android dengan antrean persisten, filter per jenis, tautan detail, snackbar, serta peringatan budget.
- Migrasi Room v4 mempertahankan ledger dan saldo; transfer memakai satu konfirmasi. Pembaruan notifikasi setelah transfer digabung tetap melekat pada transaksi yang sama.
- README diperbarui otomatis oleh proses rilis: link unduh terbaru, versi, checksum APK, dan catatan perubahan dari changelog.

## 1.0.3 — 2026-09-19

- Perbaikan navigasi akar menu, panah kembali, alur draft/ringkasan, fokus keyboard, dan bilah adaptif empat menu.
- Pemisah ribuan otomatis pada input rupiah; onboarding empat langkah menyimpan rekening pada tahap akhir.
- Rencana saldo sampai tanggal pemasukan, cadangan tagihan/transportasi, dan kalender surplus/minus budget historis.
- Lumi: maskot penguin hitam–emas, tiga ekspresi, ikon yang dapat dipilih, saran keuangan dan pengingat lokal berizin.
- Migrasi Room v3 dan snapshot kalender dalam cadangan; PRD dan panduan desain baru.
- APK bernomor v3; signature dan application ID tetap untuk update di atas v2.


## 1.0.2 — 2026-09-19

- Beranda ringkas, indikator notifikasi berkedip, riwayat transaksi, dan sembunyikan nominal.
- Pemilih berbentuk bottom sheet, pilihan Light/Dark/Sistem, serta form transaksi dua langkah.
- Edit nominal aset dan koreksi saldo rekening dengan ledger penyesuaian.
- Kalkulator aritmetika dan simulasi pertumbuhan dengan setoran bulanan.
- Cadangan transportasi terpisah dari belanja bebas; target bisnis Rp1 juta dan investasi Rp1,5 juta.
- Identitas kejadian notifikasi, penahanan calon duplikat myBCA, dan peninjauan catatan ganda lama.
- Migrasi database v1 ke v2 tanpa penghapusan data.

## 1.0.1 — 2026-09-19

- Identitas visual dompet hitam dengan aksen emas dan dashboard baru.
- Perbaikan sumber SeaBank, pemulihan listener, dropdown, dan transaksi manual.
- Penulisan ledger atomik, retry, perlindungan saldo awal, dan deduplikasi konservatif.
- Peninjauan format notifikasi yang tidak dikenali dan diagnostik pemetaan paket.
- Perbaikan pembaruan dashboard, perhitungan nominal, penyimpanan detail, dan kunci aplikasi.
- Distribusi APK bernomor melalui folder app/build/outputs/apk/debug.
