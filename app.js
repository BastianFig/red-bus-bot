// Arma la aplicación Express: middlewares y rutas. No abre ningún puerto (eso lo hace index.js).
const express   = require('express');
const rateLimit = require('./middleware/rateLimit');

const app = express();

// Detrás del proxy de Render, para que req.ip sea la IP real del cliente
app.set('trust proxy', 1);

app.use('/api', rateLimit, require('./routes/api'));

module.exports = app;
