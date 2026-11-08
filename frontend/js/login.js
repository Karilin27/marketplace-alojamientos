
const formulario = document.getElementById("loginForm");
const correo = document.getElementById("correo");
const contrasena = document.getElementById("contrasena");
const mensaje = document.getElementById("mensaje");

formulario.addEventListener("submit", function(event) {

    // Evitar que el navegador recargue la página
    event.preventDefault();

    // Limpiar mensajes anteriores
    mensaje.textContent = "";

    // Validar correo vacío
    if (correo.value.trim() === "") {
        mensaje.textContent = "Debes ingresar tu correo electrónico.";
        correo.focus();
        return;
    }

    // Validar formato del correo
   if (!correo.validity.valid) {
    mensaje.textContent = "Ingresa un correo válido. Ejemplo: usuario@gmail.com";
    correo.focus();
    return;
}
    // Validar contraseña vacía
    if (contrasena.value === "") {
        mensaje.textContent = "Debes ingresar tu contraseña.";
        contrasena.focus();
        return;
    }

    // Formulario validado
    mensaje.style.color = "green";
    mensaje.textContent = "Datos ingresados correctamente. Listo para conectar con el servidor.";

});
