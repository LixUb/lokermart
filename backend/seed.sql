-- Data awal: 3 loker contoh (hanya diisi jika tabel lockers masih kosong), masing-masing 4 slot
WITH l AS (
  INSERT INTO lockers (name, address, lat, lng)
  SELECT * FROM (VALUES
    ('Loker Gerbang UNNES',  'Jl. Raya Sekaran, Gunungpati, Semarang', -7.0521, 110.3891),
    ('Loker Perpustakaan',   'Kampus Sekaran, Gunungpati, Semarang',   -7.0548, 110.3903),
    ('Loker Simpang Lima',   'Jl. Pahlawan, Semarang',                 -6.9904, 110.4229)
  ) AS v(name, address, lat, lng)
  WHERE NOT EXISTS (SELECT 1 FROM lockers)
  RETURNING id
)
INSERT INTO locker_slots (locker_id, label, size)
SELECT l.id, s.label, s.size
FROM l CROSS JOIN (VALUES ('A1','S'), ('A2','M'), ('A3','M'), ('A4','L')) AS s(label, size);
