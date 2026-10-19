require('dotenv').config();
const express = require('express');
const { handleMessage } = require('./handlers/messageHandler');
const { getBuses }      = require('./services/redApi');
const { getNearby }     = require('./services/stopsService');

const app = express();
app.use(express.json());

const { PORT, VERIFY_TOKEN, WHATSAPP_TOKEN, PHONE_NUMBER_ID } = process.env;

// Meta verifica el webhook con GET
app.get('/webhook', (req, res) => {
  const mode      = req.query['hub.mode'];
  const token     = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode === 'subscribe' && token === VERIFY_TOKEN) {
    console.log('✅ Webhook verificado por Meta');
    return res.status(200).send(challenge);
  }
  res.sendStatus(403);
});

// Recibe mensajes de WhatsApp
app.post('/webhook', async (req, res) => {
  res.sendStatus(200); // Meta requiere respuesta rápida

  const entry   = req.body?.entry?.[0];
  const change  = entry?.changes?.[0]?.value;
  const message = change?.messages?.[0];

  if (!message || message.type !== 'text') return;

  const from = message.from;
  const text = message.text.body;

  console.log(`📩 [${from}]: ${text}`);

  const respuesta = await handleMessage(text);

  await fetch(`https://graph.facebook.com/v19.0/${PHONE_NUMBER_ID}/messages`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${WHATSAPP_TOKEN}`,
      'Content-Type':  'application/json'
    },
    body: JSON.stringify({
      messaging_product: 'whatsapp',
      to:   from,
      type: 'text',
      text: { body: respuesta }
    })
  });
});

// API para la app Wear OS
app.get('/api/buses/:paradero', async (req, res) => {
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
app.get('/api/nearby', (req, res) => {
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

// Ruta de prueba local — sin necesitar WhatsApp
app.get('/test/:paradero', async (req, res) => {
  try {
    const respuesta = await handleMessage(req.params.paradero);
    res.send(`<pre>${respuesta}</pre>`);
  } catch (err) {
    res.send(`<pre>Error: ${err.message}</pre>`);
  }
});

app.listen(PORT, () => {
  console.log(`🚀 Servidor corriendo en http://localhost:${PORT}`);
  console.log(`📡 API:   http://localhost:${PORT}/api/buses/PA433`);
  console.log(`🧪 Test:  http://localhost:${PORT}/test/PA433`);
});