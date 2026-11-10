require('dotenv').config();
const crypto = require('crypto');
const express = require('express'), cors = require('cors'), bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken'), rateLimit = require('express-rate-limit');
const pg = require('pg');

// NUMERIC dan COUNT dikembalikan sebagai angka (bukan string) agar mudah dipakai klien
pg.types.setTypeParser(1700, (v) => parseFloat(v));
pg.types.setTypeParser(20, (v) => parseInt(v, 10));

const IS_PROD = process.env.NODE_ENV === 'production';
const need = (key, devDefault) => {
  const v = process.env[key] || (IS_PROD ? null : devDefault);
  if (!v) { console.error(`Environment variable ${key} wajib diisi di produksi`); process.exit(1); }
  return v;
};
const JWT_SECRET = need('JWT_SECRET', 'dev-jwt-secret-jangan-dipakai-di-produksi');
const CODE_KEY = crypto.createHash('sha256').update(need('ORDER_CODE_SECRET', 'dev-code-secret')).digest();
const FEE_PERCENT = Number(process.env.PLATFORM_FEE_PERCENT || 2);
const MAX_ATTEMPTS = Number(process.env.MAX_CODE_ATTEMPTS || 5);
const CODE_TTL_HOURS = 72;        // kode ambil berlaku 72 jam sejak dibayar
const PAY_TIMEOUT_MIN = 30;       // pesanan belum dibayar otomatis batal setelah 30 menit
const AUTO_CONFIRM_HOURS = 48;    // jika pembeli diam 48 jam setelah mengambil barang, dana dicairkan
const PAYMENT_METHODS = ['qris', 'va_bca', 'va_mandiri', 'gopay', 'ovo'];

const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL });
const app = express();
app.use(cors(), express.json({ limit: '1mb' }));

// ---------- Utilitas ----------
class HttpError extends Error {
  constructor(status, message, extra) { super(message); this.status = status; this.extra = extra || {}; }
}
const bad = (message, status = 400, extra) => new HttpError(status, message, extra);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const q = (text, params) => pool.query(text, params);
async function tx(fn) {
  const c = await pool.connect();
  try { await c.query('BEGIN'); const r = await fn(c); await c.query('COMMIT'); return r; }
  catch (e) { await c.query('ROLLBACK'); throw e; }
  finally { c.release(); }
}
const intId = (v, label = 'ID') => {
  const n = Number(v);
  if (!Number.isInteger(n) || n <= 0 || n > 2147483647) throw bad(`${label} tidak valid`);
  return n;
};
const money = (v, label = 'Harga') => {
  const n = Number(v);
  if (!Number.isFinite(n) || n <= 0 || n > 1e10) throw bad(`${label} tidak valid`);
  return Math.round(n * 100) / 100;
};
const audit = (c, userId, action, detail) =>
  c.query('INSERT INTO audit_log(user_id, action, detail) VALUES($1,$2,$3)', [userId, action, detail || null]);

// Kode loker: hash untuk verifikasi, AES-256-GCM agar pembeli bisa melihatnya lagi
const genCode = () => String(crypto.randomInt(0, 1000000)).padStart(6, '0');
const encrypt = (text) => {
  const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', CODE_KEY, iv);
  const enc = Buffer.concat([cipher.update(text, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), enc].map((b) => b.toString('base64')).join('.');
};
const decrypt = (payload) => {
  const [iv, tag, enc] = payload.split('.').map((p) => Buffer.from(p, 'base64'));
  const d = crypto.createDecipheriv('aes-256-gcm', CODE_KEY, iv); d.setAuthTag(tag);
  return Buffer.concat([d.update(enc), d.final()]).toString('utf8');
};

// ---------- Autentikasi ----------
const sign = (u) => jwt.sign({ id: u.id }, JWT_SECRET, { expiresIn: '7d' });
const auth = (req, res, next) => {
  try {
    req.user = { id: jwt.verify((req.headers.authorization || '').replace(/^Bearer /, ''), JWT_SECRET).id };
    next();
  } catch { res.status(401).json({ error: 'Silakan login terlebih dahulu' }); }
};
const adminOnly = wrap(async (req, res, next) => {
  const u = (await q('SELECT role FROM users WHERE id=$1', [req.user.id])).rows[0];
  if (u?.role !== 'admin') throw bad('Khusus admin', 403);
  next();
});
const pubUser = (u) => ({ id: u.id, name: u.name, email: u.email, phone: u.phone, role: u.role,
  rating_avg: u.rating_avg, rating_count: u.rating_count });
const limiter = (windowMs, limit) => rateLimit({
  windowMs, limit, standardHeaders: true, legacyHeaders: false,
  message: { error: 'Terlalu banyak percobaan. Coba lagi beberapa menit lagi.' }, // JSON, bukan teks biasa
});
const authLimiter = limiter(15 * 60 * 1000, Number(process.env.AUTH_RATE_LIMIT || 30));

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.post('/auth/register', authLimiter, wrap(async (req, res) => {
  const { name, email, phone, password } = req.body || {};
  if (!name || !email || !password) throw bad('Nama, email, dan password wajib diisi');
  if (!/^\S+@\S+\.\S+$/.test(email)) throw bad('Format email tidak valid');
  if (password.length < 8) throw bad('Password minimal 8 karakter');
  try {
    const { rows } = await q('INSERT INTO users(name,email,phone,password_hash) VALUES($1,$2,$3,$4) RETURNING *',
      [name.trim(), email.toLowerCase(), phone || null, await bcrypt.hash(password, 10)]);
    res.json({ token: sign(rows[0]), user: pubUser(rows[0]) });
  } catch (e) {
    if (e.code === '23505') throw bad('Email sudah terdaftar', 409);
    throw e;
  }
}));
app.post('/auth/login', authLimiter, wrap(async (req, res) => {
  const { rows } = await q('SELECT * FROM users WHERE email=$1', [((req.body || {}).email || '').toLowerCase()]);
  const u = rows[0];
  if (!u || !(await bcrypt.compare((req.body || {}).password || '', u.password_hash))) throw bad('Email atau password salah', 401);
  res.json({ token: sign(u), user: pubUser(u) });
}));
app.get('/auth/me', auth, wrap(async (req, res) => {
  const u = (await q('SELECT * FROM users WHERE id=$1', [req.user.id])).rows[0];
  if (!u) throw bad('Pengguna tidak ditemukan', 404);
  res.json(pubUser(u));
}));
app.put('/auth/me', auth, wrap(async (req, res) => {
  const { name, phone } = req.body || {};
  const { rows } = await q('UPDATE users SET name=coalesce($1,name), phone=coalesce($2,phone) WHERE id=$3 RETURNING *',
    [name?.trim() || null, phone?.trim() || null, req.user.id]);
  res.json(pubUser(rows[0]));
}));

// ---------- Produk ----------
const PRODUCT_SELECT = `SELECT p.*, u.name AS seller_name, u.rating_avg AS seller_rating, u.rating_count AS seller_rating_count
  FROM products p JOIN users u ON u.id = p.seller_id`;
const SORTS = { newest: 'p.created_at DESC', cheapest: 'p.price ASC', priciest: 'p.price DESC' };

app.get('/products', wrap(async (req, res) => {
  const { q: term, category, condition, min, max, sort } = req.query;
  const limit = Math.min(Number(req.query.limit) || 50, 100), offset = Math.max(Number(req.query.offset) || 0, 0);
  const { rows } = await q(
    `${PRODUCT_SELECT}
     WHERE p.status='active'
       AND ($1::text IS NULL OR to_tsvector('simple', p.title||' '||coalesce(p.description,'')) @@ plainto_tsquery('simple',$1) OR p.title ILIKE '%'||$1||'%')
       AND ($2::text IS NULL OR p.category=$2) AND ($3::numeric IS NULL OR p.price>=$3) AND ($4::numeric IS NULL OR p.price<=$4)
       AND ($5::text IS NULL OR p.condition=$5)
     ORDER BY ${SORTS[sort] || SORTS.newest} LIMIT ${limit} OFFSET ${offset}`,
    [term || null, category && category !== 'Semua' ? category : null, min || null, max || null, condition || null]);
  res.json(rows);
}));
app.get('/products/mine', auth, wrap(async (req, res) =>
  res.json((await q(`${PRODUCT_SELECT} WHERE p.seller_id=$1 AND p.status <> 'removed' ORDER BY p.created_at DESC`, [req.user.id])).rows)));
app.get('/products/:id', wrap(async (req, res) => {
  const p = (await q(`${PRODUCT_SELECT} WHERE p.id=$1`, [intId(req.params.id)])).rows[0];
  if (!p) throw bad('Barang tidak ditemukan', 404);
  res.json(p);
}));
app.post('/products', auth, wrap(async (req, res) => {
  const { title, description, category, condition, image_url } = req.body || {};
  if (!title || !title.trim() || title.length > 150) throw bad('Judul wajib diisi (maks. 150 karakter)');
  const price = money(req.body.price);
  const { rows } = await q(
    `INSERT INTO products(seller_id,title,description,price,category,condition,image_url)
     VALUES($1,$2,$3,$4,coalesce($5,'Lainnya'),coalesce($6,'Bekas - Baik'),$7) RETURNING *`,
    [req.user.id, title.trim(), description || null, price, category || null, condition || null, image_url || null]);
  res.status(201).json(rows[0]);
}));
// Hanya barang berstatus aktif yang bisa diubah; status sengaja tidak bisa diubah klien
app.put('/products/:id', auth, wrap(async (req, res) => {
  const { title, description, category, condition, image_url } = req.body || {};
  const price = req.body.price == null ? null : money(req.body.price);
  const { rows } = await q(
    `UPDATE products SET title=coalesce($1,title), description=coalesce($2,description), price=coalesce($3,price),
       category=coalesce($4,category), condition=coalesce($5,condition), image_url=coalesce($6,image_url)
     WHERE id=$7 AND seller_id=$8 AND status='active' RETURNING *`,
    [title?.trim() || null, description ?? null, price, category ?? null, condition ?? null, image_url ?? null,
      intId(req.params.id), req.user.id]);
  if (!rows[0]) throw bad('Barang tidak ditemukan atau sedang dalam transaksi', 404);
  res.json(rows[0]);
}));
app.delete('/products/:id', auth, wrap(async (req, res) => {
  const r = await q("UPDATE products SET status='removed' WHERE id=$1 AND seller_id=$2 AND status='active'", [intId(req.params.id), req.user.id]);
  if (!r.rowCount) throw bad('Barang tidak ditemukan atau sedang dalam transaksi', 409);
  res.json({ ok: true });
}));

// ---------- Chat & negosiasi ----------
// Daftar percakapan: satu baris per (barang, lawan bicara)
app.get('/chat', auth, wrap(async (req, res) => {
  const { rows } = await q(
    `WITH t AS (
       SELECT m.*, CASE WHEN m.sender_id=$1 THEN m.receiver_id ELSE m.sender_id END AS other_id
       FROM messages m WHERE m.sender_id=$1 OR m.receiver_id=$1)
     SELECT * FROM (
       SELECT DISTINCT ON (t.product_id, t.other_id)
         t.product_id, p.title AS product_title, p.price AS product_price, p.image_url, p.status AS product_status,
         t.other_id, u.name AS other_name, t.body AS last_body, t.offer_price AS last_offer, t.created_at AS last_at
       FROM t JOIN products p ON p.id=t.product_id JOIN users u ON u.id=t.other_id
       ORDER BY t.product_id, t.other_id, t.created_at DESC, t.id DESC) c
     ORDER BY c.last_at DESC`, [req.user.id]);
  res.json(rows);
}));
// Pesan dalam satu percakapan. Tanpa ?with= : semua pesan saya untuk barang tsb (perilaku lama)
app.get('/chat/:productId', auth, wrap(async (req, res) => {
  const pid = intId(req.params.productId), me = req.user.id;
  if (req.query.with != null) {
    const other = intId(req.query.with, 'Lawan bicara');
    return res.json((await q(
      `SELECT * FROM messages WHERE product_id=$1 AND ((sender_id=$2 AND receiver_id=$3) OR (sender_id=$3 AND receiver_id=$2)) ORDER BY created_at, id`,
      [pid, me, other])).rows);
  }
  res.json((await q('SELECT * FROM messages WHERE product_id=$1 AND (sender_id=$2 OR receiver_id=$2) ORDER BY created_at, id', [pid, me])).rows);
}));
app.post('/chat/:productId', auth, wrap(async (req, res) => {
  const pid = intId(req.params.productId), me = req.user.id, rid = intId((req.body || {}).receiver_id, 'Penerima');
  const body = ((req.body || {}).body || '').trim() || null;
  const hasOffer = (req.body || {}).offer_price != null;
  if (!body && !hasOffer) throw bad('Pesan tidak boleh kosong');
  if (body && body.length > 2000) throw bad('Pesan terlalu panjang');
  const offer = hasOffer ? money(req.body.offer_price, 'Nominal tawaran') : null;
  if (rid === me) throw bad('Tidak bisa mengirim pesan ke diri sendiri');

  const p = (await q('SELECT seller_id, status FROM products WHERE id=$1', [pid])).rows[0];
  if (!p) throw bad('Barang tidak ditemukan', 404);
  if (me !== p.seller_id) {
    if (rid !== p.seller_id) throw bad('Chat hanya bisa dengan penjual barang', 403);
  } else if (!(await q('SELECT 1 FROM messages WHERE product_id=$1 AND sender_id=$2 AND receiver_id=$3 LIMIT 1', [pid, rid, me])).rowCount) {
    throw bad('Belum ada percakapan dengan pengguna ini', 403);
  }
  if (hasOffer && p.status !== 'active') throw bad('Barang tidak tersedia untuk ditawar', 409);

  const row = await tx(async (c) => {
    if (hasOffer) { // tawaran baru menggantikan tawaran lama yang masih menunggu dari pengirim yang sama
      await c.query(`UPDATE messages SET offer_status='withdrawn' WHERE product_id=$1 AND sender_id=$2 AND receiver_id=$3 AND offer_status='pending'`, [pid, me, rid]);
    }
    return (await c.query(
      'INSERT INTO messages(product_id,sender_id,receiver_id,body,offer_price,offer_status) VALUES($1,$2,$3,$4,$5,$6) RETURNING *',
      [pid, me, rid, body, offer, hasOffer ? 'pending' : null])).rows[0];
  });
  res.status(201).json(row);
}));
// Menanggapi tawaran: accept | reject | counter
app.patch('/chat/offers/:id', auth, wrap(async (req, res) => {
  const { action, amount } = req.body || {};
  const out = await tx(async (c) => {
    const m = (await c.query(
      `SELECT m.*, p.status AS product_status FROM messages m JOIN products p ON p.id=m.product_id WHERE m.id=$1 FOR UPDATE OF m`,
      [intId(req.params.id)])).rows[0];
    if (!m || m.offer_price == null || m.receiver_id !== req.user.id) throw bad('Tawaran tidak ditemukan', 404);
    if (m.offer_status !== 'pending') throw bad('Tawaran sudah ditanggapi', 409);
    if (m.product_status !== 'active') throw bad('Barang tidak lagi tersedia', 409);
    if (action === 'accept' || action === 'reject') {
      return (await c.query('UPDATE messages SET offer_status=$2 WHERE id=$1 RETURNING *', [m.id, action === 'accept' ? 'accepted' : 'rejected'])).rows[0];
    }
    if (action === 'counter') {
      const next = money(amount, 'Nominal tawaran balik');
      await c.query(`UPDATE messages SET offer_status='countered' WHERE id=$1`, [m.id]);
      return (await c.query(
        `INSERT INTO messages(product_id,sender_id,receiver_id,offer_price,offer_status) VALUES($1,$2,$3,$4,'pending') RETURNING *`,
        [m.product_id, req.user.id, m.sender_id, next])).rows[0];
    }
    throw bad('Aksi harus accept, reject, atau counter');
  });
  res.json(out);
}));

// ---------- Loker ----------
app.get('/lockers', wrap(async (_req, res) => res.json((await q(
  `SELECT l.*, count(s.id) FILTER (WHERE s.status='free')::int AS free_slots, count(s.id)::int AS total_slots
   FROM lockers l LEFT JOIN locker_slots s ON s.locker_id=l.id GROUP BY l.id ORDER BY l.name`)).rows)));
app.post('/lockers', auth, adminOnly, wrap(async (req, res) => {
  const { name, address, lat, lng, slots = [] } = req.body || {};
  if (!name || !address) throw bad('Nama dan alamat loker wajib diisi');
  const locker = await tx(async (c) => {
    const l = (await c.query('INSERT INTO lockers(name,address,lat,lng) VALUES($1,$2,$3,$4) RETURNING *', [name, address, lat ?? null, lng ?? null])).rows[0];
    for (const s of slots) {
      await c.query('INSERT INTO locker_slots(locker_id,label,size) VALUES($1,$2,$3)', [l.id, s.label, s.size || 'M']);
    }
    return l;
  });
  res.status(201).json(locker);
}));
app.get('/lockers/:id/slots', auth, adminOnly, wrap(async (req, res) =>
  res.json((await q('SELECT * FROM locker_slots WHERE locker_id=$1 ORDER BY label', [intId(req.params.id)])).rows)));
// Admin hanya boleh memindahkan slot antara free <-> maintenance (status lain diatur otomatis oleh pesanan)
app.patch('/lockers/slots/:id', auth, adminOnly, wrap(async (req, res) => {
  const status = (req.body || {}).status;
  if (!['free', 'maintenance'].includes(status)) throw bad('Status harus free atau maintenance');
  const { rows } = await q(`UPDATE locker_slots SET status=$1 WHERE id=$2 AND status IN ('free','maintenance') RETURNING *`, [status, intId(req.params.id)]);
  if (!rows[0]) throw bad('Slot tidak ditemukan atau sedang dipakai pesanan', 409);
  res.json(rows[0]);
}));

// ---------- Pesanan, pembayaran, pengambilan ----------
const ORDER_SELECT = `
  SELECT o.id, o.product_id, o.buyer_id, o.seller_id, o.agreed_price, o.fee, o.status,
         o.created_at, o.paid_at, o.deposited_at, o.picked_up_at, o.completed_at, o.code_expires_at, o.pickup_code_enc,
         p.title, p.image_url, b.name AS buyer_name, s.name AS seller_name,
         sl.label AS slot_label, l.name AS locker_name, l.address AS locker_address,
         EXISTS(SELECT 1 FROM ratings r WHERE r.order_id=o.id AND r.rater_id=$1) AS rated_by_me,
         EXISTS(SELECT 1 FROM disputes d WHERE d.order_id=o.id AND d.status='open') AS dispute_open
  FROM orders o JOIN products p ON p.id=o.product_id JOIN users b ON b.id=o.buyer_id JOIN users s ON s.id=o.seller_id
  LEFT JOIN locker_slots sl ON sl.id=o.slot_id LEFT JOIN lockers l ON l.id=sl.locker_id`;

// Kode loker hanya dikirim ke pembeli, dan hanya selama pesanan menunggu pengambilan
function present(o, uid) {
  const { pickup_code_enc, ...out } = o;
  out.role = o.buyer_id === uid ? 'buyer' : 'seller';
  out.total = Math.round((o.agreed_price + o.fee) * 100) / 100;
  if (out.role === 'buyer' && pickup_code_enc && ['paid', 'deposited'].includes(o.status)) out.pickup_code = decrypt(pickup_code_enc);
  return out;
}
async function loadOrder(id, uid) {
  const o = (await q(`${ORDER_SELECT} WHERE o.id=$2 AND (o.buyer_id=$1 OR o.seller_id=$1)`, [uid, id])).rows[0];
  if (!o) throw bad('Pesanan tidak ditemukan', 404);
  return present(o, uid);
}
// Mengunci baris pesanan dalam transaksi dan memastikan pemanggil adalah pihak yang terlibat
async function lockOrder(c, id, uid) {
  const o = (await c.query('SELECT * FROM orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
  if (!o || (o.buyer_id !== uid && o.seller_id !== uid)) throw bad('Pesanan tidak ditemukan', 404);
  return o;
}
const releaseSlotAndProduct = async (c, o, productStatus) => {
  if (o.slot_id) await c.query(`UPDATE locker_slots SET status='free' WHERE id=$1 AND status IN ('reserved','occupied')`, [o.slot_id]);
  if (productStatus) await c.query(`UPDATE products SET status=$2 WHERE id=$1 AND status <> 'removed'`, [o.product_id, productStatus]);
};

app.get('/orders', auth, wrap(async (req, res) => {
  const col = req.query.role === 'seller' ? 'o.seller_id' : req.query.role === 'buyer' ? 'o.buyer_id' : null;
  const where = col ? `${col}=$1` : '(o.buyer_id=$1 OR o.seller_id=$1)';
  const { rows } = await q(`${ORDER_SELECT} WHERE ${where} ORDER BY o.created_at DESC`, [req.user.id]);
  res.json(rows.map((o) => present(o, req.user.id)));
}));
app.get('/orders/:id', auth, wrap(async (req, res) => res.json(await loadOrder(intId(req.params.id), req.user.id))));

// Pembeli membuat pesanan: pilih loker, (opsional) tawaran yang disepakati dan kode ambil buatan sendiri.
// Harga dihitung server: klien tidak bisa menentukan harga.
app.post('/orders', auth, wrap(async (req, res) => {
  const { product_id, locker_id, offer_id, pickup_code } = req.body || {};
  const pid = intId(product_id, 'Barang'), lid = intId(locker_id, 'Loker');
  if (pickup_code != null && pickup_code !== '' && !/^\d{4,8}$/.test(String(pickup_code))) throw bad('Kode loker harus 4-8 digit angka');
  await maintenance();

  const id = await tx(async (c) => {
    const p = (await c.query("SELECT * FROM products WHERE id=$1 AND status='active' FOR UPDATE", [pid])).rows[0];
    if (!p) throw bad('Barang tidak tersedia', 409);
    if (p.seller_id === req.user.id) throw bad('Tidak bisa membeli barang sendiri');
    const slot = (await c.query("SELECT * FROM locker_slots WHERE locker_id=$1 AND status='free' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", [lid])).rows[0];
    if (!slot) throw bad('Tidak ada slot kosong di loker ini', 409);

    let price = p.price;
    if (offer_id != null) {
      const offer = (await c.query(
        `SELECT offer_price FROM messages WHERE id=$1 AND product_id=$2 AND offer_status='accepted'
           AND ((sender_id=$3 AND receiver_id=$4) OR (sender_id=$4 AND receiver_id=$3))`,
        [intId(offer_id, 'Tawaran'), pid, req.user.id, p.seller_id])).rows[0];
      if (!offer) throw bad('Tawaran tidak valid');
      price = offer.offer_price;
    }
    const fee = Math.round((price * FEE_PERCENT) / 100);
    const code = pickup_code ? String(pickup_code) : genCode();
    const o = (await c.query(
      `INSERT INTO orders(product_id,buyer_id,seller_id,slot_id,agreed_price,fee,pickup_code_hash,pickup_code_enc)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id`,
      [pid, req.user.id, p.seller_id, slot.id, price, fee, await bcrypt.hash(code, 10), encrypt(code)])).rows[0];
    await c.query("UPDATE locker_slots SET status='reserved' WHERE id=$1", [slot.id]);
    await c.query("UPDATE products SET status='reserved' WHERE id=$1", [pid]);
    await audit(c, req.user.id, 'order.create', { order_id: o.id, price });
    return o.id;
  });
  res.status(201).json(await loadOrder(id, req.user.id));
}));

// Pembayaran (escrow). Ganti bagian simulasi ini dengan Midtrans/Xendit: status 'paid' diatur oleh webhook terverifikasi.
app.post('/orders/:id/pay', auth, wrap(async (req, res) => {
  const method = (req.body || {}).method || 'qris';
  if (!PAYMENT_METHODS.includes(method)) throw bad('Metode pembayaran tidak didukung');
  const id = intId(req.params.id);
  await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (o.buyer_id !== req.user.id) throw bad('Hanya pembeli yang dapat membayar', 403);
    if (o.status !== 'pending_payment') throw bad('Pesanan tidak menunggu pembayaran', 409);
    await c.query("INSERT INTO payments(order_id,method,amount,status) VALUES($1,$2,$3,'held')", [id, method, Math.round((o.agreed_price + o.fee) * 100) / 100]);
    await c.query(`UPDATE orders SET status='paid', paid_at=now(), code_expires_at=now() + make_interval(hours => $2::int) WHERE id=$1`, [id, CODE_TTL_HOURS]);
    await audit(c, req.user.id, 'order.paid', { order_id: id, method });
  });
  res.json(await loadOrder(id, req.user.id));
}));

// Penjual mengonfirmasi barang sudah dititipkan di loker
app.post('/orders/:id/deposit', auth, wrap(async (req, res) => {
  const id = intId(req.params.id);
  await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (o.seller_id !== req.user.id) throw bad('Hanya penjual yang dapat menitipkan barang', 403);
    if (o.status !== 'paid') throw bad('Pesanan belum dibayar', 409);
    await c.query("UPDATE orders SET status='deposited', deposited_at=now() WHERE id=$1", [id]);
    await c.query("UPDATE locker_slots SET status='occupied' WHERE id=$1", [o.slot_id]);
    await audit(c, req.user.id, 'order.deposit', { order_id: id });
  });
  res.json(await loadOrder(id, req.user.id));
}));

// Pembeli membuka loker dengan kode. Maksimal MAX_ATTEMPTS kali salah, lalu terkunci.
const codeLimiter = limiter(60_000, Number(process.env.CODE_RATE_LIMIT || 10));
app.post('/orders/:id/retrieve', auth, codeLimiter, wrap(async (req, res) => {
  const id = intId(req.params.id), code = String((req.body || {}).code || '');
  const outcome = await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (o.buyer_id !== req.user.id) throw bad('Hanya pembeli yang dapat mengambil barang', 403);
    if (o.status !== 'deposited') throw bad('Barang belum dititipkan di loker', 409);
    if (o.failed_attempts >= MAX_ATTEMPTS || new Date(o.code_expires_at) < new Date()) {
      throw bad('Loker terkunci atau kode kedaluwarsa. Laporkan masalah ke tim Lokermart.', 423);
    }
    if (!(await bcrypt.compare(code, o.pickup_code_hash))) {
      await c.query('UPDATE orders SET failed_attempts=failed_attempts+1 WHERE id=$1', [id]);
      await audit(c, req.user.id, 'order.retrieve_failed', { order_id: id });
      return { ok: false, left: Math.max(MAX_ATTEMPTS - o.failed_attempts - 1, 0) };
    }
    await c.query("UPDATE orders SET status='picked_up', picked_up_at=now() WHERE id=$1", [id]);
    await c.query("UPDATE locker_slots SET status='free' WHERE id=$1", [o.slot_id]);
    await audit(c, req.user.id, 'order.picked_up', { order_id: id });
    return { ok: true };
  });
  if (!outcome.ok) throw bad(`Kode salah. Sisa percobaan: ${outcome.left}`, 401, { attempts_left: outcome.left });
  res.json({ unlocked: true, order: await loadOrder(id, req.user.id) });
}));

// Pembeli puas -> dana dicairkan ke penjual
app.post('/orders/:id/confirm', auth, wrap(async (req, res) => {
  const id = intId(req.params.id);
  await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (o.buyer_id !== req.user.id) throw bad('Hanya pembeli yang dapat mengonfirmasi', 403);
    if (o.status !== 'picked_up') throw bad('Pesanan belum diambil', 409);
    await completeOrder(c, o);
  });
  res.json(await loadOrder(id, req.user.id));
}));
async function completeOrder(c, o) {
  await c.query("UPDATE orders SET status='completed', completed_at=now() WHERE id=$1", [o.id]);
  await c.query("UPDATE payments SET status='released' WHERE order_id=$1 AND status='held'", [o.id]);
  await c.query("UPDATE products SET status='sold' WHERE id=$1", [o.product_id]);
}

// Pembatalan sebelum barang dititipkan. Pembeli: belum bayar / sudah bayar. Penjual: hanya setelah dibayar.
app.post('/orders/:id/cancel', auth, wrap(async (req, res) => {
  const id = intId(req.params.id);
  await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (!['pending_payment', 'paid'].includes(o.status)) throw bad('Pesanan tidak dapat dibatalkan', 409);
    if (o.status === 'pending_payment' && o.buyer_id !== req.user.id) throw bad('Hanya pembeli yang dapat membatalkan pesanan yang belum dibayar', 403);
    const refund = o.status === 'paid';
    if (refund) await c.query("UPDATE payments SET status='refunded' WHERE order_id=$1 AND status='held'", [id]);
    await c.query('UPDATE orders SET status=$2 WHERE id=$1', [id, refund ? 'refunded' : 'cancelled']);
    await releaseSlotAndProduct(c, o, 'active');
    await audit(c, req.user.id, 'order.cancel', { order_id: id, refund });
  });
  res.json(await loadOrder(id, req.user.id));
}));

// ---------- Rating & sengketa ----------
app.post('/orders/:id/rate', auth, wrap(async (req, res) => {
  const id = intId(req.params.id), stars = Number((req.body || {}).stars);
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) throw bad('Bintang harus 1-5');
  await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (o.status !== 'completed') throw bad('Rating hanya bisa diberikan setelah pesanan selesai', 409);
    const ratee = o.buyer_id === req.user.id ? o.seller_id : o.buyer_id;
    const ins = await c.query(
      'INSERT INTO ratings(order_id,rater_id,ratee_id,stars,comment) VALUES($1,$2,$3,$4,$5) ON CONFLICT (order_id, rater_id) DO NOTHING RETURNING id',
      [id, req.user.id, ratee, stars, (req.body.comment || '').trim() || null]);
    if (!ins.rowCount) throw bad('Kamu sudah memberi rating untuk pesanan ini', 409);
    await c.query(
      `UPDATE users SET rating_avg=s.avg, rating_count=s.cnt
       FROM (SELECT round(avg(stars)::numeric, 2) AS avg, count(*)::int AS cnt FROM ratings WHERE ratee_id=$1) s WHERE id=$1`, [ratee]);
  });
  res.json({ ok: true });
}));
app.get('/users/:id/ratings', auth, wrap(async (req, res) => res.json((await q(
  `SELECT r.stars, r.comment, r.created_at, u.name AS rater_name FROM ratings r JOIN users u ON u.id=r.rater_id
   WHERE r.ratee_id=$1 ORDER BY r.created_at DESC LIMIT 50`, [intId(req.params.id)])).rows)));

// Sengketa bisa diajukan pembeli atau penjual selama dana masih ditahan (sebelum dikonfirmasi)
app.post('/orders/:id/dispute', auth, wrap(async (req, res) => {
  const id = intId(req.params.id), reason = ((req.body || {}).reason || '').trim();
  if (reason.length < 10) throw bad('Jelaskan masalahnya minimal 10 karakter');
  const dispute = await tx(async (c) => {
    const o = await lockOrder(c, id, req.user.id);
    if (!['paid', 'deposited', 'picked_up'].includes(o.status)) throw bad('Sengketa hanya bisa diajukan saat dana masih ditahan', 409);
    await c.query("UPDATE orders SET status='disputed' WHERE id=$1", [id]);
    const d = (await c.query('INSERT INTO disputes(order_id,opened_by,reason) VALUES($1,$2,$3) RETURNING *', [id, req.user.id, reason])).rows[0];
    await audit(c, req.user.id, 'dispute.open', { order_id: id });
    return d;
  });
  res.status(201).json(dispute);
}));
app.get('/disputes/mine', auth, wrap(async (req, res) => res.json((await q(
  `SELECT d.*, p.title FROM disputes d JOIN orders o ON o.id=d.order_id JOIN products p ON p.id=o.product_id
   WHERE o.buyer_id=$1 OR o.seller_id=$1 ORDER BY d.created_at DESC`, [req.user.id])).rows)));

// Admin
app.get('/admin/disputes', auth, adminOnly, wrap(async (_req, res) => res.json((await q(
  `SELECT d.*, p.title, o.agreed_price, ub.name AS buyer_name, us.name AS seller_name
   FROM disputes d JOIN orders o ON o.id=d.order_id JOIN products p ON p.id=o.product_id
   JOIN users ub ON ub.id=o.buyer_id JOIN users us ON us.id=o.seller_id
   WHERE d.status='open' ORDER BY d.created_at`)).rows)));
app.post('/admin/disputes/:id/resolve', auth, adminOnly, wrap(async (req, res) => {
  const { decision, resolution } = req.body || {};
  if (!['buyer', 'seller'].includes(decision)) throw bad('Keputusan harus buyer atau seller');
  const out = await tx(async (c) => {
    const d = (await c.query("SELECT * FROM disputes WHERE id=$1 AND status='open' FOR UPDATE", [intId(req.params.id)])).rows[0];
    if (!d) throw bad('Sengketa tidak ditemukan atau sudah selesai', 404);
    const o = (await c.query('SELECT * FROM orders WHERE id=$1 FOR UPDATE', [d.order_id])).rows[0];
    if (decision === 'buyer') {
      await c.query("UPDATE payments SET status='refunded' WHERE order_id=$1 AND status='held'", [o.id]);
      await c.query("UPDATE orders SET status='refunded' WHERE id=$1", [o.id]);
      await releaseSlotAndProduct(c, o, 'active');
    } else {
      await completeOrder(c, o);
      await releaseSlotAndProduct(c, o, null);
    }
    await audit(c, req.user.id, 'dispute.resolve', { dispute_id: d.id, decision });
    return (await c.query(
      'UPDATE disputes SET status=$2, resolution=$3, resolved_by=$4, resolved_at=now() WHERE id=$1 RETURNING *',
      [d.id, decision === 'buyer' ? 'resolved_buyer' : 'resolved_seller', (resolution || '').trim() || null, req.user.id])).rows[0];
  });
  res.json(out);
}));

// ---------- Tugas berkala ----------
// 1) pesanan belum dibayar > PAY_TIMEOUT_MIN dibatalkan dan slot/barang dilepas
// 2) pesanan yang sudah diambil tapi tidak dikonfirmasi > AUTO_CONFIRM_HOURS dianggap selesai (dana cair)
async function maintenance() {
  await tx(async (c) => {
    const stale = await c.query(
      `UPDATE orders SET status='cancelled' WHERE status='pending_payment' AND created_at < now() - make_interval(mins => $1::int)
       RETURNING id, product_id, slot_id`, [PAY_TIMEOUT_MIN]);
    for (const o of stale.rows) await releaseSlotAndProduct(c, o, 'active');
    const auto = await c.query(
      `SELECT * FROM orders WHERE status='picked_up' AND picked_up_at < now() - make_interval(hours => $1::int) FOR UPDATE`, [AUTO_CONFIRM_HOURS]);
    for (const o of auto.rows) await completeOrder(c, o);
  });
}

// ---------- Penanganan error ----------
app.use((_req, res) => res.status(404).json({ error: 'Endpoint tidak ditemukan' }));
// eslint-disable-next-line no-unused-vars
app.use((err, _req, res, _next) => {
  if (err instanceof HttpError) return res.status(err.status).json({ error: err.message, ...err.extra });
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON tidak valid' });
  if (['22P02', '22003', '23503', '23514'].includes(err.code)) return res.status(400).json({ error: 'Data tidak valid' });
  console.error(err);
  res.status(500).json({ error: 'Terjadi kesalahan pada server' });
});

module.exports = { app, pool, maintenance };

if (require.main === module) {
  setInterval(() => maintenance().catch(console.error), 5 * 60 * 1000).unref();
  app.listen(process.env.PORT || 3000, '0.0.0.0', () => console.log('API running'));
}
