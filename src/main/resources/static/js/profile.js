// profile.js — "Mi cuenta" (/profile).
//
// Los formularios se envían normalmente a ProfileController
// (POST /profile/update, /profile/password, /profile/email/…, /profile/delete), que redirige
// con ?updated, ?passUpdated, ?error=… para mostrar el aviso.
// Este script:
//   - valida antes de enviar, muestra/oculta contraseñas y pone el botón en "cargando",
//   - dibuja "Mis pedidos" con GET /api/account/orders (solo devuelve los del usuario logueado),
//   - abre la ventana de confirmación de "Eliminar mi cuenta".
// Requiere form-helpers.js.

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

    // Cumpleaños (solo clientes con el email confirmado): no puede ser una fecha futura
    const birth = document.getElementById('birthDate');
    const checkBirth = () => {
      if (!birth || !birth.value) { if (birth) setFieldError(birth, ''); return true; }
      const m = birth.value > (birth.max || '9999-12-31') ? 'La fecha de cumpleaños no puede ser futura.' : '';
      setFieldError(birth, m); return !m;
    };
    if (birth) birth.addEventListener('change', checkBirth);

    dataForm.addEventListener('submit', (e) => {
      if ([checkName(), checkPhone(), checkBirth()].includes(false)) {
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

  /* ---------- Confirmá tu email: el código (solo números) ---------- */
  const verifyForm = document.getElementById('verify-form');
  if (verifyForm) {
    const code = document.getElementById('verify-code');
    code.addEventListener('input', () => {
      code.value = code.value.replace(/\D/g, '').slice(0, 6);
      if (code.getAttribute('aria-invalid') === 'true') setFieldError(code, '');
    });
    verifyForm.addEventListener('submit', (e) => {
      if (!/^\d{6}$/.test(code.value)) {
        e.preventDefault();
        setFieldError(code, 'Escribí los 6 números del código.');
        code.focus();
        return;
      }
      setLoading(document.getElementById('verify-submit'));
    });
  }

  /* ---------- Mis pedidos ---------- */
  const ordersBox = document.getElementById('my-orders-list');
  if (ordersBox) loadMyOrders(ordersBox);

  /* ---------- Eliminar mi cuenta: ventana de confirmación ---------- */
  const dialog = document.getElementById('delete-dialog');
  const openBtn = document.getElementById('delete-open');
  if (dialog && openBtn && typeof dialog.showModal === 'function') {
    openBtn.addEventListener('click', () => dialog.showModal());
    document.getElementById('delete-cancel').addEventListener('click', () => dialog.close());
    // Tocar el fondo oscuro también cierra (Esc lo cierra solo, es un <dialog>)
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });
    dialog.querySelector('form').addEventListener('submit', () => setLoading(document.getElementById('delete-confirm')));
  } else if (openBtn) {
    openBtn.hidden = true; // navegador muy viejo sin <dialog>: mejor no mostrar el botón
  }
});

// Trae "Mis pedidos" y los dibuja. Los textos se ponen con textContent (nunca como HTML),
// así un texto raro en un pedido no puede meter código en la página.
async function loadMyOrders(box) {
  const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });
  const el = (tag, cls, text) => {
    const node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  };
  const formatDate = (iso) => {
    if (!iso) return 'Sin fecha';
    const [y, m, d] = iso.split('-').map(Number);
    const text = new Date(y, m - 1, d).toLocaleDateString('es-AR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    return text.charAt(0).toUpperCase() + text.slice(1);
  };
  // Color del estado: en curso (rosa), entregado (tinta), cancelado (rojo)
  const tone = { PENDING: 'pending', CONFIRMED: 'active', IN_PROGRESS: 'active', COMPLETED: 'done', CANCELLED: 'cancelled', REJECTED: 'cancelled' };

  try {
    const res = await fetch('/api/account/orders', { headers: { 'Accept': 'application/json' } });
    if (!res.ok) throw new Error(String(res.status));
    const orders = await res.json();
    box.replaceChildren();
    box.removeAttribute('data-loading');

    if (!orders.length) {
      box.append(el('p', 'my-orders-empty', 'Todavía no tenés pedidos. ¡Cuando hagas uno, lo vas a ver acá!'));
      return;
    }
    const list = el('ul', 'my-orders-list');
    orders.forEach((o) => {
      const item = el('li', 'my-order');
      const head = el('div', 'my-order__head');
      head.append(el('span', 'my-order__number', `Pedido #${o.id}`));
      const status = el('span', `my-order__status my-order__status--${tone[o.status] || 'pending'}`, o.statusLabel);
      head.append(status);
      item.append(head);

      const when = [formatDate(o.deliveryDate), o.deliveryShift, o.deliveryMethod].filter(Boolean).join(' · ');
      item.append(el('p', 'my-order__date', when));
      if (o.items && o.items.length) item.append(el('p', 'my-order__items', o.items.join(', ')));
      const total = el('p', 'my-order__total');
      total.append(el('span', null, 'Total '), el('strong', null, money.format(o.total || 0)));
      item.append(total);
      list.append(item);
    });
    box.append(list);
  } catch (e) {
    box.replaceChildren(el('p', 'my-orders-empty', 'No pudimos cargar tus pedidos. Probá recargar la página.'));
    box.removeAttribute('data-loading');
  }
}
