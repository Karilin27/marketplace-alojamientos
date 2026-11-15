# S2 · Alojamientos (María Juliana Sánchez)

Historias: **HU-005** (SCRUM-18), **HU-006** (SCRUM-19), **HU-007** (SCRUM-20), **HU-009** (SCRUM-22), **HU-010** (SCRUM-23), **HU-023** (SCRUM-36) y **HU-024** (SCRUM-37).

## Qué hay

| Capa | Archivos |
|---|---|
| Base de datos | `database/migrations/V5__catalogo_ajustes.sql` (y su reversión en `rollback/V5__rollback.sql`). Se aplica después de V1 a V4 |
| Backend (Java) | Paquete `edu.unbosque.marketplace.alojamientos`: `ReglasAlojamiento` (reglas puras), `AlojamientoRepositorio` (SQL), `PublicacionService`, `EstadoPublicacionService`, `TarifasService`, `BusquedaService`, `AlojamientoController`, `TarifasController`. Subida de fotos (D-21): `ReglasImagen`, `AlmacenImagenes`, `AlmacenSupabase`, `ImagenService`, `ImagenController` |
| Seguridad | `FiltroAutenticacion`: quedan públicas `GET /api/accommodations`, `GET /api/accommodations/{id}` y `GET /api/cancellation-policies` |
| Frontend | `frontend/pages/alojamientos/` (`buscar`, `detalle`, `publicar`, `mis-publicaciones`), `frontend/js/alojamientos/`, `frontend/css/alojamientos.css` |
| Pruebas | `ReglasAlojamientoTest`, `ReglasImagenTest`, `ImagenServiceTest` y `AlmacenSupabaseTest` (JUnit), `frontend/test/alojamientos.test.js` (Node), `frontend/test/e2e/e2e-alojamientos.js` (Playwright), `api/S2-alojamientos.postman_collection.json` (Newman, 28 peticiones) |

## Endpoints

| Método y ruta | Historia | Quién |
|---|---|---|
| `GET /api/cancellation-policies` | HU-023 CA-01 | Público |
| `POST /api/accommodations` | HU-005 | Anfitrión |
| `POST /api/accommodations/images` (multipart, campo `archivo`) → `{url, bytes, tipo}` | HU-005 CA-03 y HU-006 (cambio D-21) | Anfitrión |
| `GET /api/accommodations/mine` · `GET /api/accommodations/mine/{id}` | HU-007 CA-09 | Anfitrión dueño (o administrador) |
| `PUT /api/accommodations/{id}` | HU-006 (y HU-023 CA-02 con `idPoliticaCancelacion`) | Anfitrión dueño o administrador |
| `PATCH /api/accommodations/{id}/status` | HU-007 | Anfitrión dueño |
| `PUT /api/accommodations/{id}/pricing` | HU-024 | Anfitrión dueño o administrador |
| `GET /api/admin/settings/service-fee` · `PUT …` | HU-024 CA-04 | Administrador |
| `GET /api/accommodations?ciudad=&llegada=&salida=&huespedes=&tipo=&precioMin=&precioMax=&servicios=&page=&size=` | HU-009 | Público |
| `GET /api/accommodations/{id}` | HU-010 (solo ACTIVAS) | Público |

Errores con el formato común `{codigo, mensaje, detalles}`: 400 datos inválidos, 403 no es el dueño o el rol no corresponde, 404 no existe o no está activa, 413 imagen de más de 5 MB, 422 regla de negocio (información incompleta, transición no permitida, publicación bloqueada) y 503 subida de fotos no configurada o almacenamiento no disponible.

## Reglas que se implementan

- Se guarda en **BORRADOR** con título, descripción, tipo, ciudad, dirección, capacidad > 0 y precio > 0. Las imágenes y la política pueden completarse después (caso A-02 de la página 19).
- Para **activar** se exigen además al menos una imagen y una política de cancelación (RN-16, HU-023 CA-03). La base lo asegura con el CHECK `alojamiento_activa_con_politica` (V5).
- Transiciones del anfitrión (RN-15): BORRADOR → ACTIVA, PAUSADA → ACTIVA y ACTIVA → PAUSADA. PENDIENTE_REVISION y BLOQUEADA solo las cambia el administrador (HU-018).
- Cada cambio de estado queda en `catalogo.publicacion_historial` con usuario, estado anterior, estado nuevo y fecha (HU-007 CA-08), y en la auditoría.
- Si el anfitrión cambia título, descripción, ciudad, dirección o tipo de una publicación **ACTIVA**, pasa a **PENDIENTE_REVISION** y se notifica a los administradores (HU-006 CA-06).
- Los servicios adicionales que se quitan se **desactivan**, no se borran: las reservas pasadas guardan su nombre y costo (S3 los copia al reservar).
- La búsqueda solo muestra publicaciones **ACTIVAS**; con fechas, excluye las que tienen una reserva PENDIENTE_PAGO o CONFIRMADA o un periodo NO_DISPONIBLE que se cruce (RN-04, RN-05).

## Cómo probar

```bash
# 1. Base de datos (Supabase → SQL Editor, una sola persona, después del PR aprobado)
#    Ejecutar database/migrations/V5__catalogo_ajustes.sql

# 2. Backend
cd backend-java
mvn verify                      # incluye ReglasAlojamientoTest
mvn spring-boot:run

# 3. API (con el backend corriendo)
npx newman run api/S2-alojamientos.postman_collection.json --env-var baseUrl=http://localhost:8080

# 4. Frontend
node --test frontend/test/alojamientos.test.js
node frontend/test/e2e/e2e-alojamientos.js        # requiere playwright; usa una API simulada
```

## Cambio D-21: subir fotos desde el computador (14 de noviembre de 2026)

**Antes:** el anfitrión solo podía pegar URL de imágenes ya publicadas en internet. **Ahora:** en *Publicar* hay un botón «Subir fotos desde tu computador»; cada foto se sube a Supabase Storage y su URL pública se agrega sola al campo de imágenes. Pegar URL sigue funcionando igual.

- **Motivo:** los anfitriones no tienen dónde alojar sus fotos; pedirles una URL era una barrera para publicar (observado al cargar datos reales para la demo).
- **Alcance:** cumple igual HU-005 CA-03 («rutas o referencias de imágenes»): en la base se sigue guardando la URL, así que no hay migración ni cambios en `catalogo.foto_alojamiento`.
- **Reglas:** solo anfitrión (403 en otro caso); JPG, PNG o WEBP detectados por el contenido del archivo, no por el nombre; máximo 5 MB por foto (413); máximo 10 imágenes por publicación (como antes). Cada archivo se guarda como `alojamientos/<id-anfitrión>/<uuid>.<ext>`.
- **Seguridad:** la llave `service_role` de Supabase vive solo en Render (`SUPABASE_SERVICE_KEY`); el navegador nunca la ve. Si la variable no está, el endpoint responde 503 y la página sugiere pegar la URL.
- **Configuración (una sola vez):**
  1. Supabase → Storage → *New bucket* `alojamientos`, marcado como **Public bucket**.
  2. Supabase → Project Settings → API: copiar la *Project URL* y la llave **service_role**.
  3. Render → servicio `marketplace-alojamientos-api` → Environment: `SUPABASE_URL` = Project URL, `SUPABASE_SERVICE_KEY` = llave service_role, `SUPABASE_BUCKET` = `alojamientos` (ya viene en `render.yaml`). Guardar y redesplegar.
- **Cómo probar:** entrar como anfitrión → Publicar → «Subir fotos desde tu computador» → elegir una foto JPG de menos de 5 MB → la URL aparece en el campo y se ve la miniatura → Guardar. Negativos: un PDF o GIF (400 `FORMATO_NO_PERMITIDO`), una foto de más de 5 MB (413 `IMAGEN_MUY_GRANDE`), un huésped que llama al endpoint (403).

## Pendiente de confirmar con el equipo

- El contrato de la página 13 usa identificadores numéricos (`integer`); el backend completo usa **UUID** desde V1. Hay que actualizar el `openapi.yaml` de la página 13 a `string`/`uuid`.
- Los estados de usuario en V1 son `ACTIVO`, `DESACTIVADO` y `RESTRINGIDO` (en masculino); en la página 06 están en femenino. Conviene dejar uno solo y corregir la documentación.
