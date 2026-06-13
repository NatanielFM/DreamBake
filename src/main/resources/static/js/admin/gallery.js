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
      const div = document.createElement('div');
      div.className = 'gallery-item';
      div.innerHTML = `<img src="${img.url}">`;
      
      if (isPicker) {
        div.addEventListener('click', () => {
          document.getElementById('prod-image-preview').innerHTML = `<img src="${img.url}">`;
          document.getElementById('modal-gallery-picker').hidden = true;
        });
      }
      container.appendChild(div);
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

        try {
          const res = await fetch('/api/gallery', { method: 'POST', body: formData });
          if (res.ok) {
            const newImage = await res.json();
            const preview = document.getElementById('prod-image-preview');
            if(preview) preview.innerHTML = `<img src="${newImage.url}">`;
            window.loadGalleryFromServer();
          } else {
            window.showErrorAlert("No se pudo subir la imagen.");
          }
        } catch (e) {
          window.showErrorAlert("Fallo en la conexión al servidor de imágenes.");
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