# Red Bus Bot

Consulta en tiempo real cuándo llega tu micro de **Red Santiago** directo desde tu **reloj Android (Wear OS)**.

---

## Arquitectura

```
Wear OS App ──┐
              ├──► API REST (Node.js) ──► red.cl
WhatsApp Bot ─┘
```

El servidor actúa como intermediario entre los clientes y red.cl, con caché del JWT para minimizar requests.

---

## Estructura del proyecto

```
red-bus-bot/
├── index.js              ← servidor Express + endpoints
├── handlers/             ← lógica de mensajes WhatsApp
├── services/             ← cliente red.cl + caché JWT
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
| `VERIFY_TOKEN` | Token secreto para el webhook de Meta |
| `WHATSAPP_TOKEN` | Token de la API de WhatsApp Business |
| `PHONE_NUMBER_ID` | ID del número en Meta |

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

### `GET /test/:paradero`

Respuesta en texto plano, útil para pruebas rápidas en el navegador.

---

## Roadmap

- [x] API REST de tiempos de llegada
- [x] App Wear OS funcional en Galaxy Watch 7
- [x] Paraderos favoritos guardados en el reloj
- [x] Bot de WhatsApp
- [ ] Notificación cuando el bus está a menos de 2 minutos
- [ ] Login + favoritos sincronizados en la nube (Firebase)
- [ ] Publicación en Google Play

---

## Licencia

MIT
