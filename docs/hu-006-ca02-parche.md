# Parche HU-006 CA-02 (publicacion ACTIVA)

Problema: al editar una publicacion ACTIVA se podia dejar sin imagenes (se guardaba) o sin politica de cancelacion
(la base de datos lo rechazaba con un error 500, por el CHECK `alojamiento_activa_con_politica` de V5).

Cambio:
- `ReglasAlojamiento.erroresEdicionActiva(Datos)`: regla pura que devuelve que falta (imagenes y politica).
- `PublicacionService.editar`: si la publicacion esta ACTIVA, responde 400 `ALOJAMIENTO_INVALIDO` con esos detalles antes de guardar.
- `ReglasAlojamientoTest`: dos pruebas nuevas (`ca02_activaNoPuedeQuedarSinImagenesNiPolitica`, `ca02_activaCompletaSePuedeGuardar`).
