# S5 · Reseñas (María Juliana Sánchez)

Historias: **HU-017** Registrar reseña (SCRUM-30) y **HU-025** Ocultar reseñas (SCRUM-38). Depende de S2 (rama `feature/SCRUM-18-alojamientos-s2`).

## Qué hay

| Capa | Archivos |
|---|---|
| Base de datos | `database/migrations/V6__resenas_moderacion.sql` (y `rollback/V6__rollback.sql`): la moderación registra también `RESTABLECER_RESENA`. Las tablas `experiencia.resena` y `administracion.moderacion` ya existían (V4) |
| Backend (Java) | Paquete `edu.unbosque.marketplace.resenas`: `ReglasResena`, `ResenaRepositorio`, `ResenaService`, `ResenaController` |
| Seguridad | `GET /api/reviews` queda público en `FiltroAutenticacion` |
| Frontend | `frontend/pages/resenas/resenar.html` (huésped), `frontend/pages/resenas/moderar.html` (administrador), `frontend/js/resenas/`; el detalle del alojamiento muestra las reseñas visibles; «Mis reservas» muestra el botón «Reseñar estancia» en las reservas COMPLETADAS |
| Pruebas | `ReglasResenaTest` (JUnit), `frontend/test/resenas.test.js`, recorrido en `frontend/test/e2e/e2e-alojamientos.js`, `api/S5-resenas.postman_collection.json`, datos de prueba `database/dev/seed_resenas.sql` |

## Endpoints

| Método y ruta | Historia | Quién | Errores |
|---|---|---|---|
| `POST /api/reviews` `{idReserva, calificacion, comentario}` | HU-017 CA-01 a CA-05, CA-07 | Huésped dueño de la reserva | 400 datos inválidos · 403 reserva ajena · 404 reserva inexistente · 409 ya reseñada · 422 reserva no COMPLETADA |
| `GET /api/reviews?accommodationId=` | HU-017 CA-06 | Público | 404 alojamiento inexistente |
| `GET /api/admin/reviews[?accommodationId=]` | HU-025 | Administrador | 403 |
| `PATCH /api/admin/reviews/{id}/visibility` `{visible, motivo}` | HU-025 CA-01 a CA-05 | Administrador | 400 sin motivo al ocultar · 403 · 404 |

## Reglas
- Solo se reseña una reserva **propia** en estado **COMPLETADA**, una sola vez (RN-13). La base lo asegura con `UNIQUE (id_reserva)`.
- El promedio y la lista pública solo cuentan las reseñas **visibles** (RN-20). Ocultar no borra: guarda el motivo.
- Cada ocultar o restablecer queda en `administracion.moderacion` (quién, reseña, acción, motivo y fecha) y en la auditoría.

## Cómo probar
1. Ejecutar `V6__resenas_moderacion.sql` en Supabase (después de V5).
2. `mvn verify` en `backend-java`.
3. Para el flujo completo: crear un huésped, cambiar su correo en `database/dev/seed_resenas.sql`, ejecutarlo y usar el `id_reserva` que devuelve.
4. `npx newman run api/S5-resenas.postman_collection.json --env-var baseUrl=http://localhost:8080`
5. `node --test frontend/test/resenas.test.js` y `node frontend/test/e2e/e2e-alojamientos.js`
