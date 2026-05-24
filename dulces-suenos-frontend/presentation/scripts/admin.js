// scripts/admin.js
document.addEventListener('DOMContentLoaded', () => {
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));

      btn.classList.add('active');
      const tabId = btn.dataset.tab;
      document.getElementById(tabId).classList.add('active');
    });
  });
});

// scripts/admin.js (añadir al final)
document.addEventListener('DOMContentLoaded', () => {
  const logoutBtn = document.getElementById('logout-button');
  if (!logoutBtn) return;

  logoutBtn.addEventListener('click', (e) => {
    e.preventDefault();
    // Confirmación simple
    const ok = window.confirm('¿Querés cerrar sesión?');
    if (!ok) return;

    // Opciones:
    // 1) Redirigir a endpoint que destruye sesión en el servidor
    window.location.href = '/logout';

    // 2) Si preferís llamada fetch (API), usar:
    // fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
    //   .then(() => window.location.href = '/login.html')
    //   .catch(() => alert('No se pudo cerrar sesión. Intentá de nuevo.'));
  });
});
