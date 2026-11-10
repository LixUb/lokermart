# Lokermart

Aplikasi jual beli barang bekas dengan **loker berkode**: pembeli bayar → penjual menitipkan barang di loker → pembeli membuka loker dengan kode yang disepakati → dana diteruskan ke penjual.

- `Lokermart/` — aplikasi Android (Kotlin, Jetpack Compose, package `com.rayy.lokermart`)
- `backend/` — API Node.js + Express + PostgreSQL

## 1. Jalankan backend (Windows PowerShell)

```powershell
cd backend
npm install
copy .env.example .env     # lalu edit .env: isi password PostgreSQL dan JWT_SECRET
```

Buat database `lokermart` (lewat pgAdmin: klik kanan *Databases → Create → Database*), lalu:

```powershell
npm run db:init            # membuat tabel + data loker contoh (aman dijalankan berulang)
npm start                  # API di http://localhost:3000
```

Cek: buka http://localhost:3000/health

> Skema database berubah dari versi sebelumnya. Jika database lama sudah ada, hapus dulu isinya
> (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`) atau buat database baru, lalu `npm run db:init`.

**Jadikan akun admin** (untuk kelola loker & sengketa): daftar dulu di aplikasi, lalu
`npm run make-admin -- email@kamu.com` dan login ulang.

**Tes otomatis** (95 pemeriksaan end-to-end): set `DATABASE_URL` ke database *kosong khusus tes*, lalu `node test/e2e.js`.

## 2. Jalankan aplikasi Android

1. Buka folder `Lokermart/` di Android Studio, tunggu Gradle sync.
2. Emulator: alamat server sudah `http://10.0.2.2:3000/`.
   HP fisik: ubah `BASE_URL` di `data/Api.kt` ke `http://IP-LAPTOP:3000/` (cek `ipconfig`), satu Wi-Fi, dan izinkan port 3000 di firewall.
3. Jalankan backend dulu, lalu Run ▶.

## 3. Fitur → lokasi kode

| Fitur | Android | Backend |
|---|---|---|
| Register & login | `ui/screens/AuthScreen.kt`, `data/Session.kt` | `/auth/*` |
| Pencarian barang | `HomeScreens.kt` (HomeScreen) | `GET /products` |
| Manajemen produk | `HomeScreens.kt` (MyProducts, ProductForm) | `/products` |
| Chat & negosiasi | `ChatScreens.kt` | `/chat/*` |
| Manajemen loker | `ProfileScreens.kt` (AdminScreen) | `/lockers/*` |
| Pembayaran & keamanan | `OrderScreens.kt` (Checkout) | `/orders`, `/orders/:id/pay` |
| Pengambilan barang | `OrderScreens.kt` (OrderDetail) | `/orders/:id/deposit`, `/retrieve` |
| Rating & sengketa | `OrderScreens.kt`, `ProfileScreens.kt` | `/orders/:id/rate`, `/dispute`, `/admin/disputes` |

## 4. Keamanan yang sudah diterapkan

- Harga & biaya layanan dihitung server (nilai dari aplikasi diabaikan).
- Kode loker disimpan terenkripsi (AES-256-GCM) + hash bcrypt; hanya pembeli yang bisa melihatnya.
- 5 kode salah → loker terkunci; rate limit di login dan pengambilan.
- Dana ditahan (escrow) sampai pembeli konfirmasi; otomatis selesai 48 jam setelah diambil; pesanan tak dibayar batal setelah 30 menit.

## 5. Batasan (jujur)

- Pembayaran **simulasi**; belum terhubung payment gateway sungguhan.
- Belum ada integrasi perangkat keras loker; "buka loker" hanya memverifikasi kode di server.
- Foto barang lewat link URL (belum upload file). Chat memakai polling 4 detik (belum WebSocket/push).
- Token disimpan di SharedPreferences biasa; `usesCleartextTraffic` aktif untuk development — wajib HTTPS di produksi.
- Untuk produksi isi `JWT_SECRET` dan `ORDER_CODE_SECRET` yang kuat, dan atur R8/keep rules.
