// cookie-notice.js — Aviso breve de cookies del sitio público (paso 6.2).
//
// Solo usamos cookies necesarias (sesión, "mantener la sesión iniciada", seguridad de formularios) y el
// carrito vive en el navegador, así que no hace falta pedir permiso: alcanza con avisar. El aviso aparece
// hasta que la persona toca "Entendido"; eso se recuerda en el navegador (localStorage) y no vuelve a salir.
// Si el navegador no deja guardar (modo privado estricto), el aviso se cierra igual por esta visita.
(function () {
  const KEY = 'dreamBakeCookieNotice';
  const notice = document.getElementById('cookie-notice');
  const ok = document.getElementById('cookie-notice-ok');
  if (!notice || !ok) return;

  let seen = false;
  try { seen = localStorage.getItem(KEY) === '1'; } catch (e) { /* sin acceso: se muestra */ }
  if (seen) return;

  notice.hidden = false;
  ok.addEventListener('click', () => {
    try { localStorage.setItem(KEY, '1'); } catch (e) { /* no se pudo guardar: solo se cierra */ }
    notice.hidden = true;
  });
})();
