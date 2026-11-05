# Despliegue en VPS (Hostinger) – SSL + Docker

## 1. Preparar el servidor (una sola vez)

```bash
# Docker + plugin compose
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # cerrar y abrir sesión

# Firewall: solo SSH, HTTP y HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

Apuntar el registro DNS `A` de `API_DOMAIN` (ej. `api.joselorza.com`) a la IP del VPS.

## 2. Variables de entorno

Clonar el repositorio y crear `.env` en la raíz a partir de `.env.example`, con valores de producción:

- `API_DOMAIN`, `FRONTEND_URL`, `DB_*`, `JWT_SECRET` (mín. 32 caracteres aleatorios)
- `DDL_AUTO=update` en el primer arranque (crea las tablas); luego puede pasarse a `validate`
- SMTP Gmail: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME`, `MAIL_PASSWORD` (contraseña de aplicación)
- Wompi / Global66 / Google Calendar según corresponda (`*_ENABLED=true`)
- `GOOGLE_TOKEN_ENCRYPTION_KEY`: `openssl rand -base64 32`

El archivo `.env` nunca se sube al repositorio (está en `.gitignore`).

## 3. Emitir el certificado SSL (primera vez)

Nginx necesita el certificado para arrancar, así que se emite antes con certbot en modo standalone:

```bash
cd deploy
docker compose -f docker-compose.prod.yml --env-file ../.env run --rm -p 80:80 --entrypoint \
  "certbot certonly --standalone -d $API_DOMAIN --email admin@joselorza.com --agree-tos --no-eff-email" certbot
```

## 4. Levantar la plataforma

```bash
cd deploy
docker compose -f docker-compose.prod.yml --env-file ../.env up -d --build
docker compose -f docker-compose.prod.yml logs -f app
```

El contenedor `certbot` renueva el certificado automáticamente. Tras una renovación, recargar Nginx:

```bash
docker exec nginx nginx -s reload
```

## 5. Webhooks y OAuth

Registrar en cada proveedor las URLs públicas (HTTPS):

| Proveedor        | URL                                                              |
|------------------|------------------------------------------------------------------|
| Wompi (eventos)  | `https://API_DOMAIN/api/payments/webhooks/wompi`                 |
| Global66         | `https://API_DOMAIN/api/payments/webhooks/global66`              |
| Google OAuth2    | `https://API_DOMAIN/api/google-calendar/oauth2/callback`         |

## 6. Actualizar a una nueva versión

```bash
git pull
cd deploy
docker compose -f docker-compose.prod.yml --env-file ../.env up -d --build app
```
