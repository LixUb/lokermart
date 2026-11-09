CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE users (
  id SERIAL PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL, rating_avg NUMERIC(3,2) DEFAULT 0, created_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE lockers (
  id SERIAL PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL,
  lat DOUBLE PRECISION, lng DOUBLE PRECISION);
CREATE TABLE locker_slots (
  id SERIAL PRIMARY KEY, locker_id INT REFERENCES lockers(id), label TEXT NOT NULL,
  size TEXT CHECK (size IN ('S','M','L')) DEFAULT 'M',
  status TEXT CHECK (status IN ('free','reserved','occupied')) DEFAULT 'free');
CREATE TABLE products (
  id SERIAL PRIMARY KEY, seller_id INT REFERENCES users(id), title TEXT NOT NULL,
  description TEXT, price NUMERIC(12,2) NOT NULL, category TEXT, condition TEXT,
  image_url TEXT, status TEXT DEFAULT 'active', created_at TIMESTAMPTZ DEFAULT now());
CREATE INDEX products_search ON products USING gin (to_tsvector('simple', title || ' ' || coalesce(description,'')));
CREATE TABLE messages (
  id SERIAL PRIMARY KEY, product_id INT REFERENCES products(id),
  sender_id INT REFERENCES users(id), receiver_id INT REFERENCES users(id),
  body TEXT, offer_price NUMERIC(12,2), created_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE orders (
  id SERIAL PRIMARY KEY, product_id INT REFERENCES products(id),
  buyer_id INT REFERENCES users(id), seller_id INT REFERENCES users(id),
  slot_id INT REFERENCES locker_slots(id), agreed_price NUMERIC(12,2) NOT NULL,
  status TEXT DEFAULT 'pending_payment'
    CHECK (status IN ('pending_payment','paid','deposited','picked_up','completed','disputed','refunded')),
  pickup_code_hash TEXT, failed_attempts INT DEFAULT 0, code_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE ratings (
  id SERIAL PRIMARY KEY, order_id INT UNIQUE REFERENCES orders(id),
  rater_id INT REFERENCES users(id), ratee_id INT REFERENCES users(id),
  stars INT CHECK (stars BETWEEN 1 AND 5), comment TEXT);
CREATE TABLE disputes (
  id SERIAL PRIMARY KEY, order_id INT REFERENCES orders(id), opened_by INT REFERENCES users(id),
  reason TEXT NOT NULL, status TEXT DEFAULT 'open', resolution TEXT, created_at TIMESTAMPTZ DEFAULT now());
