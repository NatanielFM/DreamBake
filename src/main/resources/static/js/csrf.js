// csrf.js — Agrega el token anti-CSRF a los pedidos que modifican datos.
//
// Spring Security guarda un token en la cookie "XSRF-TOKEN" y exige que cada
// POST / PUT / PATCH / DELETE lo devuelva en el encabezado "X-XSRF-TOKEN".
// Otro sitio no puede leer esa cookie, así que no puede falsificar el envío
// (ataque CSRF: "hacer cosas en nombre del usuario sin que se dé cuenta").
//
// Este archivo envuelve fetch() una sola vez: todo el JS del sitio y de los
// paneles sigue usando fetch como siempre y el token se agrega solo.
// Se carga primero en cada página (en el <head>).
(function () {
  if (window.__dreamBakeCsrfReady) return;
  window.__dreamBakeCsrfReady = true;

  const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

  function readToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
  }

  // Solo se agrega el token a pedidos a nuestro propio sitio, nunca a otros dominios
  function isSameOrigin(url) {
    try {
      return new URL(url, window.location.href).origin === window.location.origin;
    } catch (e) {
      return false;
    }
  }

  const originalFetch = window.fetch.bind(window);

  window.fetch = function (input, init) {
    init = init || {};
    const url = typeof input === 'string' || input instanceof URL ? String(input) : input.url;
    const method = (init.method || (input instanceof Request ? input.method : 'GET')).toUpperCase();
    const token = readToken();

    if (token && !SAFE_METHODS.includes(method) && isSameOrigin(url)) {
      const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
      headers.set('X-XSRF-TOKEN', token);
      init = Object.assign({}, init, { headers: headers });
    }
    return originalFetch(input, init);
  };
})();
