// ===== ADMIN CORE =====
let currentSection = 'dashboard';
let userFilter = null; // { userId, email } when drilled into a user

// isSuperAdmin check
const adminUser = getUser();
if (!adminUser || adminUser.isSuperAdmin !== '1') {
    window.location.href = '/index.html';
}

document.getElementById('adminEmail').textContent = adminUser ? (adminUser.email || adminUser.name) : '-';

function logout() { localStorage.clear(); token = null; window.location.href = '/index.html'; }

// ===== SIDEBAR TOGGLE =====
function isMobileNav() { return window.matchMedia('(max-width: 860px)').matches; }

function openSidebar() {
    const layout = document.getElementById('adminLayout');
    if (!layout) return;
    layout.classList.add('sidebar-open');
}

function closeSidebar() {
    const layout = document.getElementById('adminLayout');
    if (!layout) return;
    layout.classList.remove('sidebar-open');
}

function toggleSidebar() {
    const layout = document.getElementById('adminLayout');
    if (!layout) return;
    if (isMobileNav()) {
        layout.classList.toggle('sidebar-open');
    } else {
        layout.classList.toggle('sidebar-collapsed');
        try { localStorage.setItem('admSidebar', layout.classList.contains('sidebar-collapsed') ? '1' : '0'); } catch (e) {}
        setTimeout(() => {
            Object.values(dashboardCharts).forEach(c => { try { c && c.resize(); } catch (e) {} });
        }, 220);
    }
}

(function initSidebar() {
    try {
        if (localStorage.getItem('admSidebar') === '1' && !isMobileNav()) {
            document.getElementById('adminLayout')?.classList.add('sidebar-collapsed');
        }
    } catch (e) {}
    document.getElementById('sidebarToggle')?.addEventListener('click', toggleSidebar);
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
    sepa_debit: '#5eead4', SEPA_DEBIT: '#5eead4',
    us_bank_account: '#134e4a', ACH: '#134e4a',
    cashapp: '#99f6e4', Cashapp: '#99f6e4',
    Unknown: '#d4d4d8', unknown: '#d4d4d8', null: '#e4e4e7'
};
const FALLBACK_COLORS = ['#0f766e', '#0d9488', '#14b8a6', '#2dd4bf', '#115e59', '#5eead4', '#134e4a', '#99f6e4', '#84cc16', '#d4d4d8'];
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
            <td>${p.payments ?? 0}</td>
            <td style="color:#047857;font-weight:600;">${p.succeeded ?? 0}</td>
            <td style="color:#be123c;font-weight:600;">${p.failed ?? 0}</td>
            <td><span class="rate-bar ${rateCls}">${rate.toFixed(1)}%</span></td>
            <td style="font-weight:600;">${fmtMoney(p.revenue)}</td>
        </tr>`;
    }).join('');
    const tRate = tPay > 0 ? (tSuc * 100 / tPay) : 0;
    body.innerHTML = rows + `<tr class="summary-row">
        <td>Total</td>
        <td>${tPay}</td>
        <td>${tSuc}</td>
        <td>${tFail}</td>
        <td>${tRate.toFixed(1)}%</td>
        <td>${fmtMoney(tRev)}</td>
    </tr>`;
}

function renderDashLogs(d) {
    const body = document.getElementById('dashLogsBody');
    const countEl = document.getElementById('dashLogsCount');
    const subEl = document.getElementById('dashLogsSub');
    if (!body) return;
    const logs = Array.isArray(d.paymentLogs) ? d.paymentLogs : [];
    if (countEl) countEl.textContent = logs.length + (logs.length === 1 ? ' log' : ' logs');
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
            <td style="font-weight:600;white-space:nowrap;">${l.currency ? String(l.currency).toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
            <td>${paymentBadge(l.status)}</td>
            <td style="color:${l.failureCode ? '#be123c' : '#71717a'}">${esc(l.failureCode) || '-'}</td>
            <td style="white-space:nowrap;color:#71717a;">${fmtDate(l.createdAt)}</td>
        </tr>
    `).join('');
}

async function loadDashboard() {
    if (dashboardLoading) return;
    dashboardLoading = true;
    await ensureDashCustomers();
    const range = document.getElementById('dashRange').value;
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    const grainSel = document.getElementById('dashGrain');
    const grain = grainSel ? grainSel.value : 'auto';
    const customerSel = document.getElementById('dashCustomer');
    const customerId = customerSel ? customerSel.value : '';

    let url = '/api/v1/admin/dashboard?grain=' + grain;
    if (range === 'custom' || (startEl && startEl.value && endEl && endEl.value)) {
        const s = startEl ? startEl.value : '';
        const e = endEl ? endEl.value : '';
        if (s && e) {
            url += '&range=custom&startDate=' + encodeURIComponent(s) + '&endDate=' + encodeURIComponent(e);
        } else {
            url += '&range=' + range;
        }
    } else {
        url += '&range=' + range;
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
        if (refundAmtEl) refundAmtEl.textContent = fmtMoney(d.refundedAmount);

        const revenue = Number(d.revenue) || 0;
        animateStat(document.getElementById('statRevenue'), revenue, true);

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

function setDashDefaultDates() {
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    if (!startEl || !endEl) return;
    const end = new Date();
    const start = new Date();
    start.setDate(start.getDate() - 6); // last 7 days including today
    startEl.value = isoDate(start);
    endEl.value = isoDate(end);
    startEl.max = isoDate(end);
}

function onDashPresetChange() {
    const preset = document.getElementById('dashRange').value;
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    if (preset !== 'custom') clearMonthYear();
    const end = new Date();
    let start = new Date();
    if (preset === 'today') {
        // both today
    } else if (preset === 'week') {
        start.setDate(start.getDate() - 6);
    } else if (preset === 'month') {
        start = new Date(end.getFullYear(), end.getMonth(), 1);
    } else if (preset === 'year') {
        start = new Date(end.getFullYear(), 0, 1);
    } else if (preset === 'all') {
        start = new Date(2020, 0, 1);
    } else {
        // custom — leave dates as-is (default last 7 days if empty)
        if (!startEl.value || !endEl.value) setDashDefaultDates();
        loadDashboard();
        return;
    }
    if (startEl) startEl.value = isoDate(start);
    if (endEl) endEl.value = isoDate(end);
    loadDashboard();
}

function onDashDateChange() {
    const rangeSel = document.getElementById('dashRange');
    if (rangeSel) rangeSel.value = 'custom';
    clearMonthYear();
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    if (startEl && endEl && startEl.value && endEl.value && startEl.value > endEl.value) {
        endEl.value = startEl.value;
    }
    loadDashboard();
}

// ---- Month / Year filter ----
function populateDashYears() {
    const yearSel = document.getElementById('dashYear');
    if (!yearSel || yearSel.options.length > 1) return;
    const nowY = new Date().getFullYear();
    for (let y = nowY; y >= nowY - 5; y--) {
        const o = document.createElement('option');
        o.value = String(y);
        o.textContent = String(y);
        yearSel.appendChild(o);
    }
}

function clearMonthYear() {
    const m = document.getElementById('dashMonth');
    const y = document.getElementById('dashYear');
    if (m) m.value = '';
    if (y) y.value = '';
}

function onDashMonthYearChange() {
    const mSel = document.getElementById('dashMonth');
    const ySel = document.getElementById('dashYear');
    const presetSel = document.getElementById('dashRange');
    const startEl = document.getElementById('dashStart');
    const endEl = document.getElementById('dashEnd');
    const m = mSel ? mSel.value : '';
    const y = ySel ? ySel.value : '';
    if (!m && !y) return;
    const now = new Date();
    const yy = y ? parseInt(y, 10) : now.getFullYear();
    let start, end;
    if (m) {
        const mm = parseInt(m, 10) - 1;
        start = new Date(yy, mm, 1);
        end = new Date(yy, mm + 1, 0);
    } else {
        start = new Date(yy, 0, 1);
        end = new Date(yy, 11, 31);
    }
    if (startEl) startEl.value = isoDate(start);
    if (endEl) endEl.value = isoDate(end);
    if (presetSel) presetSel.value = 'custom';
    loadDashboard();
}

// ---- count-up animation for stat values ----
function animateStat(el, target, isMoney) {
    if (!el) return;
    if (target == null || isNaN(Number(target))) { el.textContent = '-'; el.dataset.raw = ''; return; }
    const to = Number(target);
    const from = el.dataset.raw !== undefined && el.dataset.raw !== '' && !isNaN(Number(el.dataset.raw))
        ? Number(el.dataset.raw) : 0;
    el.dataset.raw = String(to);
    const fmt = v => isMoney ? fmtMoney(v) : String(Math.round(v));
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
setDashDefaultDates();
populateDashYears();

function destroyChart(key) {
    if (dashboardCharts[key]) { dashboardCharts[key].destroy(); dashboardCharts[key] = null; }
}

const CHART_FONT = { family: "'Inter', sans-serif", size: 11 };
const CHART_GRID = { color: '#f4f4f5', drawBorder: false };
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

function renderDashboardCharts(d) {
    destroyChart('methods'); destroyChart('status'); destroyChart('transactions'); destroyChart('revenue');

    const methodEntries = Object.entries(d.methodCounts || {})
        .sort((a, b) => (b[1] || 0) - (a[1] || 0));
    const methodLabels = methodEntries.map(([k]) => prettyLabel(k));
    const methodValues = methodEntries.map(([, v]) => v || 0);
    const methodRawKeys = methodEntries.map(([k]) => k);
    const methodColors = methodRawKeys.map((k, i) => METHOD_COLORS[k] || FALLBACK_COLORS[i % FALLBACK_COLORS.length]);

    dashboardCharts.methods = new Chart(document.getElementById('chartMethods'), {
        type: 'bar',
        data: {
            labels: methodLabels.length ? methodLabels : ['No Data'],
            datasets: [{
                label: 'Payments',
                data: methodValues.length ? methodValues : [0],
                backgroundColor: methodLabels.length ? methodColors : ['#e4e4e7'],
                hoverBackgroundColor: methodLabels.length ? methodColors.map(c => c) : ['#d4d4d8'],
                borderRadius: 6,
                borderSkipped: false,
                maxBarThickness: 36,
                barPercentage: 0.7,
                categoryPercentage: 0.8
            }]
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    backgroundColor: '#18181b', padding: 10, cornerRadius: 8,
                    titleFont: { ...CHART_FONT, weight: '600' }, bodyFont: CHART_FONT,
                    displayColors: true, boxPadding: 4,
                    callbacks: { label: (c) => ' ' + c.parsed.x + ' payments' }
                }
            },
            scales: {
                x: {
                    beginAtZero: true,
                    ticks: { stepSize: 1, font: CHART_FONT, color: '#71717a', precision: 0 },
                    grid: CHART_GRID,
                    border: { display: false }
                },
                y: {
                    grid: { display: false },
                    ticks: { font: { ...CHART_FONT, weight: '600', size: 12 }, color: '#3f3f46' },
                    border: { display: false }
                }
            }
        }
    });

    const statusEntries = Object.entries(d.statusCounts || {})
        .sort((a, b) => (b[1] || 0) - (a[1] || 0));
    const statusLabels = statusEntries.map(([k]) => prettyLabel(k));
    const statusValues = statusEntries.map(([, v]) => v || 0);
    const statusRawKeys = statusEntries.map(([k]) => k);
    const statusColors = statusRawKeys.map(k => STATUS_COLORS[k] || STATUS_COLORS[prettyLabel(k)] || '#71717a');

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

    dashboardCharts.transactions = new Chart(document.getElementById('chartTransactions'), {
        type: 'bar',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{ label: 'Transactions', data: counts.length ? counts : [0], backgroundColor: counts.map((_, i) => i % 2 === 0 ? '#0f766e' : '#5eead4'), hoverBackgroundColor: '#115e59', borderRadius: 6, borderSkipped: false, maxBarThickness: 30, barPercentage: 0.72, categoryPercentage: 0.85 }]
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

    dashboardCharts.revenue = new Chart(document.getElementById('chartRevenue'), {
        type: 'line',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{
                label: 'Revenue ($)',
                data: revs.length ? revs : [0],
                borderColor: '#0f766e',
                backgroundColor: 'rgba(15, 118, 110, 0.08)',
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
                y: { beginAtZero: true, ticks: { font: CHART_FONT, color: '#71717a', callback: v => '$' + v }, grid: CHART_GRID, border: { display: false } },
                x: { grid: { display: false }, ticks: { font: CHART_FONT, color: '#71717a', maxRotation: 45, autoSkipPadding: 8 }, border: { display: false } }
            }
        }
    });
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
    const errEl = document.getElementById('allLogsError');
    errEl.classList.remove('show');
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
        if (!data.success) { showMsg(errEl, data.message || 'Failed to load.'); body.innerHTML = ''; return; }

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
                <td style="font-weight:600;white-space:nowrap;">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                <td>${paymentBadge(l.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(l.message) || '-'}</td>
                <td style="color:${l.failureCode ? '#be123c' : '#71717a'}">${esc(l.failureCode) || '-'}</td>
                <td style="white-space:nowrap;color:#71717a;">${fmtDate(l.createdAt)}</td>
            </tr>
        `).join('');

        renderAllLogsPagination(d.currentPage ?? page, d.totalPages ?? 1, d.totalElements ?? items.length);
    } catch (e) {
        showMsg(errEl, 'Failed to load: ' + e.message);
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
    const errEl = document.getElementById('adminRefundLogsError');
    errEl.classList.remove('show');
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
        if (!data.success) { showMsg(errEl, data.message || 'Failed to load.'); body.innerHTML = ''; return; }

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
                <td style="font-weight:600;white-space:nowrap;">${r.currency ? r.currency.toUpperCase() : 'USD'} $${parseFloat(r.amount).toFixed(2)}</td>
                <td>${refundBadge(r.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(r.message) || '-'}</td>
                <td style="white-space:nowrap;color:#71717a;">${fmtDate(r.createdAt)}</td>
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
        showMsg(errEl, 'Failed to load: ' + e.message);
        body.innerHTML = '';
    }
}

// init
loadDashboard();
