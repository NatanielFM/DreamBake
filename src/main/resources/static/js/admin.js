document.addEventListener('DOMContentLoaded', () => {
  // --- Lógica de Pestañas (Tabs) ---
  const buttons = document.querySelectorAll('.tab-btn');
  const contents = document.querySelectorAll('.tab-content');

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      // Remover active de todos
      buttons.forEach(b => b.classList.remove('active'));
      contents.forEach(c => c.classList.remove('active'));

      // Agregar active al clickeado
      btn.classList.add('active');
      const tabId = btn.dataset.tab;
      document.getElementById(tabId).classList.add('active');
    });
  });

  // --- Lógica de Cierre de Sesión Seguro ---
  const logoutBtn = document.getElementById('logout-button');
  const logoutForm = document.getElementById('logoutForm');

  if (logoutBtn && logoutForm) {
    logoutBtn.addEventListener('click', (e) => {
      e.preventDefault();
      
      // Confirmación nativa
      const ok = window.confirm('¿Querés cerrar sesión del panel de administración?');
      if (ok) {
        // Enviar el formulario por POST (Requisito de Spring Security)
        logoutForm.submit();
      }
    });
  }
});