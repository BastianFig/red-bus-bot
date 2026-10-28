// API para la app Wear OS
const express = require('express');
const { getBuses }  = require('../services/redApi');
const { getNearby } = require('../services/stopsService');

const router = express.Router();

// Tiempos de llegada de un paradero
router.get('/buses/:paradero', async (req, res) => {
  const { paradero } = req.params;
  const servicio     = req.query.servicio || '';

  if (!/^[A-Za-z]{2}\d{1,5}$/.test(paradero)) {
    return res.status(400).json({ error: 'Código de paradero inválido (ej: PA433 o PF54)' });
  }

  try {
    const buses = await getBuses(paradero.toUpperCase(), servicio.toUpperCase());
    res.json({ paradero: paradero.toUpperCase(), buses });
  } catch (err) {
    res.status(502).json({ error: 'Error consultando red.cl', detail: err.message });
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
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
