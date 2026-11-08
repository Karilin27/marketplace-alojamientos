# HU-004 — Gestión de perfil (SCRUM-17) · S1 · Sprint A · v0.1

Autor: Kevin Rojas. Todo el módulo vive en `backend-java/.../identidad/` y usa las tablas `identidad.usuario` e
`identidad.perfil` (migración V1). No necesita migraciones nuevas.

## Endpoints (contrato de la página 13)

| Método y ruta | Qué hace | Criterios |
|---|---|---|
| `GET /api/users/me` | Devuelve nombre, correo, teléfono, documento, país, foto, rol y estado del usuario autenticado | CA-01, CA-04 |
| `PUT /api/users/me` | Actualiza nombre, teléfono, documento, país y foto. Cuerpo: `{"nombre","telefono","documento","pais","fotoUrl"}` | CA-02, CA-03, CA-04 |
| `PATCH /api/users/me/status` | Desactiva o reactiva la cuenta. Cuerpo: `{"estado":"DESACTIVADO"}` o `{"estado":"ACTIVO"}` | CA-05, CA-06, CA-07 |

Todos exigen `Authorization: Bearer <JWT>`. El id del usuario sale siempre del JWT: ninguna ruta recibe el id de otra
persona, así que nadie puede consultar ni modificar la cuenta de otro (CA-04).

Respuestas de error con el formato común `{"codigo","mensaje","detalles"}`:

| Situación | HTTP | `codigo` |
|---|---|---|
| Sin token o token inválido | 401 | `NO_AUTENTICADO` |
| Datos inválidos (CA-03); `detalles` indica qué corregir | 400 | `PERFIL_INVALIDO` |
| Estado distinto de ACTIVO o DESACTIVADO | 400 | `ESTADO_INVALIDO` |
| Reservas confirmadas futuras al desactivar (CA-06) | 422 | `RESERVAS_CONFIRMADAS_PENDIENTES` |
| Cuenta restringida por un administrador (RN-24) | 403 | `ACCESO_DENEGADO` |

Respuesta de `PATCH`: `{"estado","sesionCerrada","publicacionesPausadas"}`. Con `sesionCerrada = true` el frontend
debe borrar el token y volver al inicio de sesión.

## Reglas implementadas

- **Validación (CA-03):** el nombre es obligatorio (2 a 100 caracteres). Teléfono (7 a 15 dígitos, `+` opcional,
  se admiten espacios y guiones), documento (5 a 20 letras, números o guiones), país (solo letras) y foto
  (URL http o https de hasta 500 caracteres) son opcionales, pero si se envían deben ser válidos. Un campo opcional
  vacío se guarda como nulo. El correo y el rol no se pueden cambiar desde aquí.
- **Desactivar (CA-05, CA-06):** se rechaza si el usuario tiene reservas `CONFIRMADA` cuya fecha de salida es posterior
  a hoy, ya sea como huésped o como anfitrión (dueño del alojamiento). Si puede desactivar: estado `DESACTIVADO` y
  sus publicaciones `ACTIVA` pasan a `PAUSADA`, todo en una sola transacción y con la fila del usuario bloqueada.
- **Reactivar (CA-07):** el estado vuelve a `ACTIVO`; las publicaciones siguen `PAUSADA` hasta que el anfitrión las active.
- Repetir el mismo estado es idempotente (200 sin cambios). Los eventos `CUENTA_DESACTIVADA`, `CUENTA_REACTIVADA` y
  `CUENTA_DESACTIVACION_RECHAZADA` van al log estructurado y a la auditoría (puerto `AuditoriaPuerto`).

## Pendientes y supuestos para confirmar

1. **"Cierra su sesión" (CA-05).** El JWT no se puede revocar desde el backend. Hoy el backend responde
   `sesionCerrada = true` y el frontend descarta el token. Para que el cierre sea real hace falta que el login (HU-002)
   rechace emitir tokens a cuentas `DESACTIVADO` salvo para reactivar, o que el token lleve el estado.
2. **"Reactivar con credenciales válidas" (CA-07).** Lo que hay aquí es el `PATCH ... {"estado":"ACTIVO"}` con un token
   válido. El inicio de sesión de una cuenta `DESACTIVADO` pertenece a HU-002; confirmar con quien la implemente.
3. **Campos obligatorios.** Solo el nombre es obligatorio. Si el Product Owner quiere más campos obligatorios, se
   cambia en `ReglasPerfil.errores`.
4. **"Futura".** Se usa fecha de salida posterior a hoy (una estadía en curso también bloquea).
5. **Carrera rara.** Una reserva confirmada exactamente mientras se desactiva podría no verse; el riesgo es bajo y
   se resolvería bloqueando también por reserva.

## Cómo se probó

- `ReglasPerfilTest`: 9 pruebas JUnit de validación (CA-02, CA-03).
- `database/dev/prueba_hu004.sql`: 7 comprobaciones SQL con las mismas consultas del repositorio, sobre V1 a V4 y el
  seed, dentro de una transacción que termina en ROLLBACK (CA-01, CA-02, CA-05, CA-06, CA-07).
- `api/prueba-perfil.ps1`: prueba en vivo contra el backend y Supabase (ver abajo).

## Preparar la prueba en vivo

En el SQL Editor de Supabase, con nada resaltado, crea el usuario de prueba (no tiene alojamientos):

```sql
INSERT INTO identidad.usuario (id, nombre, correo, contrasena_hash, rol)
VALUES ('00000000-0000-0000-0000-00000000c001', 'Huésped de pruebas HU-004', 'huesped.hu004@example.com', 'NO-INICIA-SESION', 'HUESPED')
ON CONFLICT DO NOTHING;
```

Con el backend corriendo (terminal 1), en otra terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\api\prueba-perfil.ps1 -Secret "<el mismo JWT_SECRET del backend>"
```
