async function main() {
  const res = await fetch('https://www.red.cl/planifica-tu-viaje/cuando-llega/', {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      'Accept': 'text/html',
      'Accept-Language': 'es-CL,es;q=0.9',
    }
  });

  const html = await res.text();
  const fs = require('fs');

  // Buscar variantes de cómo puede estar el token
  const patrones = [
    /jwt\s*[=:]\s*['"`]([^'"`]+)/gi,
    /token\s*[=:]\s*['"`]([^'"`]+)/gi,
    /\$jwt\s*=\s*['"`]([^'"`]+)/gi,
    /"t"\s*:\s*"([^"]+)"/gi,
    /prediccion\?t=([^&'"]+)/gi,
    /predictorPlus[^'"]*t=([^&'" ]+)/gi,
  ];

  console.log('🔍 Buscando patrones del token...\n');
  patrones.forEach((regex, i) => {
    const matches = [...html.matchAll(regex)];
    if (matches.length) {
      console.log(`✅ Patrón ${i} encontró ${matches.length} resultado(s):`);
      matches.forEach(m => console.log('  →', m[0].substring(0, 120)));
    } else {
      console.log(`❌ Patrón ${i} — sin resultados`);
    }
  });

  // Buscar scripts externos que podrían generar el token
  const scripts = [...html.matchAll(/<script[^>]*src=["']([^"']+)["']/gi)];
  console.log('\n📜 Scripts externos encontrados:');
  scripts.forEach(s => console.log(' -', s[1]));

  fs.writeFileSync('debug_html.txt', html);
}

main().catch(console.error);