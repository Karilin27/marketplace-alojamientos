
const formulario = document.getElementById("loginForm");
const correo = document.getElementById("correo");
const contrasena = document.getElementById("contrasena");
const mensaje = document.getElementById("mensaje");
const boton = document.getElementById("btnLogin");
const botonReactivar = document.getElementById("btnReactivar");

// Clave de localStorage donde se guarda el JWT (la misma que usa frontend/js/reservas/config.js)
const TOKEN_KEY = "token";

function mostrarMensaje(texto, tipo = "error") {
    mensaje.style.color = tipo === "exito" ? "green" : "#c0392b";
    mensaje.textContent = texto;
}

// Página a la que se va después de entrar: la que pidió el usuario (?volver=...) o la de su rol
function destinoTrasLogin(rol) {
    const volver = new URLSearchParams(window.location.search).get("volver");

    // Solo rutas del mismo sitio, para no redirigir a páginas externas
    if (volver && volver.startsWith("/") && !volver.startsWith("//")) {
        return volver;
    }
    return rol === "ANFITRION" ? "reservas/recibidas.html" : "reservas/mis-reservas.html";
}

// Envía las credenciales; reactivar = true solo cuando la cuenta está desactivada
async function iniciarSesion(reactivar) {
    boton.disabled = true;
    botonReactivar.disabled = true;
    mostrarMensaje(reactivar ? "Reactivando tu cuenta..." : "Verificando tus datos...", "exito");

    try {
        const { ok, datos } = await postJson("/api/auth/login", {
            correo: correo.value.trim(),
            contrasena: contrasena.value,
            reactivar: reactivar
        });

        if (ok && datos && datos.token) {
            try {
                localStorage.setItem(TOKEN_KEY, datos.token);
                localStorage.setItem("usuario", JSON.stringify(datos.usuario));
            } catch (error) {
                mostrarMensaje("Tu navegador no permite guardar la sesión. Revisa la configuración de privacidad.");
                return;
            }

            mostrarMensaje("Bienvenido, " + datos.usuario.nombre + ".", "exito");
            window.location.href = destinoTrasLogin(datos.usuario.rol);
            return;
        }

        // Cuenta desactivada: se ofrece reactivarla con las mismas credenciales
        if (datos && datos.codigo === "CUENTA_DESACTIVADA") {
            mostrarMensaje(datos.mensaje);
            botonReactivar.hidden = false;
            return;
        }

        // 401 credenciales incorrectas, 403 cuenta restringida, 400 datos incompletos...
        botonReactivar.hidden = true;
        mostrarMensaje(textoDeError(datos, "No fue posible iniciar sesión. Intenta de nuevo."));
        contrasena.value = "";
        contrasena.focus();

    } catch (error) {
        mostrarMensaje(error.message);
    } finally {
        boton.disabled = false;
        botonReactivar.disabled = false;
    }
}

formulario.addEventListener("submit", function(event) {

    // Evitar que el navegador recargue la página
    event.preventDefault();

    // Limpiar mensajes anteriores
    mensaje.textContent = "";
    botonReactivar.hidden = true;

    // Validar correo vacío
    if (correo.value.trim() === "") {
        mostrarMensaje("Debes ingresar tu correo electrónico.");
        correo.focus();
        return;
    }

    // Validar formato del correo
    if (!correo.validity.valid) {
        mostrarMensaje("Ingresa un correo válido. Ejemplo: usuario@gmail.com");
        correo.focus();
        return;
    }

    // Validar contraseña vacía
    if (contrasena.value === "") {
        mostrarMensaje("Debes ingresar tu contraseña.");
        contrasena.focus();
        return;
    }

    iniciarSesion(false);
});

botonReactivar.addEventListener("click", function() {
    if (contrasena.value === "") {
        mostrarMensaje("Ingresa tu contraseña para reactivar la cuenta.");
        contrasena.focus();
        return;
    }
    iniciarSesion(true);
});
