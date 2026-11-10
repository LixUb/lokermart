// Menjalankan schema.sql lalu seed.sql ke database dari DATABASE_URL
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

(async () => {
  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  try {
    for (const file of ['schema.sql', 'seed.sql']) {
      await pool.query(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'));
      console.log(`OK: ${file}`);
    }
  } catch (err) {
    console.error('Gagal inisialisasi database:', err.message);
    process.exitCode = 1;
  } finally {
    await pool.end();
  }
})();
