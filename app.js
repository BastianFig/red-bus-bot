// Arma la aplicación Express: middlewares y rutas. No abre ningún puerto (eso lo hace index.js).
const express   = require('express');
const rateLimit = require('./middleware/rateLimit');

const app = express();

// Render va detrás de Cloudflare: son dos proxys (Cloudflare y el balanceador de Render).
// Con 2, req.ip es la IP real del cliente; con 1 sería la de Cloudflare y el límite no contaría bien.
app.set('trust proxy', 2);

// No anunciar qué framework usa el servidor
app.disable('x-powered-by');

app.use('/api', rateLimit, require('./routes/api'));

module.exports = app;
