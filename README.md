# Red Bus Bot

API de tiempos de llegada de micros **Red Santiago** en tiempo real.
Consulta cuándo llega el próximo bus desde tu **reloj Android (Wear OS)** o por **WhatsApp**.

---

## Arquitectura

```
Wear OS App ──┐
              ├──► API REST (Node.js) ──► red.cl
WhatsApp Bot ─┘
```

El servidor actúa como intermediario entre los clientes y red.cl, con caché del JWT para minimizar requests.

---

## Stack

- **Backend:** Node.js + Express
- **Cliente móvil:** Wear OS (Kotlin + Jetpack Compose) — en desarrollo
- **Fuente de datos:** red.cl (scraping del predictor de llegada)

---

## Endpoints

### `GET /api/buses/:paradero`

Devuelve los tiempos de llegada de todos los servicios en un paradero.

```
GET /api/buses/PA433
GET /api/buses/PA433?servicio=506
```

**Respuesta:**
```json
{
  "paradero": "PA433",
  "buses": [
    {
      "servicio": "506",
      "destino": "Centro",
      "horaprediccionbus1": "Entre 5 Y 9 min",
      "horaprediccionbus2": "Entre 12 Y 15 min",
      "codigorespuesta": "00"
    }
  ]
}
```

### `GET /test/:paradero`

Respuesta formateada en texto plano, útil para pruebas rápidas en el navegador.

---

## Instalación local

```bash
git clone https://github.com/BastianFigM/red-bus-bot.git
cd red-bus-bot
npm install
cp .env.example .env
# Rellena las variables en .env
npm start
```

Luego abre: `http://localhost:3000/api/buses/PA433`

---

## Variables de entorno

| Variable | Descripción |
|---|---|
| `PORT` | Puerto del servidor (default: 3000) |
| `VERIFY_TOKEN` | Token secreto para verificar el webhook de Meta |
| `WHATSAPP_TOKEN` | Token de acceso de la API de WhatsApp Business |
| `PHONE_NUMBER_ID` | ID del número de teléfono en Meta |

---

## Roadmap

- [x] API REST de tiempos de llegada
- [x] Bot de WhatsApp
- [ ] App Wear OS — consulta desde el reloj
- [ ] Paraderos favoritos guardados en el reloj
- [ ] Notificaciones cuando el bus está a menos de 2 minutos

---

## Licencia

MIT
