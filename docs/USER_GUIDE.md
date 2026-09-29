# Panduan Lumi

[Beranda proyek](../README.md) · [Desain](../DESIGN.md) · [Spesifikasi](../PRD.md)

## Tinjauan pesan dan hapus sekaligus

Pesan asli tampil langsung pada kartu tinjauan. Ketuk **Selengkapnya** untuk pesan panjang. Notifikasi yang sudah dibersihkan oleh kebijakan retensi menampilkan catatan/merchant sebagai pengganti; aplikasi tidak mengambil ulang pesan dari bank.

Centang kartu yang ingin dihapus, atau tekan **Pilih semua**. Tombol **Hapus terpilih** tetap ada di atas daftar. Periksa jumlahnya, lalu konfirmasi sekali. Notifikasi baru yang datang setelah pilihan dibuat tidak otomatis ikut terpilih. **Batal pilih** mengosongkan pilihan.

Hapus berarti mengabaikan catatan dari tinjauan, bukan menghilangkan histori. Pengaruh transaksi pada saldo dibalik tepat sekali; format yang belum dikenali belum memengaruhi saldo. Satu batch disimpan utuh atau dibatalkan seluruhnya bila gagal. Catatan yang sudah dikonfirmasi di tempat lain tidak ikut dihapus.

Mode sembunyikan nominal juga menyamarkan digit di pesan asli, termasuk nomor rekening. Nonaktifkan melalui tombol mata di Beranda/Aset bila ingin membaca nominalnya.

## Instalasi dan mulai memakai

1. Unduh APK, buka di Android 10 atau lebih baru, lalu izinkan instalasi dari aplikasi pengunduh jika diminta.
2. Ikuti empat langkah perkenalan, sumber notifikasi, saldo rekening, dan target bulanan. Ketik `1500000`; tampilan menjadi `1.500.000` otomatis. Titik yang ditempel harus memakai kelompok ribuan Indonesia.
3. Izinkan **Akses notifikasi** lewat onboarding atau ikon lonceng kecil di Beranda atau halaman pengaturan notifikasi.
4. Pastikan notifikasi transaksi dari aplikasi bank/e-wallet juga aktif.
5. Periksa **ikon lonceng berkedip** di kanan atas Beranda: titik hijau berarti pemantauan tersambung; merah berarti belum aktif atau ada kendala. Ketuk untuk membuka pengaturan notifikasi.

Saldo merupakan estimasi: saldo awal ditambah transaksi setelah waktu saldo awal. Aplikasi tidak mengambil riwayat bank yang sudah hilang dari notifikasi. Snapshot saldo dari notifikasi disimpan sebagai bukti, bukan mengganti saldo estimasi secara diam-diam.

Jika Android menampilkan **Setelan dibatasi**, buka info aplikasi Lumi, menu titik tiga, lalu **Izinkan setelan dibatasi** bila pilihan itu tersedia. Setelah itu aktifkan akses notifikasi kembali.

## Jika transaksi belum tercatat

- Pastikan rekening sumber sudah dibuat dan sumber terkait aktif pada Pengaturan.
- Ketuk lonceng Beranda, lalu gunakan **Sambungkan ulang** di halaman notifikasi. Setelah update APK, buka kembali Lumi.
- Periksa **Lainnya → Perlu ditinjau** untuk transaksi atau format notifikasi yang belum dikenali.
- Pada perangkat yang membatasi aplikasi latar belakang, izinkan aktivitas latar belakang untuk Lumi lewat pengaturan perangkat.
- Aktifkan **Pengaturan → Diagnostik → Mode diagnostik**, lalu buka Notification Lab untuk melihat notifikasi yang diterima.
- Mode penemuan paket hanya mencatat nama paket aplikasi. Jika perlu, masukkan nama paket aplikasi keuangan dan izinkan pemetaannya secara eksplisit; matikan penemuan setelah selesai.

ID SeaBank Indonesia yang digunakan adalah `id.co.bankbkemobile.digitalbank`, sesuai [listing resmi Google Play](https://play.google.com/store/apps/details?id=id.co.bankbkemobile.digitalbank). Gmail dan aplikasi lain tidak diproses tanpa pemetaan eksplisit.

## Build dan penomoran update

Toolchain proyek: Android SDK 34, minSdk 29, Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24, JDK 17–21 disarankan. Buat `local.properties` berisi lokasi SDK:

```properties
sdk.dir=D:\\Android\\Sdk
```

Pada Windows, gunakan perintah ini untuk **setiap update yang akan dibagikan**:

```powershell
.\build-update.ps1
# Jika semua dependensi telah tersimpan:
.\build-update.ps1 -Offline
```

Skrip menjalankan unit/integration test, Android Lint, dan build APK. Build pertama menghasilkan `app-debug-v1.apk`, berikutnya `app-debug-v2.apk`, lalu v3 dan seterusnya. Versi terakhir yang berhasil disimpan dalam `version.properties`, sehingga urutan tetap berlanjut setelah folder build dibersihkan. Build yang gagal tidak menaikkan nomor versi. APK lama tidak dihapus oleh skrip.

Untuk membangun ulang versi saat ini tanpa menaikkan nomor:

```powershell
.\gradlew.bat assembleDebug
```

Instal lewat ADB (sesuaikan nomor file):

```powershell
$latest = Get-ChildItem app/build/outputs/apk/debug/app-debug-v*.apk |
    Sort-Object { [int]($_.BaseName -replace 'app-debug-v', '') } -Descending |
    Select-Object -First 1
adb install -r $latest.FullName
```

Setiap pembaruan wajib menyertakan catatan versi baru di `CHANGELOG.md`. `build-update.ps1` menjalankan pengujian, lint, dan build, lalu otomatis memperbarui bagian rilis README: tautan unduh langsung, lokasi file, versi, SHA-256, serta catatan perubahan. Tidak perlu meminta pemilik mengingatkan pembaruan README lagi. Preview tidak mengubah file; kegagalan build tidak menerbitkan link versi baru.

Commit `README.md`, `CHANGELOG.md`, `version.properties`, dan APK bernomor baru bersama perubahan sumber. Folder build lain, konfigurasi lokal, dan keystore diabaikan Git.

## Pembaruan tanpa kehilangan data

Pertahankan application ID debug `com.luxwallet.app.debug`, sertifikat signing yang sama, dan migrasi Room yang kompatibel. Jangan uninstall aplikasi sebelum memasang pembaruan.

Build debug memakai keystore debug lokal Android. Simpan salinan keystore tersebut secara pribadi jika berpindah komputer; keystore baru tidak dapat memperbarui instalasi yang ditandatangani keystore lama. Keystore dan kata sandi tidak disertakan dalam repo.

Build release opsional memakai `keystore.properties` lokal dengan `storeFile`, `storePassword`, `keyAlias`, dan `keyPassword`, lalu jalankan `assembleRelease`. Paket release `com.luxwallet.app` terpisah dari paket debug.

## Konfirmasi transaksi dan profil

Atur nama panggilan di **Pengaturan → Profil**. Beranda menampilkan `Hi, Nama 👋`, atau `Hi there 👋` bila belum diisi. Tombol mata menyembunyikan nominal secara konsisten; input yang sedang diedit tetap terlihat agar bisa diperiksa.

Buka **Pengaturan → Notifikasi → Konfirmasi transaksi & peringatan budget**, lalu izinkan notifikasi Android. Pilihan transaksi tercatat, pemasukan, pengeluaran, transfer, perlu ditinjau, dan peringatan budget aktif secara default. Izin mengirim notifikasi berbeda dari akses membaca notifikasi bank. Mematikan konfirmasi tidak mematikan pencatatan.

Konfirmasi memakai channel **Lumi Transactions**. Notifikasi biasa dikirim setelah pencatatan tersimpan. Transfer otomatis menunggu pasangan selama jendela pencocokan 30 menit sejak dicatat; pasangan yang cocok mempercepat konfirmasi. Batas baterai Android bisa menunda pengiriman. Jika pasangan baru tiba setelah konfirmasi terkirim, ledger tetap digabung tanpa mengirim konfirmasi kedua. Ketuk notifikasi untuk membuka detail setelah melewati kunci aplikasi. Mode hide tidak mengirim nominal; layar kunci selalu memakai pesan umum. Pemberitahuan yang terlewat karena izin/filter mati tidak diputar ulang.

Lumi menyesuaikan ekspresi dengan penggunaan budget, transaksi yang perlu diperiksa, pemasukan terbaru, atau lonjakan transaksi terhadap catatan minggu terakhir. Hari yang selesai dalam budget dapat menampilkan Lumi bangga. Sisa anggaran adalah kesempatan menabung, bukan pemasukan baru. Peringatan budget muncul pada ambang 75%, lewat 100%, dan lewat 120%, masing-masing maksimal sekali per hari; tampil sebagai satu kartu per hari.

## Kalender, rencana, dan rekomendasi budget

Kalender menampilkan kartu Masuk/Keluar/Selisih serta arus kas harian bertanda. Grafik **Tren Pengeluaran** mengikuti bulan yang dipilih dan hanya menampilkan tanggal yang sudah berjalan. Ketuk titik atau geser pemilih tanggal untuk melihat nominal dan transaksi hari tersebut. Mode sembunyikan nominal juga menutup grafik.

**Saran budget hari ini** selalu bertanggal hari ini, termasuk saat melihat kalender bulan lain. Empat angka dibedakan: rekomendasi per hari, budget tersimpan, sisa budget hari ini, dan aman dibelanjakan. Rekomendasi tidak mengubah anggaran tersimpan atau memindahkan uang.

Perhitungan menggunakan nilai lebih rendah antara sisa saldo snapshot rencana dan total estimasi rekening aktif (jika tersedia). Pemasukan/pengeluaran aktual sudah masuk snapshot berjalan sehingga tidak ditambahkan dua kali. Cadangan tagihan/transportasi yang belum terpakai, penyangga, modal bisnis, dan investasi tetap dilindungi. Dana bebas bersama pemakaian hari ini dibagi sisa hari. Aman dibelanjakan dibatasi sisa target harian dan rekomendasi; perkiraan gaji tanggal 25/1 tidak dianggap uang tersedia. Jika saldo rekening belum sesuai, koreksi melalui Aset lalu konfirmasi rencana baru.

Rencana sampai gajian menampilkan ringkasan visual dan proyeksi saldo sebelum pemasukan. Proyeksi mengasumsikan pemakaian sisa budget serta cadangan tagihan/transportasi; ini simulasi, bukan saldo bank yang terverifikasi. Rincian alokasi/jadwal dapat dibuka. Saran Lumi menampilkan satu insight utama dan paling banyak dua angka pendukung; pengingat, ikon, dan edukasi investasi ada dalam bagian yang bisa dibuka.

## Peninjauan, aset, dan perlindungan transaksi ganda

- **Tinjau** membuka detail untuk memilih kategori, mengedit catatan, dan menyimpan. Food & Drink berada paling atas. Simpan/abaikan yang berhasil langsung kembali ke Beranda dengan data terbaru.
- **Hapus** di daftar peninjauan meminta konfirmasi, mengabaikan catatan, dan membalik pengaruh saldo jika sebelumnya sudah dihitung. Bukti dan histori tetap tersimpan.
- Notifikasi yang belum dikenali dapat dibaca untuk dicatat manual bila valid; nilainya belum masuk saldo otomatis. Catatan tanpa rekening harus dipasangkan dengan rekening aktif yang benar. Transaksi sebelum saldo awal perlu diabaikan karena sudah tercakup saldo awal.
- Ketuk aset/rekening untuk mengedit nama dan nilai. **Hapus dari aset aktif** mengarsipkannya. Aset manual dipulihkan dari **Arsip aset**; rekening dari **Kelola / tambah rekening → Aktifkan kembali**. Koreksi rekening tetap memakai ledger penyesuaian, bukan pemasukan.
- Lintas BCA mobile/myBCA: rekening tunggal, nominal, arah, jenis, waktu, referensi, nama pihak, serta isi notifikasi diperiksa. Referensi identik atau identitas kejadian yang kuat digabung menjadi satu transaksi dengan beberapa observation. Nama/nominal saja tidak cukup untuk menghapus transaksi.
- Kemiripan dalam 30 detik tanpa bukti kuat menjadi calon duplikat **belum dihitung**. Abaikan bila satu pembayaran; konfirmasi sebagai transaksi berbeda bila memang dua pembayaran. Referensi berbeda, pihak berbeda, atau waktu berjauhan tetap dipisahkan. Bila beberapa rekening memakai provider sama, pilih rekening saat peninjauan; aplikasi tidak menebak rekening pertama.

## Pemulihan pemantau dan ikon Lumi

Pengaturan pemantauan membedakan **Aktif / Terputus / Perlu izin**, disertai waktu notifikasi diterima dan parser terakhir. Listener terpisah dari Activity. Notifikasi yang sudah diterima dipersistenkan melalui scope aplikasi; antrean parser dapat dilanjutkan setelah restart. Disconnect, startup proses, boot, dan update meminta rebind bila izin masih ada, disusul maksimal tiga percobaan dengan backoff. Tidak ada polling service atau foreground service terus-menerus.

Android tetap mengendalikan binding dan penghematan baterai. Setelah **Paksa berhenti**, buka aplikasi kembali. Notifikasi yang hilang sebelum diterima dan tidak tersedia lagi di Android tidak dapat dipulihkan dari bank. Uji otomatis menggunakan Robolectric; perilaku OEM dan launcher HP fisik tetap perlu diverifikasi pada perangkat pengguna.

Sembilan ikon berasal dari [Lumi-app-cover.png](Lumi-app-cover.png). Pilih **Saran Lumi → Pengingat & ikon Lumi**: otomatis atau satu ekspresi tetap. Mode otomatis diperbarui dari data lokal selama proses aplikasi hidup; tidak membangunkan perangkat hanya untuk mengganti ikon. Alias baru diaktifkan sebelum alias lain dinonaktifkan, tanpa membunuh proses. Launcher dapat menyimpan cache ikon sesaat. Alias tiga versi lama tetap dideklarasikan agar update tidak memutus pintasan lama.

## Pengujian dan batas validasi

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

Suite mencakup parser dan ledger, duplikasi Rp3, migrasi Room dari v1/v2/v3/v4 ke v5, navigasi dan tombol kembali, alur catat transaksi sebenarnya, format rupiah/cursor, rencana gajian, cadangan transportasi/tagihan, kalender historis, izin/privasi notifikasi, ikon launcher, serta render tema terang/gelap.

Arsip validasi v5: **137 pengujian unit/integrasi/UI lulus**; build APK berhasil; Android Lint **0 error, 13 warning** (kompatibilitas resource/ikon, resource lama, dan parameter komponen). Render sembilan launcher, Beranda/Kalender terang–gelap, serta Planner/Coach/Tren pada layar 320 dp diperiksa. Alur konfirmasi/abaikan kembali Beranda, arsip aset, rekomendasi, BCA lintas aplikasi, dan pemulihan listener tanpa Activity turut diuji. Belum diverifikasi pada HP fisik dalam revisi ini. Format notifikasi dapat berubah antar versi aplikasi bank. GoPay tetap ditandai untuk ditinjau karena belum tersedia sampel notifikasi asli yang terkalibrasi. Penggabungan/pemisahan transaksi secara manual belum tersedia.

## Privasi dan cadangan

Pemrosesan inti berjalan lokal. Tidak ada pengiriman isi notifikasi ke server. Database Room termasuk riwayat rencana kalender berada di penyimpanan privat aplikasi; cadangan file dienkripsi menggunakan Android Keystore. Cadangan tersebut terikat kunci instalasi/perangkat, sehingga **bukan** cadangan portabel untuk reinstall atau pindah perangkat. Ekspor CSV tersedia untuk arsip transaksi.

Notifikasi yang tidak dikenal dipertahankan untuk ditinjau. Retensi mengosongkan isi mentah notifikasi yang sudah diproses; hash dan metadata identitas tetap disimpan untuk mencegah kiriman ulang dihitung kembali. Proteksi tangkapan layar dan kunci biometrik dapat diaktifkan dari Pengaturan.


## Laporan bulanan dan PDF

1. Buka Lainnya → Laporan bulanan, atau tombol laporan di Kalender.
2. Pilih bulan. Default bulan berjalan; laporan berjalan hanya sampai waktu dibuat.
3. Centang Sertakan nominal dalam PDF jika ingin angka lengkap; default disamarkan. Nama merchant/kategori tetap tercantum.
4. Buat preview PDF. Gunakan tombol halaman dan zoom untuk memeriksa isinya.
5. Simpan / download PDF, lalu pilih folder pada pemilih file Android. Membatalkan pemilih file tidak mengubah laporan atau transaksi.
6. Jika transaksi berubah, tekan Perbarui laporan. Ekspor memakai snapshot yang dipreview.

Arus kas tidak mencakup transfer sendiri, top-up e-wallet, koreksi saldo, dan catatan diabaikan. Selisih laporan bukan saldo rekening. Lumi menegur pelampauan budget hanya pada hari dengan rencana. Catatan perlu ditinjau membuat evaluasi sementara; notifikasi yang tidak tertangkap perlu dicatat manual. Tidak ada pengiriman laporan otomatis ke pihak lain.

## Saran ketika rencana belum diperbarui

Beranda, Rencana, dan Saran Lumi menampilkan rekomendasi sementara bila ada rekening aktif. Saldo rekening tercatat dibagi sampai jadwal 25/1 berikutnya setelah menyisihkan cadangan terbesar dari profil/rencana lama dan transportasi. Cadangan ditahan penuh sampai dikonfirmasi, sehingga rekomendasi dapat lebih kecil daripada dana yang sebenarnya tersedia. Belanja hari ini mengurangi sisa rekomendasi. Perbarui saldo dan rencana jika pemasukan terlambat atau tagihan sudah dibayar. Saran tidak mengubah rencana tersimpan.

Logo Lumi berwarna kini tampil pada notifikasi transaksi, budget, dan pengingat. Ikon status bar tetap monokrom sesuai Android; tampilan besar/kecil mengikuti perangkat. Contoh notifikasi tersedia di Saran Lumi → Pengingat & ikon Lumi.


## Kalkulator dan grafik v8

Kalkulator menampilkan `1.500.000,50`: titik ribuan, koma desimal. Tempel angka dengan format tersebut, atau ketik tanpa titik; pengelompokan tampil otomatis. Persen berarti dibagi 100, jadi `1.500.000 × 10% = 150.000`. Operator setelah hasil melanjutkan perhitungan; angka memulai hitungan baru. Tombol kekayaan bersih mengganti ekspresi dengan total saat ini sebagai nilai awal. Tidak ada perubahan saldo.

Pada Kalender, grafik Tren Pengeluaran menampilkan batang setiap hari aktual. Ketuk batang atau geser tanggal untuk rincian. Rata-rata memakai semua hari aktual pada periode, termasuk hari bernilai nol. Mode sembunyikan nominal juga menutup grafik dan statistiknya.
