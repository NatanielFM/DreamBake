// recuperar.js — "Olvidé mi contraseña" (/recuperar y /recuperar/nueva, paso 6.1).
//
// El servidor (PasswordResetController) valida todo igual; este script solo mejora la experiencia:
//   - /recuperar: revisa el formato del email antes de enviar.
//   - /recuperar/nueva: mostrar/ocultar contraseña, la regla "al menos 8 caracteres" se tilda sola
//     y avisa si las dos contraseñas no coinciden.
//   - estado "cargando" en el botón mientras se envía.
// Requiere form-helpers.js.

document.addEventListener('DOMContentLoaded', () => {
  if (!window.DreamForm) return;
  const { initPasswordToggles, setFieldError, setLoading, EMAIL_RE } = window.DreamForm;

  // ---------- Paso 1: pedir el link ----------
  const requestForm = document.getElementById('reset-request-form');
  if (requestForm) {
    const email = document.getElementById('email');
    const submit = document.getElementById('reset-submit');

    const validateEmail = () => {
      const v = email.value.trim();
      const msg = !v ? 'Escribí tu email.' : (EMAIL_RE.test(v) ? '' : 'Revisá el email (por ejemplo nombre@gmail.com).');
      setFieldError(email, msg);
      return !msg;
    };

    email.addEventListener('blur', () => { if (email.value) validateEmail(); });
    email.addEventListener('input', () => { if (email.getAttribute('aria-invalid') === 'true') validateEmail(); });
    requestForm.addEventListener('submit', (e) => {
      if (!validateEmail()) {
        e.preventDefault();
        email.focus();
        return;
      }
      setLoading(submit);
    });
    // Si el servidor marcó un error, el foco va al email para corregirlo
    if (email.getAttribute('aria-invalid') === 'true') email.focus();
  }

  // ---------- Paso 2: contraseña nueva ----------
  const newForm = document.getElementById('reset-new-form');
  if (newForm) {
    const password = document.getElementById('password');
    const confirm = document.getElementById('confirmPassword');
    const rule = document.getElementById('password-rule');
    const submit = document.getElementById('reset-submit');
    const min = Number(rule?.dataset.min) || 8;

    initPasswordToggles(newForm);

    const validatePassword = () => {
      const msg = password.value.length >= min ? '' : `La contraseña tiene que tener al menos ${min} caracteres.`;
      setFieldError(password, msg);
      return !msg;
    };
    const validateConfirm = () => {
      const msg = !confirm.value ? 'Repetí la contraseña.' : (confirm.value === password.value ? '' : 'Las contraseñas no coinciden.');
      setFieldError(confirm, msg);
      return !msg;
    };

    password.addEventListener('input', () => {
      if (rule) rule.classList.toggle('is-ok', password.value.length >= min);
      if (password.getAttribute('aria-invalid') === 'true') validatePassword();
      if (confirm.getAttribute('aria-invalid') === 'true') validateConfirm();
    });
    confirm.addEventListener('input', () => { if (confirm.getAttribute('aria-invalid') === 'true') validateConfirm(); });
    confirm.addEventListener('blur', () => { if (confirm.value) validateConfirm(); });

    newForm.addEventListener('submit', (e) => {
      const ok = [validatePassword(), validateConfirm()];
      if (ok.includes(false)) {
        e.preventDefault();
        newForm.querySelector('[aria-invalid="true"]')?.focus();
        return;
      }
      setLoading(submit);
    });
    password.focus();
  }
});
