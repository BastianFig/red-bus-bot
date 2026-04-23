const tokenCache = require('./tokenCache');

const RED_PAGE = 'https://www.red.cl/planifica-tu-viaje/cuando-llega/';
const RED_API  = 'https://www.red.cl/predictorPlus/prediccion';

let sessionCookies = '';

async function fetchToken() {
  const res = await fetch(RED_PAGE, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'es-CL,es;q=0.9',
    }
  });

  // Guardar cookies de sesión
  const setCookie = res.headers.get('set-cookie');
  if (setCookie) {
    sessionCookies = setCookie
      .split(',')
      .map(c => c.split(';')[0].trim())
      .join('; ');
    console.log('🍪 Cookies:', sessionCookies);
  }

  const html = await res.text();

  // El token está en Base64, hay que decodificarlo
  const match = html.match(/\$jwt\s*=\s*'([^']+)'/);
  if (!match) throw new Error('No se encontró el JWT en red.cl');

  const tokenBase64 = match[1];
  // Decodificar de Base64 a string — así lo hace el JS del browser
  const tokenDecoded = Buffer.from(tokenBase64, 'base64').toString('utf8');

  console.log('🔑 Token Base64:', tokenBase64.substring(0, 40) + '...');
  console.log('🔓 Token decodificado:', tokenDecoded.substring(0, 60) + '...');

  tokenCache.setToken(tokenDecoded);
  return tokenDecoded;
}

async function getJwt() {
  if (tokenCache.isValid()) return tokenCache.getToken();
  return fetchToken();
}

async function getBuses(paradero, servicio = '') {
  const jwt = await getJwt();

  const params = new URLSearchParams({
    codsimt: paradero.toUpperCase(),
    codser:  servicio.toUpperCase(),
    t:       jwt
  });

  const url = `${RED_API}?${params}`;
  console.log('🌐 Consultando:', url);

  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': '*/*',
      'Accept-Language': 'es,es-ES;q=0.9',
      'Referer': `https://www.red.cl/planifica-tu-viaje/cuando-llega/?codsimt=${paradero}`,
      'sec-fetch-dest': 'empty',
      'sec-fetch-mode': 'cors',
      'sec-fetch-site': 'same-origin',
      'Cookie': sessionCookies
    }
  });

  const rawText = await res.text();
  console.log('📦 Status:', res.status);
  console.log('📦 Respuesta:', rawText.substring(0, 800));

  if (!res.ok) throw new Error(`HTTP ${res.status} desde red.cl`);

  const data = JSON.parse(rawText);

  // Función para ver si hay mas códigos disponibles en la respuesta:
  // Debug temporal — ver todos los códigos
    const codigos = data?.servicios?.item?.map(b => ({
      servicio: b.servicio,
      codigo: b.codigorespuesta,
      mensaje: b.respuestaServicio
    }));
    console.log('📊 Códigos:', JSON.stringify(codigos, null, 2));

  return data?.servicios?.item || [];
}

module.exports = { getBuses };