# Lumi — Product Requirements Document

Versi produk: **v8 / 1.0.8** · Revisi: **29 September 2026**
Platform: Android 10+ · Bahasa: Indonesia · Pemilik repo: Asricky

Dokumen ini menjadi spesifikasi produk aktif. Arah tampilan dan komponen ada di [DESIGN.md](DESIGN.md), petunjuk instalasi di [README.md](README.md).

## 1. Tujuan dan batas produk

Lumi membantu pengguna mengetahui posisi uang, mencatat aktivitas bank/e-wallet dari notifikasi, dan membagi uang yang tersedia sampai pemasukan berikutnya. Lumi adalah penguin pendamping produk.

Fungsi inti berjalan offline dan data finansial disimpan di perangkat. Aplikasi tidak meminta kredensial bank, melakukan pembayaran, memindahkan dana, membeli investasi, atau menganggap saldo estimasi sebagai saldo bank yang terverifikasi.

Prioritas: navigasi dapat diprediksi, nominal tidak terhitung dua kali, perhitungan dapat dijelaskan, dan pengguna tetap dapat mengoreksi catatan.

## 2. Navigasi dan alur utama

- Empat tujuan utama: Beranda, Kalender, Aset, Lainnya.
- Ponsel memakai bilah bawah; lebar minimal 600 dp memakai bilah samping. Label tetap terlihat.
- Catat transaksi merupakan tombol aksi, bukan tab. Setelah disimpan atau dibatalkan, pengguna kembali ke halaman asal.
- Halaman lanjutan memiliki app bar dan panah kembali; bilah utama disembunyikan saat formulir/detail dibuka.
- Memilih menu utama selalu membuka akar menu itu. Formulir atau detail lama tidak dibuka kembali sebagai menu utama.
- Tombol kembali Android pada menu utama selain Beranda kembali ke Beranda. Pada Beranda, perilaku keluar mengikuti Android.
- Panah kembali dan tombol kembali sistem mengikuti alur yang sama. Sheet/dialog ditutup terlebih dahulu.
- Catat transaksi: detail → ringkasan → simpan. Kembali dari ringkasan mempertahankan isian; keluar dari draft berisi memerlukan pilihan lanjut atau buang.
- Saat menyimpan, tombol simpan dinonaktifkan. Halaman ditutup hanya setelah penyimpanan berhasil. Kegagalan tampil dan isian dipertahankan.
- Pilihan rekening/kategori/periode membuka bottom sheet. Keyboard dilepas saat pemilih dibuka.

## 3. Onboarding

Empat langkah: perkenalan dan privasi, sumber notifikasi, saldo rekening, target bulanan. Setiap langkah setelah perkenalan dapat kembali tanpa menyimpan rekening baru. Nilai input bertahan saat perubahan konfigurasi.

Akses notifikasi bisa diberikan sekarang atau nanti; pencatatan manual tetap tersedia. Akun yang tidak diisi tidak dibuat. Aset nonlikuid, kewajiban, dan tujuan tambahan dikelola setelah onboarding.

Rekening dan profil disimpan pada langkah terakhir dalam transaksi database. Pengulangan setelah kegagalan tidak membuat akun provider yang sama lagi. Status onboarding selesai ditulis setelah penyimpanan berhasil.

## 4. Sumber, parsing, dan bukti

Sumber bawaan: BCA mobile/myBCA, SeaBank, ShopeePay, GoPay. Hanya paket yang diizinkan diproses. Paket tambahan memerlukan pemetaan eksplisit dari diagnostik. Notifikasi Lumi sendiri tidak menjadi sumber keuangan.

Observation menyimpan sumber, paket, kunci notifikasi Android, waktu kirim/terima, original event time bila ada, isi notifikasi, hash payload, hash isi kanonik, status parser, dan relasi ke transaksi logis.

Parser membaca text, bigText, subText, dan textLines. Notifikasi gagal, pending, OTP, serta promosi tidak dijadikan transaksi. Nilai, merchant, atau jenis yang belum pasti tidak dikarang dan masuk peninjauan. Format rupiah Indonesia dan notasi desimal bank diproses oleh parser yang diuji terpisah dari input nominal manual.

GoPay tetap memerlukan peninjauan karena kalibrasi sampel asli belum lengkap. Format notifikasi bank bisa berubah; diagnosis harus menunjukkan bukti yang diterima, bukan menganggap koneksi berarti semua transaksi pasti terbaca.

## 5. Transaksi, deduplikasi, dan ledger

- Satu kejadian finansial dapat memiliki beberapa observations, tetapi hanya satu dampak ledger.
- Pembaruan dengan identitas kejadian yang sama tidak membuat transaksi baru.
- Ringkasan myBCA identik dalam jendela 30 detik dengan identitas belum pasti menjadi calon duplikat: nominal ditahan, tidak mengubah saldo/arus kas sampai dikonfirmasi berbeda.
- Nominal sama saja tidak cukup untuk menghapus transaksi. Dua referensi eksplisit berbeda dipertahankan sebagai kejadian berbeda.
- Peninjauan duplikat lama hanya bekerja bila bukti yang diperlukan masih tersedia. Pembalikan dampak ganda berlangsung tepat sekali.
- Mengabaikan transaksi membalik dampak ledger tepat sekali. Mengonfirmasi calon duplikat berbeda menerapkan dampak nominal tepat sekali.
- Transfer antar akun sendiri dan top-up sendiri adalah perpindahan saldo; bukan pemasukan atau pengeluaran.
- Pencatatan transaksi dan perubahan saldo berlangsung atomik. Pemrosesan dapat diulang tanpa menggandakan dampak.
- Koreksi saldo rekening mencatat selisih sebagai penyesuaian, bukan arus kas. Saldo laporan dari notifikasi tidak diam-diam mengganti estimasi.
- Catatan manual wajib memiliki nominal positif, akun sumber, serta kategori atau tujuan yang sesuai. Tujuan transfer tidak boleh sama dengan sumber.

Jenis transaksi mencakup pemasukan, pengeluaran, QRIS, transfer internal/eksternal, top-up, pembayaran marketplace, refund, biaya, bunga, cashback, penyesuaian saldo, dan jenis belum dikenali.

## 6. Rekening, aset, dan kewajiban

Rekening memiliki saldo awal dan waktu acuannya, saldo estimasi, provider, kepemilikan, status aktif, serta pilihan hitung dalam kekayaan. Transaksi sebelum waktu saldo awal tidak ditambahkan kembali ke saldo estimasi.

Aset dibagi menjadi simpanan, investasi, lainnya, serta utang. Nominal akun/aset/kewajiban dapat diedit manual. Kekayaan bersih = aset yang disertakan − kewajiban. Kekayaan bersih tidak sama dengan uang likuid yang bisa dibelanjakan.

Tombol mata menyembunyikan nominal pada Beranda, Aset, kalender, dan ringkasan rencana. Proporsi sebaran aset ikut disembunyikan. Saat pengguna secara eksplisit membuka editor nominal, isian tetap terlihat agar dapat diperiksa.

## 7. Beranda

Header memuat Lumi, identitas Lumi, indikator pemantauan kecil yang dapat diketuk, dan pengaturan. Hijau berkedip berarti listener tersambung dan tidak melaporkan kegagalan; merah berarti akses/koneksi perlu diperiksa.

Konten: kekayaan bersih, pintasan riwayat/rencana/kalkulator/peninjauan, ruang belanja sampai gajian, saran Lumi, transaksi terbaru, sebaran aset. Tidak ada kartu besar terpisah untuk status notifikasi.

Tanpa rencana saldo terkonfirmasi, tampilkan ajakan membuat rencana; jangan menampilkan perkiraan pendapatan bulanan sebagai uang yang tersedia.

## 8. Rencana saldo sampai pemasukan

Tanggal pemasukan bawaan adalah 25 dan 1, dengan tanggal terdekat setelah hari ini. Pengguna boleh menggeser tanggal 1–62 hari ke depan jika jadwal berbeda atau terlambat. Nominal perkiraan kedua pemasukan bersifat opsional dan **tidak** menambah saldo atau budget.

Input rencana:

| Input | Arti |
| --- | --- |
| Saldo tersedia | Total uang likuid yang benar-benar dikonfirmasi sekarang; mencakup uang yang akan dilindungi di bawah |
| Tagihan | Kewajiban yang belum dibayar sebelum tanggal pemasukan |
| Penyangga | Dana yang tidak dialokasikan untuk belanja rutin |
| Modal bisnis | Bagian saldo yang dilindungi untuk bisnis |
| Investasi | Bagian saldo yang dilindungi untuk rencana investasi |
| Transportasi | Biaya per hari perjalanan dan pilihan Senin–Jumat/setiap hari |
| Tanggal pemasukan | Batas eksklusif periode: rencana membiayai hari ini sampai sehari sebelumnya |

Default target bulanan adalah modal bisnis Rp1 juta dan investasi Rp1,5 juta. Rencana periode meminta pengguna mengonfirmasi bagian yang masih ada dalam saldo; tidak ada pemotongan otomatis setiap tanggal gajian. Estimasi total rekening hanya diambil setelah pengguna memilihnya dan tetap perlu diverifikasi.

Rumus awal:

```text
cadangan transportasi = biaya harian × jumlah hari perjalanan dalam periode
saldo dilindungi = tagihan + penyangga + bisnis + investasi + transportasi
dana bebas awal = saldo terkonfirmasi − saldo dilindungi
target harian = max(0, floor(dana bebas awal ÷ jumlah hari periode))
```

Transaksi sesudah waktu konfirmasi diperhitungkan. Saldo yang dikonfirmasi sudah mencakup transaksi sebelumnya, sehingga tidak boleh dikurangi lagi. Pendapatan nyata dalam periode menambah dana tersisa; target harian yang disimpan tidak otomatis dinaikkan. Pengguna dapat mengonfirmasi rencana baru.

Kategori **Transportasi rutin** dan **Tagihan terencana** memakai cadangannya terlebih dahulu. Setelah cadangan habis, kelebihan masuk penggunaan budget bebas. Transfer sendiri, top-up sendiri, calon duplikat yang ditahan, transaksi diabaikan, dan penyesuaian saldo tidak menjadi pemakaian budget.

```text
kas tersisa = saldo terkonfirmasi + pemasukan nyata − pengeluaran nyata
cadangan tersisa = penyangga + bisnis + investasi
                  + max(0, tagihan awal − tagihan dibayar)
                  + max(0, transportasi awal − transportasi dibayar)
dana bebas tersisa = kas tersisa − cadangan tersisa
sisa belanja hari ini = min(target harian − pemakaian hari ini, dana bebas tersisa)
```

Nilai negatif memicu penjelasan kekurangan alokasi; angka belanja yang ditawarkan tidak negatif. Selisih budget tetap terlihat pada detail. Persentase dapat melebihi 100%; bar visual dibatasi penuh.

Setelah rencana berakhir, minta konfirmasi saldo dan tanggal baru. Jadwal gajian tidak membuktikan uang sudah diterima. Koreksi saldo atau transaksi terlambat dari sebelum snapshot memerlukan konfirmasi ulang rencana.

## 9. Kalender pemasukan/pengeluaran

Kalender bulanan dimulai Senin. Tersedia bulan sebelumnya/berikutnya, pemilih periode, ringkasan masuk/keluar/selisih, serta detail tanggal dan daftar transaksi yang dapat dibuka.

Dua indikator harus dibedakan:

1. **Selisih arus kas** = seluruh pemasukan − seluruh pengeluaran hari tersebut.
2. **Surplus/minus budget** = target budget bebas tersimpan − pemakaian budget bebas hari tersebut.

Arus kas mencakup transportasi dan tagihan. Pemakaian budget hanya memasukkan kelebihan di atas cadangannya. Transfer sendiri dan koreksi saldo tidak masuk arus kas.

Hari tanpa rencana diberi label belum ada target; jangan disimpulkan surplus. Hari mendatang tidak memiliki hasil aktual. Hari ini menunjukkan sisa sementara. Hari pertama rencana memakai transaksi setelah konfirmasi, dengan keterangan bahwa arus kas tetap mencakup satu hari penuh.

Riwayat rencana disimpan di Room. Revisi hari ini mengganti snapshot hari ini; hari-hari sebelumnya tetap memakai target yang berlaku pada hari tersebut. Koreksi transaksi nyata dapat mengubah realisasi historis, tetapi perubahan target hari ini tidak menulis ulang target kemarin.

## 10. Saran Lumi dan notifikasi

Saran menggunakan aturan yang dapat dijelaskan dari data lokal: belum ada rencana, rencana berakhir, alokasi kekurangan dana, budget terlampaui, besok jadwal pemasukan, perlindungan dana kebutuhan dekat, lalu edukasi investasi.

Pengguna mengaktifkan pengingat secara eksplisit di **Saran Lumi**. Android 13+ meminta izin POST_NOTIFICATIONS. Penolakan izin tidak memblokir aplikasi atau saran dalam aplikasi.

WorkManager memeriksa secara berkala; maksimal satu pengingat otomatis per tanggal perangkat, hanya pukul 09.00–20.59. Penghematan baterai dapat menunda atau meniadakan pengiriman hari tersebut. Tidak menjanjikan waktu presisi. Tombol contoh notifikasi adalah aksi pengguna dan terpisah dari kuota otomatis.

Channel pengingat terpisah dari akses membaca notifikasi. Mematikan pengingat membatalkan pekerjaan periodik dan notifikasi yang tampil. Ketukan notifikasi membuka Saran Lumi, termasuk saat aplikasi sudah terbuka; kunci aplikasi tetap harus dilewati bila aktif.

Notifikasi tidak memuat nominal saldo. Versi layar kunci memakai pesan umum. Saran investasi bersifat bersyarat pada kebutuhan likuiditas, dana darurat, horizon, risiko, dan biaya; tidak menjanjikan hasil atau menyebut satu produk selalu terbaik. Sumber primer ditautkan ke OJK dan Kementerian Keuangan.

## 11. Kalkulator dan target bulanan

Kalkulator aritmetika mendukung prioritas operasi, persen, tanda kurung, dan pemakaian angka kekayaan bersih. Simulasi investasi memakai modal awal, setoran akhir bulan, asumsi hasil tahunan efektif, dan durasi. Hasil tidak memperhitungkan biaya, pajak, dan inflasi; bukan transaksi atau imbal hasil terjamin.

Anggaran & target mempertahankan profil bulanan, rencana transportasi, tabungan bisnis, investasi, serta tujuan tambahan. Rencana saldo sampai gajian menjadi sumber ruang belanja Beranda. Profil pendapatan bulanan tetap untuk perencanaan, bukan dianggap saldo nyata.

Input rupiah manual menggunakan bilangan bulat sampai 15 digit dengan titik pemisah ribuan visual. Nilai yang disimpan tetap angka rupiah, kursor dan penghapusan bekerja di tengah angka. Paste kelompok Indonesia diterima; format desimal ambigu ditolak tanpa mengalikan nilainya secara diam-diam.

## 12. Identitas Lumi

Lumi mengikuti referensi `Lumi-mascot.png`: burung membulat teal, wajah/perut krem, kaki emas, dan huruf L. Sembilan ekspresi: Happy, Proud, Calm, Excited, Curious, Nervous, Shocked, Sad, Angry. Ikon launcher memiliki versi tenang, senang, fokus serta mode mengikuti rencana.

Ikon launcher dapat dipilih tetap atau mengikuti kondisi finansial aktual selama proses aplikasi berjalan. Perubahan mengaktifkan alias baru sebelum menonaktifkan alias lama agar tetap ada pintu masuk. Launcher tertentu mungkin memperbarui tampilan dengan jeda.

Lumi tidak mengejek, menakut-nakuti, atau menekan pengguna untuk berinvestasi. Fase berikutnya dapat menambahkan pendamping percakapan dengan identitas Lumi, persetujuan data yang jelas, dan fungsi baca-saja terlebih dahulu. Fitur percakapan belum diimplementasikan dalam v5.

## 13. Privasi, cadangan, dan diagnostik

Database berada di penyimpanan privat aplikasi. Kunci biometrik dan blokir tangkapan layar dapat diaktifkan. Retensi mengosongkan isi notifikasi yang sudah diproses, mempertahankan hash/metadata deduplikasi. Notifikasi yang belum dikenali tetap dapat ditinjau.

Cadangan manual terenkripsi mencakup rekening, aset, kewajiban, kategori, transaksi, ledger, aturan, anggaran, tujuan, profil, dan snapshot rencana kalender. Cadangan terikat Android Keystore instalasi/perangkat; bukan cadangan portabel untuk reinstall. Ekspor CSV tersedia untuk arsip transaksi. Preferensi tampilan/pengingat bukan bagian dari cadangan finansial.

Mode diagnostik tidak aktif secara default. Penemuan paket hanya mencatat nama paket; pemetaan sumber tambahan dilakukan eksplisit. Kredensial, keystore, konfigurasi lokal, dan berkas alat pengembangan tidak disertakan dalam repo.

## 14. Arsitektur dan migrasi

Kotlin, Jetpack Compose Material 3, Navigation Compose, ViewModel/Flow, Room, DataStore Preferences, WorkManager, dan Android Keystore. Parser dan mesin perhitungan memiliki pengujian terpisah dari UI.

Database v1 → v2 menambah eventTime/contentHash observation. V2 → v3 menambah tabel payday_plans. V3 → v4 menambah transaction_confirmations, kosong saat migrasi, agar histori lama tidak dinotifikasi ulang. V4 → v5 menambah status arsip aset (default false). Upgrade tidak memakai destructive migration. Backup payload tetap dapat membaca cadangan lama tanpa snapshot kalender melalui nilai default kosong.

Pengamatan data UI mengikuti perubahan transaksi dan tanggal lokal. Tidak ada backend, biaya langganan, atau akses layanan daring untuk perhitungan inti. Tautan sumber investasi dibuka oleh pengguna di browser.

## 15. Distribusi dan penerimaan

Artefak berada di `app/build/outputs/apk/debug/app-debug-vN.apk`. Jalankan `build-update.ps1` untuk test, lint, build, dan kenaikan nomor hanya setelah build berhasil. Setiap pembaruan wajib memperbarui CHANGELOG; script otomatis memperbarui README dengan link download langsung APK terbaru, versi, checksum, serta catatan perubahan. Kebijakan ini berlaku untuk seluruh rilis berikutnya tanpa menunggu permintaan ulang pemilik. Nama launcher tetap Lumi. Application ID debug dan sertifikat harus tetap sama agar update mempertahankan data.

Penerimaan dasar (dipertahankan sejak v3):

- Empat menu dapat dipilih berulang, halaman detail bisa kembali, formulir kembali ke asal setelah disimpan/dibatalkan.
- Rupiah bertitik, cursor mapping, hapus, dan paste diuji.
- Pembayaran Rp3 tidak digandakan; peninjauan dan rollback ledger tetap lulus regresi.
- Perhitungan gajian meliputi tanggal 25/1, akhir bulan/tahun, tahun kabisat, kekurangan dana, transfer, cadangan, dan historis.
- Perkiraan gaji tidak menjadi saldo; hari tanpa rencana tidak dianggap surplus.
- Notifikasi memeriksa izin, channel, batas harian, jam tenang, dan privasi layar kunci.
- Ikon selalu menyisakan satu alias launcher aktif.
- Migrasi dari database v1 dan v2 mempertahankan bukti lama serta membuat tabel rencana.
- Build dan lint selesai; pemeriksaan pada perangkat fisik dicatat terpisah dari pengujian JVM/UI simulasi.

## 16. Batasan dan pengembangan berikutnya

Belum tersedia sinkronisasi cloud, impor riwayat bank yang tidak tertangkap, penggabungan/pemisahan transaksi manual, percakapan Lumi, atau jaminan format notifikasi semua versi bank. Otomasi rencana berdasarkan seluruh tagihan terjadwal dan perhitungan khusus hari libur dapat dikembangkan berikutnya.

Snapshot anggaran bukan saldo bank langsung: data yang terlambat masuk, pencatatan dari akun yang tidak termasuk uang snapshot, atau perpindahan investasi eksternal harus ditinjau. Jangan menambahkan nilai aset nonlikuid ke saldo belanja hanya agar angka alokasi tampak cukup.

## 17. Penyempurnaan v4

- Sapaan memakai nama panggilan opsional dari onboarding atau Pengaturan → Profil; fallback `Hi there 👋`. Nama bukan identitas rekening dan tidak dipakai untuk mencocokkan transfer.
- Empat tab menyimpan state/posisi gulir melalui saveState/restoreState/launchSingleTop. Halaman detail dibuang sebelum menyimpan state tab sehingga form lama tidak muncul lagi. Transisi NavHost tanpa fade.
- Tema mengikuti Lumi: teal/mint dengan emas sebagai aksen. Kartu 20–28 dp, chevron Material, maksimal satu ilustrasi Lumi utama per layar.
- Mode privasi memakai `********` pada tampilan finansial dan menutup proporsi grafik. Input dan ringkasan transaksi yang sedang dibuat sengaja tetap terbaca untuk verifikasi.
- Sel kalender menampilkan pemasukan dikurangi pengeluaran bertanda, format K/M/B maksimal dua desimal dibulatkan ke nol. Rincian mempertahankan rupiah penuh dan hasil budget sebagai konsep terpisah.

### Aturan Lumi

Jika budget nol tetapi ada belanja, tampilkan peringatan serius (persentase tidak dianggap rasio bermakna). Budget 0–50% Happy, di atas 50 sampai kurang dari 75% Calm, 75–100% Nervous, di atas 100 sampai 120% Sad, di atas 120% Angry (peringatan serius yang suportif). Kekurangan dana bebas mendapat prioritas peringatan. Hari lampau dengan rencana dan penggunaan di bawah 75% dapat memakai Proud. Jangan menganggap sisa budget sebagai pendapatan.

Di Beranda: risiko budget serius mengalahkan ekspresi peristiwa; transaksi belum ditinjau memakai Curious; transaksi pengeluaran terbaru dalam satu jam yang lebih dari tiga kali rata-rata minimal tiga pengeluaran tujuh hari sebelumnya memakai Shocked; pemasukan nyata terbaru memakai Excited. Transfer sendiri, penyesuaian, dan transaksi diabaikan tidak boleh dianggap pendapatan. Tidak membuat klaim komparasi tanpa sampel.

### Konfirmasi pencatatan

Satu receipt persisten per ID transaksi logis, ditulis atomik bersama transaksi/ledger. Penggabungan transfer memperbarui waktu siap receipt yang sama. Duplikat tidak membuat receipt baru. Pekerja hanya membaca transaksi committed. Filter semua transaksi, pemasukan, pengeluaran, transfer, perlu ditinjau, dan budget default ON, terpisah dari izin Android dan pengingat harian opt-in.

Transfer otomatis ditunda selama jendela pencocokan 30 menit dari waktu pencatatan; pasangan yang cocok membuatnya siap lebih cepat. Kedatangan pasangan setelah receipt ditangani tetap memperbaiki ledger tanpa konfirmasi kedua. Pengiriman Android memakai tag ID stabil dan onlyAlertOnce untuk retry. Tidak ada transaksi sistem terdistribusi atomik antara SQLite dan NotificationManager: proses mati tepat setelah notify sebelum commit dapat memanggil notify lagi dengan tag sama (mengganti kartu aktif, tidak menambah kartu kedua).

Izin/filter mati mengonsumsi receipt tanpa replay setelah diaktifkan. Migrasi dan pemulihan cadangan tidak membuat receipt untuk transaksi historis. Isi notifikasi mengikuti mode privasi; layar kunci selalu umum. Ketukan membuka detail lewat gerbang onboarding/biometrik. Listener menolak paket aplikasi sendiri sebelum pemetaan/parser. Saat aplikasi aktif, snackbar singkat memberi aksi Lihat tanpa dialog pemblokir.

Peringatan budget memakai rencana aktual dan transaksi cashflow eligible, ambang 75%, >100%, >120%; maksimal satu per ambang/hari, satu kartu per tanggal. Pengingat harian edukasi tetap terpisah dan opt-in.

### Validasi rilis v4

Regresi parser/ledger lama, konfirmasi sekali, izin/filter, self-notification, transfer satu receipt, migrasi v1/v2/v3 dengan saldo/ledger, nominal kalender, batas ekspresi, sapaan, privasi, state tab, serta render tema terang/gelap. Pengujian perangkat fisik dinyatakan terpisah dari simulasi JVM. Build tidak diterbitkan sebelum test dan lint selesai.


## Ketentuan pembaruan v5

Ketentuan ini memperinci dan menggantikan perilaku v3/v4 yang bertentangan.

1. Detail transaksi menyimpan draft kategori/nama/catatan secara atomik. Setelah Simpan & konfirmasi atau Abaikan duplikat berhasil, navigasi menuju akar Beranda dan Flow memperbarui data. Gagal simpan tidak menutup layar. Food & Drink diurutkan pertama pada semua pemilih kategori. Rekening yang belum diketahui harus dipilih secara eksplisit; beberapa rekening provider sama tidak boleh dipilih sembarang.
2. Kalender mempertahankan P&L bertanda dan hari terpilih, memakai tiga kartu ringkasan. Grafik pengeluaran mengikuti bulan aktif, mengabaikan transfer sendiri/koreksi/catatan diabaikan, dan tidak menggambar hari mendatang sebagai aktual. Ketuk titik atau slider aksesibel membuka rincian tanggal. Semua angka/grafik menghormati mode hide.
3. Rekomendasi adaptif hanya berlaku bagi rencana aktif. Saldo dasar = min(saldo snapshot berjalan, total rekening aktif); tanpa rekening gunakan saldo rencana. Kewajiban terlindungi = sisa cadangan tagihan + sisa transportasi + buffer + modal bisnis + investasi. Dana bebas = max(saldo dasar − kewajiban terlindungi, 0). Rekomendasi harian = (dana bebas + belanja hari ini) / sisa hari hingga tanggal pemasukan, tidak termasuk hari pemasukan. Safe to Spend = max(0, min(rekomendasi − belanja hari ini, target manual − belanja hari ini, saldo dasar − kewajiban terlindungi)). Target tersimpan dan hasil historis tidak berubah.
4. Proyeksi saldo = saldo dasar − sisa tagihan/transportasi − sisa target belanja hari ini − target harian × jumlah hari berikutnya sebelum pemasukan. Belum termasuk perkiraan gaji; proyeksi dapat negatif. Cadangan buffer tersedia dibatasi saldo setelah alokasi lain. Dana yang sudah dipindahkan keluar untuk investasi/bisnis perlu direkonsiliasi dalam rencana baru.
5. Aset memakai isArchived (default false), Room 4→5 hanya ALTER TABLE; jalur migrasi 1/2/3/4→5 wajib diuji. Arsip dikeluarkan dari total aset tetapi tetap masuk backup dan dapat dipulihkan. Rekening memakai isActive; ledger dan transaksi lama tetap ada. Pembaruan nama/status rekening tidak menimpa saldo dari snapshot UI lama.
6. Perlu ditinjau: nominal, sumber, waktu, kategori/status, Tinjau dan Hapus. Hapus meminta konfirmasi dan menandai ignored; pembalikan ledger satu kali. Format gagal parse tetap dapat dibuka atau diabaikan, bukan dikarang menjadi transaksi.
7. Launcher mempunyai sembilan alias ekspresi dan alias Focus lama untuk kompatibilitas. Identitas visual dari Lumi-app-cover.png, sumber gambar tidak digambar ulang. State aktual terpusat: risiko serius, perlu tinjau termasuk gagal parse, lonjakan, pemasukan satu jam terakhir, pencapaian target/hari sebelumnya dalam budget, lalu penggunaan harian. Pembaruan ikon hanya ketika mood berubah; tidak menjadwalkan pekerjaan periodik untuk ikon.
8. Listener recovery berbasis kejadian, maksimal tiga retry dengan backoff selain permintaan langsung. Trigger: disconnect, destruction, application startup, boot, package replacement. Permission wajib dicek. Diagnostics timestamp persisten tanpa payload di log; label aktif hanya bila listener benar-benar terhubung. Batas Android/force-stop dijelaskan dalam README.
9. Deduplikasi lintas paket BCA mensyaratkan satu rekening aktif teridentifikasi, amount/direction/type yang kompatibel dan jendela maksimum 90 detik. Referensi sama atau identitas event serta pihak tanpa masking yang cocok merupakan bukti kuat; referensi/pihak berbeda menolak merge. Kemiripan tanpa bukti kuat dalam 30 detik ditahan, excluded, tanpa ledger tambahan. Observations tetap terhubung ke transaksi asli dan tercantum dalam sourceObservationIds. Bukti meragukan tidak menghapus transaksi sah.
10. Pengujian meliputi alur simpan/abaikan kembali Beranda, review, kategori, arsip/pulihkan, saldo/ledger, migrasi, rekomendasi, periode tren, compact cashflow, launcher, dan listener tanpa Activity. Rilis wajib melalui build-update.ps1, README/CHANGELOG/PRD/DESIGN diperbarui, APK bernomor naik, tautan langsung terbaru dan SHA-256 selalu tersedia tanpa permintaan ulang.

Validasi rilis v5: 137 test lulus; testDebugUnitTest, lintDebug, assembleDebug berhasil. Lint: 0 error, 13 warning nonblocking. Rendering native termasuk sembilan ikon dan layar 320/393 dp; perangkat fisik belum diuji.


## Pembaruan v6 · Lumi

Nama publik aplikasi dan repositori menjadi Lumi/lumi. Judul layar, akses biometrik, label launcher, channel notifikasi dan nama ekspor mengikuti nama Lumi. Application ID, namespace internal, nama database, kunci preferensi/keystore, ID channel, dan alias launcher lama dipertahankan untuk kompatibilitas update; bukan nama yang ditampilkan kepada pengguna. Tidak ada migrasi destruktif atau penggantian paket instalasi.

Tinjauan menampilkan pesan asli dari observation yang terkait, termasuk notifikasi gagal parse. Isi panjang dapat diperluas; data yang sudah dibersihkan retensi menggunakan catatan/merchant atau keterangan pesan tidak tersedia. Mode hide menyamarkan seluruh digit pada pesan agar nominal dan nomor rekening tidak bocor.

Setiap kartu memiliki checkbox. Pilih semua dan Hapus terpilih tetap terlihat pada toolbar di atas daftar. Konfirmasi menghapus hanya snapshot pilihan saat tombol ditekan; notifikasi baru yang datang sesudahnya tidak ikut dihapus. Penghapusan berarti diabaikan dari tinjauan, dengan pembalikan ledger satu kali untuk transaksi yang sudah dihitung. Recheck status terbaru mencegah catatan yang sudah dikonfirmasi di tempat lain ikut dihapus. Seluruh batch dijalankan dalam satu transaksi database: semua berhasil atau seluruh perubahan dibatalkan. Riwayat dan bukti tetap ada.

README menjadi halaman ringkas berisi identitas Lumi, link APK utama, catatan versi, fitur dan cara mulai. Detail operasional berada di docs/USER_GUIDE.md. update-readme.ps1 menjaga struktur ringkas dan tautan repo lumi setiap rilis.


## Pembaruan v7 · Notifikasi, rekomendasi sementara, laporan

1. Semua notifikasi Lumi memakai ikon status monokrom, logo maskot besar berwarna dari aset launcher yang sama, warna teal, dan judul Lumi. Channel ID, pengiriman sekali, izin, intent biometrik, serta publicVersion layar kunci dipertahankan. Tampilan akhir logo mengikuti UI notifikasi produsen Android.
2. Saat tidak ada rencana aktif, hitung saran sementara hanya jika ada rekening aktif milik pengguna. Saldo sumber ialah jumlah saldo estimasi rekening, bukan snapshot rencana kedaluwarsa atau nilai aset nonlikuid. Tanggal tujuan adalah jadwal 25/1 berikutnya, eksklusif hari pemasukan; hari pemasukan saat ini belum dianggap uang tersedia.
3. Cadangan sementara = max(tagihan + buffer + bisnis + investasi rencana terakhir, kewajiban + cicilan + tabungan + investasi + pengeluaran terencana + buffer bulanan profil) + transportasi hingga tanggal tujuan dikurangi transportasi hari ini. Cadangan lama/profil ditahan penuh sampai pengguna mengonfirmasi pembayaran; pendekatan konservatif ini dinyatakan di UI. Dana bebas = max(saldo − cadangan, 0); batas harian = (dana bebas + belanja harian tercatat) / hari tersisa; sisa rekomendasi = max(0, min(dana bebas, batas − belanja hari ini)). Transfer sendiri, top-up dan koreksi saldo tidak dianggap belanja; transportasi dan tagihan memakai cadangan. Rekomendasi tidak menulis plan, ledger, ataupun penilaian historis.
4. Pengingat harian opt-in mengajak mengecek batas sementara ketika rencana berakhir. Tetap maksimal sekali sehari, tanpa nominal di notifikasi. Saran aktual mengikuti perubahan rekening, transaksi, profil, preferensi transportasi, dan pergantian tanggal pada layar.
5. Laporan bulanan lokal: pilih bulan berjalan atau historis, buat snapshot atomik data, render PDF A4, preview file tersebut halaman demi halaman dengan zoom, lalu simpan lewat CreateDocument Android (application/pdf). Tidak meminta izin penyimpanan luas, tidak mengunggah data, tidak memakai server PDF. File cache privat dibersihkan setelah keluar dan file sisa proses sebelumnya dibersihkan pada pembuatan berikutnya. PDF yang diekspor tetap menjadi milik pengguna.
6. Total pemasukan/pengeluaran laporan mengikuti arus kas eligible: tidak mencakup transfer sendiri, top-up e-wallet, penyesuaian saldo, transaksi excluded atau ignored. Waktu memakai zona lokal dan snapshot waktu pembuatan; tanggal mendatang tidak dihitung. Transaksi needs-review eligible tetap tampak dengan label sementara. Jumlah catatan belum ditinjau dan yang dikeluarkan dari arus kas dijelaskan.
7. Isi: periode/cakupan; total masuk, keluar, selisih (bukan saldo bank); rincian kategori dengan bar persentase; arus kas setiap tanggal; jumlah hari dengan rencana dan pelampauan budget; daftar transaksi; evaluasi Lumi serta langkah bulan berikutnya. Penilaian budget memakai snapshot rencana yang berlaku per tanggal. Hari tanpa plan tidak dinyatakan surplus; biaya transportasi/tagihan tidak dibandingkan mentah dengan batas belanja bebas. Teguran serius hanya berdasarkan pelampauan yang tercatat. Data provisional didahulukan sebelum menghakimi perilaku. Defisit arus kas bukan bukti otomatis boros karena dapat berasal dari tabungan atau pemasukan yang belum tercatat.
8. Nominal PDF disamarkan default; pengguna bisa memilih untuk menyertakannya. Mode samarkan menutup nominal, persentase, dan status budget harian; kategori dan merchant tetap terlihat dan dijelaskan sebelum ekspor. Preview dan download memakai file snapshot sama, bukan dua hitungan berbeda. Mengganti bulan/opsi membuang preview lama; pembaruan data setelah generate memerlukan tombol Perbarui laporan.
9. Database, package, sertifikat, dan nomor versi berurutan tetap dipertahankan. README, CHANGELOG, panduan, serta dokumentasi desain diperbarui setiap rilis.

Validasi rilis v7: 151 pengujian lulus (0 gagal/skip); lint 0 error, 14 peringatan, 6 informasi; APK 1.0.7 berlabel Lumi memakai sertifikat yang sama dengan v6. Tidak ada migrasi database. Verifikasi preview PDF dan pemilih lokasi simpan pada HP fisik belum dilakukan.


## Pembaruan v8 · Kalkulator dan grafik pengeluaran

- Kalkulator menyimpan ekspresi tanpa pemisah ribuan; koma adalah desimal pada editor, titik hanya format visual. Setiap operand dan hasil dikelompokkan per tiga digit. Offset mapping menjaga kursor dan seleksi ketika pemisah ditambah otomatis. Tempelan menerima pengelompokan Indonesia yang valid; `1.5` ditolak agar tidak berubah diam-diam menjadi 15 atau 1.500. Gunakan `1,5` untuk satu koma lima.
- Keypad memasukkan karakter pada posisi kursor, hapus menghilangkan seleksi atau satu karakter sebelumnya. Setelah hasil, operator melanjutkan dari hasil dan angka memulai perhitungan baru. Tombol kekayaan bersih menjadikan total saat ini sebagai nilai awal dan tidak mengubah data. Batas 250 karakter dan pembagian nol tetap divalidasi. Pertumbuhan memakai format nominal besar tanpa pemotongan ke batas Long; simulasi tetap asumsi, bukan janji hasil.
- Tren pengeluaran memakai batang per hari aktual dalam bulan terpilih. Total dan rata-rata dihitung dari seluruh hari aktual termasuk hari tanpa pengeluaran; bulan mendatang menampilkan empty state. Skala atas dibulatkan ke kelipatan 1/2/5/10 dan tidak memotong nilai puncak. Semua titik nol ditampilkan pada baseline, bukan dibuat menjadi batang bernilai semu.
- Ketuk area batang atau slider aksesibel untuk memilih tanggal. Hari terpilih mempunyai warna penekanan, tanggal lengkap, nominal penuh, serta label puncak jika sama dengan maksimum positif. Periode dan filter transaksi mengikuti engine arus kas sebelumnya. Mode privasi menghilangkan nominal, ringkasan, grafik, serta pemilih nominal dari semantics.

Validasi v8: 156 pengujian lulus tanpa gagal/skip; lint 0 error, 14 peringatan. APK 1.0.8 tetap memakai package dan sertifikat yang sama. Rendering kalkulator 320 dp, grafik 393 dp, tempelan, hasil lanjutan, pembagian nol, navigasi kembali, dan privasi lulus. Belum diuji pada HP fisik.


## Pembaruan v9 · Sisa harian, aset, dan SeaBank

- Konfirmasi rencana mengambil snapshot transaksi dalam satu transaksi database. Saldo konfirmasi adalah saldo **sekarang**: transaksi sebelum waktu konfirmasi tidak dipotong ulang. Belanja bebas sejak tengah malam hingga sebelum konfirmasi disimpan sebagai pemakaian awal hari pertama. Budget = (saldo sekarang − alokasi tersisa + pemakaian awal) ÷ hari sampai pemasukan; sisa hari ini = budget − pemakaian awal − belanja setelah konfirmasi. Sisa yang dapat dibelanjakan dibatasi rekomendasi adaptif dan dana aktual. Konfirmasi berulang tidak menggandakan pemakaian; pemakaian awal tidak dibawa ke hari berikutnya. Plan versi lama tetap terbaca; perhitungan baru berlaku setelah konfirmasi ulang.
- Contoh: saldo sekarang Rp800.000, belanja tadi Rp200.000, dua hari sampai pemasukan, tanpa alokasi lain → budget Rp500.000, terpakai Rp200.000, sisa Rp300.000. Saldo tetap Rp800.000. Pengeluaran baru Rp100.000 membuat saldo Rp700.000 dan sisa budget Rp200.000.
- Cadangan transportasi adalah jumlah hari perjalanan mulai hari ini sampai sehari sebelum pemasukan × biaya harian. Pembayaran transportasi sebelum konfirmasi mengurangi kebutuhan hari pertama, maksimum tarif hari itu. Selisih di atas tarif hari pertama masuk belanja bebas. Setelah konfirmasi, kategori Transportasi rutin menggunakan cadangan periode sebelum masuk belanja bebas. Sabtu/Minggu tidak dialokasikan bila pilihan Senin–Jumat aktif. Top-up dompet sendiri bukan pengeluaran. Tagihan yang diisikan hanya tagihan **belum dibayar**; pembayaran sebelum konfirmasi tidak dipotong ulang. Tagihan tersisa dan penyangga sebelumnya diprefill, nominal nol berupa placeholder.
- Catatan opsional maksimum 2.000 karakter tersedia pada simpanan, investasi, aset lain, dan utang. Catatan pada daftar disembunyikan saat privasi nominal aktif; editor menampilkan catatan untuk penyuntingan. Perubahan nama/catatan rekening tanpa perubahan angka tidak membuat koreksi saldo dan tidak menimpa transaksi yang datang selama editor terbuka.
- Hapus aset manual/utang menghapus record permanen setelah konfirmasi, tanpa mengubah transaksi atau mencatat pelunasan. Hapus rekening mengecualikannya dari aset aktif/pemantauan otomatis, tetap menyimpan ledger untuk laporan, serta dapat dipulihkan di Kelola rekening. Arsip aset dari versi terdahulu tetap dapat dipulihkan atau dihapus melalui bagian Aset yang disembunyikan. Room v6 menambah kolom notes nullable pada accounts dengan migrasi nondestruktif dari seluruh versi sebelumnya; cadangan lama tetap kompatibel.
- SeaBank memprioritaskan bukti transfer masuk sebelum pola transfer real-time keluar. Nominal harus berada setelah senilai/sebesar/sejumlah atau mata uang pada frasa transfer masuk; angka rekening tidak boleh menjadi nominal. Format gagal, belum diterima, rencana masa depan, dan arah bertentangan tidak membuat pemasukan. Registry parser menjadi v4. Format contoh pengguna diuji dari notifikasi sampai ledger dan saldo, termasuk pengiriman ulang.
- Tinjauan menyediakan Coba baca ulang hanya untuk observasi gagal yang belum terhubung ke transaksi. Pengguna mengonfirmasi belum mencatatnya manual. Pemrosesan ulang atomik dan idempoten; timestamp sebelum/sama dengan koreksi saldo ditolak agar saldo tidak dikredit ulang. Tidak ada replay historis otomatis setelah upgrade.
- Kondisi risiko budget tetap prioritas ekspresi Lumi. Rencana aktif yang aman tidak dikalahkan antrean tinjauan; kebutuhan tinjauan ditampilkan sebagai jumlah terpisah di Beranda. Tanpa rencana aktif, tinjauan transaksi baru (maksimum 24 jam) masih bisa membuat Lumi penasaran. Pilihan ikon manual tetap dihormati.

Validasi v9: 175 pengujian lulus (0 gagal/error/skip); lint 0 error, 14 peringatan, 7 informasi. APK 1.0.9 memakai package dan sertifikat pembaruan yang sama; APK versi sebelumnya tetap dipertahankan. Rendering kalkulator dan rencana 320 dp diperiksa. Belum diuji pada HP fisik.
