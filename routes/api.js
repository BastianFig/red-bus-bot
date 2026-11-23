// API para la app Wear OS
const express = require('express');
const { getBuses }  = require('../services/redApi');
const { getNearby, getStop } = require('../services/stopsService');

const router = express.Router();

// El nombre es opcional: si faltan los datos de paraderos, los tiempos siguen funcionando
function nombreParadero(codigo) {
  try {
    return getStop(codigo)?.n ?? null;
  } catch {
    return null;
  }
}

// Tiempos de llegada de un paradero
router.get('/buses/:paradero', async (req, res) => {
  const { paradero } = req.params;
  const servicio     = req.query.servicio ?? '';

  if (!/^[A-Za-z]{2}\d{1,5}$/.test(paradero)) {
    return res.status(400).json({ error: 'Código de paradero inválido (ej: PA433 o PF54)' });
  }
  // Opcional: debe ser un solo valor alfanumérico (ej: 506v, B01)
  if (servicio !== '' && !/^[A-Za-z0-9]{1,6}$/.test(servicio)) {
    return res.status(400).json({ error: 'Servicio inválido (ej: 506v o B01)' });
  }

  try {
    const codigo = paradero.toUpperCase();
    const buses  = await getBuses(codigo, servicio.toUpperCase());
    res.json({ paradero: codigo, nombre: nombreParadero(codigo), buses });
  } catch (err) {
    // red.cl responde 400 cuando el paradero no existe
    if (err.status === 400) return res.status(404).json({ error: 'Paradero no encontrado' });
    console.error('Error consultando red.cl:', err.message);
    res.status(502).json({ error: 'Error consultando red.cl' });
  }
});

// Paraderos cercanos a una coordenada
router.get('/nearby', (req, res) => {
  const lat   = parseFloat(req.query.lat);
  const lng   = parseFloat(req.query.lng);
  const radio = Math.min(parseInt(req.query.radio, 10) || 500, 2000);
  const limit = Math.min(parseInt(req.query.limit, 10) || 20, 50);

  if (Number.isNaN(lat) || Number.isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return res.status(400).json({ error: 'Parámetros lat y lng requeridos (ej: ?lat=-33.45&lng=-70.65)' });
  }

  try {
    res.json({ lat, lng, radio, paraderos: getNearby(lat, lng, radio, limit) });
  } catch (err) {
    console.error('Error buscando paraderos cercanos:', err.message);
    res.status(500).json({ error: 'Error buscando paraderos cercanos' });
  }
});

module.exports = router;
