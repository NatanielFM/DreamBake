document.addEventListener('DOMContentLoaded', () => {
    // Mobile Menu
    const menuBtn = document.getElementById('menuButton');
    const mobileMenu = document.getElementById('mobileMenu');
    
    if (menuBtn) {
        menuBtn.addEventListener('click', () => {
            const isHidden = mobileMenu.hidden;
            mobileMenu.hidden = !isHidden;
            menuBtn.setAttribute('aria-expanded', !isHidden);
        });
    }

    // User Dropdown
    const accBtn = document.getElementById('accountButton');
    const userMenu = document.getElementById('userMenu');

    if (accBtn) {
        accBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            userMenu.hidden = !userMenu.hidden;
        });

        // Cerrar al hacer clic afuera
        document.addEventListener('click', () => {
            userMenu.hidden = true;
        });
    }
});