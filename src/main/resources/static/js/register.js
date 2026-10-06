// register.js — Formulario de registro (/register).
//
// El envío lo procesa AuthController (POST /register con name, email, phone y
// password). Si el servidor encuentra errores (por ejemplo, email repetido),
// vuelve a mostrar el formulario con el mensaje debajo del campo.
// Este script suma:
//   - mostrar/ocultar contraseña en los dos campos,
//   - aviso en vivo de "al menos 6 caracteres" y de "las contraseñas no coinciden",
//   - validación antes de enviar y estado "cargando" del botón.
// Requiere form-helpers.js.

document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('register-form');
  if (!form || !window.DreamForm) return;

  const { initPasswordToggles, setFieldError, setLoading, EMAIL_RE } = window.DreamForm;
  const name = document.getElementById('name');
  const email = document.getElementById('email');
  const phone = document.getElementById('phone');
  const password = document.getElementById('password');
  const confirmPassword = document.getElementById('confirmPassword');
  const rule = document.getElementById('password-rule');
  const submit = document.getElementById('register-submit');

  initPasswordToggles(form);

  const checks = {
    name: () => setFieldError(name, name.value.trim().length >= 3 ? '' : 'Escribí tu nombre y apellido.'),
    email: () => {
      const v = email.value.trim();
      setFieldError(email, !v ? 'Escribí tu email.' : (EMAIL_RE.test(v) ? '' : 'Revisá el email (por ejemplo nombre@gmail.com).'));
    },
    phone: () => setFieldError(phone, (phone.value.match(/\d/g) || []).length >= 8 ? '' : 'Escribí un celular válido, con código de área.'),
    password: () => setFieldError(password, password.value.length >= 6 ? '' : 'La contraseña tiene que tener al menos 6 caracteres.'),
    confirm: () => setFieldError(confirmPassword,
      confirmPassword.value && confirmPassword.value !== password.value ? 'Las contraseñas no coinciden.' : (!confirmPassword.value ? 'Repetí la contraseña.' : ''),
      'passwordError')
  };

  // Indicador "al menos 6 caracteres" mientras se escribe
  function updateRule() {
    if (rule) rule.classList.toggle('is-ok', password.value.length >= 6);
  }

  [[name, 'name'], [email, 'email'], [phone, 'phone']].forEach(([input, key]) => {
    input.addEventListener('blur', () => { if (input.value) checks[key](); });
    input.addEventListener('input', () => { if (input.getAttribute('aria-invalid') === 'true') checks[key](); });
  });
  password.addEventListener('input', () => {
    updateRule();
    if (password.getAttribute('aria-invalid') === 'true') checks.password();
    if (confirmPassword.value) checks.confirm();
  });
  confirmPassword.addEventListener('input', () => { if (confirmPassword.value.length >= password.value.length || confirmPassword.getAttribute('aria-invalid') === 'true') checks.confirm(); });
  confirmPassword.addEventListener('blur', () => { if (confirmPassword.value) checks.confirm(); });
  updateRule();

  form.addEventListener('submit', (e) => {
    Object.values(checks).forEach(fn => fn());
    const firstInvalid = form.querySelector('[aria-invalid="true"]');
    if (firstInvalid) {
      e.preventDefault();
      firstInvalid.focus();
      return;
    }
    setLoading(submit);
  });

  // Si el servidor devolvió errores, el foco va al primer campo con error
  const serverError = form.querySelector('.field--error .input');
  if (serverError) {
    serverError.setAttribute('aria-invalid', 'true');
    serverError.focus();
  }
});
