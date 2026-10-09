// complete-email.js — Pantalla "Completá tu email" (/login/completar-email).
//
// Aparece cuando alguien entra con Facebook y Facebook no manda el email.
// El trabajo real lo hace el servidor (SocialSignupController); este script solo:
//   - revisa el email o el código antes de enviar,
//   - pone el botón en "cargando" mientras se envía.
// Requiere form-helpers.js.

document.addEventListener('DOMContentLoaded', () => {
  if (!window.DreamForm) return;
  const { setFieldError, setLoading, EMAIL_RE } = window.DreamForm;

  // Paso 1: email
  const emailForm = document.getElementById('complete-email-form');
  if (emailForm) {
    const email = document.getElementById('email');
    const validate = () => {
      const v = email.value.trim();
      const msg = !v ? 'Escribí tu email.' : (EMAIL_RE.test(v) ? '' : 'Revisá el email (por ejemplo nombre@gmail.com).');
      setFieldError(email, msg);
      return !msg;
    };
    email.addEventListener('input', () => { if (email.getAttribute('aria-invalid') === 'true') validate(); });
    emailForm.addEventListener('submit', (e) => {
      if (!validate()) { e.preventDefault(); email.focus(); return; }
      setLoading(document.getElementById('complete-submit'));
    });
  }

  // Paso 2: código (solo números: se borran espacios y letras mientras se escribe)
  const codeForm = document.getElementById('complete-code-form');
  if (codeForm) {
    const code = document.getElementById('code');
    code.addEventListener('input', () => {
      code.value = code.value.replace(/\D/g, '').slice(0, 6);
      if (code.getAttribute('aria-invalid') === 'true') setFieldError(code, '');
    });
    codeForm.addEventListener('submit', (e) => {
      if (!/^\d{6}$/.test(code.value)) {
        e.preventDefault();
        setFieldError(code, 'Escribí los 6 números del código.');
        code.focus();
        return;
      }
      setLoading(document.getElementById('complete-submit'));
    });
  }
});
