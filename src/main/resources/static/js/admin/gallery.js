// /js/admin/gallery.js - Módulo de Galería
document.addEventListener('DOMContentLoaded', () => {

  let dbGallery = [];
  const fileInput = document.getElementById('hidden-file-input');
  
  // Lo hacemos global por si 'products.js' necesita forzar una recarga al abrir el modal picker
  window.loadGalleryFromServer = async function() {
    try {
      const res = await fetch('/api/gallery');
      if (res.ok) {
        dbGallery = await res.json();
        renderGallery('main-gallery-grid', false);
        renderGallery('picker-gallery-grid', true);
      }
    } catch (e) { console.error("Error cargando galería", e); }
  };

  function renderGallery(containerId, isPicker) {
    const container = document.getElementById(containerId);
    if (!container) return;
    
    const uploadBtn = container.querySelector('.upload-item');
    container.innerHTML = '';
    if (uploadBtn) container.appendChild(uploadBtn);

    dbGallery.forEach(img => {
      // En el selector cada foto es un botón (se puede elegir con el teclado); en la galería, solo una foto
      const div = document.createElement(isPicker ? 'button' : 'div');
      div.className = 'gallery-item';
      if (isPicker) { div.type = 'button'; div.setAttribute('aria-label', 'Usar esta foto'); }
      div.innerHTML = `<img src="${img.url}" alt="" loading="lazy">`;
      
      if (isPicker) {
        div.addEventListener('click', () => {
          document.getElementById('prod-image-preview').innerHTML = `<img src="${img.url}" alt="">`;
          document.getElementById('modal-gallery-picker').hidden = true;
        });
      } else {
        // Botón para borrar la foto (solo en la pestaña Galería, no en el selector)
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'btn btn-red btn-icon gallery-delete';
        del.setAttribute('aria-label', 'Borrar esta foto');
        del.title = 'Borrar foto';
        del.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>';
        del.addEventListener('click', () => deleteGalleryImage(img));
        div.appendChild(del);
      }
      container.appendChild(div);
    });
  }

  // Borra la foto (DELETE /api/gallery/{id}). Si un producto la usa, el servidor no deja y explica cuál.
  function deleteGalleryImage(img) {
    window.showConfirmModal('Borrar foto', 'La foto deja de verse en la página de inicio. No se puede deshacer.', 'Borrar', 'btn-red', async () => {
      try {
        const res = await fetch(`/api/gallery/${img.id}`, { method: 'DELETE' });
        if (res.ok) {
          window.loadGalleryFromServer();
        } else {
          const data = await res.json().catch(() => ({}));
          window.showErrorAlert(data.message || 'No se pudo borrar la foto.');
        }
      } catch (e) {
        window.showErrorAlert('No se pudo conectar con el servidor.');
      }
    });
  }

  // Eventos para subir fotos
  const btnUpload1 = document.getElementById('btn-upload-img');
  const btnUpload2 = document.getElementById('btn-gallery-upload-new');
  if(btnUpload1) btnUpload1.addEventListener('click', () => fileInput.click());
  if(btnUpload2) btnUpload2.addEventListener('click', () => fileInput.click());

  const btnPicker = document.getElementById('btn-gallery-img');
  if(btnPicker) {
    btnPicker.addEventListener('click', () => {
      window.loadGalleryFromServer();
      document.getElementById('modal-gallery-picker').hidden = false;
    });
  }

  if(fileInput) {
    fileInput.addEventListener('change', async (e) => {
      if (e.target.files.length > 0) {
        const file = e.target.files[0];
        const formData = new FormData();
        formData.append("file", file);
        // Mientras sube, la tarjeta "Subir foto" muestra que está trabajando
        const uploadTile = document.getElementById('btn-gallery-upload-new');
        if (uploadTile) uploadTile.setAttribute('aria-busy', 'true');

        try {
          const res = await fetch('/api/gallery', { method: 'POST', body: formData });
          if (res.ok) {
            const newImage = await res.json();
            const preview = document.getElementById('prod-image-preview');
            if(preview) preview.innerHTML = `<img src="${newImage.url}">`;
            window.loadGalleryFromServer();
          } else {
            window.showErrorAlert("No se pudo subir la foto. Probá con una imagen JPG, PNG o WEBP.");
          }
        } catch (e) {
          window.showErrorAlert("No se pudo conectar con el servidor para subir la foto.");
        } finally {
          if (uploadTile) uploadTile.removeAttribute('aria-busy');
          fileInput.value = '';   // permite volver a elegir la misma foto
        }
      }
    });
  }

  // Escuchar cuando el usuario entra a la pestaña galería
  document.addEventListener('tabChanged', e => {
    if (e.detail === 'galeria') window.loadGalleryFromServer();
  });

  // Carga inicial
  window.loadGalleryFromServer();
});