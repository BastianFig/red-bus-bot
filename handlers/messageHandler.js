const { getBuses } = require('../services/redApi');

function parseInput(msg) {
  const upper = msg.trim().toUpperCase();
  const paraderoMatch = upper.match(/\b[A-Z]{2}\d{1,5}\b/);
  if (!paraderoMatch) return null;
  const resto = upper.replace(paraderoMatch[0], '').trim();
  const servicioMatch = resto.match(/\b[A-Z]?\d{1,3}[A-Z]?\b/);
  return {
    paradero: paraderoMatch[0],
    servicio: servicioMatch ? servicioMatch[0] : ''
  };
}

function formatTiempo(bus) {
  // Parada inhabilitada u otro problema
  if (bus.codigorespuesta !== '00') {
    return `⚠️ ${bus.respuestaServicio}`;
  }

  // horaprediccionbus1 viene como "Entre 9 Y 13 min" o "Menos de 1 min"
  const t1 = bus.horaprediccionbus1 || '';
  const t2 = bus.horaprediccionbus2 || '';

  const lineas = [];
  if (t1) lineas.push(`  próximo: ${t1}`);
  if (t2) lineas.push(`  siguiente: ${t2}`);
  return lineas.join('\n') || '🟢 Llegando';
}

function formatRespuesta(buses, paradero, servicio) {
  if (!buses.length) {
    return `😕 Sin información para *${paradero}*${servicio ? ` / ${servicio}` : ''} ahora.`;
  }

  const lineas = buses.map(b => {
    const tiempo = formatTiempo(b);
    return `• *${b.servicio}* → ${b.destino}\n${tiempo}`;
  }).join('\n\n');

  return [
    `🚌 *Paradero ${paradero}*${servicio ? ` · ${servicio}` : ''}`,
    '',
    lineas,
    '',
    `_Actualizado ahora_`
  ].join('\n');
}

async function handleMessage(body) {
  const msg = body.trim().toLowerCase();

  if (['hola', 'ayuda', 'help', 'inicio', 'start'].includes(msg)) {
    return [
      '👋 *Bot Buses Red Santiago*',
      '',
      'Envíame el código del paradero y te digo cuándo llega el bus.',
      '',
      '📌 *Ejemplos:*',
      '• `PA433` → todos los buses',
      '• `PA433 506` → solo el servicio 506',
      '',
      '🔎 El código está en el poste del paradero o en red.cl'
    ].join('\n');
  }

  const parsed = parseInput(msg);
  if (!parsed) {
    return `❓ No reconocí el paradero.\n\nEjemplo: *PA433* o *PA433 506*\n\nEscribe *ayuda* para más info.`;
  }

  try {
    const buses = await getBuses(parsed.paradero, parsed.servicio);
    return formatRespuesta(buses, parsed.paradero, parsed.servicio);
  } catch (err) {
    console.error('❌ Error:', err.message);
    return `⚠️ Error consultando *${parsed.paradero}*. Intenta en un momento.`;
  }
}

module.exports = { handleMessage };