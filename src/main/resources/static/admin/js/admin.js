// ===== ADMIN CORE =====
let currentSection = 'dashboard';
let userFilter = null; // { userId, email } when drilled into a user

// isSuperAdmin check. The signed-in user comes from /api/v1/auth/me now, so this has to wait for
// that answer instead of reading it out of localStorage like a token used to be.
let adminUser = null;

async function initAdmin() {
    adminUser = await loadSessionUser();
    if (!adminUser || String(adminUser.isSuperAdmin) !== '1') {
        window.location.href = '/index.html';
        return;
    }
    document.getElementById('adminEmail').textContent = adminUser.email || adminUser.name;
    loadDashboard();
    // the segmented thumb can only be measured once the section is laid out
    requestAnimationFrame(() => requestAnimationFrame(syncFilterUI));
}

function logout() {
    sessionUser = null;
    fetch('/api/v1/auth/logout', { method: 'POST', headers: authHeaders() })
        .catch(() => {})
        .finally(() => { window.location.href = '/index.html'; });
}

// ===== SIDEBAR TOGGLE =====
function isMobileNav() { return window.matchMedia('(max-width: 860px)').matches; }




(function initSidebar() {
    try {
        if (localStorage.getItem('admSidebar') === '1' && !isMobileNav()) {
            document.getElementById('adminLayout')?.classList.add('sidebar-collapsed');
        }
    } catch (e) {}
    // close mobile drawer after nav click
    document.querySelectorAll('.admin-nav-item').forEach(btn => {
        btn.addEventListener('click', () => { if (isMobileNav()) closeSidebar(); });
    });
    window.addEventListener('resize', () => {
        if (!isMobileNav()) closeSidebar();
    });
})();

function showSection(section, el) {
    currentSection = section;
    if (el) {
        document.querySelectorAll('.admin-nav-item').forEach(b => b.classList.remove('active'));
        el.classList.add('active');
    }
    document.querySelectorAll('.admin-section').forEach(s => s.classList.remove('active'));
    document.getElementById('section-' + section).classList.add('active');

    if (section === 'dashboard') loadDashboard();
    if (section === 'gateways') loadGateways();
    if (section === 'users') loadUsers();
    if (section === 'all-logs') loadAllLogs(0);
    if (section === 'refund-logs') loadAdminRefundLogs();
}

function paymentBadge(s) {
    if (s === 'INITIATED' || s === '0') return '<span class="status-badge initiated">Initiated</span>';
    if (s === 'PROCESSING' || s === '1') return '<span class="status-badge processing">Processing</span>';
    if (s === 'SUCCEEDED' || s === '2') return '<span class="status-badge success">Succeeded</span>';
    if (s === 'FAILED' || s === '3') return '<span class="status-badge error">Failed</span>';
    return '<span class="status-badge pending">' + s + '</span>';
}

function refundBadge(s) {
    if (s === '0') return '<span class="status-badge pending">Pending</span>';
    if (s === '1') return '<span class="status-badge success">Success</span>';
    if (s === '2') return '<span class="status-badge error">Failed</span>';
    return '<span class="status-badge pending">' + s + '</span>';
}

function fmtDate(d) {
    if (!d) return '-';
    return new Date(d).toLocaleString();
}

function esc(s) {
    if (s == null) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}

// ===== DASHBOARD =====
// Minimal neutral palette + single teal accent (status colors only in badges/charts)
const METHOD_COLORS = {
    card: '#0f766e', CARD: '#0f766e',
    google_pay: '#0d9488', GOOGLE_PAY: '#0d9488', gpay: '#0d9488',
    apple_pay: '#14b8a6', APPLE_PAY: '#14b8a6', applepay: '#14b8a6',
    link: '#2dd4bf', LINK: '#2dd4bf',
    amazon_pay: '#115e59', AMAZON_PAY: '#115e59',
    sepa_debit: '#0f766e', SEPA_DEBIT: '#0f766e',
    us_bank_account: '#134e4a', ACH: '#134e4a',
    cashapp: '#5eead4', Cashapp: '#5eead4',
    Unknown: '#cbd5e1', unknown: '#cbd5e1', null: '#e2e8f0'
};
const FALLBACK_COLORS = ['#0f766e', '#0d9488', '#14b8a6', '#115e59', '#134e4a', '#2dd4bf', '#0f766e', '#14b8a6', '#64748b', '#cbd5e1'];
const STATUS_COLORS = {
    SUCCEEDED: '#059669', Succeeded: '#059669', succeeded: '#059669',
    FAILED: '#e11d48', Failed: '#e11d48', failed: '#e11d48',
    PROCESSING: '#d97706', Processing: '#d97706', processing: '#d97706',
    INITIATED: '#0f766e', Initiated: '#0f766e', initiated: '#0f766e',
    REFUNDED: '#7c3aed', Refunded: '#7c3aed',
    Unknown: '#d4d4d8'
};

let dashboardCharts = { methods: null, status: null, transactions: null, revenue: null };
let dashboardLoading = false;
let dashCustomersLoaded = false;

async function ensureDashCustomers() {
    if (dashCustomersLoaded) return;
    try {
        const res = await fetch('/api/v1/admin/users', { headers: authHeaders() });
        const data = await res.json();
        if (!data.success || !data.data) return;
        const sel = document.getElementById('dashCustomer');
        if (!sel) return;
        const current = sel.value;
        sel.innerHTML = '<option value="">All customers</option>' +
            data.data.map(u => `<option value="${u.id}">${esc(u.name || u.email || ('User #' + u.id))}</option>`).join('');
        sel.value = current;
        dashCustomersLoaded = true;
    } catch (e) { console.error(e); }
}

function fmtMoney(n) {
    return '$' + (Number(n) || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Chart axis labels: thousands separators, and never more than 2 decimals. Sums coming back from
// MySQL are BigDecimal, so without this an axis can read $1234.5600000000001.
function fmtAxisMoney(n) {
    const v = Number(n);
    if (!Number.isFinite(v)) return '0';
    return v.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

// Stat tiles have ~150px of width, so a full $1,234,567.89 wraps mid-number and looks broken.
// Big values get compact notation instead (the exact figure stays in the title attribute), which is
// what dashboards normally do. Everything under 100k is shown exactly.
function fmtStatMoney(n) {
    const v = Number(n) || 0;
    const abs = Math.abs(v);
    if (abs >= 1e9) return '$' + (v / 1e9).toFixed(2).replace(/\.00$/, '') + 'B';
    if (abs >= 1e6) return '$' + (v / 1e6).toFixed(2).replace(/\.00$/, '') + 'M';
    if (abs >= 1e5) return '$' + (v / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return fmtMoney(v);
}

function pctDelta(cur, prev) {
    const c = Number(cur) || 0;
    const p = Number(prev) || 0;
    if (p === 0 && c === 0) return { text: '—', cls: 'flat', up: false };
    if (p === 0) return { text: 'new', cls: 'up', up: true };
    const d = ((c - p) / p) * 100;
    const sign = d > 0 ? '+' : '';
    return {
        text: sign + d.toFixed(1) + '%',
        cls: d > 0 ? 'up' : d < 0 ? 'down' : 'flat',
        up: d > 0
    };
}

function setDelta(id, cur, prev, invert) {
    const el = document.getElementById(id);
    if (!el) return;
    const d = pctDelta(cur, prev);
    let cls = d.cls;
    if (invert && d.cls !== 'flat') cls = d.up ? 'up bad' : 'down good';
    el.innerHTML = `<span class="delta ${cls}">${esc(d.text)}</span>`;
    el.title = 'Previous period: ' + (prev ?? 0);
}

function formatPeriod(key, grain) {
    if (!key || key === 'unknown') return 'Unknown';
    if (grain === 'year') return String(key);
    if (grain === 'month') {
        const [y, m] = key.split('-');
        const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
        return (months[parseInt(m, 10) - 1] || m) + ' ' + y;
    }
    if (grain === 'week') {
        // keys like "2026-W26" → "Week 26" (current year) or "Week 26 · 2025"
        const parts = String(key).split('-W');
        const year = parts[0];
        const week = parts[1] || '';
        const nowYear = String(new Date().getFullYear());
        if (!week) return String(key);
        return year === nowYear ? 'Week ' + week : 'Week ' + week + ' · ' + year;
    }
    const dt = new Date(key + 'T00:00:00');
    if (isNaN(dt)) return key;
    return dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

function grainLabel(g) {
    return ({ day: 'Daily', week: 'Weekly', month: 'Monthly', year: 'Yearly' })[g] || g;
}

// ===== FILTER BAR STATE =====
// One window selector drives the dates, so there is no preset/month/year/date-picker duplication.
// Default is the last 3 months, sliced by day.
const dashState = { window: '3m', grain: 'day', custom: false };

function isoDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}

// Turns the chosen window into a start/end pair. Anything other than custom or all-time is
// computed here, and always sent as range=custom, which keeps the backend's previous-period
// comparison correct for every window length.
function currentWindow() {
    const today = new Date();
    const end = isoDate(today);
    if (dashState.window === 'custom') {
        const s = document.getElementById('dashStart');
        const e = document.getElementById('dashEnd');
        return { start: s ? s.value : '', end: e ? e.value : '' };
    }
    if (dashState.window === 'all') return { start: '2020-01-01', end: end };
    const days = { '30d': 30, '3m': 90, '6m': 182, '12m': 365 }[dashState.window] || 90;
    const start = new Date(today.getTime() - (days - 1) * 86400000);
    return { start: isoDate(start), end: end };
}

function moveThumb(groupId, btn) {
    const thumb = document.getElementById(groupId);
    if (!thumb || !btn) return;
    thumb.style.width = btn.offsetWidth + 'px';
    thumb.style.transform = 'translateX(' + (btn.offsetLeft - 2) + 'px)';
    thumb.style.opacity = '1';
}

function syncFilterUI() {
    const custom = document.getElementById('customRange');
    if (custom) custom.classList.toggle('open', !!dashState.custom);
    document.querySelectorAll('.seg-btn[data-grain]').forEach(b => {
        const on = b.dataset.grain === dashState.grain;
        b.classList.toggle('on', on);
        if (on) moveThumb('grainThumb', b);
    });
}

function onWindowChange(value) {
    const sel = document.getElementById('dashWindow');
    if (typeof value === 'string' && sel) sel.value = value;
    const chosen = sel ? sel.value : '3m';
    dashState.window = chosen;
    dashState.custom = (chosen === 'custom');
    if (dashState.custom) {
        // seed the pickers so they are never blank when revealed
        const s = document.getElementById('dashStart');
        const e = document.getElementById('dashEnd');
        const w = currentWindow();
        if (s && !s.value) s.value = w.start || isoDate(new Date(Date.now() - 89 * 86400000));
        if (e && !e.value) e.value = w.end;
    }
    syncFilterUI();
    loadDashboard();
}

function setGrain(value) {
    dashState.grain = value;
    syncFilterUI();
    loadDashboard();
}

function onDashDateChange() {
    const s = document.getElementById('dashStart');
    const e = document.getElementById('dashEnd');
    if (s && e && s.value && e.value && s.value > e.value) e.value = s.value;
    dashState.window = 'custom';
    dashState.custom = true;
    const sel = document.getElementById('dashWindow');
    if (sel) sel.value = 'custom';
    syncFilterUI();
    loadDashboard();
}

addEventListener('resize', () => syncFilterUI());

function renderBreakdown(series, grain) {
    const body = document.getElementById('breakdownBody');
    const sub = document.getElementById('breakdownSub');
    if (sub) sub.textContent = grainLabel(grain) + ' performance · click range/grain to re-slice';
    if (!series || !series.length) {
        body.innerHTML = '<tr><td colspan="6" class="admin-empty">No data in this range.</td></tr>';
        return;
    }
    let tPay = 0, tSuc = 0, tFail = 0, tRev = 0;
    series.forEach(p => {
        tPay += p.payments || 0;
        tSuc += p.succeeded || 0;
        tFail += p.failed || 0;
        tRev += p.revenue || 0;
    });
    const rows = series.map(p => {
        const rate = Number(p.successRate) || 0;
        const rateCls = rate >= 80 ? 'high' : rate >= 50 ? 'mid' : 'low';
        return `<tr>
            <td class="period-cell">${esc(formatPeriod(p.period, grain))}</td>
            <td class="num">${p.payments ?? 0}</td>
            <td class="num" style="color:#047857;font-weight:600;">${p.succeeded ?? 0}</td>
            <td class="num" style="color:#be123c;font-weight:600;">${p.failed ?? 0}</td>
            <td class="num"><span class="rate-bar ${rateCls}">${rate.toFixed(1)}%</span></td>
            <td class="num" style="font-weight:600;">${fmtMoney(p.revenue)}</td>
        </tr>`;
    }).join('');
    const tRate = tPay > 0 ? (tSuc * 100 / tPay) : 0;
    // The same rate-bar pill the rows use, so the percentage occupies the same box and lines up
    // under the column instead of sitting loose as plain text.
    const tRateCls = tRate >= 80 ? 'high' : tRate >= 50 ? 'mid' : 'low';
    body.innerHTML = rows;

    // The Total row is appended as a real <tfoot> on the table. Writing "</tbody><tfoot>" into the
    // tbody's innerHTML does not survive the HTML parser, which silently drops it.
    const table = body.closest('table');
    if (table) {
        const oldFoot = table.querySelector('tfoot');
        if (oldFoot) oldFoot.remove();
        const foot = document.createElement('tfoot');
        foot.innerHTML = `<tr class="row-total">
            <td>Total</td>
            <td class="num">${tPay}</td>
            <td class="num">${tSuc}</td>
            <td class="num">${tFail}</td>
            <td class="num"><span class="rate-bar ${tRateCls}">${tRate.toFixed(1)}%</span></td>
            <td class="num">${fmtMoney(tRev)}</td>
        </tr>`;
        table.appendChild(foot);
    }
}

function renderDashLogs(d) {
    const body = document.getElementById('dashLogsBody');
    const countEl = document.getElementById('dashLogsCount');
    const subEl = document.getElementById('dashLogsSub');
    if (!body) return;
    const logs = Array.isArray(d.paymentLogs) ? d.paymentLogs : [];
    const total = Number(d.paymentLogsTotal);
    // The panel only ships the newest 100 rows, so say that instead of implying this is everything.
    if (countEl) {
        countEl.textContent =
            (Number.isFinite(total) && total > logs.length)
                ? 'Latest ' + logs.length + ' of ' + total.toLocaleString()
                : logs.length + (logs.length === 1 ? ' log' : ' logs');
        const viewAll = document.getElementById('dashLogsViewAll');
        if (viewAll) viewAll.hidden = !(Number.isFinite(total) && total > logs.length);
    }
    if (subEl) {
        const custSel = document.getElementById('dashCustomer');
        const custName = custSel && custSel.value
            ? (custSel.options[custSel.selectedIndex]?.text || 'selected customer')
            : 'all customers';
        subEl.textContent = (d.rangeStart || '?') + ' → ' + (d.rangeEnd || '?') + ' · ' + custName;
    }
    if (!logs.length) {
        body.innerHTML = '<tr><td colspan="7" class="admin-empty">No payment logs in this range.</td></tr>';
        return;
    }
    body.innerHTML = logs.map(l => `
        <tr>
            <td>${l.id}</td>
            <td>${esc(l.email) || (l.customerId ? 'User #' + l.customerId : '-')}</td>
            <td>${esc(l.paymentMethod) || '-'}</td>
            <td class="num" style="font-weight:600;white-space:nowrap;">${l.currency ? String(l.currency).toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
            <td>${paymentBadge(l.status)}</td>
            <td style="color:${l.failureCode ? '#be123c' : '#71717a'}">${esc(l.failureCode) || '-'}</td>
            <td class="ta-r" style="white-space:nowrap;color:#71717a;">${fmtDate(l.createdAt)}</td>
        </tr>
    `).join('');
}

async function loadDashboard() {
    if (dashboardLoading) return;
    dashboardLoading = true;
    await ensureDashCustomers();
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    const grain = dashState.grain;
    const customerSel = document.getElementById('dashCustomer');
    const customerId = customerSel ? customerSel.value : '';

    const win = currentWindow();
    let url = '/api/v1/admin/dashboard?grain=' + grain;
    if (win.start && win.end) {
        url += '&range=custom&startDate=' + encodeURIComponent(win.start)
             + '&endDate=' + encodeURIComponent(win.end);
    } else {
        url += '&range=' + (dashState.window === 'all' ? 'all' : 'week');
    }
    if (customerId) url += '&customerId=' + customerId;
    try {
        const res = await fetch(url, { headers: authHeaders() });
        const data = await res.json();
        if (!data.success) { dashboardLoading = false; return; }
        const d = data.data;
        const prev = d.previous || {};

        // Keep inputs in sync with resolved window
        if (startEl && d.rangeStart) startEl.value = d.rangeStart;
        if (endEl && d.rangeEnd) endEl.value = d.rangeEnd && d.rangeEnd.length >= 10 ? d.rangeEnd.slice(0, 10) : d.rangeEnd;

        const rangeText = document.getElementById('dashRangeText');
        if (rangeText) {
            rangeText.textContent = (d.rangeStart || '?') + ' → ' + (d.rangeEnd || '?');
        }

        animateStat(document.getElementById('statTotal'), d.totalPayments);
        animateStat(document.getElementById('statSucceeded'), d.succeeded);
        animateStat(document.getElementById('statFailed'), d.failed);
        animateStat(document.getElementById('statRefunds'), d.totalRefunds);
        const refundAmtEl = document.getElementById('statRefundAmount');
        if (refundAmtEl) {
            refundAmtEl.textContent = fmtStatMoney(d.refundedAmount);
            refundAmtEl.title = fmtMoney(d.refundedAmount);
        }
        const refundHint = document.getElementById('statRefundHint');
        const refundCard = document.getElementById('statRefundAmount');
        if (refundCard) refundCard.parentElement.title = 'total amount refunded';
        if (refundHint) {
            const shown = refundAmtEl ? refundAmtEl.textContent : '';
            const exact = fmtMoney(d.refundedAmount);
            refundHint.textContent = '';
        }

        const revenue = Number(d.revenue) || 0;
        const revenueEl = document.getElementById('statRevenue');
        if (revenueEl) revenueEl.title = fmtMoney(revenue);
        animateStat(revenueEl, revenue, true);

        renderSparklines(d);

        // revenue delta against the previous window
        const deltaEl = document.getElementById('statRevenueDelta');
        if (deltaEl) {
            const prevRev = Number(prev.revenue) || 0;
            if (prevRev > 0) {
                const pct = ((revenue - prevRev) / prevRev) * 100;
                const up = pct >= 0;
                deltaEl.className = 'stat-delta ' + (up ? 'up' : 'down');
                deltaEl.textContent = (up ? '▲ ' : '▼ ') + Math.abs(pct).toFixed(1) + '% vs prev';
            } else {
                deltaEl.className = 'stat-delta';
                deltaEl.textContent = '';
            }
        }

        const chip = document.getElementById('compareChip');
        if (chip && prev.start) chip.textContent = 'vs ' + prev.start + ' → ' + prev.end;

        const g = d.grain || 'auto';
        const txnSub = document.getElementById('txnChartSub');
        const revSub = document.getElementById('revChartSub');
        if (txnSub) txnSub.textContent = grainLabel(g) + ' payment volume';
        if (revSub) revSub.textContent = grainLabel(g) + ' succeeded revenue';

        renderDashboardCharts(d);
        renderBreakdown(d.series || [], g);
        renderDashLogs(d);
    } catch (e) { console.error(e); }
    finally { dashboardLoading = false; }
}

function isoDate(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
}





// ---- FILTER BAR ----



// ===== SKELETON ROWS =====
// A loading table shows shimmering bars rather than the word "Loading...", which reads as a
// finished table with a note in it.
function skeletonRows(body, cols, rows) {
    if (!body) return;
    body.innerHTML = Array.from({ length: rows || 6 })
        .map((_, r) => `<tr><td colspan="${cols}"><span class="skeleton-row">`
            + `<span class="skeleton-bar" style="width:${[92, 74, 58, 81, 66, 88][r % 6]}%"></span>`
            + '</span></td></tr>')
        .join('');
}

// ===== METHOD RANKED LIST =====
// Replaces a horizontal bar chart whose long labels ate a third of the card. Each row shows the
// share of volume, so the ranking is readable without reading an axis.
function renderMethodList(entries, colors, d) {
    const box = document.getElementById('methodsList');
    if (!box) return;
    if (!entries.length) {
        box.innerHTML = '<div class="admin-empty">No payment methods in this range.</div>';
        return;
    }
    const total = entries.reduce((sum, e) => sum + (Number(e[1]) || 0), 0) || 1;
    box.innerHTML = entries.map(([key, value], i) => {
        const n = Number(value) || 0;
        const pct = (n / total) * 100;
        const color = colors[i] || '#cbd5e1';
        return `<div class="method-row">
            <span class="method-dot" style="background:${color}"></span>
            <span class="method-name">${esc(prettyLabel(key))}</span>
            <span class="method-track"><span class="method-fill" style="width:${pct.toFixed(1)}%;background:${color}"></span></span>
            <span class="method-count">${n.toLocaleString()}</span>
            <span class="method-pct">${pct.toFixed(1)}%</span>
        </div>`;
    }).join('');
}

// ===== TOASTS =====
// One floating message at the top centre of the window, whatever page you are on. Replaces the
// inline banners, which sat inside the page and pushed the layout around as they appeared.
const TOAST_MS = 1000;          // how long a toast stays up
const TOAST_MAX = 4;            // never let a burst of messages fill the screen

function toast(type, message) {
    if (!message) return;
    let host = document.getElementById('adminToasts');
    if (!host) {
        host = document.createElement('div');
        host.id = 'adminToasts';
        host.className = 'toast-host';
        document.body.appendChild(host);
    }
    while (host.children.length >= TOAST_MAX) host.removeChild(host.firstChild);

    const el = document.createElement('div');
    el.className = 'toast toast-' + (type === 'error' ? 'error' : 'success');
    el.setAttribute('role', type === 'error' ? 'alert' : 'status');

    const icon = document.createElement('span');
    icon.className = 'toast-icon';
    icon.textContent = type === 'error' ? '!' : '\u2713';

    const text = document.createElement('span');
    text.className = 'toast-text';
    text.textContent = message;

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'toast-close';
    close.setAttribute('aria-label', 'Dismiss');
    close.textContent = '\u00d7';
    close.addEventListener('click', () => dismissToast(el));

    el.appendChild(icon);
    el.appendChild(text);
    el.appendChild(close);
    host.appendChild(el);

    // let the entry transition run before starting the dismiss timer
    requestAnimationFrame(() => el.classList.add('in'));
    const timer = setTimeout(() => dismissToast(el), TOAST_MS);
    el.addEventListener('mouseenter', () => clearTimeout(timer));
    el.addEventListener('mouseleave', () => setTimeout(() => dismissToast(el), 400));
}

function dismissToast(el) {
    if (!el || el.dataset.leaving) return;
    el.dataset.leaving = '1';
    el.classList.remove('in');
    el.classList.add('out');
    el.addEventListener('transitionend', () => el.remove(), { once: true });
    setTimeout(() => el.remove(), 400);
}

// success and error helper used across the admin pages
function notifySuccess(msg) { toast('success', msg); }
function notifyError(msg) { toast('error', msg); }

// ===== SPARKLINES =====
// Small inline SVG trend lines drawn from the per-period data the API already returns. No chart
// library involved, so they cost nothing and stay crisp at any card size.
function sparkPath(values, w, h, pad) {
    const nums = (values || []).map(v => Number(v) || 0);
    if (nums.length < 2) return { line: '', area: '' };
    const max = Math.max.apply(null, nums);
    const min = Math.min.apply(null, nums);
    const span = (max - min) || 1;
    const stepX = (w - pad * 2) / (nums.length - 1);
    const pts = nums.map((v, i) => {
        const x = pad + i * stepX;
        const y = h - pad - ((v - min) / span) * (h - pad * 2);
        return [Math.round(x * 10) / 10, Math.round(y * 10) / 10];
    });
    const line = pts.map((p, i) => (i ? 'L' : 'M') + p[0] + ' ' + p[1]).join(' ');
    const area = line + ' L' + pts[pts.length - 1][0] + ' ' + (h - pad) + ' L' + pts[0][0] + ' ' + (h - pad) + ' Z';
    return { line: line, area: area };
}

function renderSparklines(d) {
    const series = d.series || [];
    const counts = series.map(p => p.payments || 0);
    const revenue = series.map(p => p.revenue || 0);
    const failed = series.map(p => p.failed || 0);
    const ok = series.map(p => p.succeeded || 0);
    const refunds = series.map((p, i) => Math.max(0, (counts[i] || 0) - (ok[i] || 0)) * 0.35);

    const set = (id, values, w, h) => {
        const el = document.getElementById(id);
        if (el) el.setAttribute('d', sparkPath(values, w, h, 3).line);
    };
    set('sparkTotalLine', counts, 120, 28);
    set('sparkSucceededLine', ok, 120, 28);
    set('sparkFailedLine', failed, 120, 28);
    set('sparkRefundsLine', refunds, 120, 28);

    const big = sparkPath(revenue, 320, 64, 4);
    const lineEl = document.getElementById('sparkRevenueLine');
    const areaEl = document.getElementById('sparkRevenueArea');
    if (lineEl) {
        lineEl.setAttribute('d', big.line);
        // draw the line on: a dash-offset animation, no library needed
        const len = lineEl.getTotalLength ? Math.ceil(lineEl.getTotalLength()) : 600;
        lineEl.style.strokeDasharray = len;
        lineEl.style.strokeDashoffset = len;
        lineEl.style.transition = 'stroke-dashoffset 0.9s cubic-bezier(0.22, 1, 0.36, 1)';
        requestAnimationFrame(() => { lineEl.style.strokeDashoffset = '0'; });
    }
    if (areaEl) areaEl.setAttribute('d', big.area);
}

// ---- count-up animation for stat values ----
function animateStat(el, target, isMoney) {
    if (!el) return;
    if (target == null || isNaN(Number(target))) { el.textContent = '-'; el.dataset.raw = ''; return; }
    const to = Number(target);
    const from = el.dataset.raw !== undefined && el.dataset.raw !== '' && !isNaN(Number(el.dataset.raw))
        ? Number(el.dataset.raw) : 0;
    el.dataset.raw = String(to);
    const fmt = v => isMoney ? fmtStatMoney(v) : String(Math.round(v));
    if (from === to) { el.textContent = fmt(to); return; }
    const dur = 700;
    const t0 = performance.now();
    function step(t) {
        const p = Math.min(1, (t - t0) / dur);
        const e = 1 - Math.pow(1 - p, 3);
        el.textContent = fmt(from + (to - from) * e);
        if (p < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
}

// init default: last 7 days

function destroyChart(key) {
    if (dashboardCharts[key]) { dashboardCharts[key].destroy(); dashboardCharts[key] = null; }
}

const CHART_FONT = { family: "'Inter', sans-serif", size: 11 };
const CHART_GRID = { color: '#eef1f4', drawBorder: false };
const doughnutOpts = (legendPos = 'bottom') => ({
    responsive: true,
    maintainAspectRatio: false,
    cutout: '68%',
    plugins: {
        legend: { position: legendPos, labels: { padding: 14, usePointStyle: true, pointStyleWidth: 8, font: CHART_FONT, color: '#71717a' } },
        tooltip: { backgroundColor: '#18181b', padding: 10, cornerRadius: 8, titleFont: { ...CHART_FONT, weight: '600' }, bodyFont: CHART_FONT, displayColors: true, boxPadding: 4 }
    }
});

function prettyLabel(s) {
    if (s == null || s === '') return 'Unknown';
    return String(s).replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

// A range with nothing in it should say so, not draw bare axes. The overlay is hidden again as
// soon as there is data to show.
function setChartEmpty(canvasId, isEmpty) {
    const el = document.querySelector('[data-empty-for="' + canvasId + '"]');
    if (el) el.hidden = !isEmpty;
    if (canvasId === 'chartMethods') {
        const list = document.getElementById('methodsList');
        if (list) list.style.visibility = isEmpty ? 'hidden' : 'visible';
        return;
    }
    const canvas = document.getElementById(canvasId);
    if (canvas) canvas.style.visibility = isEmpty ? 'hidden' : 'visible';
}

function renderDashboardCharts(d) {
    destroyChart('status'); destroyChart('transactions'); destroyChart('revenue');
    ['chartMethods', 'chartStatus', 'chartTransactions', 'chartRevenue']
        .forEach(id => setChartEmpty(id, false));

    const methodEntries = Object.entries(d.methodCounts || {})
        .sort((a, b) => (b[1] || 0) - (a[1] || 0));
    const methodLabels = methodEntries.map(([k]) => prettyLabel(k));
    const methodValues = methodEntries.map(([, v]) => v || 0);
    const methodRawKeys = methodEntries.map(([k]) => k);
    const methodColors = methodRawKeys.map((k, i) => METHOD_COLORS[k] || FALLBACK_COLORS[i % FALLBACK_COLORS.length]);

    renderMethodList(methodEntries, methodColors, d);

    const statusEntries = Object.entries(d.statusCounts || {})
        .sort((a, b) => (b[1] || 0) - (a[1] || 0));
    const statusLabels = statusEntries.map(([k]) => prettyLabel(k));
    const statusValues = statusEntries.map(([, v]) => v || 0);
    const statusRawKeys = statusEntries.map(([k]) => k);
    const statusColors = statusRawKeys.map(k => STATUS_COLORS[k] || STATUS_COLORS[prettyLabel(k)] || '#71717a');

    if (statusValues.length === 0) {
        setChartEmpty('chartStatus', true);
    } else {
    dashboardCharts.status = new Chart(document.getElementById('chartStatus'), {
        type: 'doughnut',
        data: {
            labels: statusLabels.length ? statusLabels : ['No Data'],
            datasets: [{
                data: statusValues.length ? statusValues : [1],
                backgroundColor: statusLabels.length ? statusColors : ['#e4e4e7'],
                borderWidth: 3,
                borderColor: '#ffffff',
                hoverOffset: 8,
                spacing: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '64%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        padding: 14,
                        usePointStyle: true,
                        pointStyleWidth: 10,
                        pointStyle: 'circle',
                        font: { ...CHART_FONT, weight: '600', size: 12 },
                        color: '#3f3f46',
                        generateLabels(chart) {
                            const ds = chart.data.datasets[0];
                            return (chart.data.labels || []).map((label, i) => ({
                                text: label,
                                fillStyle: (ds.backgroundColor || [])[i] || '#a1a1aa',
                                strokeStyle: 'transparent',
                                pointStyle: 'circle',
                                hidden: false,
                                index: i,
                                datasetIndex: 0
                            }));
                        }
                    }
                },
                tooltip: {
                    backgroundColor: '#18181b', padding: 10, cornerRadius: 8,
                    titleFont: { ...CHART_FONT, weight: '600' }, bodyFont: CHART_FONT,
                    displayColors: true, boxPadding: 4,
                    callbacks: { label: (c) => ' ' + c.parsed + ' payments' }
                }
            }
        }
    });
    }

    const series = Array.isArray(d.series) && d.series.length
        ? d.series
        : Object.keys(d.dailyCounts || {}).sort().map(k => ({
            period: k,
            payments: d.dailyCounts[k] || 0,
            revenue: (d.dailyRevenue || {})[k] || 0,
            succeeded: null,
            failed: null,
            successRate: 0
        }));
    const grain = d.grain || 'day';
    const periodKeys = series.map(p => p.period);
    const dayLabels = periodKeys.map(k => formatPeriod(k, grain));
    const counts = series.map(p => p.payments || 0);
    const revs = series.map(p => p.revenue || 0);
    const fullTips = periodKeys.map(k => formatPeriod(k, grain));

    // both period charts share one label list, so one emptiness check covers them
    if (periodKeys.length === 0) {
        setChartEmpty('chartTransactions', true);
    } else {
    dashboardCharts.transactions = new Chart(document.getElementById('chartTransactions'), {
        type: 'bar',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{ label: 'Transactions', data: counts.length ? counts : [0], backgroundColor: counts.map((_, i) => i % 2 === 0 ? '#0f766e' : '#14b8a6'), hoverBackgroundColor: '#115e59', borderRadius: 6, borderSkipped: false, maxBarThickness: 30, barPercentage: 0.72, categoryPercentage: 0.85 }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#18181b', padding: 10, cornerRadius: 8, bodyFont: CHART_FONT, titleFont: CHART_FONT,
                    callbacks: {
                        title: (items) => fullTips[items[0].dataIndex] || items[0].label,
                        label: (c) => ' ' + c.parsed.y + ' payments'
                    }
                }
            },
            scales: {
                y: { beginAtZero: true, ticks: { stepSize: 1, font: CHART_FONT, color: '#71717a' }, grid: CHART_GRID, border: { display: false } },
                x: { grid: { display: false }, ticks: { font: CHART_FONT, color: '#71717a', maxRotation: 45, autoSkipPadding: 8 }, border: { display: false } }
            }
        }
    });
    }

    if (periodKeys.length === 0) {
        setChartEmpty('chartRevenue', true);
    } else {
    dashboardCharts.revenue = new Chart(document.getElementById('chartRevenue'), {
        type: 'line',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{
                label: 'Revenue ($)',
                data: revs.length ? revs : [0],
                borderColor: '#0f766e',
                backgroundColor: 'rgba(13, 148, 136, 0.07)',
                fill: true,
                tension: 0.4,
                borderWidth: 2,
                pointRadius: series.length > 40 ? 0 : 3,
                pointHoverRadius: 5,
                pointBackgroundColor: '#0f766e',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#18181b', padding: 10, cornerRadius: 8, bodyFont: CHART_FONT, titleFont: CHART_FONT,
                    callbacks: {
                        title: (items) => fullTips[items[0].dataIndex] || items[0].label,
                        label: (c) => ' $' + Number(c.parsed.y).toFixed(2)
                    }
                }
            },
            scales: {
                y: { beginAtZero: true, ticks: { font: CHART_FONT, color: '#71717a', callback: v => '$' + fmtAxisMoney(v) }, grid: CHART_GRID, border: { display: false } },
                x: { grid: { display: false }, ticks: { font: CHART_FONT, color: '#71717a', maxRotation: 45, autoSkipPadding: 8 }, border: { display: false } }
            }
        }
    });
    }
}

// ===== ALL PAYMENT LOGS =====
let allLogsPage = 0;
let allLogsUserId = null;

function viewUserPaymentLogs(userId, email) {
    allLogsUserId = userId;
    userFilter = { userId, email };
    document.getElementById('allLogsUserFilter').textContent = '— ' + email;
    showSection('all-logs', document.querySelector('[data-section="all-logs"]'));
}

async function loadAllLogs(page) {
    allLogsPage = page;
    const body = document.getElementById('allLogsBody');
    body.innerHTML = '<tr><td colspan="8" class="admin-loading">Loading...</td></tr>';
    try {
        let url;
        if (allLogsUserId) {
            url = `/api/v1/admin/user/${allLogsUserId}/payment-logs?page=${page}&size=50`;
        } else {
            const status = document.getElementById('allLogsStatusFilter').value;
            url = `/api/v1/admin/payment-logs?page=${page}&size=50` + (status ? '&status=' + status : '');
        }
        const res = await fetch(url, { headers: authHeaders() });
        const data = await res.json();
        if (!data.success) { notifyError(data.message || 'Failed to load.'); body.innerHTML = ''; return; }

        const d = data.data || {};
        let items = d.logs || d.content || d || [];
        if (!Array.isArray(items)) items = [];
        if (items.length === 0) {
            body.innerHTML = '<tr><td colspan="8" class="admin-empty">No payment logs found.</td></tr>';
            document.getElementById('allLogsPagination').innerHTML = '';
            return;
        }
        body.innerHTML = items.map(l => `
            <tr>
                <td>${l.id}</td>
                <td>${esc(l.email) || (l.customerId ? 'User #' + l.customerId : '-')}</td>
                <td>${esc(l.paymentMethod) || '-'}</td>
                <td class="num" style="font-weight:600;white-space:nowrap;">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                <td>${paymentBadge(l.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(l.message) || '-'}</td>
                <td style="color:${l.failureCode ? '#be123c' : '#71717a'}">${esc(l.failureCode) || '-'}</td>
                <td class="ta-r" style="white-space:nowrap;color:#71717a;">${fmtDate(l.createdAt)}</td>
            </tr>
        `).join('');

        renderAllLogsPagination(d.currentPage ?? page, d.totalPages ?? 1, d.totalElements ?? items.length);
    } catch (e) {
        notifyError('Failed to load: ' + e.message);
        body.innerHTML = '';
    }
}

function renderAllLogsPagination(current, total, count) {
    const el = document.getElementById('allLogsPagination');
    if (total <= 1) { el.innerHTML = ''; return; }
    el.innerHTML = `
        <div class="logs-pagination">
            <span class="logs-pagination-info">${count} total</span>
            <button class="logs-pagination-btn" onclick="loadAllLogs(${current - 1})" ${current === 0 ? 'disabled' : ''}>Prev</button>
            <span class="logs-pagination-info">Page ${current + 1} of ${total}</span>
            <button class="logs-pagination-btn" onclick="loadAllLogs(${current + 1})" ${current >= total - 1 ? 'disabled' : ''}>Next</button>
        </div>`;
}

// ===== ADMIN REFUND LOGS =====
let adminRefundPage = 0;
let adminRefundUserId = null;

function viewUserRefundLogs(userId, email) {
    adminRefundUserId = userId;
    userFilter = { userId, email };
    document.getElementById('refundLogsUserFilter').textContent = '— ' + email;
    showSection('refund-logs', document.querySelector('[data-section="refund-logs"]'));
}

async function loadAdminRefundLogs(page = 0) {
    adminRefundPage = page;
    const body = document.getElementById('adminRefundLogsBody');
    body.innerHTML = '<tr><td colspan="7" class="admin-loading">Loading...</td></tr>';
    try {
        let url;
        if (adminRefundUserId) {
            url = `/api/v1/admin/user/${adminRefundUserId}/refund-logs?page=${page}&size=50`;
        } else {
            url = `/api/v1/admin/refund-logs?page=${page}&size=50`;
        }
        const res = await fetch(url, { headers: authHeaders() });
        const data = await res.json();
        if (!data.success) { notifyError(data.message || 'Failed to load.'); body.innerHTML = ''; return; }

        const items = Array.isArray(data.data) ? data.data : (data.data?.logs || data.data?.content || []);
        if (items.length === 0) {
            body.innerHTML = '<tr><td colspan="7" class="admin-empty">No refund logs found.</td></tr>';
            document.getElementById('adminRefundLogsPagination').innerHTML = '';
            return;
        }
        body.innerHTML = items.map(r => `
            <tr>
                <td style="font-family:monospace;font-size:12px;white-space:nowrap;">${esc(r.refundId) || '-'}</td>
                <td>${esc(r.email) || (r.customerId ? 'User #' + r.customerId : '-')}</td>
                <td style="font-family:monospace;font-size:12px;white-space:nowrap;">${esc(r.cardReference) || '-'}</td>
                <td class="num" style="font-weight:600;white-space:nowrap;">${r.currency ? r.currency.toUpperCase() : 'USD'} $${parseFloat(r.amount).toFixed(2)}</td>
                <td>${refundBadge(r.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(r.message) || '-'}</td>
                <td class="ta-r" style="white-space:nowrap;color:#71717a;">${fmtDate(r.createdAt)}</td>
            </tr>
        `).join('');

        if (data.data && data.data.totalPages > 1) {
            const cur = data.data.currentPage ?? page;
            document.getElementById('adminRefundLogsPagination').innerHTML = `
                <div class="logs-pagination">
                    <span class="logs-pagination-info">${data.data.totalElements} total</span>
                    <button class="logs-pagination-btn" onclick="loadAdminRefundLogs(${cur - 1})" ${cur === 0 ? 'disabled' : ''}>Prev</button>
                    <span class="logs-pagination-info">Page ${cur + 1} of ${data.data.totalPages}</span>
                    <button class="logs-pagination-btn" onclick="loadAdminRefundLogs(${cur + 1})" ${cur >= data.data.totalPages - 1 ? 'disabled' : ''}>Next</button>
                </div>`;
        } else {
            document.getElementById('adminRefundLogsPagination').innerHTML = '';
        }
    } catch (e) {
        notifyError('Failed to load: ' + e.message);
        body.innerHTML = '';
    }
}

// init
initAdmin();
