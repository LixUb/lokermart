// Tes end-to-end. Butuh PostgreSQL yang sudah di-init (npm run db:init) dan DATABASE_URL di .env.
// Jalankan:  node test/e2e.js     (memakai email acak, aman dijalankan berulang)
require('dotenv').config();
// Longgarkan pembatas laju hanya untuk tes ini (tes memanggil login/ambil-kode berkali-kali dari satu IP)
process.env.AUTH_RATE_LIMIT = '1000';
process.env.CODE_RATE_LIMIT = '1000';
const assert = require('assert');
const { app, pool, maintenance } = require('../server');

(async () => {
  const server = app.listen(0);
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, url, token, body) => {
    const r = await fetch(base + url, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    return { status: r.status, body: text ? JSON.parse(text) : null };
  };
  let n = 0;
  const ok = (r, status, label) => {
    assert.strictEqual(r.status, status, `${label}: harapannya ${status}, dapat ${r.status} ${JSON.stringify(r.body)}`);
    n++; console.log('ok  ', label);
    return r.body;
  };
  const tag = Date.now();
  const mail = (x) => `${x}${tag}@tes.id`;

  try {
    // --- Auth
    const S = ok(await call('POST', '/auth/register', null, { name: 'Sari', email: mail('sari'), password: 'password123' }), 200, 'daftar penjual');
    const B = ok(await call('POST', '/auth/register', null, { name: 'Budi', email: mail('budi'), password: 'password123' }), 200, 'daftar pembeli');
    const E = ok(await call('POST', '/auth/register', null, { name: 'Eve', email: mail('eve'), password: 'password123' }), 200, 'daftar pihak ketiga');
    ok(await call('POST', '/auth/register', null, { name: 'X', email: mail('sari'), password: 'password123' }), 409, 'email ganda ditolak');
    ok(await call('POST', '/auth/register', null, { name: 'X', email: mail('pendek'), password: '123' }), 400, 'password pendek ditolak');
    ok(await call('POST', '/auth/login', null, { email: mail('budi'), password: 'salah-salah' }), 401, 'password salah ditolak');
    ok(await call('POST', '/auth/login', null, { email: mail('budi'), password: 'password123' }), 200, 'login');
    ok(await call('GET', '/products/mine'), 401, 'tanpa token ditolak');
    ok(await call('PUT', '/auth/me', B.token, { name: 'Budi S', phone: '0812' }), 200, 'ubah profil');

    // --- Produk
    const p = ok(await call('POST', '/products', S.token, { title: 'Laptop ASUS Bekas', description: 'Mulus', category: 'Elektronik', price: 3000000 }), 201, 'buat produk');
    const book = ok(await call('POST', '/products', S.token, { title: 'Buku Kalkulus', category: 'Buku & Alat Tulis', price: 50000 }), 201, 'buat produk 2');
    ok(await call('POST', '/products', S.token, { title: 'Gratis', price: 0 }), 400, 'harga 0 ditolak');
    assert.strictEqual(typeof p.price, 'number'); n++; console.log('ok   harga dikirim sebagai angka');
    let list = ok(await call('GET', `/products?q=${encodeURIComponent('Laptop ASUS')}`), 200, 'cari');
    assert.ok(list.some((x) => x.id === p.id));
    list = ok(await call('GET', '/products?category=Elektronik&sort=cheapest'), 200, 'filter kategori + urut');
    assert.ok(list.every((x) => x.category === 'Elektronik'));
    ok(await call('PUT', `/products/${p.id}`, B.token, { title: 'hack' }), 404, 'non-pemilik tidak bisa edit');
    ok(await call('PUT', `/products/${p.id}`, S.token, { title: 'Laptop ASUS Bekas Mulus', status: 'sold' }), 200, 'edit produk (status diabaikan)');
    const got = ok(await call('GET', `/products/${p.id}`), 200, 'detail produk');
    assert.strictEqual(got.status, 'active'); assert.strictEqual(got.title, 'Laptop ASUS Bekas Mulus');
    ok(await call('GET', '/products/abc'), 400, 'ID salah format -> 400');

    // --- Chat & tawar
    ok(await call('POST', `/chat/${p.id}`, E.token, { receiver_id: B.user.id, body: 'hai' }), 403, 'chat hanya dengan penjual');
    ok(await call('POST', `/chat/${p.id}`, S.token, { receiver_id: B.user.id, body: 'hai' }), 403, 'penjual tak bisa memulai chat');
    ok(await call('POST', `/chat/${p.id}`, B.token, { receiver_id: S.user.id, body: 'Masih ada?' }), 201, 'pembeli kirim pesan');
    ok(await call('POST', `/chat/${p.id}`, S.token, { receiver_id: B.user.id, body: 'Ada kak' }), 201, 'penjual membalas');
    const o1 = ok(await call('POST', `/chat/${p.id}`, B.token, { receiver_id: S.user.id, offer_price: 2500000 }), 201, 'pembeli menawar');
    ok(await call('PATCH', `/chat/offers/${o1.id}`, B.token, { action: 'accept' }), 404, 'pengirim tak bisa menerima tawarannya sendiri');
    const c1 = ok(await call('PATCH', `/chat/offers/${o1.id}`, S.token, { action: 'counter', amount: 2800000 }), 200, 'penjual tawar balik');
    assert.strictEqual(c1.offer_price, 2800000);
    ok(await call('PATCH', `/chat/offers/${o1.id}`, S.token, { action: 'accept' }), 409, 'tawaran lama sudah ditanggapi');
    const acc = ok(await call('PATCH', `/chat/offers/${c1.id}`, B.token, { action: 'accept' }), 200, 'pembeli menerima tawar balik');
    assert.strictEqual(acc.offer_status, 'accepted');
    const thread = ok(await call('GET', `/chat/${p.id}?with=${S.user.id}`, B.token), 200, 'baca percakapan');
    assert.strictEqual(thread.length, 4); // pesan, balasan, tawaran, tawar balik
    ok(await call('GET', `/chat/${p.id}?with=${B.user.id}`, E.token), 200, 'pihak ketiga hanya melihat kosong');
    const convs = ok(await call('GET', '/chat', B.token), 200, 'daftar percakapan');
    assert.strictEqual(convs.length, 1); assert.strictEqual(convs[0].other_name, 'Sari');
    assert.strictEqual((await call('GET', `/chat/${p.id}?with=${B.user.id}`, E.token)).body.length, 0);

    // --- Loker
    const lockers = ok(await call('GET', '/lockers'), 200, 'daftar loker');
    assert.ok(lockers.length >= 3 && lockers[0].free_slots >= 1);
    ok(await call('POST', '/lockers', B.token, { name: 'X', address: 'Y' }), 403, 'bukan admin tak bisa tambah loker');

    // --- Pesanan: harga dari tawaran, kode buatan pembeli
    ok(await call('POST', '/orders', B.token, { product_id: p.id, locker_id: lockers[0].id, offer_id: o1.id }), 400, 'tawaran yang belum diterima ditolak');
    ok(await call('POST', '/orders', B.token, { product_id: p.id, locker_id: lockers[0].id, pickup_code: '12' }), 400, 'kode terlalu pendek');
    const order = ok(await call('POST', '/orders', B.token, { product_id: p.id, locker_id: lockers[0].id, offer_id: c1.id, pickup_code: '482915', agreed_price: 1 }), 201, 'buat pesanan');
    assert.strictEqual(order.agreed_price, 2800000, 'harga dari server, bukan klien');
    assert.strictEqual(order.fee, 56000);
    assert.strictEqual(order.total, 2856000);
    assert.strictEqual(order.pickup_code, undefined, 'kode belum tampil sebelum bayar');
    assert.strictEqual(order.pickup_code_enc, undefined);
    ok(await call('POST', '/orders', E.token, { product_id: p.id, locker_id: lockers[1].id }), 409, 'barang yang dipesan tak bisa dipesan lagi');
    ok(await call('PUT', `/products/${p.id}`, S.token, { title: 'ubah saat dipesan' }), 404, 'produk terkunci selama transaksi');
    ok(await call('POST', `/chat/${p.id}`, B.token, { receiver_id: S.user.id, offer_price: 100 }), 409, 'tak bisa menawar barang yang dipesan');

    // --- Bayar
    ok(await call('POST', `/orders/${order.id}/pay`, S.token, { method: 'qris' }), 403, 'penjual tak bisa bayar');
    ok(await call('POST', `/orders/${order.id}/pay`, B.token, { method: 'bitcoin' }), 400, 'metode tidak valid');
    const paid = ok(await call('POST', `/orders/${order.id}/pay`, B.token, { method: 'qris' }), 200, 'pembeli bayar');
    assert.strictEqual(paid.status, 'paid'); assert.strictEqual(paid.pickup_code, '482915', 'pembeli melihat kodenya');
    ok(await call('POST', `/orders/${order.id}/pay`, B.token, { method: 'qris' }), 409, 'tak bisa bayar dua kali');
    const sv = ok(await call('GET', `/orders/${order.id}`, S.token), 200, 'penjual lihat pesanan');
    assert.strictEqual(sv.pickup_code, undefined);
    assert.ok(!JSON.stringify(sv).includes('482915'), 'KODE TIDAK BOCOR KE PENJUAL');
    ok(await call('GET', `/orders/${order.id}`, E.token), 404, 'pihak ketiga tak bisa lihat pesanan');

    // --- Titip & ambil
    ok(await call('POST', `/orders/${order.id}/retrieve`, B.token, { code: '482915' }), 409, 'tak bisa ambil sebelum dititip');
    ok(await call('POST', `/orders/${order.id}/deposit`, B.token), 403, 'pembeli tak bisa menitip');
    ok(await call('POST', `/orders/${order.id}/deposit`, S.token), 200, 'penjual menitipkan');
    const bad = await call('POST', `/orders/${order.id}/retrieve`, B.token, { code: '000000' });
    assert.strictEqual(bad.status, 401); assert.strictEqual(bad.body.attempts_left, 4); n++; console.log('ok   kode salah mengurangi percobaan');
    ok(await call('POST', `/orders/${order.id}/retrieve`, S.token, { code: '482915' }), 403, 'penjual tak bisa mengambil');
    const got2 = ok(await call('POST', `/orders/${order.id}/retrieve`, B.token, { code: '482915' }), 200, 'kode benar membuka loker');
    assert.strictEqual(got2.order.status, 'picked_up');
    assert.strictEqual(got2.order.pickup_code, undefined, 'kode disembunyikan setelah diambil');
    const slot = (await pool.query('SELECT status FROM locker_slots WHERE id=(SELECT slot_id FROM orders WHERE id=$1)', [order.id])).rows[0];
    assert.strictEqual(slot.status, 'free'); n++; console.log('ok   slot kosong lagi');
    ok(await call('POST', `/orders/${order.id}/rate`, B.token, { stars: 5 }), 409, 'rating sebelum selesai ditolak');
    ok(await call('POST', `/orders/${order.id}/confirm`, S.token), 403, 'penjual tak bisa konfirmasi');
    const done = ok(await call('POST', `/orders/${order.id}/confirm`, B.token), 200, 'pembeli konfirmasi');
    assert.strictEqual(done.status, 'completed');
    assert.strictEqual((await pool.query('SELECT status FROM payments WHERE order_id=$1', [order.id])).rows[0].status, 'released'); n++; console.log('ok   dana dicairkan');
    assert.strictEqual((await call('GET', `/products/${p.id}`)).body.status, 'sold'); n++; console.log('ok   produk terjual');

    // --- Rating dua arah
    ok(await call('POST', `/orders/${order.id}/rate`, B.token, { stars: 9 }), 400, 'bintang tidak valid');
    ok(await call('POST', `/orders/${order.id}/rate`, B.token, { stars: 5, comment: 'Mantap' }), 200, 'pembeli menilai penjual');
    ok(await call('POST', `/orders/${order.id}/rate`, B.token, { stars: 4 }), 409, 'tak bisa menilai dua kali');
    ok(await call('POST', `/orders/${order.id}/rate`, S.token, { stars: 4 }), 200, 'penjual menilai pembeli (dua arah)');
    const me = ok(await call('GET', '/auth/me', S.token), 200, 'rating tersimpan');
    assert.strictEqual(me.rating_avg, 5); assert.strictEqual(me.rating_count, 1);
    assert.strictEqual((await call('GET', `/orders/${order.id}`, B.token)).body.rated_by_me, true);

    // --- Penguncian kode, sengketa, admin
    const o2 = ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: lockers[2].id }), 201, 'pesanan 2 (kode otomatis)');
    const paid2 = ok(await call('POST', `/orders/${o2.id}/pay`, B.token, { method: 'gopay' }), 200, 'bayar pesanan 2');
    assert.match(paid2.pickup_code, /^\d{6}$/); n++; console.log('ok   kode otomatis 6 digit');
    ok(await call('POST', `/orders/${o2.id}/deposit`, S.token), 200, 'titip pesanan 2');
    for (let i = 0; i < 5; i++) await call('POST', `/orders/${o2.id}/retrieve`, B.token, { code: '111111' });
    ok(await call('POST', `/orders/${o2.id}/retrieve`, B.token, { code: paid2.pickup_code }), 423, 'terkunci setelah 5 kali salah (kode benar pun ditolak)');
    ok(await call('POST', `/orders/${o2.id}/dispute`, B.token, { reason: 'pendek' }), 400, 'alasan sengketa terlalu pendek');
    const d = ok(await call('POST', `/orders/${o2.id}/dispute`, B.token, { reason: 'Loker tidak bisa dibuka sama sekali' }), 201, 'ajukan sengketa');
    assert.strictEqual((await call('GET', `/orders/${o2.id}`, B.token)).body.status, 'disputed');
    ok(await call('GET', '/admin/disputes', B.token), 403, 'non-admin tak bisa lihat sengketa');
    await pool.query("UPDATE users SET role='admin' WHERE id=$1", [E.user.id]);
    const adm = ok(await call('POST', '/auth/login', null, { email: mail('eve'), password: 'password123' }), 200, 'login admin');
    assert.strictEqual(adm.user.role, 'admin');
    assert.ok((await call('GET', '/admin/disputes', adm.token)).body.some((x) => x.id === d.id)); n++; console.log('ok   admin melihat sengketa');
    ok(await call('POST', `/admin/disputes/${d.id}/resolve`, adm.token, { decision: 'buyer', resolution: 'Refund' }), 200, 'admin refund pembeli');
    assert.strictEqual((await call('GET', `/orders/${o2.id}`, B.token)).body.status, 'refunded');
    assert.strictEqual((await call('GET', `/products/${book.id}`)).body.status, 'active'); n++; console.log('ok   produk tayang lagi');
    assert.strictEqual((await pool.query('SELECT status FROM payments WHERE order_id=$1', [o2.id])).rows[0].status, 'refunded'); n++; console.log('ok   dana dikembalikan');
    // Admin: loker & slot
    const L = ok(await call('POST', '/lockers', adm.token, { name: 'Loker Tes', address: 'Jl. Tes', slots: [{ label: 'T1', size: 'S' }] }), 201, 'admin tambah loker');
    const slots = ok(await call('GET', `/lockers/${L.id}/slots`, adm.token), 200, 'admin lihat slot');
    ok(await call('PATCH', `/lockers/slots/${slots[0].id}`, adm.token, { status: 'maintenance' }), 200, 'slot -> perawatan');
    ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: L.id }), 409, 'loker perawatan tidak bisa dipesan');

    // --- Pembatalan & tugas berkala
    const o3 = ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: lockers[0].id }), 201, 'pesanan 3');
    ok(await call('POST', `/orders/${o3.id}/cancel`, S.token), 403, 'penjual tak bisa batalkan pesanan belum bayar');
    ok(await call('POST', `/orders/${o3.id}/cancel`, B.token), 200, 'pembeli batalkan');
    assert.strictEqual((await call('GET', `/products/${book.id}`)).body.status, 'active');
    const o4 = ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: lockers[0].id }), 201, 'pesanan 4');
    ok(await call('POST', `/orders/${o4.id}/pay`, B.token, { method: 'ovo' }), 200, 'bayar pesanan 4');
    const c4 = ok(await call('POST', `/orders/${o4.id}/cancel`, S.token), 200, 'penjual batalkan pesanan berbayar');
    assert.strictEqual(c4.status, 'refunded'); n++; console.log('ok   pembatalan berbayar -> refund');
    const o5 = ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: lockers[0].id }), 201, 'pesanan 5');
    await pool.query("UPDATE orders SET created_at = now() - interval '2 hours' WHERE id=$1", [o5.id]);
    await maintenance();
    assert.strictEqual((await call('GET', `/orders/${o5.id}`, B.token)).body.status, 'cancelled'); n++; console.log('ok   pesanan tak dibayar otomatis batal');
    assert.strictEqual((await call('GET', `/products/${book.id}`)).body.status, 'active');
    const o6 = ok(await call('POST', '/orders', B.token, { product_id: book.id, locker_id: lockers[0].id, pickup_code: '7777' }), 201, 'pesanan 6');
    ok(await call('POST', `/orders/${o6.id}/pay`, B.token, { method: 'qris' }), 200, 'bayar pesanan 6');
    ok(await call('POST', `/orders/${o6.id}/deposit`, S.token), 200, 'titip pesanan 6');
    ok(await call('POST', `/orders/${o6.id}/retrieve`, B.token, { code: '7777' }), 200, 'ambil pesanan 6');
    await pool.query("UPDATE orders SET picked_up_at = now() - interval '3 days' WHERE id=$1", [o6.id]);
    await maintenance();
    assert.strictEqual((await call('GET', `/orders/${o6.id}`, S.token)).body.status, 'completed'); n++; console.log('ok   konfirmasi otomatis setelah 48 jam');
    const mine = ok(await call('GET', '/orders?role=seller', S.token), 200, 'daftar pesanan penjual');
    assert.ok(mine.every((o) => o.role === 'seller'));

    // Pembatas laju membalas JSON (aplikasi mengharapkan JSON)
    process.env.CODE_RATE_LIMIT = '2';
    delete require.cache[require.resolve('../server')];
    const second = require('../server');
    const srv2 = second.app.listen(0);
    const hit = () => fetch(`http://127.0.0.1:${srv2.address().port}/orders/999999/retrieve`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${B.token}` }, body: '{"code":"1"}' });
    await hit(); await hit();
    const limitedRes = await hit();
    assert.strictEqual(limitedRes.status, 429);
    assert.ok((await limitedRes.json()).error); n++; console.log('ok   rate limit membalas JSON (429)');
    srv2.close();
    await second.pool.end();

    console.log(`\nSEMUA ${n} PEMERIKSAAN LULUS`);
  } catch (e) {
    console.error('\nGAGAL:', e.message);
    process.exitCode = 1;
  } finally {
    server.close();
    await pool.end();
  }
})();
