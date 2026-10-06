// ===== LOGOUT =====
// Tell the server to drop the session, then go back to the login page.
function logout() {
    sessionUser = null;
    fetch('/api/v1/auth/logout', { method: 'POST', headers: authHeaders() })
        .catch(() => {})
        .finally(() => { window.location.href = '/index.html'; });
}

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
        if (res.status === 401 || res.status === 403) { sessionUser = null; return; }
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

// ===== AUTH BUTTON LOADING STATE =====
function setAuthLoading(row, activeBtn, text) {
    if (!activeBtn.dataset.orig) activeBtn.dataset.orig = activeBtn.innerHTML;
    row.querySelectorAll('button').forEach(b => { b.disabled = true; });
    activeBtn.innerHTML = '<span class="btn-spinner"></span>' + text;
}
function clearAuthLoading(row) {
    row.querySelectorAll('button').forEach(b => {
        b.disabled = false;
        if (b.dataset.orig) b.innerHTML = b.dataset.orig;
    });
}

// ===== LOGIN =====
document.getElementById('loginBtn').addEventListener('click', async () => {
    const email = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value.trim();
    const err = document.getElementById('login-error');
    if (!email || !password) { showMsg(err, 'Please fill in all fields.'); return; }
    const btn = document.getElementById('loginBtn');
    const row = btn.closest('.btn-row');
    setAuthLoading(row, btn, 'Signing in...');
    try {
        const res = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Login failed.'); return; }
        // the browser already holds the session cookie, so we are in
        sessionUser = data.data;
        await routeToGateway();
    } catch (e) { showMsg(err, 'Server error: ' + e.message); }
    finally { clearAuthLoading(row); }
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
    const btn = document.getElementById('registerBtn');
    const row = btn.closest('.btn-row');
    setAuthLoading(row, btn, 'Creating account...');
    try {
        const res = await fetch('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, email, password }) });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Registration failed.'); return; }
        sessionUser = data.data;
        await routeToGateway();
    } catch (e) { showMsg(err, 'Server error: ' + e.message); }
    finally { clearAuthLoading(row); }
});

// ===== INIT =====
// Already signed in? The session cookie is still valid, so go straight where we belong.
loadSessionUser().then(user => { if (user) routeToGateway(); });
