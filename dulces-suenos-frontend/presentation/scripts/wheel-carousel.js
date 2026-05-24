// scripts/wheel-carousel.js
// Carrusel tipo "rueda" — desplazamiento continuo, loop perfecto y pausa al hover/touch/focus.
// Ajustá SPEED_PX_PER_SEC para controlar la velocidad (px por segundo).

document.addEventListener('DOMContentLoaded', () => {
  const SPEED_PX_PER_SEC = 30; // px por segundo. Reducí para más lento, aumentá para más rápido.

  const carousel = document.getElementById('wheelCarousel');
  if (!carousel) return;

  const track = carousel.querySelector('.wheel-track');
  const wrapper = carousel.querySelector('.wheel-track-wrapper');

  if (!track || !wrapper) return;

  // Tomamos los elementos originales (antes de clonar)
  const originalNodes = Array.from(track.children);

  // Si no hay items, salimos
  if (originalNodes.length === 0) return;

  // Evitar duplicados si el script se ejecuta más de una vez
  if (!track.dataset.cloned) {
    const frag = document.createDocumentFragment();
    originalNodes.forEach(node => frag.appendChild(node.cloneNode(true)));
    track.appendChild(frag);
    track.dataset.cloned = 'true';
  }

  // Variables runtime
  let originalWidth = 0; // ancho total de la secuencia original (px)
  let pos = 0;           // desplazamiento acumulado en px
  let lastTime = null;
  let rafId = null;
  let running = true;

  // Calcula el ancho total de los slides originales (incluye gaps)
  function calculateOriginalWidth() {
    const originals = Array.from(track.children).slice(0, originalNodes.length);
    if (originals.length === 0) return 0;

    // Suma de anchos de cada item
    let sum = 0;
    originals.forEach(item => {
      const rect = item.getBoundingClientRect();
      sum += rect.width;
    });

    // Añadir gaps entre items (si existe gap en CSS)
    const style = getComputedStyle(track);
    const gap = parseFloat(style.gap || style.columnGap || 0);
    if (gap && originals.length > 1) {
      sum += gap * (originals.length - 1);
    }

    // Fallback: si sum es 0, usar scrollWidth/2
    if (!sum || !isFinite(sum)) {
      const total = track.scrollWidth || track.getBoundingClientRect().width;
      sum = total / 2 || 1;
    }

    originalWidth = sum;
    // Aseguramos pos dentro del rango para evitar saltos al recalcular
    pos = pos % originalWidth;
    if (pos < 0) pos += originalWidth;
  }

  // Loop de animación con requestAnimationFrame
  function step(timestamp) {
    if (!lastTime) lastTime = timestamp;
    const dt = (timestamp - lastTime) / 1000; // segundos
    lastTime = timestamp;

    if (running && originalWidth > 0) {
      pos += SPEED_PX_PER_SEC * dt;
      // wrap-around perfecto
      if (pos >= originalWidth) pos -= originalWidth;
      // aplicar transform
      track.style.transform = `translateX(${-pos}px)`;
    }

    rafId = requestAnimationFrame(step);
  }

  // Pausa / reanudar
  function pause() {
    running = false;
  }
  function resume() {
    // reset lastTime para evitar salto grande en dt
    lastTime = null;
    running = true;
  }

  // Handlers de interacción
  function addInteractionHandlers() {
    // pausa al entrar y reanuda al salir
    wrapper.addEventListener('mouseenter', pause);
    wrapper.addEventListener('mouseleave', resume);

    // pausa al focus y reanuda al blur
    carousel.addEventListener('focusin', pause);
    carousel.addEventListener('focusout', resume);

    // pausa al tocar (mobile)
    wrapper.addEventListener('touchstart', pause, { passive: true });
    wrapper.addEventListener('touchend', resume, { passive: true });

    // pausa si el usuario mantiene el dedo (touchmove)
    wrapper.addEventListener('touchmove', () => {}, { passive: true });

    // pausa cuando se hace hover sobre un item (por si wrapper no cubre todo)
    track.querySelectorAll('.carousel-item').forEach(item => {
      item.addEventListener('mouseenter', pause);
      item.addEventListener('mouseleave', resume);
    });

    // pausa cuando la pestaña pierde visibilidad
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) pause();
      else resume();
    });
  }

  // Recalcular medidas al cambiar tamaño
  window.addEventListener('resize', () => {
    // recalcular después de un pequeño delay para estabilizar layout
    clearTimeout(window.__wheelRecalcTimer);
    window.__wheelRecalcTimer = setTimeout(() => {
      calculateOriginalWidth();
    }, 120);
  });

  // Esperar a que las imágenes (de los originales) carguen para medir correctamente
  function waitForImagesThenStart() {
    const originals = Array.from(track.children).slice(0, originalNodes.length);
    const imgs = originals.flatMap(item => Array.from(item.querySelectorAll('img')));
    let toLoad = imgs.length;

    if (toLoad === 0) {
      calculateOriginalWidth();
      rafId = requestAnimationFrame(step);
      return;
    }

    imgs.forEach(img => {
      if (img.complete) {
        toLoad--;
        if (toLoad === 0) {
          calculateOriginalWidth();
          rafId = requestAnimationFrame(step);
        }
      } else {
        img.addEventListener('load', () => {
          toLoad--;
          if (toLoad === 0) {
            calculateOriginalWidth();
            rafId = requestAnimationFrame(step);
          }
        }, { once: true });
        // también manejar error para no bloquear
        img.addEventListener('error', () => {
          toLoad--;
          if (toLoad === 0) {
            calculateOriginalWidth();
            rafId = requestAnimationFrame(step);
          }
        }, { once: true });
      }
    });
  }

  // Inicializar
  addInteractionHandlers();
  waitForImagesThenStart();

  // Limpieza si se navega fuera
  window.addEventListener('beforeunload', () => {
    if (rafId) cancelAnimationFrame(rafId);
  });

  // Exponer funciones útiles en el elemento para debugging (opcional)
  try {
    carousel.__wheel = {
      pause,
      resume,
      recalc: calculateOriginalWidth,
      getState: () => ({ originalWidth, pos, running })
    };
  } catch (e) {
    // no crítico si falla
  }
});
