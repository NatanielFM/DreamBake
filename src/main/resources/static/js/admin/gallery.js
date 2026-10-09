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

  // ================= PREPARAR LA FOTO ANTES DE SUBIRLA =================
  // Las fotos de un celular pesan 2–5 MB y el iPhone las guarda en formato HEIC, que la
  // mayoría de los navegadores no puede mostrar. Antes de subirlas se achican (1600 px de
  // lado como máximo) y se pasan a JPG: suben rápido, se ven en cualquier navegador y la
  // página de inicio carga más liviana. Las fotos que ya son chicas se suben tal cual.
  const MAX_SIDE = 1600;
  const KEEP_AS_IS_BYTES = 1.5 * 1024 * 1024;

  async function prepareImage(file) {
    if (file.type === 'image/gif') return file;   // un GIF puede ser animado: se sube como está
    try {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.src = url;
      await img.decode();                           // el navegador lee la foto (Safari también lee HEIC)
      URL.revokeObjectURL(url);

      const scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
      const isWebFormat = ['image/jpeg', 'image/png', 'image/webp'].includes(file.type);
      if (isWebFormat && scale === 1 && file.size <= KEEP_AS_IS_BYTES) return file;

      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.naturalWidth * scale);
      canvas.height = Math.round(img.naturalHeight * scale);
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#ffffff';                    // fondo blanco: el JPG no tiene transparencia
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

      const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.85));
      if (!blob) return file;
      const name = (file.name || 'foto').replace(/\.[^.]+$/, '') + '.jpg';
      return new File([blob], name, { type: 'image/jpeg' });
    } catch (err) {
      return file;   // el navegador no la pudo leer: se manda la original y el servidor explica si no sirve
    }
  }

  if(fileInput) {
    fileInput.addEventListener('change', async (e) => {
      if (e.target.files.length > 0) {
        const file = await prepareImage(e.target.files[0]);
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
            // El servidor explica el motivo (formato no permitido, foto demasiado pesada…)
            const data = await res.json().catch(() => ({}));
            window.showErrorAlert(data.message || "No se pudo subir la foto. Probá con una imagen JPG, PNG o WEBP.");
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