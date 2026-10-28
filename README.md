# Red Bus Bot

Consulta en tiempo real cuándo llega tu micro de **Red Santiago** directo desde tu **reloj Android (Wear OS)**.

---

## Arquitectura

```
Wear OS App ──► API REST (Node.js) ──► red.cl
                       │
                       └──► data/stops.json (paraderos y recorridos del GTFS)
```

El servidor actúa como intermediario entre la app y red.cl, con caché del JWT para minimizar requests. Los paraderos cercanos se calculan localmente con los datos del GTFS.

---

## Estructura del proyecto

```
red-bus-bot/
├── index.js              ← arranca el servidor (abre el puerto)
├── app.js                ← arma Express: middlewares y rutas
├── routes/
│   └── api.js            ← /api/buses/:paradero y /api/nearby
├── middleware/
│   └── rateLimit.js      ← límite de consultas por IP
├── services/             ← cliente red.cl + caché JWT + paraderos cercanos
├── scripts/              ← buildStops.js: genera data/stops.json desde el GTFS
├── data/                 ← stops.json (paraderos y recorridos)
├── wear-os/              ← app Android (Kotlin + Jetpack Compose)
│   └── app/src/main/
│       ├── data/         ← modelos + Retrofit
│       └── presentation/ ← UI + ViewModel
└── .env.example
```

---

## Stack

- **Backend:** Node.js + Express — desplegado en [Render](https://render.com)
- **App reloj:** Kotlin + Jetpack Compose for Wear OS
- **Fuente de datos:** red.cl (predictor de llegada en tiempo real)

---

## Instalación — Servidor

```bash
git clone https://github.com/BastianFigM/red-bus-bot.git
cd red-bus-bot
npm install
cp .env.example .env
# Rellena las variables en .env
npm start
```

Prueba en el navegador: `http://localhost:3000/api/buses/PA433`

### Variables de entorno

| Variable | Descripción |
|---|---|
| `PORT` | Puerto del servidor (default: 3000) |

---

## Instalación — App Wear OS

**Requisitos:** Android Studio + reloj con Wear OS 3 o superior

```bash
# Abre el proyecto desde Android Studio
# File → Open → red-bus-bot/wear-os/
```

1. Conecta tu reloj vía Wi-Fi (Opciones de desarrollador → Depuración inalámbrica)
2. Selecciona el reloj como dispositivo en Android Studio
3. Dale ▶ para instalar

> Para instalar sin Android Studio: descarga el APK desde [Releases](https://github.com/BastianFigM/red-bus-bot/releases) y habilita fuentes desconocidas en el reloj.

---

## API — Endpoints

### `GET /api/buses/:paradero`

```
GET /api/buses/PA433
GET /api/buses/PA433?servicio=506
GET /api/buses/PF54
```

**Respuesta:**
```json
{
  "paradero": "PA433",
  "buses": [
    {
      "servicio": "506v",
      "destino": "Peñalolén",
      "horaprediccionbus1": "Llegando",
      "horaprediccionbus2": "En menos de 3 min",
      "codigorespuesta": "00"
    }
  ]
}
```

### `GET /api/nearby`

Paraderos cercanos a una coordenada, ordenados por distancia.

```
GET /api/nearby?lat=-33.4489&lng=-70.6693
GET /api/nearby?lat=-33.4489&lng=-70.6693&radio=300&limit=10
```

`radio` en metros (por defecto 500, máximo 2000) y `limit` (por defecto 20, máximo 50).

**Respuesta:**
```json
{
  "lat": -33.4489,
  "lng": -70.6693,
  "radio": 300,
  "paraderos": [
    {
      "codigo": "PA369",
      "nombre": "Parada 5 / (M) República",
      "lat": -33.448398,
      "lng": -70.668887,
      "distancia": 68,
      "recorridos": ["401", "405", "406"]
    }
  ]
}
```

Todos los endpoints de `/api` tienen un límite de 60 consultas por minuto por IP (responden `429` al pasarse).

---

## Roadmap

- [x] API REST de tiempos de llegada
- [x] App Wear OS funcional en Galaxy Watch 7
- [x] Paraderos favoritos guardados en el reloj
- [x] Paraderos cercanos con recorridos (`/api/nearby`)
- [x] Refresco automático y buses ordenados por llegada
- [ ] Notificación cuando el bus está a menos de 2 minutos
- [ ] Login + favoritos sincronizados en la nube (Firebase)
- [ ] Publicación en Google Play

---

## Licencia

MIT
