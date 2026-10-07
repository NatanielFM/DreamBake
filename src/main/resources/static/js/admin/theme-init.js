// /js/admin/theme-init.js - Aplica el modo oscuro ANTES de dibujar la página.
// Se carga en el <head> sin "defer" para que no se vea un destello blanco al entrar.
// (Antes estaba escrito dentro del HTML; la política de seguridad ya no lo permite.)
(function () {
  try {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme) document.documentElement.setAttribute('data-theme', savedTheme);
  } catch (e) { /* navegador sin acceso a localStorage: queda el modo claro */ }
})();
