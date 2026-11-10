// Menjadikan pengguna sebagai admin:  npm run make-admin -- email@contoh.com
require('dotenv').config();
const { Pool } = require('pg');

(async () => {
  const email = (process.argv[2] || '').toLowerCase();
  if (!email) { console.error('Pemakaian: npm run make-admin -- email@contoh.com'); process.exit(1); }
  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  try {
    const r = await pool.query("UPDATE users SET role='admin' WHERE email=$1 RETURNING id, name, email", [email]);
    console.log(r.rowCount ? `Admin: ${r.rows[0].name} <${r.rows[0].email}>` : 'Email tidak ditemukan. Daftar dulu lewat aplikasi.');
  } finally {
    await pool.end();
  }
})();
