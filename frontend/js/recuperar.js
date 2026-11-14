// HU-003 — Recuperación simulada de acceso (A-04): el código se muestra en pantalla en lugar de enviarse por correo.
const formSolicitar = document.getElementById("formSolicitar");
const formRestablecer = document.getElementById("formRestablecer");
const msgSolicitar = document.getElementById("mensajeSolicitar");
const msgRestablecer = document.getElementById("mensajeRestablecer");

function mostrar(p, texto, ok) {
    p.textContent = texto;
    p.style.color = ok ? "#1e7b34" : "";
}

// Si llega ?token=... (enlace simulado) se va directo al paso 2
const tokenUrl = new URLSearchParams(window.location.search).get("token");
if (tokenUrl) {
    formSolicitar.hidden = true;
    formRestablecer.hidden = false;
    document.getElementById("token").value = tokenUrl;
}

formSolicitar.addEventListener("submit", async (e) => {
    e.preventDefault();
    const correo = document.getElementById("correo").value.trim();
    if (!correo) { mostrar(msgSolicitar, "Ingresa tu correo.", false); return; }
    document.getElementById("btnSolicitar").disabled = true;
    try {
        const r = await postJson("/api/auth/password-recovery", { correo });
        if (!r.ok) { mostrar(msgSolicitar, textoDeError(r.datos, "No se pudo procesar la solicitud."), false); return; }
        mostrar(msgSolicitar, r.datos.mensaje, true);                                  // CA-02: mismo mensaje
        if (r.datos.tokenSimulado) {                                                    // simulación del correo
            formRestablecer.hidden = false;
            document.getElementById("token").value = r.datos.tokenSimulado;
            document.getElementById("subtitulo").textContent =
                "Recuperación simulada: este es el código que llegaría a tu correo. Vence en 30 minutos.";
        }
    } catch (error) {
        mostrar(msgSolicitar, error.message, false);
    } finally {
        document.getElementById("btnSolicitar").disabled = false;
    }
});

formRestablecer.addEventListener("submit", async (e) => {
    e.preventDefault();
    const token = document.getElementById("token").value.trim();
    const nueva = document.getElementById("nueva").value;
    if (!token) { mostrar(msgRestablecer, "Ingresa el código de recuperación.", false); return; }
    if (nueva.length < 8) { mostrar(msgRestablecer, "La contraseña debe tener al menos 8 caracteres.", false); return; }
    if (nueva !== document.getElementById("confirmar").value) { mostrar(msgRestablecer, "Las contraseñas no coinciden.", false); return; }
    document.getElementById("btnRestablecer").disabled = true;
    try {
        const r = await postJson("/api/auth/password-reset", { token, nuevaContrasena: nueva });
        if (!r.ok) { mostrar(msgRestablecer, textoDeError(r.datos, "No se pudo cambiar la contraseña."), false); return; }
        mostrar(msgRestablecer, r.datos.mensaje + " Redirigiendo…", true);
        setTimeout(() => { window.location.href = "login.html"; }, 1800);
    } catch (error) {
        mostrar(msgRestablecer, error.message, false);
    } finally {
        document.getElementById("btnRestablecer").disabled = false;
    }
});
