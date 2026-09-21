// ===== PAYMENT LOGS =====
let logsPage = 0;
const logsPageSize = 50;

async function goToLogs() {
    logsPage = 0;
    hideAll();
    document.getElementById('step-logs').classList.remove('hidden');
    document.getElementById('logs-error').classList.remove('show');
    document.getElementById('logsTable').innerHTML = '<div style="text-align:center;padding:20px 0;color:#8b8fa3;font-size:13px;">Loading...</div>';
    document.getElementById('logsEmpty').classList.add('hidden');
    document.getElementById('logsPagination').innerHTML = '';
    await loadLogs();
}

async function loadLogs() {
    try {
        const res = await fetch(`/api/v1/payments?page=${logsPage}&size=${logsPageSize}`, { headers: authHeaders() });
        const data = await res.json();
        if (!data.success || !data.data || !data.data.logs || data.data.logs.length === 0) {
            document.getElementById('logsTable').innerHTML = '';
            document.getElementById('logsEmpty').classList.remove('hidden');
            document.getElementById('logsPagination').innerHTML = '';
            return;
        }
        renderLogs(data.data.logs);
        renderPagination(data.data.currentPage, data.data.totalPages, data.data.totalElements);
    } catch (e) {
        document.getElementById('logsTable').innerHTML = '';
        showMsg(document.getElementById('logs-error'), 'Failed to load: ' + e.message);
    }
}

function renderLogs(logs) {
    const statusBadge = (s) => {
        if (s === 'PROCESSING' || s === '1') return '<span class="status-badge pending">Processing</span>';
        if (s === 'INITIATED' || s === '0') return '<span class="status-badge pending">Initiated</span>';
        if (s === 'SUCCEEDED' || s === '2') return '<span class="status-badge success">Succeeded</span>';
        return '<span class="status-badge error">Failed</span>';
    };
    const fmt = (d) => {
        if (!d) return '-';
        const dt = new Date(d);
        return dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) + ' ' + dt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
    };
    document.getElementById('logsTable').innerHTML = `
        <div class="logs-table-wrap">
            <table class="logs-table">
                <thead>
                    <tr>
                        <th style="min-width:90px">Payment Method</th>
                        <th style="min-width:90px">Amount</th>
                        <th style="min-width:90px">Status</th>
                        <th style="min-width:90px">Charge ID</th>
                        <th style="min-width:100px">Gateway Status</th>
                        <th style="min-width:100px">Failure</th>
                        <th style="min-width:200px">Transaction ID</th>
                        <th style="min-width:200px">Message</th>
                        <th style="min-width:100px">Created At</th>
                    </tr>
                </thead>
                <tbody>
                    ${logs.map(l => `
                        <tr>
                            <td>${l.paymentMethod || '-'}</td>
                            <td class="amt">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                            <td>${statusBadge(l.status)}</td>
                            <td class="mono">${l.chargeId || '-'}</td>
                            <td>${l.gatewayStatus || '-'}</td>
                            <td style="color:${l.failureCode ? '#d93025' : '#8b8fa3'}">${l.failureCode || '-'}</td>
                            <td class="mono">${l.transactionId || '-'}</td>
                            <td class="msg" title="${l.message || ''}">${l.message || '-'}</td>
                            <td>${fmt(l.createdAt)}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>`;
}

function renderPagination(currentPage, totalPages, totalElements) {
    if (totalPages <= 1) {
        document.getElementById('logsPagination').innerHTML = '';
        return;
    }

    const prevDisabled = currentPage === 0 ? 'disabled' : '';
    const nextDisabled = currentPage >= totalPages - 1 ? 'disabled' : '';

    document.getElementById('logsPagination').innerHTML = `
        <div class="logs-pagination">
            <span class="logs-pagination-info">${totalElements} total</span>
            <button class="logs-pagination-btn" onclick="changeLogsPage(${currentPage - 1})" ${prevDisabled}>Prev</button>
            <span class="logs-pagination-info">Page ${currentPage + 1} of ${totalPages}</span>
            <button class="logs-pagination-btn" onclick="changeLogsPage(${currentPage + 1})" ${nextDisabled}>Next</button>
        </div>`;
}

function changeLogsPage(newPage) {
    logsPage = newPage;
    loadLogs();
}
