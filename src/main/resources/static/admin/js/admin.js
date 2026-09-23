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
    if (s === 'INITIATED' || s === '0') return '<span class="status-badge" style="background:#fef3c7;color:#92400e;">Initiated</span>';
    if (s === 'PROCESSING' || s === '1') return '<span class="status-badge" style="background:#dbeafe;color:#1e40af;">Processing</span>';
    if (s === 'SUCCEEDED' || s === '2') return '<span class="status-badge" style="background:#d1fae5;color:#065f46;">Succeeded</span>';
    if (s === 'FAILED' || s === '3') return '<span class="status-badge" style="background:#fee2e2;color:#991b1b;">Failed</span>';
    return '<span class="status-badge">' + s + '</span>';
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
const METHOD_COLORS = { CARD:'#6366f1', card:'#6366f1', google_pay:'#22c55e', GOOGLE_PAY:'#22c55e', apple_pay:'#f59e0b', APPLE_PAY:'#f59e0b', link:'#06b6d4', LINK:'#06b6d4', amazon_pay:'#ef4444', Unknown:'#94a3b8' };
const FALLBACK_COLORS = ['#6366f1','#22c55e','#f59e0b','#ef4444','#06b6d4','#8b5cf6','#ec4899','#14b8a6'];
const STATUS_COLORS = { Succeeded:'#22c55e', Failed:'#ef4444', Processing:'#f59e0b', Initiated:'#94a3b8' };

let dashboardCharts = { methods: null, status: null, transactions: null, revenue: null };
let dashboardLoading = false;

async function loadDashboard() {
    if (dashboardLoading) return;
    dashboardLoading = true;
    const range = document.getElementById('dashRange').value;
    try {
        const res = await fetch('/api/v1/admin/dashboard?range=' + range, { headers: authHeaders() });
        const data = await res.json();
        if (!data.success) { dashboardLoading = false; return; }
        const d = data.data;

        document.getElementById('statTotal').textContent = d.totalPayments ?? '-';
        document.getElementById('statSucceeded').textContent = d.succeeded ?? '-';
        document.getElementById('statFailed').textContent = d.failed ?? '-';
        document.getElementById('statRefunds').textContent = d.totalRefunds ?? '-';

        renderDashboardCharts(d);
    } catch (e) { console.error(e); }
    finally { dashboardLoading = false; }
}

function destroyChart(key) {
    if (dashboardCharts[key]) { dashboardCharts[key].destroy(); dashboardCharts[key] = null; }
}

function renderDashboardCharts(d) {
    destroyChart('methods'); destroyChart('status'); destroyChart('transactions'); destroyChart('revenue');

    const methodLabels = Object.keys(d.methodCounts || {});
    const methodValues = Object.values(d.methodCounts || {});
    const methodColors = methodLabels.map((l, i) => METHOD_COLORS[l] || FALLBACK_COLORS[i % FALLBACK_COLORS.length]);

    dashboardCharts.methods = new Chart(document.getElementById('chartMethods'), {
        type: 'doughnut',
        data: {
            labels: methodLabels.length ? methodLabels : ['No Data'],
            datasets: [{ data: methodValues.length ? methodValues : [1], backgroundColor: methodLabels.length ? methodColors : ['#e2e4e9'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom', labels: { padding: 12, font: { size: 11 } } } } }
    });

    const statusLabels = Object.keys(d.statusCounts || {});
    const statusValues = Object.values(d.statusCounts || {});

    dashboardCharts.status = new Chart(document.getElementById('chartStatus'), {
        type: 'doughnut',
        data: {
            labels: statusLabels.length ? statusLabels : ['No Data'],
            datasets: [{ data: statusValues.length ? statusValues : [1], backgroundColor: statusLabels.length ? statusLabels.map(s => STATUS_COLORS[s] || '#94a3b8') : ['#e2e4e9'], borderWidth: 0 }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom', labels: { padding: 12, font: { size: 11 } } } } }
    });

    const days = Object.keys(d.dailyCounts || {}).sort();
    const dayLabels = days.map(day => new Date(day).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }));

    dashboardCharts.transactions = new Chart(document.getElementById('chartTransactions'), {
        type: 'bar',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{ label: 'Transactions', data: days.length ? days.map(day => d.dailyCounts[day] || 0) : [0], backgroundColor: '#6366f1', borderRadius: 4 }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } }, x: { grid: { display: false } } } }
    });

    dashboardCharts.revenue = new Chart(document.getElementById('chartRevenue'), {
        type: 'line',
        data: {
            labels: dayLabels.length ? dayLabels : ['No Data'],
            datasets: [{ label: 'Revenue ($)', data: days.length ? days.map(day => (d.dailyRevenue || {})[day] || 0) : [0], borderColor: '#22c55e', backgroundColor: 'rgba(34,197,94,0.1)', fill: true, tension: 0.3, pointRadius: 4, pointBackgroundColor: '#22c55e' }]
        },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true }, x: { grid: { display: false } } } }
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
                <td>${l.customerId || '-'}</td>
                <td>${esc(l.paymentMethod) || '-'}</td>
                <td style="font-weight:600;white-space:nowrap;">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                <td>${paymentBadge(l.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(l.message) || '-'}</td>
                <td style="color:${l.failureCode ? '#d93025' : '#8b8fa3'}">${esc(l.failureCode) || '-'}</td>
                <td style="white-space:nowrap;color:#8b8fa3;">${fmtDate(l.createdAt)}</td>
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
    body.innerHTML = '<tr><td colspan="8" class="admin-loading">Loading...</td></tr>';
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
            body.innerHTML = '<tr><td colspan="8" class="admin-empty">No refund logs found.</td></tr>';
            document.getElementById('adminRefundLogsPagination').innerHTML = '';
            return;
        }
        body.innerHTML = items.map(r => `
            <tr>
                <td style="font-family:monospace;font-size:12px;white-space:nowrap;">${esc(r.refundId) || '-'}</td>
                <td>${r.paymentLogId || '-'}</td>
                <td>${r.customerId || '-'}</td>
                <td style="font-family:monospace;font-size:12px;white-space:nowrap;">${esc(r.cardReference) || '-'}</td>
                <td style="font-weight:600;white-space:nowrap;">${r.currency ? r.currency.toUpperCase() : 'USD'} $${parseFloat(r.amount).toFixed(2)}</td>
                <td>${refundBadge(r.status)}</td>
                <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${esc(r.message) || '-'}</td>
                <td style="white-space:nowrap;color:#8b8fa3;">${fmtDate(r.createdAt)}</td>
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
