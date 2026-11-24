// Arma la aplicación Express: middlewares y rutas. No abre ningún puerto (eso lo hace index.js).
const express   = require('express');
const rateLimit = require('./middleware/rateLimit');

const app = express();

// En Render la cadena es: cliente -> Cloudflare -> red interna de Render -> app, así que
// X-Forwarded-For trae 3 IPs. Con 3, req.ip es la del cliente; con menos sería la de un proxy.
app.set('trust proxy', 3);

// No anunciar qué framework usa el servidor
app.disable('x-powered-by');

// Para monitores de disponibilidad (UptimeRobot): responde rápido y no consulta a red.cl
app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.use('/api', rateLimit, require('./routes/api'));

module.exports = app;
