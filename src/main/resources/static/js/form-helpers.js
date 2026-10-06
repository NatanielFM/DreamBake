// form-helpers.js — Ayudas compartidas por los formularios de cuenta
// (login, registro y perfil). Expone window.DreamForm con:
//   - initPasswordToggles(): botones de mostrar/ocultar contraseña
//   - setFieldError(input, mensaje): muestra u oculta el error de un campo
//   - setLoading(boton): estado "cargando" del botón al enviar
//
// Los botones de mostrar contraseña llevan data-toggle-password="idDelInput".
// El error de un campo es el elemento con id "<idDelInput>-error" (o el que se indique).

(function () {
  function initPasswordToggles(root) {
    (root || document).querySelectorAll('[data-toggle-password]').forEach(btn => {
      const input = document.getElementById(btn.getAttribute('data-toggle-password'));
      if (!input) return;
      btn.addEventListener('click', () => {
        const show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.setAttribute('aria-pressed', show ? 'true' : 'false');
        btn.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña');
        input.focus();
      });
    });
  }

  function setFieldError(input, message, errorId) {
    if (!input) return;
    const error = document.getElementById(errorId || `${input.id}-error`);
    const field = input.closest('.field');
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (field) field.classList.toggle('field--error', Boolean(message));
    if (error) {
      error.hidden = !message;
      const span = error.querySelector('span');
      if (message) { if (span) span.textContent = message; else error.textContent = message; }
    }
  }

  // El botón queda "cargando" (spinner de components.css) y no se puede tocar dos veces
  function setLoading(button) {
    if (!button) return;
    button.setAttribute('aria-busy', 'true');
    button.setAttribute('aria-disabled', 'true');
  }

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  window.DreamForm = { initPasswordToggles, setFieldError, setLoading, EMAIL_RE };
})();
