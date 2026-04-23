require('dotenv').config();
const express = require('express');
const { handleMessage } = require('./handlers/messageHandler');

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
  console.log(`🚀 Bot corriendo en http://localhost:${PORT}`);
  console.log(`🧪 Prueba: http://localhost:${PORT}/test/PA433`);
});