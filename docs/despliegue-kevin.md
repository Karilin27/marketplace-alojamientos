# Despliegue de S3, S4 y S5 (aporte de Kevin a la página 14)

## Variables de entorno en Render (servicio Java)
| Variable | Valor | Notas |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<host>:6543/postgres?sslmode=require` | Cadena del *pooler* de Supabase |
| `DATABASE_USER` / `DATABASE_PASSWORD` | las de Supabase | Secretos: nunca en el repositorio |
| `JWT_SECRET` | mismo secreto con el que S1 firma el JWT (HS256) | Mínimo 32 caracteres |
| `COTIZACION_SECRET` | cadena aleatoria de 32+ caracteres | `openssl rand -base64 48` |
| `PASARELA` | `simulada` o `stripe` | Stripe solo con claves de prueba |
| `STRIPE_API_KEY` | `sk_test_...` | Solo si `PASARELA=stripe` |
| `TARIFA_SERVICIO_PCT` | `10` | Se usa solo si S2 no define el parámetro |

## Base de datos (Supabase → SQL Editor, en este orden)
El modelo sigue la página 12 de Confluence: un esquema por servicio, ids UUID, autenticación propia de S1 (D-17, sin `auth.users`).
1. `db/migrations/V1__identidad.sql` (S1: usuario, perfil, token_recuperacion)
2. `db/migrations/V2__catalogo.sql` (S2: alojamiento, políticas FLEXIBLE/MODERADA/ESTRICTA, servicios adicionales, parámetros)
3. `db/migrations/V3__disponibilidad.sql`, `V3_1__vistas_catalogo_s2.sql`, `V3_2__reservas.sql`, `V3_3__pagos.sql`, `V3_4__notificaciones.sql` (S3, S4, S5)
4. `db/migrations/V4__resenas_y_administracion.sql` (reseñas, moderación, auditoría, exportaciones)
5. (solo desarrollo) `db/dev/seed_s2_minimo.sql`: aloja A-01 a A-03 de prueba. No ejecutarlo en la base compartida.

Reversión, en este orden: `rollback/V4__rollback.sql`, `rollback/V3__rollback.sql`, `rollback/V1_V2__rollback.sql` (borran datos: hacer `pg_dump` antes).

## Verificación posterior al despliegue
- `GET /actuator/health` responde `{"status":"UP"}`.
- `GET /api/availability/<id de un alojamiento>` responde 200 sin token.
- Ejecutar la colección de Postman contra la URL de Render.

## Auditoría (S6 en Java, decisión D-20)
No hay servicio Python ni variables `AUDITORIA_URL`/`SERVICE_KEY`. Los eventos auditables pasan por `logs.AuditoriaPuerto`; el módulo de auditoría de S6 (HU-019) lo implementa como `@Component` dentro de la misma aplicación. Sin esa implementación solo se escribe el log JSON.
