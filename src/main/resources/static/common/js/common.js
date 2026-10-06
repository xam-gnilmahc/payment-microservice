// ===== SHARED STATE =====
// There is no token in the browser any more. The server keeps who is signed in in its session and
// the browser sends the session cookie by itself, so requests only need the JSON content type.
// sessionUser is just a cache of what /api/v1/auth/me answered, for the page to display.
let sessionUser = null;

async function loadSessionUser() {
    try {
        const res = await fetch('/api/v1/auth/me', { headers: authHeaders() });
        if (res.status === 401) { sessionUser = null; return null; }
        const data = await res.json();
        sessionUser = data.success ? data.data : null;
    } catch (e) { sessionUser = null; }
    return sessionUser;
}

function getUser() { return sessionUser; }
function authHeaders() { return { 'Content-Type': 'application/json' }; }

// ===== HELPERS =====
function hideAll() { document.body.classList.remove('wide-header'); ['step-no-gateway', 'step-checkout', 'step-card', 'step-address', 'step-logs'].forEach(id => { const el = document.getElementById(id); if (el) el.classList.add('hidden'); }); }
function showMsg(el, msg) { el.textContent = msg; el.classList.add('show'); el.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }
