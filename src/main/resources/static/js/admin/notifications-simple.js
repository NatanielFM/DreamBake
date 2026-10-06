document.addEventListener('DOMContentLoaded', () => {
    const btnBell = document.getElementById('btn-notifications');
    const dropdown = document.getElementById('notif-dropdown');
    const badge = document.getElementById('notif-badge');
    const list = document.getElementById('notif-list');

    // 1. Lógica de Apertura y Cierre del Menú
    if (btnBell && dropdown) {
        btnBell.addEventListener('click', (e) => {
            e.stopPropagation();
            dropdown.hidden = !dropdown.hidden;
        });
    }

    // Cerrar al hacer clic fuera del menú o la campana
    document.addEventListener('click', (e) => {
        if (dropdown && !dropdown.hidden) {
            const isClickInside = dropdown.contains(e.target);
            const isClickOnBell = btnBell && btnBell.contains(e.target);
            if (!isClickInside && !isClickOnBell) {
                dropdown.hidden = true;
            }
        }
    });

    // 2. Consulta a la Base de Datos
    async function checkNewOrders() {
        try {
            const res = await fetch('/api/orders');
            if (res.ok) {
                const orders = await res.json();
                const pendingOrders = orders.filter(o => o.status === 'PENDING');

                // Actualizar la burbuja roja
                if (badge) {
                    badge.innerText = pendingOrders.length;
                    badge.style.display = pendingOrders.length > 0 ? 'inline-flex' : 'none';
                }

                // Cada aviso es un botón: al tocarlo se abre la pestaña Pedidos (estilos en admin.css)
                if (list) {
                    if (pendingOrders.length > 0) {
                        const esc = (t) => String(t ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
                        list.innerHTML = pendingOrders.map(o => `
                            <button type="button" class="notif-item" data-open-orders>
                                <strong>Pedido web #${o.id}</strong>
                                <span>${esc(o.customerName || 'Cliente')} · $${Number(o.totalPrice || 0).toLocaleString('es-AR')}</span>
                            </button>
                        `).join('');
                    } else {
                        list.innerHTML = `<p class="notif-empty">No hay pedidos nuevos para confirmar.</p>`;
                    }
                }
            }
        } catch (error) {
            console.error("Error silencioso consultando notificaciones.");
        }
    }

    // Tocar un aviso abre la pestaña Pedidos y cierra el menú
    if (list) {
        list.addEventListener('click', (e) => {
            if (!e.target.closest('[data-open-orders]')) return;
            document.querySelector('.tab-btn[data-tab="pedidos"]')?.click();
            if (dropdown) dropdown.hidden = true;
        });
    }

    checkNewOrders();
    setInterval(checkNewOrders, 10000);
});