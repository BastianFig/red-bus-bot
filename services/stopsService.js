const fs   = require('fs');
const path = require('path');

const STOPS_FILE = path.join(__dirname, '..', 'data', 'stops.json');

let stops = null;

function load() {
  if (stops) return stops;
  if (!fs.existsSync(STOPS_FILE)) {
    throw new Error('Falta data/stops.json — ejecuta: node scripts/buildStops.js');
  }
  stops = JSON.parse(fs.readFileSync(STOPS_FILE, 'utf8'));
  console.log(`🚏 ${stops.length} paraderos cargados en memoria`);
  return stops;
}

// Distancia en metros entre dos coordenadas (fórmula de Haversine)
function distanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = d => d * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function getNearby(lat, lng, radio = 500, limit = 20) {
  const all = load();

  // Descarte rápido con un cuadro alrededor del punto antes de calcular Haversine
  const dLat = radio / 111320;
  const dLng = radio / (111320 * Math.cos(lat * Math.PI / 180));

  const result = [];
  for (const s of all) {
    if (Math.abs(s.la - lat) > dLat || Math.abs(s.lo - lng) > dLng) continue;
    const d = distanceMeters(lat, lng, s.la, s.lo);
    if (d <= radio) {
      result.push({ codigo: s.c, nombre: s.n, lat: s.la, lng: s.lo, distancia: Math.round(d) });
    }
  }

  return result.sort((a, b) => a.distancia - b.distancia).slice(0, limit);
}

module.exports = { getNearby, load };
