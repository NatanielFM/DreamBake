// profile.js — Formularios de "Mi cuenta" (/profile).
//
// Los dos formularios se envían normalmente a ProfileController
// (POST /profile/update y POST /profile/password), que redirige con
// ?updated, ?passUpdated o ?error=… para mostrar el aviso.
// Este script valida antes de enviar, muestra/oculta contraseñas y
// pone el botón en estado "cargando". Requiere form-helpers.js.

document.addEventListener('DOMContentLoaded', () => {
  if (!window.DreamForm) return;
  const { initPasswordToggles, setFieldError, setLoading } = window.DreamForm;
  initPasswordToggles();

  /* ---------- Datos personales ---------- */
  const dataForm = document.getElementById('profile-form');
  if (dataForm) {
    const name = document.getElementById('name');
    const phone = document.getElementById('phone');
    const checkName = () => { const m = name.value.trim().length >= 3 ? '' : 'Escribí tu nombre y apellido.'; setFieldError(name, m); return !m; };
    const checkPhone = () => {
      const digits = (phone.value.match(/\d/g) || []).length;
      const m = !phone.value.trim() || digits >= 8 ? '' : 'Escribí un celular válido, con código de área.';
      setFieldError(phone, m); return !m;
    };
    name.addEventListener('input', () => { if (name.getAttribute('aria-invalid') === 'true') checkName(); });
    phone.addEventListener('input', () => { if (phone.getAttribute('aria-invalid') === 'true') checkPhone(); });

    dataForm.addEventListener('submit', (e) => {
      if ([checkName(), checkPhone()].includes(false)) {
        e.preventDefault();
        dataForm.querySelector('[aria-invalid="true"]')?.focus();
        return;
      }
      setLoading(document.getElementById('profile-submit'));
    });
  }

  /* ---------- Cambiar contraseña ---------- */
  const passForm = document.getElementById('password-form');
  if (passForm) {
    const current = document.getElementById('currentPassword');
    const next = document.getElementById('newPassword');
    const confirm = document.getElementById('confirmPassword');

    const checks = [
      () => { const m = current.value ? '' : 'Escribí tu contraseña actual.'; setFieldError(current, m); return !m; },
      () => { const m = next.value.length >= 6 ? '' : 'La nueva contraseña tiene que tener al menos 6 caracteres.'; setFieldError(next, m); return !m; },
      () => { const m = !confirm.value ? 'Repetí la nueva contraseña.' : (confirm.value === next.value ? '' : 'Las contraseñas no coinciden.'); setFieldError(confirm, m); return !m; }
    ];

    [current, next, confirm].forEach((input, i) => {
      input.addEventListener('input', () => { if (input.getAttribute('aria-invalid') === 'true') checks[i](); });
    });
    confirm.addEventListener('blur', () => { if (confirm.value) checks[2](); });

    passForm.addEventListener('submit', (e) => {
      if (checks.map(fn => fn()).includes(false)) {
        e.preventDefault();
        passForm.querySelector('[aria-invalid="true"]')?.focus();
        return;
      }
      setLoading(document.getElementById('password-submit'));
    });
  }
});
