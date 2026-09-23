// ===== LOGOUT =====
function logout() { localStorage.clear(); token = null; window.location.href = '/index.html'; }

// ===== ROUTE AFTER LOGIN =====
async function routeToGateway() {
    const user = getUser();
    if (!user) return;
    if (String(user.isSuperAdmin) === '1') {
        window.location.href = '/admin/index.html';
        return;
    }
    try {
        const res = await fetch('/api/v1/payment-gateways/user/' + user.userId + '/enabled', { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { localStorage.clear(); token = null; return; }
        const data = await res.json();
        console.log('[Route] gateway data:', data);
        if (!data.success || !data.data) {
            document.getElementById('loginForm').classList.add('hidden');
            document.getElementById('registerForm').classList.add('hidden');
            document.getElementById('noGatewayMsg').classList.remove('hidden');
            return;
        }
        const title = (data.data.title || '').toLowerCase();
        console.log('[Route] gateway title:', title);
        if (title.includes('authorize')) {
            window.location.href = '/authorize/index.html';
        } else {
            window.location.href = '/stripe/payment.html';
        }
    } catch (e) {
        console.error('Route error:', e);
    }
}

// ===== LOGIN =====
document.getElementById('loginBtn').addEventListener('click', async () => {
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    const err = document.getElementById('login-error');
    if (!email || !password) { showMsg(err, 'Please fill in all fields.'); return; }
    try {
        const res = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Login failed.'); return; }
        token = data.data.token;
        localStorage.setItem('jwtToken', token);
        console.log('[Login] token set, calling routeToGateway');
        console.log('[Login] getUser result:', getUser());
        await routeToGateway();
    } catch (e) { showMsg(err, 'Server error: ' + e.message); }
});

// ===== TOGGLE LOGIN/REGISTER =====
document.getElementById('showRegister').addEventListener('click', (e) => {
    e.preventDefault();
    document.getElementById('loginForm').classList.add('hidden');
    document.getElementById('registerForm').classList.remove('hidden');
    document.getElementById('login-error').classList.remove('show');
});

document.getElementById('showLogin').addEventListener('click', (e) => {
    e.preventDefault();
    document.getElementById('registerForm').classList.add('hidden');
    document.getElementById('loginForm').classList.remove('hidden');
    document.getElementById('reg-error').classList.remove('show');
});

// ===== REGISTER =====
document.getElementById('registerBtn').addEventListener('click', async () => {
    const name = document.getElementById('regName').value.trim();
    const email = document.getElementById('regEmail').value.trim();
    const password = document.getElementById('regPassword').value.trim();
    const err = document.getElementById('reg-error');
    if (!name || !email || !password) { showMsg(err, 'Please fill in all fields.'); return; }
    try {
        const res = await fetch('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, email, password }) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Registration failed.'); return; }
        token = data.data.token;
        localStorage.setItem('jwtToken', token);
        await routeToGateway();
    } catch (e) { showMsg(err, 'Server error: ' + e.message); }
});

// ===== INIT =====
if (token) {
    routeToGateway();
}
