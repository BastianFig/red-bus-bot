// Límite de consultas por IP, para no sobrecargar red.cl
const VENTANA_MS    = 60 * 1000;
const MAX_CONSULTAS = 60;
const consultas     = new Map();

function rateLimit(req, res, next) {
  const ahora  = Date.now();
  const actual = consultas.get(req.ip);

  if (!actual || ahora - actual.inicio > VENTANA_MS) {
    consultas.set(req.ip, { inicio: ahora, total: 1 });
    return next();
  }
  if (++actual.total > MAX_CONSULTAS) {
    res.set('Retry-After', Math.ceil((actual.inicio + VENTANA_MS - ahora) / 1000));
    return res.status(429).json({ error: 'Demasiadas consultas, intenta en un minuto' });
  }
  next();
}

// Limpia IPs antiguas para que el mapa no crezca sin límite
setInterval(() => {
  const limite = Date.now() - VENTANA_MS;
  for (const [ip, c] of consultas) if (c.inicio < limite) consultas.delete(ip);
}, VENTANA_MS).unref();

module.exports = rateLimit;
