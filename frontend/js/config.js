// Configuración común de las pantallas de cuenta (registro e inicio de sesión).
// API_BASE debe apuntar al mismo backend que usa frontend/js/reservas/config.js.
window.APP_CONFIG = {
    API_BASE: "http://localhost:8080"
};

// Envía un JSON por POST y devuelve { ok, status, datos }.
// Si el servidor no responde (apagado, sin red, CORS) lanza un Error con un mensaje claro.
async function postJson(ruta, cuerpo) {
    let respuesta;

    try {
        respuesta = await fetch(window.APP_CONFIG.API_BASE + ruta, {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(cuerpo)
        });
    } catch (error) {
        throw new Error("No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.");
    }

    let datos = null;
    try {
        datos = await respuesta.json();
    } catch (error) {
        datos = null;
    }

    return { ok: respuesta.ok, status: respuesta.status, datos: datos };
}

// Convierte la respuesta de error del backend ({ codigo, mensaje, detalles }) en un texto para el usuario.
function textoDeError(datos, porDefecto) {
    if (!datos || !datos.mensaje) {
        return porDefecto;
    }
    if (Array.isArray(datos.detalles) && datos.detalles.length > 0 && datos.codigo !== "CORREO_YA_REGISTRADO") {
        return datos.mensaje + " " + datos.detalles.join(" ");
    }
    return datos.mensaje;
}
