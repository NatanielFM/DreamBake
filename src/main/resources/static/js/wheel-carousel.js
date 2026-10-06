// wheel-carousel.js — Carrusel de la galería de la home.
//
// Se apoya en el scroll nativo del navegador con "scroll-snap" (cada foto
// se acomoda sola al soltar) y le suma:
//   - Flechas anterior / siguiente.
//   - Teclado: con el carrusel enfocado, ← y → avanzan de a una foto; Inicio y Fin van a los extremos.
//   - Arrastre con el mouse en computadora (en el celular ya se desliza con el dedo, con inercia).
//   - Una línea de progreso que muestra cuánto se recorrió.
//
// Contrato con el HTML (index.html):
//   #wheelCarousel (contenedor que hace scroll) > .gallery-track > .gallery-slide
//   [data-carousel-prev], [data-carousel-next], .carousel-progress span

document.addEventListener('DOMContentLoaded', () => {
  const carousel = document.getElementById('wheelCarousel');
  if (!carousel) return;

  const track = carousel.querySelector('.gallery-track');
  if (!track) return;

  const slides = Array.from(track.children);
  const prevBtn = document.querySelector('[data-carousel-prev]');
  const nextBtn = document.querySelector('[data-carousel-next]');
  const progress = document.querySelector('.carousel-progress span');
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  // Cada foto se anuncia como "Foto 3 de 9" para lectores de pantalla
  slides.forEach((slide, i) => {
    slide.setAttribute('aria-label', `Foto ${i + 1} de ${slides.length}`);
  });

  // Distancia de una foto + el espacio entre fotos
  function slideStep() {
    if (!slides[0]) return carousel.clientWidth;
    const gap = parseFloat(getComputedStyle(track).columnGap) || 0;
    return slides[0].getBoundingClientRect().width + gap;
  }

  function scrollBySlides(count) {
    carousel.scrollBy({
      left: count * slideStep(),
      behavior: reduceMotion.matches ? 'auto' : 'smooth'
    });
  }

  if (prevBtn) prevBtn.addEventListener('click', () => scrollBySlides(-1));
  if (nextBtn) nextBtn.addEventListener('click', () => scrollBySlides(1));

  carousel.addEventListener('keydown', (e) => {
    const max = carousel.scrollWidth - carousel.clientWidth;
    if (e.key === 'ArrowRight') { e.preventDefault(); scrollBySlides(1); }
    else if (e.key === 'ArrowLeft') { e.preventDefault(); scrollBySlides(-1); }
    else if (e.key === 'Home') { e.preventDefault(); carousel.scrollTo({ left: 0 }); }
    else if (e.key === 'End') { e.preventDefault(); carousel.scrollTo({ left: max }); }
  });

  /* ---------- Estado de las flechas y línea de progreso ---------- */
  let ticking = false;
  function updateState() {
    const max = carousel.scrollWidth - carousel.clientWidth;
    const pos = carousel.scrollLeft;

    if (prevBtn) prevBtn.disabled = pos <= 2;
    if (nextBtn) nextBtn.disabled = pos >= max - 2;

    if (progress) {
      // La barra arranca mostrando la parte visible y llega a 1 al final
      const visible = max > 0 ? carousel.clientWidth / carousel.scrollWidth : 1;
      const ratio = max > 0 ? pos / max : 1;
      progress.style.setProperty('--progress', (visible + (1 - visible) * ratio).toFixed(3));
    }
    ticking = false;
  }

  carousel.addEventListener('scroll', () => {
    if (!ticking) {
      ticking = true;
      requestAnimationFrame(updateState);
    }
  }, { passive: true });
  window.addEventListener('resize', updateState);
  updateState();

  /* ---------- Arrastre con el mouse (solo puntero de mouse) ---------- */
  let dragging = false;
  let moved = false;
  let startX = 0;
  let startScroll = 0;

  carousel.addEventListener('pointerdown', (e) => {
    if (e.pointerType !== 'mouse' || e.button !== 0) return;
    dragging = true;
    moved = false;
    startX = e.clientX;
    startScroll = carousel.scrollLeft;
    carousel.setPointerCapture(e.pointerId);
  });

  carousel.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dx = e.clientX - startX;
    if (!moved && Math.abs(dx) > 4) {
      moved = true;
      carousel.classList.add('is-dragging'); // desactiva el snap mientras se arrastra
    }
    if (moved) carousel.scrollLeft = startScroll - dx;
  });

  function endDrag(e) {
    if (!dragging) return;
    dragging = false;
    if (carousel.hasPointerCapture && carousel.hasPointerCapture(e.pointerId)) {
      carousel.releasePointerCapture(e.pointerId);
    }
    if (moved) {
      // Al soltar se vuelve a activar el snap y se acomoda en la foto más cercana
      carousel.classList.remove('is-dragging');
      const step = slideStep();
      const target = Math.round(carousel.scrollLeft / step) * step;
      carousel.scrollTo({ left: target, behavior: reduceMotion.matches ? 'auto' : 'smooth' });
    }
  }
  carousel.addEventListener('pointerup', endDrag);
  carousel.addEventListener('pointercancel', endDrag);

  // Si hubo arrastre, el click que viene después no debe disparar nada
  carousel.addEventListener('click', (e) => {
    if (moved) {
      e.preventDefault();
      e.stopPropagation();
      moved = false;
    }
  }, true);
});
