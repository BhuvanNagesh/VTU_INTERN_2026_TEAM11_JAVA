/**
 * localStorage-backed mock that mimics the Supabase JS client API.
 *
 * Supports: .from().select().insert().upsert().delete().update()
 *           .eq() .in() .order() .single()
 *
 * Switch to real Supabase later by changing the import back to supabaseClient.
 */

const STORE_KEY = 'ww_local_db';

// ── Seed data — mirrors actual Supabase tables ────────────────────────
const SEED = {
  users: [
    { id: 1, full_name: 'Aditya Raman', email: 'aditya@example.com', currency: 'INR', created_at: new Date().toISOString() },
  ],
  investment_lots: [
    { id: 1, user_id: 1, folio_number: '1234567890', scheme_name: 'HDFC Mid-Cap Opportunities Fund', purchase_amount: 150000, units_remaining: 245.678, created_at: new Date().toISOString() },
    { id: 2, user_id: 1, folio_number: '9876543210', scheme_name: 'ICICI Prudential Bluechip Fund',  purchase_amount: 280000, units_remaining: 512.340, created_at: new Date().toISOString() },
    { id: 3, user_id: 1, folio_number: '5555566666', scheme_name: 'SBI Small Cap Fund',              purchase_amount: 380000, units_remaining: 890.123, created_at: new Date().toISOString() },
    { id: 4, user_id: 1, folio_number: '7777788888', scheme_name: 'Axis Long Term Equity (ELSS)',    purchase_amount: 120000, units_remaining: 312.500, created_at: new Date().toISOString() },
    { id: 5, user_id: 1, folio_number: '3333344444', scheme_name: 'Mirae Asset Tax Saver Fund',      purchase_amount: 180000, units_remaining: 678.900, created_at: new Date().toISOString() },
  ],
  goals: [],
  goal_fund_links: [],
};

// ── DB helpers ────────────────────────────────────────────────────────
function loadDB() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (raw) return JSON.parse(raw);
  } catch { /* corrupt — reset */ }
  localStorage.setItem(STORE_KEY, JSON.stringify(SEED));
  return structuredClone(SEED);
}

function saveDB(db) {
  localStorage.setItem(STORE_KEY, JSON.stringify(db));
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

// ── Parse a Supabase-style select string ──────────────────────────────
// e.g.  "*, goal_fund_links ( id, folio_id, allocation_pct )"
// Returns { cols: '*' | [...], relations: { name: { cols, fk } } }
function parseSelect(str) {
  if (!str) return { cols: '*', relations: {} };

  const relations = {};
  // Pull out nested relations like "goal_fund_links ( id, folio_id )"
  const cleaned = str.replace(
    /(\w+)\s*\(\s*([^)]+)\)/g,
    (_, relName, relCols) => {
      relations[relName] = relCols.split(',').map(c => c.trim());
      return '';
    }
  );

  const cols = cleaned.replace(/,\s*,/g, ',').replace(/^[,\s]+|[,\s]+$/g, '').trim();
  return { cols: cols === '*' || cols === '' ? '*' : cols.split(',').map(c => c.trim()), relations };
}

// ── Relation FK conventions ───────────────────────────────────────────
// goal_fund_links.goal_id → goals, goal_fund_links.folio_id → folios
const FK_MAP = {
  goal_fund_links: { goals: 'goal_id' },
};

// ── Query builder ─────────────────────────────────────────────────────
class QueryBuilder {
  constructor(table) {
    this._table = table;
    this._op = 'select';
    this._selectStr = '*';
    this._filters = [];       // [{ type, col, val }]
    this._orderCol = null;
    this._orderAsc = true;
    this._single = false;
    this._insertData = null;
    this._upsertData = null;
    this._upsertConflict = null;
    this._updateData = null;
  }

  select(str) { this._op = 'select'; this._selectStr = str || '*'; return this; }
  insert(data) { this._op = 'insert'; this._insertData = Array.isArray(data) ? data : [data]; return this; }
  upsert(data, opts) { this._op = 'upsert'; this._upsertData = Array.isArray(data) ? data : [data]; this._upsertConflict = opts?.onConflict; return this; }
  update(data) { this._op = 'update'; this._updateData = data; return this; }
  delete() { this._op = 'delete'; return this; }

  eq(col, val) { this._filters.push({ type: 'eq', col, val }); return this; }
  in(col, vals) { this._filters.push({ type: 'in', col, vals }); return this; }
  order(col, opts) { this._orderCol = col; this._orderAsc = opts?.ascending ?? true; return this; }
  single() { this._single = true; return this; }

  // Terminal — executes and returns { data, error }
  then(resolve, reject) {
    try {
      const result = this._exec();
      resolve(result);
    } catch (e) {
      if (reject) reject(e);
      else resolve({ data: null, error: { message: e.message } });
    }
  }

  _exec() {
    const db = loadDB();
    const table = db[this._table];
    if (!table) return { data: null, error: { message: `Table "${this._table}" not found` } };

    switch (this._op) {
      case 'select': return this._execSelect(db, table);
      case 'insert': return this._execInsert(db, table);
      case 'upsert': return this._execUpsert(db, table);
      case 'update': return this._execUpdate(db, table);
      case 'delete': return this._execDelete(db, table);
      default: return { data: null, error: { message: 'Unknown op' } };
    }
  }

  _applyFilters(rows) {
    return rows.filter(row =>
      this._filters.every(f => {
        if (f.type === 'eq') return row[f.col] == f.val;  // loose equality for number/string
        if (f.type === 'in') return f.vals.includes(row[f.col]);
        return true;
      })
    );
  }

  _applyOrder(rows) {
    if (!this._orderCol) return rows;
    return [...rows].sort((a, b) => {
      const av = a[this._orderCol], bv = b[this._orderCol];
      if (av < bv) return this._orderAsc ? -1 : 1;
      if (av > bv) return this._orderAsc ? 1 : -1;
      return 0;
    });
  }

  _execSelect(db, table) {
    let rows = this._applyFilters([...table]);
    rows = this._applyOrder(rows);

    // Attach relations
    const { relations } = parseSelect(this._selectStr);
    if (Object.keys(relations).length > 0) {
      rows = rows.map(row => {
        const r = { ...row };
        for (const [relName, relCols] of Object.entries(relations)) {
          const relTable = db[relName];
          if (!relTable) continue;

          // Figure out FK: which column in relTable points to this table's id?
          const fkCol = this._guessFk(relName);
          let linked = relTable.filter(rr => rr[fkCol] == row.id);

          // If relCols isn't '*', pick only those columns (plus id always)
          if (relCols[0] !== '*') {
            linked = linked.map(rr => {
              const picked = { id: rr.id };
              relCols.forEach(c => { if (c in rr) picked[c] = rr[c]; });

              // Nested: if a relCol references another table (e.g., "goals" inside goal_fund_links)
              // We check if any relCol matches a table name
              relCols.forEach(c => {
                if (db[c] && rr[c.slice(0, -1) + '_id']) {
                  // not applicable here, skip
                }
              });

              return picked;
            });
          }

          r[relName] = linked;
        }
        return r;
      });
    }

    if (this._single) {
      return { data: rows[0] ?? null, error: rows.length === 0 ? { message: 'No rows found' } : null };
    }
    return { data: rows, error: null };
  }

  _guessFk(relName) {
    // goal_fund_links → goals table: FK is goal_id
    // goal_fund_links → folios table: FK is folio_id
    // For child relations: if relName = 'goal_fund_links' and this._table = 'goals', FK = 'goal_id'
    const singularParent = this._table.replace(/s$/, '');
    const fkGuess = singularParent + '_id';  // e.g. "goal_id" for goals table

    // Verify it exists in the relation's data
    const db = loadDB();
    const sample = db[relName]?.[0];
    if (sample && fkGuess in sample) return fkGuess;

    // Fallback: try table_id pattern
    return fkGuess;
  }

  _execInsert(db, table) {
    const now = new Date().toISOString();
    const inserted = this._insertData.map(row => ({
      id: row.id || uuid(),
      ...row,
      created_at: row.created_at || now,
      updated_at: row.updated_at || now,
    }));
    table.push(...inserted);
    saveDB(db);

    if (this._single) return { data: inserted[0], error: null };
    return { data: inserted, error: null };
  }

  _execUpsert(db, table) {
    const now = new Date().toISOString();
    const conflictCols = this._upsertConflict?.split(',').map(c => c.trim()) ?? ['id'];

    this._upsertData.forEach(row => {
      const idx = table.findIndex(existing =>
        conflictCols.every(c => existing[c] != null && existing[c] == row[c])
      );
      if (idx >= 0) {
        table[idx] = { ...table[idx], ...row, updated_at: now };
      } else {
        table.push({ id: row.id || uuid(), ...row, created_at: now, updated_at: now });
      }
    });

    saveDB(db);
    return { data: this._upsertData, error: null };
  }

  _execUpdate(db, table) {
    const now = new Date().toISOString();
    const matched = this._applyFilters(table);
    matched.forEach(row => {
      Object.assign(row, this._updateData, { updated_at: now });
    });
    saveDB(db);
    return { data: matched, error: null };
  }

  _execDelete(db, table) {
    const before = table.length;
    const keep = table.filter(row =>
      !this._filters.every(f => {
        if (f.type === 'eq') return row[f.col] == f.val;
        if (f.type === 'in') return f.vals.includes(row[f.col]);
        return true;
      })
    );
    db[this._table] = keep;
    saveDB(db);
    return { data: null, error: null };
  }
}

// ── Public API (mimics createClient().from()) ─────────────────────────
export const supabase = {
  from(table) {
    return new QueryBuilder(table);
  },
};

/**
 * Call this from the browser console to wipe all local data:
 *   localStorage.removeItem('ww_local_db')
 *   location.reload()
 */
