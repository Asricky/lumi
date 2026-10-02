# Lumi — Panduan desain

Revisi 29 September 2026 · v8. Perhitungan dan perilaku: [PRD.md](PRD.md).

## Arah produk

Aplikasi keuangan personal yang tenang, ringkas, dan ramah. Dahulukan nominal yang mudah dibaca, keadaan uang sebenarnya, serta satu tindakan yang jelas. Pola bank/dompet digital menjadi referensi interaksi familiar; identitas Lumi tetap mengikuti Lumi milik produk.

## Identitas Lumi

<img src="Lumi-app-cover.png" width="540" alt="Sembilan ekspresi Lumi" />

Lumi: penguin teal, wajah krem, paruh emas, koin di atas jambul. Acuan cover aktif: [Lumi-app-cover.png](Lumi-app-cover.png). Gambar dibaca sebagai atlas native dengan sembilan wilayah kartu, bukan sembilan sel sama besar; ukuran dan margin sumber dipertahankan. Urutan: Happy/Proud/Calm, Excited/Curious/Nervous, Shocked/Sad/Angry. Komponen Lumi memakai source rectangles dan sudut membulat. Referensi karakter lama [Lumi-mascot.png](Lumi-mascot.png) tetap menjadi arsip identitas.

| Ekspresi | Bukti pemicu |
| --- | --- |
| Happy | Pemakaian budget 0–50% |
| Calm | Belum ada rencana aktif atau penggunaan normal >50–<75% |
| Nervous | Budget 75–100%; angka persen memperjelas kedekatan ke batas |
| Sad | Budget >100–120% |
| Angry | Budget >120% atau kekurangan dana bebas; bahasa tetap suportif |
| Curious | Ada transaksi berstatus perlu ditinjau |
| Shocked | Pengeluaran terbaru >3× rata-rata minimal tiga catatan tujuh hari sebelumnya |
| Excited | Pemasukan nyata baru tercatat, bukan pindah saldo/penyesuaian |
| Proud | Target tabungan tercapai atau hari sebelumnya selesai dalam budget |

Risiko budget mendahului ekspresi peristiwa. Tidak mengarang pencapaian/angka. Sisa anggaran tidak dicatat menjadi pendapatan. Maksimal satu Lumi utama per layar; ukuran 64–100 dp untuk pendamping informasi, 140 dp untuk onboarding. Jangan tampilkan avatar kosong atau hukuman visual.

Launcher memakai sembilan adaptive icon dari atlas cover yang sama. InsetDrawable persentase memilih wilayah sumber dan inset luar mengimbangi perluasan adaptive foreground; ikon tetap proporsional pada berbagai ukuran. Implementasi mengikuti [InsetDrawable Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/drawable/InsetDrawable.java). Sembilan alias mood serta Focus lama tetap dideklarasikan. Aktifkan tujuan sebelum menonaktifkan alias lain dengan DONT_KILL_APP. Mode AUTO memakai state keuangan terpusat, mengikuti transaksi dan rencana selama proses berjalan; launcher dapat menunda penyegaran. Nama launcher tetap Lumi.

## Warna

| Token | Nilai | Peran |
| --- | --- | --- |
| Teal | #16B8B2 | Identitas Lumi/aksen |
| Deep teal | #087A78 | Aksi/teks aktif tema terang dengan kontras memadai |
| Aqua / mint | #DDF8F6 / #EAFBF7 | Container dan latar ikon |
| Gold / light gold | #F5B942 / #FFF2C9 | Aksen kecil, pencapaian |
| Teks | #16324F / #64748B | Primer / sekunder |
| Background / surface | #F7FAFC / #FFFFFF | Tema terang |
| Background / surface gelap | #0D1A22 / #142731 | Tema gelap |
| Primary gelap | #4FD1C5 | Aksi dengan kontras di latar gelap |
| Positive / warning / danger / info | #22C55E / #F59E0B / #EF4444 / #3B82F6 | Arah warna semantik; teks memakai varian lebih gelap/terang agar terbaca |

Gunakan token Theme dan SemanticColors, jangan menaruh warna baru per halaman tanpa alasan. Gradasi hanya pada hero/aksen penting. Grafik, kartu, dan form tetap sederhana. Status harus mempunyai label/simbol selain warna.

## Komponen

- Spasi 4/8/12/16/20/24/32 dp. Margin ponsel 16–20 dp. Kartu radius 20–28 dp, field 16 dp. Target sentuh tindakan minimal 48 dp.
- Tipografi sistem di `Type.kt`. Headline untuk nominal utama, title untuk judul, body untuk keterangan. Hindari banyak bobot/ukuran bersaing.
- CTA navigasi memakai chevron Material 18–24 dp, terpusat vertikal, berjarak dari teks. Jangan memakai panah literal sebagai hiasan tombol.
- Pemilih `ChoiceField` membuka bottom sheet yang bisa digulir; tutup fokus keyboard dahulu, checkmark pilihan aktif.
- Rupiah `MoneyField`: digit integer dalam model, titik ribuan visual, kursor/hapus/paste tetap benar. Tampilan laporan memakai `privateRupiah` atau `visibleAmount`.
- Mode hide memakai `********` rapat, menutup nominal, proporsi grafik, dan warna finansial kalender. Input serta ringkasan draft tetap terbaca karena pengguna sedang memverifikasi data sebelum simpan.
- Simpan hanya menutup form setelah database berhasil. Draft dipertahankan saat gagal. Back pada ringkasan kembali mengedit; keluar draft yang berubah meminta pilihan lanjut/buang.
- Lonceng kecil Beranda menunjukkan koneksi listener, bukan izin mengirim konfirmasi. Animasi lembut; label aksesibilitas harus tetap menjelaskan status tanpa animasi.

## Navigasi dan performa

Empat tab: Beranda, Kalender, Aset, Lainnya. Bilah bawah di ponsel; rail pada lebar ≥600 dp. Tombol catat adalah aksi, bukan tab kelima. Halaman detail punya app bar dan panah kembali; toolbar dan Back sistem memakai dispatcher yang sama.

`openScreen` menyimpan state akar tab (`saveState`, `restoreState`, `launchSingleTop`) setelah membuang halaman detail. Tab memulihkan pilihan dan posisi gulir, tidak membuka formulir lama. NavHost tidak memakai fade. State finansial berasal dari Flow/ViewModel, bukan salinan lokal yang dimuat ulang setiap klik. Kalender mengelompokkan transaksi sekali untuk seluruh bulan.

Referensi implementasi: [Android multiple back stacks](https://developer.android.com/guide/navigation/backstack/multi-back-stacks).

## Kalender dan budget

Grid Senin–Minggu. Setiap tanggal menunjukkan income minus expense dalam format bertanda +100K/−45K/+1.25M; nol netral. Dua desimal maksimum, tanpa pembulatan yang membesarkan angka. Detail menunjukkan rupiah lengkap. Pada layar sempit label dibuat ringkas satu baris, tanggal tetap dominan.

Budget ditampilkan terpisah: batas, pemakaian, sisa, persen dan progress bar (visual dibatasi 100%, angka boleh lebih). Hari ini masih sementara, hari mendatang belum aktual, hari tanpa rencana tidak disebut surplus. Transportasi/tagihan memakai cadangannya lebih dahulu; saldo perkiraan gaji tidak menjadi dana yang bisa dibelanjakan.

## Notifikasi dan bahasa

Bahasa Indonesia singkat dan tidak menghakimi: “Yuk, susun ulang belanja hari ini”, “Ada transaksi yang perlu kamu cek”. Sertakan alasan nyata, jangan menjanjikan hasil investasi.

Konfirmasi memakai channel Lumi Transactions dan ID logis transaksi. Antrean persisten mencegah duplikat/replay. Transfer ditunda saat menunggu pasangan; pembatasan Android dapat menambah jeda. Satu snackbar singkat tanpa modal, dengan aksi Lihat ketika aplikasi aktif. Pesan layar kunci selalu umum; nominal hanya ada pada notifikasi privat jika mode hide tidak aktif. Pengingat harian edukasi terpisah, opt-in, maksimal sekali sehari pada jam siang.

## Pemeriksaan dan rilis

Periksa terang/gelap, layar 320/393 dp, angka panjang, daftar kosong, pilihan tab setelah kembali, keyboard/sheet, privasi, serta kondisi Lumi. Test otomatis meliputi matematika, parser, ledger, migrasi, notifikasi dan UI. Simulasi tidak menggantikan pemeriksaan launcher/biometrik/notifikasi pada HP fisik.

Setiap update harus menyertakan CHANGELOG dan menjalankan `build-update.ps1`. README wajib selalu memuat link langsung APK versi terbaru, checksum, dan perubahannya; script mengisi blok rilis secara otomatis. Aturan ini tetap berlaku pada pembaruan berikutnya tanpa permintaan ulang pemilik.


## Komposisi layar v5

- Kalender: pemilih periode → tiga kartu Masuk/Keluar/Selisih → grid bertanda → Tren Pengeluaran → saran budget hari ini → rincian tanggal/transaksi. Rekomendasi hari ini selalu mencantumkan tanggal agar tidak disalahartikan sebagai rekomendasi historis saat melihat bulan lain.
- FinancialMetric: label kecil, nominal titleMedium, padding 14 dp. Dua kolom untuk angka rencana, tiga kolom ringkasan kalender dengan nominal compact (Rp80K). Angka panjang boleh membungkus; tidak dipotong ellipsis.
- Grafik: garis teal tipis, tiga garis bantu, sumbu nominal/tanggal, penanda tanggal terpilih. Tap point dan slider tersedia. Grafik disembunyikan saat hide, bukan sekadar label nominalnya.
- Planner: hero hari tersisa + safe to spend + progress, dua baris metrik saldo/budget/pemakaian/buffer, proyeksi saldo. Rincian alokasi/jadwal tertutup default; tetap tersedia lewat tombol.
- Coach: Lumi 88 dp, satu insight utama, dua kartu pendukung maksimum. Pengaturan pengingat/ikon dan panduan investasi berada dalam bagian terpisah yang dapat dibuka.
- Review: kartu ringkas dengan Tinjau/Hapus; dialog singkat sebelum mengabaikan catatan. Detail menyimpan draft sekaligus lalu menuju Beranda. Tidak melakukan perubahan kategori saat pemilih sekadar diketuk.
- Aset: edit nama/nilai pada sheet, aksi arsip dengan konfirmasi, arsip aset dapat dipulihkan. Rekening memiliki status aktif/arsip, histori tidak dihapus.
- Pengaturan notifikasi: status Active/Inactive/Permission Required disertai bahasa Indonesia, tombol akses sistem, dan dua timestamp diagnostik. Jangan mengklaim pasti merekam notifikasi saat Android membatasi proses.


## Identitas publik dan tinjauan v6

Nama produk adalah **Lumi** di seluruh permukaan pengguna. Gambar hero README memakai hasil render ikon launcher asli; jangan menggambar ulang maskot. README memakai satu CTA unduhan utama, fitur ringkas dalam tabel, dan detail checksum yang dapat dibuka. Panduan panjang dipisahkan ke docs/USER_GUIDE.md.

Tinjauan: toolbar jumlah pilihan + Pilih semua/Batal pilih + Hapus terpilih tetap di atas daftar. Kartu menampilkan sumber/nominal, waktu, status, pesan asli empat baris awal (Selengkapnya untuk memperluas), checkbox kanan, dan aksi Tinjau/Hapus. Pilihan memakai warna primaryContainer dan checkbox agar tidak bergantung pada warna saja. Konfirmasi batch satu kali dengan jumlah catatan. Jangan menghilangkan pesan demi kartu yang tampak ringkas; sediakan perluasan. Saat mode hide aktif seluruh digit pesan disamarkan.


## v7 · Notifikasi dan laporan

- Notifikasi menggunakan siluet monokrom yang kontras untuk status bar dan maskot berwarna 192 px pada large icon. Judul diawali Lumi, warna aksen teal. Hindari layout custom agar kontrol privasi dan aksesibilitas Android tetap bekerja.
- Rekomendasi sementara berupa kartu berlabel eksplisit, angka sisa hari ini sebagai hirarki utama, lalu batas/terpakai, sumber saldo, cadangan, serta asumsi. Tidak memakai label “aman” untuk saldo yang belum dikonfirmasi.
- Laporan A4 595 × 842 pt: margin 40 pt, masthead Lumi, garis teal, kartu ringkasan mint, teks navy, aksen merah untuk pengeluaran/pelampauan, nomor halaman. Ukuran isi 11–12 pt, metadata minimal 9 pt; paragraf dan nama panjang membungkus ke baris berikutnya.
- Struktur PDF: ringkasan/evaluasi/langkah selanjutnya, rincian kategori dengan bar, kalender arus kas, daftar transaksi. Header dan footer konsisten tiap halaman; konten mengalir otomatis ke halaman baru. Bulan berjalan diberi label sementara.
- Preview memakai PDF asli, satu halaman agar penggunaan memori terkendali. Tombol Sebelumnya/Berikutnya, indikator halaman, slider zoom 100–250%, dan Simpan/download selalu memakai snapshot yang sama. Form, proses, error, empty report, dan pembatalan pemilih file mempunyai state jelas. Back mengikuti scaffold aplikasi.
- Nada Lumi: tegas pada perilaku yang terukur dan disertai tindakan praktis. Tidak menghina pengguna; kategori terbesar tidak otomatis disebut berlebihan tanpa budget pembanding.


## v8 · Kalkulator dan batang pengeluaran

Panel kalkulator putih/surface dengan sudut 24 dp, ekspresi rata kanan, label hasil ringkas, hasil berwarna primary. Tombol angka netral; operator mint; tombol sama dengan teal penuh. Target sentuh minimal 54 dp, jarak 8 dp, sudut 18 dp. Layar tetap bisa digulir pada perangkat kecil dan saat keyboard terbuka. Titik adalah ribuan dan koma desimal, konsisten dengan MoneyField.

Grafik pengeluaran memakai batang terpisah agar setiap tanggal mudah dibaca. Batang umum teal transparan, batang terpilih teal solid, latar garis pilih ringan; grid putus-putus. Skala kiri 0/setengah/maksimum, tanggal di bawah, kartu detail mint, total dan rata-rata di atas. Tidak memakai kurva halus yang mengarang nilai di antara tanggal. Privasi menyembunyikan ringkasan dan grafik sekaligus.


## v9 · Kejelasan sisa budget dan pengelolaan aset

- Kartu utama memakai label **Sisa budget hari ini** dengan angka terbesar, diikuti “Budget … − terpakai …”, bar pemakaian, serta cadangan transportasi tersisa. Penyesuaian karena dana terbatas diberi alasan. Penjelasan rumus disediakan lewat ikon info dan rincian rencana; hindari istilah saldo untuk menyebut budget.
- Transportasi menampilkan jumlah hari × tarif, pembayaran sebelum konfirmasi, cadangan awal tersisa, pemakaian sejak konfirmasi, dan cadangan saat ini. Nominal tidak muncul ketika mode privasi aktif.
- MoneyField tetap memakai titik setiap tiga digit. Nilai nol menjadi placeholder, sehingga angka baru menggantikannya; kolom opsional kosong dianggap nol saat menyimpan rencana.
- Sheet aset: nama → jenis (jika relevan) → nominal → catatan multiline opsional → Simpan perubahan → Hapus. Dialog menjelaskan akibat berbeda untuk rekening dan aset manual. Catatan ringkas maksimum dua baris di kartu, disembunyikan saat privasi aktif. Arsip lama tetap dapat dikelola, tanpa menawarkan arsip baru.
- Kalkulator mempertahankan keypad yang konsisten; hasil headlineMedium rata kanan, aksi “Gunakan kekayaan bersih” ringkas, tanpa petunjuk “Tekan =”.
- Tinjauan memiliki aksi Coba baca ulang untuk pesan gagal, disertai konfirmasi bahwa transaksi belum dicatat manual. Hasil dan alasan penolakan ditampilkan di toolbar. Jumlah tinjauan di Beranda merupakan tautan terpisah dari ekspresi Lumi.
