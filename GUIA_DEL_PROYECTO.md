# Guía del proyecto: Backend José Fernando Elorza

API REST de la plataforma web de José Fernando Elorza (biohipnoterapeuta sistémico): registro de usuarios, catálogo de servicios, agenda de citas, pagos en línea, notificaciones por correo y panel de administración.

El frontend vive en otro repositorio (`jose_elorza_frontend_cesde`) y tiene su propia guía (`GUIA_DEL_PROYECTO.md`).

---

## 1. Tecnologías

| Componente      | Tecnología                                   |
|-----------------|----------------------------------------------|
| Lenguaje        | Java 21                                      |
| Framework       | Spring Boot 3.3                              |
| Base de datos   | PostgreSQL 16                                |
| Persistencia    | Spring Data JPA / Hibernate                  |
| Seguridad       | Spring Security + JWT (access + refresh)     |
| Correo          | Spring Mail (MailDev en local, Gmail en producción) |
| PDF             | OpenPDF (comprobantes de pago)               |
| Documentación   | Swagger UI (springdoc)                       |
| Pruebas         | JUnit 5, Mockito, Testcontainers             |
| Contenedores    | Docker + Docker Compose                      |
| Producción      | VPS (Hostinger) con Nginx + Let's Encrypt    |

---

## 2. Puesta en marcha local (paso a paso)

### Requisitos
- **JDK 21.** Importante: en IntelliJ, `File > Project Structure > SDK` debe ser Java 21. Con Java 17 no compila, y con versiones más nuevas (24 o 26) Lombok puede fallar.
- **Docker Desktop** encendido.
- Git.

### Pasos
1. Copiar `.env.example` como `.env` y completar los valores. Para local basta con:
   - `DB_*`: nombre, usuario, contraseña y puerto de PostgreSQL.
   - `DB_URL_DOCKER=jdbc:postgresql://postgres:5432/<DB_NAME>`
   - `DB_URL_LOCAL=jdbc:postgresql://localhost:<DB_PORT>/<DB_NAME>`
   - `MAIL_HOST=localhost`, `MAIL_PORT=1025` (MailDev).
   - `JWT_SECRET`: texto aleatorio de 32 caracteres o más.
   - `ADMIN_EMAIL` y `ADMIN_PASSWORD`: credenciales del administrador inicial.
   - `PAYMENTS_SANDBOX_ENABLED=true` para usar el simulador de pagos.
2. Levantar todo con Docker:
   ```bash
   docker compose up -d --build
   ```
   Esto inicia: `postgres` (BD), `pgadmin` (http://localhost:5050), `maildev` (correos de prueba en http://localhost:1080) y `app` (API en http://localhost:8080).
3. Alternativa para desarrollar desde IntelliJ: levantar solo los servicios de apoyo (`docker compose up -d postgres maildev pgadmin`) y ejecutar `JoselorzaApp` desde el IDE. La app usa `DB_URL_LOCAL` automáticamente.
4. Verificar:
   - Salud: http://localhost:8080/actuator/health → `{"status":"UP"}`
   - Documentación interactiva: http://localhost:8080/swagger-ui.html
   - Catálogo: http://localhost:8080/api/service-plans

### Qué se crea solo al arrancar
- **Administrador** (`AdminSeeder`): si no existe ningún ADMIN, se crea con `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
- **Catálogo y redes** (`CatalogSeeder`): si las tablas están vacías (base de datos nueva), se crean:
  - *Sesiones individuales: Conversaciones conscientes*: $290.000 COP / $80 USD, 2 horas, virtual o presencial.
  - *Consultas individuales: Biohipnosis Sistémica*: $370.000 COP / $100 USD, 4 horas, virtual.
  - Redes: YouTube (https://www.youtube.com/@EmprenSerPodcast), Instagram y WhatsApp.

  Después, el administrador los gestiona desde el panel y el seeder no los vuelve a tocar.

### Pruebas
```bash
./mvnw verify
```
Necesita Docker encendido (las pruebas de integración levantan un PostgreSQL temporal). Estado actual: **47 pruebas, todas pasan.**

---

## 3. Estructura del código

Arquitectura por capas (estilo hexagonal), en `src/main/java/com/joselorza/app/`:

```
domain/            Entidades (User, ServicePlan, Appointment, Payment, ...), enums y excepciones de negocio
application/
  service/         Reglas de negocio (citas, pagos, disponibilidad, reportes, notificaciones)
  port/out/        Interfaces hacia servicios externos (pasarelas, correo, calendario, PDF)
  event/           Eventos de dominio (cita creada, pago aprobado...) que disparan correos
infrastructure/
  adapter/         Implementaciones: Wompi, Global66, pago manual, SMTP, Google Calendar, OpenPDF
  repository/      Repositorios JPA
  security/        JWT, filtro de autenticación, CORS, cifrado de tokens
  scheduler/       Tareas programadas
  seed/            Datos iniciales (admin, catálogo, redes)
  config/          Propiedades de configuración (app.*)
presentation/
  controller/      Endpoints REST
  dto/             Objetos de entrada/salida
  exception/       Manejo global de errores
```

Toda la configuración está en `src/main/resources/application.yml` y se alimenta de variables de entorno (`.env`).

---

## 4. Funcionalidades y reglas de negocio

### Usuarios y seguridad
- Registro, inicio de sesión, recuperación de contraseña por correo y cambio de contraseña.
- Roles: `USER` (cliente) y `ADMIN`.
- El access token dura 15 minutos y viaja en el header `Authorization`. El refresh token dura 7 días, viaja en una cookie httpOnly y en la BD solo se guarda su hash.

### Catálogo
- Servicios con precio en COP y USD, duración, modalidad (`LIVE`, `ONLINE`, `IN_PERSON`, `RECORDED`) y tipo (individual o grupal, de 2 a 10 cupos).
- **IVA 19 %:** la BD guarda el precio *base sin IVA*; el cliente siempre ve y paga el total con IVA.

### Agenda
- El admin define reglas de disponibilidad semanales y excepciones (vacaciones, días bloqueados) en la zona horaria `America/Bogota`.
- Las horas se guardan en UTC y se muestran en la zona horaria de cada usuario.
- Anticipación mínima para reservar: 12 horas.
- Una reserva queda **pendiente de pago**; si no se paga dentro del plazo, se libera automáticamente.
- Cancelar o reagendar con menos de 7 días de anticipación genera una penalización del 10 % (el admin no genera penalización).
- Si el cliente no asiste, la cita se marca como `NO_SHOW`, sin reembolso.

### Pagos
- **Wompi** → pagos en COP. **Global66** → pagos en USD. **Manual** → el admin confirma un pago recibido por fuera.
- La confirmación llega por *webhook* con firma verificada; también se valida que el monto y la moneda coincidan.
- Comprobante PDF descargable con el IVA desglosado.
- Reembolsos manuales: el admin devuelve el dinero por fuera y lo marca como reembolsado.
- **Simulador de pasarelas** (solo desarrollo, `PAYMENTS_SANDBOX_ENABLED=true`): permite probar el flujo completo sin dinero real.

### Notificaciones y tareas automáticas
- Correos de confirmación, cancelación, reagendamiento, pago y recuperación de contraseña.
- Cada 15 minutos se ejecutan tres tareas: recordatorio 24 h antes de la cita, marcar inasistencias y expirar reservas sin pagar.

### Integraciones
- **Google Calendar** (opcional): el admin conecta su cuenta y las citas se crean en su calendario.

### Panel de administración (API)
Dashboard con métricas, gestión de usuarios, servicios, agenda, pagos, redes sociales, notificaciones y reportes CSV (citas y pagos).

### Resumen de endpoints

| Área            | Ruta base                                  | Acceso   |
|-----------------|--------------------------------------------|----------|
| Autenticación   | `/api/auth/*`                              | Público  |
| Catálogo        | `/api/service-plans`                       | Público  |
| Disponibilidad  | `/api/availability`                        | Público  |
| Redes sociales  | `/api/social-links`                        | Público  |
| Mi perfil       | `/api/users/me`                            | Usuario  |
| Mis citas       | `/api/appointments`                        | Usuario  |
| Mis pagos       | `/api/payments`                            | Usuario  |
| Webhooks        | `/api/payments/webhooks/{wompi,global66}`  | Firma    |
| Administración  | `/api/admin/*`                             | Admin    |

El detalle completo de cada endpoint está en Swagger (`/swagger-ui.html`, solo en desarrollo).

---

## 5. Despliegue en producción

La guía técnica completa está en [`deploy/README.md`](deploy/README.md). En resumen:

1. Preparar el VPS: instalar Docker y abrir en el firewall solo SSH, 80 y 443.
2. Apuntar el DNS de `api.<dominio>` a la IP del VPS.
3. Copiar el código al servidor y crear el `.env` de producción (ver la lista de la sección 6).
4. Emitir el certificado SSL con certbot (una sola vez).
5. Levantar todo:
   ```bash
   cd deploy
   docker compose -f docker-compose.prod.yml --env-file ../.env up -d --build
   ```
6. Registrar en Wompi, Global66 y Google las URLs públicas de webhooks y OAuth.

La configuración de producción ya incluye:
- HTTPS obligatorio con renovación automática del certificado.
- Base de datos no expuesta a Internet.
- Cookie de sesión solo por HTTPS.
- Simulador de pagos apagado.
- Swagger desactivado.
- `/actuator/health` sin detalles internos.
- Contenedor de la app sin privilegios de root.

---

## 6. Estado actual y pendientes para producción

**El código está terminado y probado, pero el proyecto aún no está listo para recibir clientes reales.** Faltan estos puntos:

### Bloqueantes (antes de abrir al público)
| # | Pendiente | Responsable | Detalle |
|---|-----------|-------------|---------|
| 1 | **Activar los pagos reales** | Cliente + equipo | Obtener las llaves de producción de Wompi y Global66, activarlas en el `.env` y hacer **al menos un pago real de prueba con cada pasarela**. Hasta hoy solo se probaron con el simulador; la integración con Global66 no se ha probado contra su API real. |
| 2 | **`.env` de producción** | Equipo | Cambiar todas las URLs `localhost` (`FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `WOMPI_REDIRECT_URL`, `GLOBAL66_*`, `GOOGLE_*`, `PAYMENTS_SANDBOX_CHECKOUT_URL`). Configurar el correo real (Gmail + contraseña de aplicación). Usar un `ADMIN_PASSWORD` fuerte: si se deja vacío, el admin se crea con `admin123`. Generar un `JWT_SECRET` nuevo. |
| 3 | **Dominio y servidor** | Cliente | Contratar o confirmar el VPS y el dominio, y crear los registros DNS. |
| 4 | **Control de versiones** | Equipo | El repositorio no tiene commits todavía. Hay que subirlo a GitHub (el CI en `.github/workflows/ci.yml` ya está listo) para poder desplegarlo con `git clone` / `git pull`. |

### Recomendados
- **Copias de seguridad automáticas de PostgreSQL** en el VPS (por ejemplo, un `pg_dump` diario guardado fuera del servidor). Hoy no existen.
- Pasar `DDL_AUTO` de `update` a `validate` después del primer arranque y, más adelante, adoptar migraciones versionadas (Flyway).
- Validar la duración de *Biohipnosis Sistémica*: está en 4 horas como dice el portafolio, así que cada reserva bloquea 4 horas seguidas en la agenda. Si se prefiere bloquear solo las 2 horas de la consulta, se cambia desde el panel admin.
- Monitoreo básico (alerta si `/actuator/health` deja de responder).
