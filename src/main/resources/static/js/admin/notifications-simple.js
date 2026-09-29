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

                // Llenar la lista del menú desplegable
                if (list) {
                    if (pendingOrders.length > 0) {
                        list.innerHTML = pendingOrders.map(o => `
                            <div style="padding: 12px 16px; border-bottom: 1px solid #f5f5f5; cursor: pointer; transition: background 0.2s;" 
                                 onmouseover="this.style.background='#fdf2f6'" 
                                 onmouseout="this.style.background='transparent'"
                                 onclick="document.querySelector('.tab-btn[data-tab=\\'pedidos\\']').click(); document.getElementById('notif-dropdown').hidden = true;">
                                <div style="font-weight: 600; color: #222; font-size: 0.9rem;">Nuevo Pedido Web #${o.id}</div>
                                <div style="color: #666; font-size: 0.8rem; margin-top: 4px;">${o.customerName || 'Cliente'} - $${o.totalPrice}</div>
                            </div>
                        `).join('');
                    } else {
                        list.innerHTML = `<div style="padding: 20px; text-align: center; color: #888; font-size: 0.9rem;">No hay pedidos pendientes de confirmación.</div>`;
                    }
                }
            }
        } catch (error) {
            console.error("Error silencioso consultando notificaciones.");
        }
    }

    checkNewOrders();
    setInterval(checkNewOrders, 10000);
});