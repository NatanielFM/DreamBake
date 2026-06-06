const password = document.getElementById('password');
const confirmPassword = document.getElementById('confirmPassword');
const passwordError = document.getElementById('passwordError');
const submitBtn = document.getElementById('register-submit');
const togglePass = document.getElementById('toggle-password');
const passInput = document.getElementById('password');

function validatePasswords() {
    if (password.value !== confirmPassword.value) {
        passwordError.style.display = 'block';
        submitBtn.disabled = true; // Deshabilita el botón si no coinciden
    } else {
        passwordError.style.display = 'none';
        submitBtn.disabled = false; // Habilita si coinciden
    }
}

togglePass.addEventListener('click', () => {
    const type = passInput.getAttribute('type') === 'password' ? 'text' : 'password';
    passInput.setAttribute('type', type);
});

// Escucha cada vez que el usuario escribe en cualquiera de los dos campos
password.addEventListener('input', validatePasswords);
confirmPassword.addEventListener('input', validatePasswords);