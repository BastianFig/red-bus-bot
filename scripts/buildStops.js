// Convierte data/gtfs/stops.txt (GTFS del DTPM) en data/stops.json,
// dejando solo paraderos de bus con el código que entiende red.cl.
// Uso: node scripts/buildStops.js
const fs   = require('fs');
const path = require('path');

const INPUT  = path.join(__dirname, '..', 'data', 'gtfs', 'stops.txt');
const OUTPUT = path.join(__dirname, '..', 'data', 'stops.json');

// P + letra + número (PA433, PF54). PT0xxx son estaciones de tren, no buses.
const BUS_STOP_ID = /^P(?!T)[A-Z]\d{1,5}$/;

function parseCsvLine(line) {
  const out = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') { cur += '"'; i++; }
      else if (ch === '"') quoted = false;
      else cur += ch;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') { out.push(cur); cur = ''; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}

if (!fs.existsSync(INPUT)) {
  console.error(`No existe ${INPUT}\nCopia ahí el stops.txt del GTFS del DTPM.`);
  process.exit(1);
}

const lines  = fs.readFileSync(INPUT, 'utf8').replace(/^﻿/, '').split(/\r?\n/).filter(Boolean);
const header = parseCsvLine(lines[0]);
const col    = name => header.indexOf(name);
const iId = col('stop_id'), iName = col('stop_name'), iLat = col('stop_lat'), iLon = col('stop_lon');

const seen  = new Set();
const stops = [];
let skipped = 0, duplicated = 0;

for (const line of lines.slice(1)) {
  const f   = parseCsvLine(line);
  const id  = f[iId]?.trim();
  const lat = parseFloat(f[iLat]);
  const lon = parseFloat(f[iLon]);

  if (!BUS_STOP_ID.test(id) || Number.isNaN(lat) || Number.isNaN(lon)) { skipped++; continue; }
  if (seen.has(id)) { duplicated++; continue; }
  seen.add(id);

  // "PA433-Nataniel Cox / esq. Tarapacá" -> "Nataniel Cox / esq. Tarapacá"
  const nombre = (f[iName] || '').replace(new RegExp(`^${id}\\s*-\\s*`), '').trim();
  stops.push({
    c: id,
    n: nombre,
    la: +lat.toFixed(6),
    lo: +lon.toFixed(6)
  });
}

fs.writeFileSync(OUTPUT, JSON.stringify(stops));
console.log(`✅ ${stops.length} paraderos -> ${path.relative(process.cwd(), OUTPUT)}`);
console.log(`   descartados: ${skipped} (metro/tren/otros), duplicados: ${duplicated}`);
