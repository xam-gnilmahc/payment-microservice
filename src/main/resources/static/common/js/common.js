// ===== SHARED STATE =====
let token = localStorage.getItem('jwtToken');

// ===== TOKEN =====
function decodeToken(t) {
    try {
        const payload = JSON.parse(atob(t.split('.')[1]));
        return { userId: payload.userId, email: payload.sub, name: payload.name };
    } catch (e) { return null; }
}

function getUser() { return token ? decodeToken(token) : null; }
function authHeaders() { return { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }; }

// ===== HELPERS =====
function hideAll() { ['step-no-gateway', 'step-checkout', 'step-card', 'step-address', 'step-logs'].forEach(id => { const el = document.getElementById(id); if (el) el.classList.add('hidden'); }); }
function showMsg(el, msg) { el.textContent = msg; el.classList.add('show'); el.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }
