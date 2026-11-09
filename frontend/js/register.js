
const formulario = document.getElementById("registerForm");

const nombre = document.getElementById("nombre");
const correo = document.getElementById("correo");
const contrasena = document.getElementById("contrasena");
const rol = document.getElementById("rol");

const mensaje = document.getElementById("mensaje");
const boton = document.getElementById("btnRegistro");

// Validar formato del correo (mismo patrón que el login y el backend)
function correoValido(valor) {

    const patron = /^[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$/;

    return patron.test(valor);
}

// Mostrar mensajes
function mostrarMensaje(texto, tipo = "error") {

    mensaje.textContent = texto;

    mensaje.style.color =
        tipo === "exito" ? "green" : "#c0392b";
}

// Procesar el registro
formulario.addEventListener("submit", async function(event) {

    event.preventDefault();

    mensaje.textContent = "";

    const valorNombre = nombre.value.trim();
    const valorCorreo = correo.value.trim();
    const valorContrasena = contrasena.value;
    const valorRol = rol.value;

    // CA-02: validar nombre
    if (valorNombre === "") {

        mostrarMensaje("Debes ingresar tu nombre completo.");
        nombre.focus();
        return;
    }

    // CA-02: validar correo vacío
    if (valorCorreo === "") {

        mostrarMensaje("Debes ingresar tu correo electrónico.");
        correo.focus();
        return;
    }

    // CA-02: validar formato del correo
    if (!correoValido(valorCorreo)) {

        mostrarMensaje("Ingresa un correo válido. Ejemplo: usuario@gmail.com o usuario@gmail.co");
        correo.focus();
        return;
    }

    // CA-02: validar contraseña
    if (valorContrasena.trim() === "") {

        mostrarMensaje("Debes ingresar una contraseña.");
        contrasena.focus();
        return;
    }

    if (valorContrasena.length < 8) {

        mostrarMensaje("La contraseña debe tener al menos 8 caracteres.");
        contrasena.focus();
        return;
    }

    if (new TextEncoder().encode(valorContrasena).length > 72) {

        mostrarMensaje("La contraseña no puede superar 72 caracteres.");
        contrasena.focus();
        return;
    }

    // CA-02: validar rol
    if (valorRol === "") {

        mostrarMensaje("Debes seleccionar un tipo de cuenta.");
        rol.focus();
        return;
    }

    // CA-04: impedir rol administrador
    if (!["HUESPED", "ANFITRION"].includes(valorRol)) {

        mostrarMensaje("El rol seleccionado no está permitido.");
        rol.focus();
        return;
    }

    // Enviar al backend: POST /api/auth/register
    boton.disabled = true;
    mostrarMensaje("Creando tu cuenta...", "exito");

    try {
        const { ok, datos } = await postJson("/api/auth/register", {
            nombre: valorNombre,
            correo: valorCorreo,
            contrasena: valorContrasena,
            rol: valorRol
        });

        if (ok) {
            // CA-01: registro exitoso
            mostrarMensaje("Cuenta creada correctamente. Redirigiendo al inicio de sesión...", "exito");
            formulario.reset();
            setTimeout(() => {
                window.location.href = "login.html";
            }, 1500);
            return;
        }

        // 400 datos inválidos, 409 correo ya registrado, 5xx error del servidor
        mostrarMensaje(textoDeError(datos, "No fue posible completar el registro. Intenta de nuevo."));

        if (datos && datos.codigo === "CORREO_YA_REGISTRADO") {
            correo.focus();
        }

    } catch (error) {
        mostrarMensaje(error.message);
    } finally {
        boton.disabled = false;
    }

});
