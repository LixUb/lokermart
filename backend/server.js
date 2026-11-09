require('dotenv').config();
const express = require('express'), cors = require('cors'), bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken'), rateLimit = require('express-rate-limit');
const { Pool } = require('pg');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const app = express(); app.use(cors(), express.json());
const q = (t, p) => pool.query(t, p);
const wrap = fn => (req, res) => fn(req, res).catch(e => { console.error(e); res.status(500).json({ error: 'Server error' }); });

const auth = (req, res, next) => {
  try { req.user = jwt.verify((req.headers.authorization || '').replace('Bearer ', ''), process.env.JWT_SECRET); next(); }
  catch { res.status(401).json({ error: 'Unauthorized' }); }
};
const sign = u => jwt.sign({ id: u.id }, process.env.JWT_SECRET, { expiresIn: '7d' });

// ---------- Auth ----------
app.post('/auth/register', wrap(async (req, res) => {
  const { name, email, password } = req.body;
  if (!name || !email || !password || password.length < 8) return res.status(400).json({ error: 'Invalid input' });
  const hash = await bcrypt.hash(password, 10);
  try {
    const { rows } = await q('INSERT INTO users(name,email,password_hash) VALUES($1,$2,$3) RETURNING id,name,email', [name, email.toLowerCase(), hash]);
    res.json({ token: sign(rows[0]), user: rows[0] });
  } catch { res.status(409).json({ error: 'Email already registered' }); }
}));
app.post('/auth/login', wrap(async (req, res) => {
  const { rows } = await q('SELECT * FROM users WHERE email=$1', [(req.body.email || '').toLowerCase()]);
  const u = rows[0];
  if (!u || !(await bcrypt.compare(req.body.password || '', u.password_hash))) return res.status(401).json({ error: 'Wrong email or password' });
  res.json({ token: sign(u), user: { id: u.id, name: u.name, email: u.email } });
}));

// ---------- Products ----------
app.get('/products', wrap(async (req, res) => {
  const { q: term, category, min, max } = req.query;
  const { rows } = await q(
    `SELECT p.*, u.name AS seller_name FROM products p JOIN users u ON u.id=p.seller_id
     WHERE p.status='active'
       AND ($1::text IS NULL OR to_tsvector('simple', p.title||' '||coalesce(p.description,'')) @@ plainto_tsquery('simple',$1) OR p.title ILIKE '%'||$1||'%')
       AND ($2::text IS NULL OR p.category=$2) AND ($3::numeric IS NULL OR p.price>=$3) AND ($4::numeric IS NULL OR p.price<=$4)
     ORDER BY p.created_at DESC LIMIT 50`, [term || null, category || null, min || null, max || null]);
  res.json(rows);
}));
app.get('/products/mine', auth, wrap(async (req, res) =>
  res.json((await q('SELECT * FROM products WHERE seller_id=$1 ORDER BY created_at DESC', [req.user.id])).rows)));
app.post('/products', auth, wrap(async (req, res) => {
  const { title, description, price, category, condition, image_url } = req.body;
  if (!title || !(price > 0)) return res.status(400).json({ error: 'Title and price required' });
  const { rows } = await q('INSERT INTO products(seller_id,title,description,price,category,condition,image_url) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING *',
    [req.user.id, title, description, price, category, condition, image_url]);
  res.status(201).json(rows[0]);
}));
app.put('/products/:id', auth, wrap(async (req, res) => {
  const { title, description, price, status } = req.body;
  const { rows } = await q(`UPDATE products SET title=coalesce($1,title), description=coalesce($2,description),
    price=coalesce($3,price), status=coalesce($4,status) WHERE id=$5 AND seller_id=$6 RETURNING *`,
    [title, description, price, status, req.params.id, req.user.id]);
  rows[0] ? res.json(rows[0]) : res.status(404).json({ error: 'Not found' });
}));
app.delete('/products/:id', auth, wrap(async (req, res) => {
  await q("UPDATE products SET status='removed' WHERE id=$1 AND seller_id=$2", [req.params.id, req.user.id]);
  res.json({ ok: true });
}));

// ---------- Chat & negotiation ----------
app.get('/chat/:productId', auth, wrap(async (req, res) =>
  res.json((await q(`SELECT * FROM messages WHERE product_id=$1 AND (sender_id=$2 OR receiver_id=$2) ORDER BY created_at`,
    [req.params.productId, req.user.id])).rows)));
app.post('/chat/:productId', auth, wrap(async (req, res) => {
  const { receiver_id, body, offer_price } = req.body;
  const { rows } = await q('INSERT INTO messages(product_id,sender_id,receiver_id,body,offer_price) VALUES($1,$2,$3,$4,$5) RETURNING *',
    [req.params.productId, req.user.id, receiver_id, body, offer_price || null]);
  res.status(201).json(rows[0]);
}));

// ---------- Lockers ----------
app.get('/lockers', wrap(async (_req, res) => res.json((await q(
  `SELECT l.*, count(s.*) FILTER (WHERE s.status='free') AS free_slots FROM lockers l LEFT JOIN locker_slots s ON s.locker_id=l.id GROUP BY l.id`)).rows)));
app.post('/lockers', auth, wrap(async (req, res) => { // admin tooling: protect with a role check in production
  const { name, address, lat, lng, slots = [] } = req.body;
  const { rows } = await q('INSERT INTO lockers(name,address,lat,lng) VALUES($1,$2,$3,$4) RETURNING *', [name, address, lat, lng]);
  for (const s of slots) await q('INSERT INTO locker_slots(locker_id,label,size) VALUES($1,$2,$3)', [rows[0].id, s.label, s.size || 'M']);
  res.status(201).json(rows[0]);
}));

// ---------- Orders, payment, retrieval ----------
// Buyer + seller agree on a price (chat) -> buyer creates an order and picks a locker.
app.post('/orders', auth, wrap(async (req, res) => {
  const { product_id, locker_id, agreed_price } = req.body;
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const p = (await c.query("SELECT * FROM products WHERE id=$1 AND status='active' FOR UPDATE", [product_id])).rows[0];
    if (!p || p.seller_id === req.user.id) throw new Error('bad product');
    const slot = (await c.query("SELECT * FROM locker_slots WHERE locker_id=$1 AND status='free' LIMIT 1 FOR UPDATE SKIP LOCKED", [locker_id])).rows[0];
    if (!slot) throw new Error('no slot');
    await c.query("UPDATE locker_slots SET status='reserved' WHERE id=$1", [slot.id]);
    await c.query("UPDATE products SET status='reserved' WHERE id=$1", [product_id]);
    const o = (await c.query('INSERT INTO orders(product_id,buyer_id,seller_id,slot_id,agreed_price) VALUES($1,$2,$3,$4,$5) RETURNING *',
      [product_id, req.user.id, p.seller_id, slot.id, agreed_price || p.price])).rows[0];
    await c.query('COMMIT'); res.status(201).json(o);
  } catch (e) { await c.query('ROLLBACK'); res.status(400).json({ error: e.message }); } finally { c.release(); }
}));
// Payment (escrow). Replace the stub with Midtrans/Xendit/Stripe webhook verification.
app.post('/orders/:id/pay', auth, wrap(async (req, res) => {
  const code = String(Math.floor(100000 + Math.random() * 900000));
  const { rows } = await q(`UPDATE orders SET status='paid', pickup_code_hash=$1, code_expires_at=now()+interval '72 hours'
    WHERE id=$2 AND buyer_id=$3 AND status='pending_payment' RETURNING id,status`, [await bcrypt.hash(code, 10), req.params.id, req.user.id]);
  rows[0] ? res.json({ ...rows[0], pickup_code: code }) : res.status(400).json({ error: 'Cannot pay' }); // code shown once to buyer
}));
// Seller confirms the item was placed in the locker.
app.post('/orders/:id/deposit', auth, wrap(async (req, res) => {
  const { rows } = await q("UPDATE orders SET status='deposited' WHERE id=$1 AND seller_id=$2 AND status='paid' RETURNING *", [req.params.id, req.user.id]);
  if (!rows[0]) return res.status(400).json({ error: 'Cannot deposit' });
  await q("UPDATE locker_slots SET status='occupied' WHERE id=$1", [rows[0].slot_id]);
  res.json(rows[0]);
}));
// Buyer opens the locker with the code. Brute-force protection: 5 attempts max.
const codeLimiter = rateLimit({ windowMs: 60_000, max: 10 });
app.post('/orders/:id/retrieve', auth, codeLimiter, wrap(async (req, res) => {
  const o = (await q("SELECT * FROM orders WHERE id=$1 AND buyer_id=$2 AND status='deposited'", [req.params.id, req.user.id])).rows[0];
  if (!o) return res.status(400).json({ error: 'Not available' });
  if (o.failed_attempts >= 5 || new Date(o.code_expires_at) < new Date()) return res.status(423).json({ error: 'Locked. Contact support.' });
  if (!(await bcrypt.compare(String(req.body.code || ''), o.pickup_code_hash))) {
    await q('UPDATE orders SET failed_attempts=failed_attempts+1 WHERE id=$1', [o.id]);
    return res.status(401).json({ error: 'Wrong code', attempts_left: 4 - o.failed_attempts });
  }
  await q("UPDATE orders SET status='picked_up' WHERE id=$1", [o.id]);
  await q("UPDATE locker_slots SET status='free' WHERE id=$1", [o.slot_id]);
  res.json({ unlocked: true, slot_id: o.slot_id }); // here: send the open command to the locker hardware
}));
// Buyer confirms OK -> funds released to seller. Otherwise open a dispute.
app.post('/orders/:id/confirm', auth, wrap(async (req, res) => {
  const { rows } = await q("UPDATE orders SET status='completed' WHERE id=$1 AND buyer_id=$2 AND status='picked_up' RETURNING *", [req.params.id, req.user.id]);
  if (rows[0]) await q("UPDATE products SET status='sold' WHERE id=$1", [rows[0].product_id]);
  rows[0] ? res.json(rows[0]) : res.status(400).json({ error: 'Cannot confirm' });
}));
app.get('/orders', auth, wrap(async (req, res) => res.json((await q(
  'SELECT o.*, p.title FROM orders o JOIN products p ON p.id=o.product_id WHERE buyer_id=$1 OR seller_id=$1 ORDER BY o.created_at DESC', [req.user.id])).rows)));

// ---------- Ratings & disputes ----------
app.post('/orders/:id/rate', auth, wrap(async (req, res) => {
  const o = (await q("SELECT * FROM orders WHERE id=$1 AND status='completed' AND (buyer_id=$2 OR seller_id=$2)", [req.params.id, req.user.id])).rows[0];
  if (!o) return res.status(400).json({ error: 'Cannot rate' });
  const ratee = o.buyer_id === req.user.id ? o.seller_id : o.buyer_id;
  await q('INSERT INTO ratings(order_id,rater_id,ratee_id,stars,comment) VALUES($1,$2,$3,$4,$5)', [o.id, req.user.id, ratee, req.body.stars, req.body.comment]);
  await q('UPDATE users SET rating_avg=(SELECT avg(stars) FROM ratings WHERE ratee_id=$1) WHERE id=$1', [ratee]);
  res.json({ ok: true });
}));
app.post('/orders/:id/dispute', auth, wrap(async (req, res) => {
  const { rows } = await q("UPDATE orders SET status='disputed' WHERE id=$1 AND buyer_id=$2 AND status IN ('picked_up','deposited') RETURNING id", [req.params.id, req.user.id]);
  if (!rows[0]) return res.status(400).json({ error: 'Cannot dispute' });
  res.status(201).json((await q('INSERT INTO disputes(order_id,opened_by,reason) VALUES($1,$2,$3) RETURNING *', [req.params.id, req.user.id, req.body.reason])).rows[0]);
}));

app.listen(process.env.PORT || 3000, () => console.log('API running'));
