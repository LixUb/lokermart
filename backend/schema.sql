-- Lokermart: skema PostgreSQL (ID angka SERIAL, sama seperti versi awal)
-- Catatan: jika database lama sudah memakai skema versi sebelumnya, buat ulang database-nya
-- (atau jalankan: DROP SCHEMA public CASCADE; CREATE SCHEMA public;) lalu npm run db:init.

CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  phone TEXT,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user','admin')),
  rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
  rating_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS lockers (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  address TEXT NOT NULL,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS locker_slots (
  id SERIAL PRIMARY KEY,
  locker_id INT NOT NULL REFERENCES lockers(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  size TEXT NOT NULL DEFAULT 'M' CHECK (size IN ('S','M','L')),
  status TEXT NOT NULL DEFAULT 'free' CHECK (status IN ('free','reserved','occupied','maintenance')),
  UNIQUE (locker_id, label)
);

CREATE TABLE IF NOT EXISTS products (
  id SERIAL PRIMARY KEY,
  seller_id INT NOT NULL REFERENCES users(id),
  title TEXT NOT NULL,
  description TEXT,
  price NUMERIC(12,2) NOT NULL CHECK (price > 0),
  category TEXT NOT NULL DEFAULT 'Lainnya',
  condition TEXT NOT NULL DEFAULT 'Bekas - Baik',
  image_url TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','reserved','sold','removed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS products_search ON products USING gin (to_tsvector('simple', title || ' ' || coalesce(description,'')));
CREATE INDEX IF NOT EXISTS products_status ON products(status, category);

-- Chat sekaligus negosiasi: pesan biasa (body) dan/atau tawaran harga (offer_price + offer_status)
CREATE TABLE IF NOT EXISTS messages (
  id SERIAL PRIMARY KEY,
  product_id INT NOT NULL REFERENCES products(id),
  sender_id INT NOT NULL REFERENCES users(id),
  receiver_id INT NOT NULL REFERENCES users(id),
  body TEXT,
  offer_price NUMERIC(12,2) CHECK (offer_price > 0),
  offer_status TEXT CHECK (offer_status IN ('pending','accepted','rejected','countered','withdrawn')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (body IS NOT NULL OR offer_price IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS messages_thread ON messages(product_id, created_at);

CREATE TABLE IF NOT EXISTS orders (
  id SERIAL PRIMARY KEY,
  product_id INT NOT NULL REFERENCES products(id),
  buyer_id INT NOT NULL REFERENCES users(id),
  seller_id INT NOT NULL REFERENCES users(id),
  slot_id INT REFERENCES locker_slots(id),
  agreed_price NUMERIC(12,2) NOT NULL,
  fee NUMERIC(12,2) NOT NULL DEFAULT 0,
  status TEXT NOT NULL DEFAULT 'pending_payment'
    CHECK (status IN ('pending_payment','paid','deposited','picked_up','completed','disputed','refunded','cancelled')),
  pickup_code_hash TEXT,          -- untuk verifikasi kode
  pickup_code_enc TEXT,           -- terenkripsi, agar pembeli bisa melihat kodenya lagi
  failed_attempts INT NOT NULL DEFAULT 0,
  code_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at TIMESTAMPTZ,
  deposited_at TIMESTAMPTZ,
  picked_up_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS orders_buyer ON orders(buyer_id);
CREATE INDEX IF NOT EXISTS orders_seller ON orders(seller_id);

-- Escrow: dana ditahan (held) sampai pembeli mengonfirmasi, lalu dicairkan (released) atau dikembalikan (refunded)
CREATE TABLE IF NOT EXISTS payments (
  id SERIAL PRIMARY KEY,
  order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  method TEXT NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  status TEXT NOT NULL DEFAULT 'held' CHECK (status IN ('held','released','refunded')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ratings (
  id SERIAL PRIMARY KEY,
  order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  rater_id INT NOT NULL REFERENCES users(id),
  ratee_id INT NOT NULL REFERENCES users(id),
  stars INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (order_id, rater_id)
);

CREATE TABLE IF NOT EXISTS disputes (
  id SERIAL PRIMARY KEY,
  order_id INT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  opened_by INT NOT NULL REFERENCES users(id),
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','resolved_buyer','resolved_seller')),
  resolution TEXT,
  resolved_by INT REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  user_id INT,
  action TEXT NOT NULL,
  detail JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
