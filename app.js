// Arma la aplicación Express: middlewares y rutas. No abre ningún puerto (eso lo hace index.js).
const express   = require('express');
const rateLimit = require('./middleware/rateLimit');

const app = express();

// Render va detrás de Cloudflare: son dos proxys (Cloudflare y el balanceador de Render).
// Con 2, req.ip es la IP real del cliente; con 1 sería la de Cloudflare y el límite no contaría bien.
app.set('trust proxy', 2);

// No anunciar qué framework usa el servidor
app.disable('x-powered-by');

// TEMPORAL: diagnóstico del límite por IP. Quitar apenas se confirme el valor de trust proxy.
app.get('/debug-ip', (req, res) => {
  res.json({
    ip: req.ip,
    ips: req.ips,
    xForwardedFor: req.get('x-forwarded-for'),
    cfConnectingIp: req.get('cf-connecting-ip'),
    socket: req.socket.remoteAddress
  });
});

app.use('/api', rateLimit, require('./routes/api'));

module.exports = app;
