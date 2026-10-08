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

// ===== LOST SESSION =====
// A blocked account, an expired session and a sign-out on another tab all come back as 401 on the
// next API call. Without this the page just goes quiet - no data, no explanation - and you only get
// sent to the login page on the next refresh. So any 401 carries the server's message over to the
// login page, which shows it. The login/register/logout calls are left alone: they are handled
// where they are made, and the login page checks /auth/me itself.
const AUTH_CALL = /\/api\/v1\/auth\/(login|register|logout)/;

function onLoginPage() {
    const p = window.location.pathname;
    return p === '/' || p === '' || /\/index\.html$/.test(p);
}

function sendToLogin(message) {
    if (onLoginPage()) return;
    try { if (message) sessionStorage.setItem('authNotice', message); } catch (e) {}
    window.location.href = '/index.html';
}

const _fetch = window.fetch;
window.fetch = function () {
    const req = arguments[0];
    return _fetch.apply(this, arguments).then(async function (res) {
        if (res.status !== 401) return res;
        const url = typeof req === 'string' ? req : (req && req.url) || '';
        if (AUTH_CALL.test(url)) return res;
        // read the body on a copy before the caller does, so the message is saved first -
        // otherwise the caller can navigate away while we are still parsing
        let message = null;
        try { const body = await res.clone().json(); message = body && body.message; } catch (e) {}
        sendToLogin(message);
        return res;
    });
};
