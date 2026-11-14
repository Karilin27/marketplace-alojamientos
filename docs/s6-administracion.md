# S6 · Administración y S1 · Recuperación de acceso

Historias: **HU-003** Recuperar acceso (SCRUM-16), **HU-018** Moderar publicación (SCRUM-31), **HU-019** Auditoría (SCRUM-32), **HU-020** Dashboard (SCRUM-33), **HU-021** Reportes PDF y Excel (SCRUM-34) y **HU-022** Administrar usuarios (SCRUM-35).

## Qué hay
| Capa | Archivos |
|---|---|
| Base de datos | No requiere migración nueva: usa `identidad.token_recuperacion`, `administracion.auditoria`, `administracion.moderacion`, `administracion.reporte_exportado` (V1 y V4), `catalogo.publicacion_historial` (V5) y `reservas.reserva.revision_manual` (V3_2) |
| Backend | Paquete `edu.unbosque.marketplace.administracion`: `ReglasAdministracion`, `AdminRepositorio`, `AdminService`, `RecuperacionService`, `ReporteService` (PDF con OpenPDF, Excel con Apache POI), `AuditoriaJdbc` (implementa `AuditoriaPuerto`: desde ahora **todos** los eventos auditados de la aplicación quedan en `administracion.auditoria`) y `AdminController` |
| Dependencias | `pom.xml`: `org.apache.poi:poi-ooxml:5.2.5` y `com.github.librepdf:openpdf:1.3.30` |
| Seguridad | `FiltroAutenticacion`: quedan públicas `POST /api/auth/password-recovery` y `POST /api/auth/password-reset` |
| Frontend | `pages/recuperar.html` (enlace «¿Olvidaste tu contraseña?» en el login) y `pages/admin/` (`dashboard`, `usuarios`, `publicaciones`, `auditoria`). El administrador entra directo a `admin/dashboard.html` después del login |
| Pruebas | `ReglasAdministracionTest` (JUnit, incluye la generación real de PDF y Excel) y `frontend/test/e2e/e2e-admin.js` |

## Endpoints
| Método y ruta | Historia |
|---|---|
| `POST /api/auth/password-recovery` `{correo}` → `{mensaje, tokenSimulado, expiraEn}` | HU-003 (público; mismo mensaje exista o no el correo) |
| `POST /api/auth/password-reset` `{token, nuevaContrasena}` | HU-003 (público; token de un solo uso, 30 min, se guarda su hash SHA-256) |
| `GET /api/admin/users?buscar=&rol=&estado=` · `PATCH /api/admin/users/{id}/status` `{estado: RESTRINGIDO|ACTIVO, motivo}` | HU-022 |
| `GET /api/admin/accommodations?estado=` · `PATCH /api/admin/accommodations/{id}/moderation` `{accion: APROBAR|PAUSAR|BLOQUEAR|DESBLOQUEAR, motivo}` | HU-018 |
| `GET /api/admin/bookings/manual-review` · `PATCH /api/admin/bookings/{id}/manual-review` `{decision}` | HU-018 (CU-17 A3, RN-19) |
| `GET /api/admin/audit?usuario=&entidad=&accion=&desde=&hasta=&page=&size=` | HU-019 (solo lectura) |
| `GET /api/admin/dashboard` | HU-020 (ingresos = CONFIRMADAS + COMPLETADAS, D-16) |
| `POST /api/admin/reports/export` `{tipo: USUARIOS|PUBLICACIONES|RESERVAS|INGRESOS|DEMANDA, formato: PDF|EXCEL}` → archivo | HU-021 |

Todas las rutas `/api/admin/**` exigen rol ADMINISTRADOR (403 en otro caso).

## Para la demostración
Crear el administrador una sola vez (el registro público no permite el rol ADMINISTRADOR, HU-001 CA-04): registrar un usuario normal y luego en Supabase → SQL Editor:
```sql
UPDATE identidad.usuario SET rol = 'ADMINISTRADOR' WHERE correo = 'admin@test.com';
```
