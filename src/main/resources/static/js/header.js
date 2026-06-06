document.addEventListener('DOMContentLoaded', () => {
    // 1. Menú Mobile (Toggle)
    const menuBtn = document.getElementById('menuButton');
    const mobileMenu = document.getElementById('mobileMenu');
    
    if (menuBtn && mobileMenu) {
        menuBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const isHidden = mobileMenu.hidden;
            mobileMenu.hidden = !isHidden;
            menuBtn.setAttribute('aria-expanded', !isHidden);
        });
    }

    // 2. Dropdown "Mi Cuenta"
    const accBtn = document.getElementById('accountBtn');
    const userMenu = document.getElementById('userMenu');

    if (accBtn && userMenu) {
        accBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            userMenu.hidden = !userMenu.hidden;
        });
    }

    // 3. Cerrar menús al hacer clic en cualquier parte fuera de ellos
    document.addEventListener('click', (e) => {
        // Cierra el menú mobile
        if (mobileMenu && !mobileMenu.hidden && !menuBtn.contains(e.target) && !mobileMenu.contains(e.target)) {
            mobileMenu.hidden = true;
            menuBtn.setAttribute('aria-expanded', 'false');
        }
        
        // Cierra el menú de usuario
        if (userMenu && !userMenu.hidden && !accBtn.contains(e.target) && !userMenu.contains(e.target)) {
            userMenu.hidden = true;
        }
    });
});