// login.js — Formulario de inicio de sesión (/login).
//
// El envío lo procesa Spring Security (POST /login con los campos "username" y
// "password"). Este script solo mejora la experiencia:
//   - mostrar/ocultar contraseña,
//   - validar antes de enviar (email con formato válido y contraseña no vacía),
//   - estado "cargando" en el botón mientras se envía.
// Si el email o la contraseña son incorrectos, Spring vuelve a /login?error
// y la página muestra el aviso.
// Requiere form-helpers.js.

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('login-form');
  if (!form || !window.DreamForm) return;

  const { initPasswordToggles, setFieldError, setLoading, EMAIL_RE } = window.DreamForm;
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const submit = document.getElementById('login-submit');

  initPasswordToggles(form);

  function validateEmail() {
    const v = email.value.trim();
    const msg = !v ? 'Escribí tu email.' : (EMAIL_RE.test(v) ? '' : 'Revisá el email (por ejemplo nombre@gmail.com).');
    setFieldError(email, msg);
    return !msg;
  }
  function validatePassword() {
    const msg = password.value ? '' : 'Escribí tu contraseña.';
    setFieldError(password, msg);
    return !msg;
  }

  email.addEventListener('blur', () => { if (email.value) validateEmail(); });
  email.addEventListener('input', () => { if (email.getAttribute('aria-invalid') === 'true') validateEmail(); });
  password.addEventListener('input', () => { if (password.getAttribute('aria-invalid') === 'true') validatePassword(); });

  form.addEventListener('submit', (e) => {
    const ok = [validateEmail(), validatePassword()];
    if (ok.includes(false)) {
      e.preventDefault();
      form.querySelector('[aria-invalid="true"]')?.focus();
      return;
    }
    setLoading(submit);   // el formulario se envía normalmente
  });

  // Con ?error, el foco va al email para corregir rápido
  if (new URLSearchParams(location.search).has('error')) email.focus();
});
