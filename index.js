require('dotenv').config();
const app = require('./app');

const { PORT } = process.env;

app.listen(PORT, () => {
  console.log(`🚀 Servidor corriendo en http://localhost:${PORT}`);
  console.log(`📡 Buses:   http://localhost:${PORT}/api/buses/PA433`);
  console.log(`📍 Cercanos: http://localhost:${PORT}/api/nearby?lat=-33.4489&lng=-70.6693`);
});
