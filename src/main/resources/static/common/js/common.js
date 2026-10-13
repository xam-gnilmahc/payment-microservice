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

// Messages only ever appear in the central notification below - never in the inline banners,
// which are left in the markup but stay empty. The banner/card elements are still handed to
// showMsg so the caller says where the message came from and success vs error is picked from it.
function hideOtherMsgs() {
    document.querySelectorAll('.banner.show').forEach(b => b.classList.remove('show'));
    const card = document.getElementById('card-msg');
    if (card) { card.textContent = ''; card.className = ''; }
}

function showMsg(el, msg) {
    hideOtherMsgs();
    notify(el && el.classList.contains('banner-success') ? 'success' : 'error', msg);
}

// ===== CENTRAL NOTIFICATIONS =====
// One floating popup at the top centre of the window, whatever page you are on - the only place
// a message is shown. Every response the app gives comes through here: sign in, sign up, payment
// success/failure, refunds, addresses, validation.
const NOTIFY_MS = 4500;          // how long a notification stays up
const NOTIFY_MAX = 3;            // at most this many on screen at once

function notify(type, message) {
    if (!message) return;
    let host = document.getElementById('notifyHost');
    if (!host) {
        host = document.createElement('div');
        host.id = 'notifyHost';
        host.className = 'notify-host';
        host.setAttribute('aria-live', 'polite');
        document.body.appendChild(host);
    }

    const isOk = type === 'success';
    const el = document.createElement('div');
    el.className = 'notify ' + (isOk ? 'notify-success' : 'notify-error');
    el.setAttribute('role', isOk ? 'status' : 'alert');

    const icon = document.createElement('span');
    icon.className = 'notify-icon';
    icon.textContent = isOk ? '\u2713' : '!';

    const text = document.createElement('span');
    text.className = 'notify-text';
    text.textContent = message;

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'notify-close';
    close.setAttribute('aria-label', 'Dismiss');
    close.textContent = '\u00d7';
    close.addEventListener('click', () => dismissNotify(el));

    const bar = document.createElement('span');
    bar.className = 'notify-bar';
    bar.style.animationDuration = NOTIFY_MS + 'ms';

    el.appendChild(icon);
    el.appendChild(text);
    el.appendChild(close);
    el.appendChild(bar);
    host.appendChild(el);

    // never crowd the screen - the oldest one still on its way out goes first. Leaving elements
    // stay in the DOM until their fade finishes, so the count only looks at live ones.
    const live = Array.prototype.slice.call(host.children).filter(c => !c.dataset.leaving);
    for (let i = 0; i < live.length - NOTIFY_MAX; i++) dismissNotify(live[i]);

    // let the entry transition run before starting the dismiss timer
    requestAnimationFrame(() => el.classList.add('in'));
    let remaining = NOTIFY_MS;
    let startedAt = Date.now();
    let timer = setTimeout(() => dismissNotify(el), remaining);
    // hovering holds the message (and its countdown line) until the pointer leaves again
    el.addEventListener('mouseenter', () => {
        clearTimeout(timer);
        remaining -= Date.now() - startedAt;
        bar.style.animationPlayState = 'paused';
    });
    el.addEventListener('mouseleave', () => {
        if (el.dataset.leaving) return;
        startedAt = Date.now();
        bar.style.animationPlayState = 'running';
        timer = setTimeout(() => dismissNotify(el), Math.max(remaining, 600));
    });
}

function dismissNotify(el) {
    if (!el || el.dataset.leaving) return;
    el.dataset.leaving = '1';
    el.classList.remove('in');
    el.classList.add('out');
    setTimeout(() => el.remove(), 320);
}

function notifySuccess(message) { notify('success', message); }
function notifyError(message) { notify('error', message); }

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
