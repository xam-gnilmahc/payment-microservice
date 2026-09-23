// ===== PAYMENT LOGS =====
let logsPage = 0;
const logsPageSize = 50;
let selectedLog = null;

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
        if (s === 'INITIATED' || s === '0') return '<span class="status-badge" style="background:#fef3c7;color:#92400e;">Initiated</span>';
        if (s === 'PROCESSING' || s === '1') return '<span class="status-badge" style="background:#dbeafe;color:#1e40af;">Processing</span>';
        if (s === 'SUCCEEDED' || s === '2') return '<span class="status-badge" style="background:#d1fae5;color:#065f46;">Succeeded</span>';
        if (s === 'FAILED' || s === '3') return '<span class="status-badge" style="background:#fee2e2;color:#991b1b;">Failed</span>';
        return '<span class="status-badge">' + s + '</span>';
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
                        <th style="width:180px">Message</th>
                        <th style="min-width:100px">Failure</th>
                        <th style="min-width:100px">Created At</th>
                        <th style="min-width:80px">Action</th>
                    </tr>
                </thead>
                <tbody>
                    ${logs.map(l => `
                        <tr>
                            <td>${l.paymentMethod || '-'}</td>
                            <td class="amt">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                            <td>${statusBadge(l.status)}</td>
                            <td class="mono">${l.chargeId || '-'}</td>
                            <td style="white-space:normal;word-wrap:break-word;max-width:180px;">${l.message || '-'}</td>
                            <td style="color:${l.failureCode ? '#d93025' : '#8b8fa3'}">${l.failureCode || '-'}</td>
                            <td>${fmt(l.createdAt)}</td>
                            <td>${(l.status === 'SUCCEEDED' || l.status === '2') && l.chargeId
                                ? (l.refundId
                                    ? '<button class="btn-link" style="font-size:12px;color:#6b7280;" onclick=\'showRefundLogs(' + JSON.stringify({chargeId: l.chargeId}).replace(/'/g, "\\'") + ')\'>Refund Logs</button>'
                                    : '<button class="btn-link" style="font-size:12px;color:#d93025;" onclick=\'openRefundModal(' + JSON.stringify({id: l.id, transactionId: l.transactionId, chargeId: l.chargeId, amount: l.amount, customerId: l.customerId}).replace(/'/g, "\\'") + ')\'>Refund</button>')
                                : '-'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>`;
}

// ===== REFUND MODAL =====
function openRefundModal(log) {
    selectedLog = log;
    document.getElementById('refundTransactionId').value = log.transactionId;
    document.getElementById('refundAmount').textContent = '$' + parseFloat(log.amount).toFixed(2);
    document.getElementById('refundReason').value = 'requested_by_customer';
    document.getElementById('refund-error').classList.remove('show');
    document.getElementById('refund-success').classList.remove('show');
    document.getElementById('refundModal').style.display = 'flex';
}

function closeRefundModal() {
    document.getElementById('refundModal').style.display = 'none';
    selectedLog = null;
}

async function submitRefund() {
    const err = document.getElementById('refund-error');
    const ok = document.getElementById('refund-success');
    err.classList.remove('show'); ok.classList.remove('show');

    const reason = document.getElementById('refundReason').value;

    const btn = document.getElementById('refundSubmitBtn');
    btn.disabled = true; btn.textContent = 'Processing...';

    try {
        const res = await fetch('/api/v1/payments/refund', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({
                chargeId: selectedLog.chargeId,
                reason: reason,
                customerId: selectedLog.customerId
            })
        });
        const data = await res.json();
        if (!data.success) { showMsg(err, data.message || 'Refund failed.'); btn.disabled = false; btn.textContent = 'Refund'; return; }
        showMsg(ok, 'Refund successful! ID: ' + data.data.refundId);
        btn.disabled = false; btn.textContent = 'Refund';
        setTimeout(() => { closeRefundModal(); loadLogs(); }, 2000);
    } catch (e) {
        showMsg(err, 'Server error: ' + e.message);
        btn.disabled = false; btn.textContent = 'Refund';
    }
}

// ===== REFUND LOGS =====
async function showRefundLogs(filter) {
    const modal = document.getElementById('refundLogsModal');
    const body = document.getElementById('refundLogsBody');
    body.innerHTML = '<div style="text-align:center;padding:20px 0;color:#8b8fa3;font-size:13px;">Loading...</div>';
    modal.style.display = 'flex';

    try {
        const res = await fetch('/api/v1/payments/refund-logs?chargeId=' + encodeURIComponent(filter.chargeId), { headers: authHeaders() });
        const data = await res.json();
        if (!data.success || !data.data || data.data.length === 0) {
            body.innerHTML = '<div style="text-align:center;padding:20px 0;color:#b0b3c1;font-size:13px;">No refund logs found.</div>';
            return;
        }
        const statusLabel = (s) => {
            if (s === '0') return '<span class="status-badge pending">Pending</span>';
            if (s === '1') return '<span class="status-badge success">Success</span>';
            if (s === '2') return '<span class="status-badge error">Failed</span>';
            return '<span class="status-badge pending">' + s + '</span>';
        };
        body.innerHTML = `
            <div style="overflow-x:auto;">
            <table style="width:100%;border-collapse:collapse;font-size:13px;">
                <thead>
                    <tr style="border-bottom:1.5px solid #e2e4e9;">
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;white-space:nowrap;">Refund ID</th>
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;white-space:nowrap;">Card Reference</th>
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;white-space:nowrap;">Amount</th>
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;white-space:nowrap;">Status</th>
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;min-width:250px;">Message</th>
                        <th style="text-align:left;padding:12px 16px;font-size:11px;font-weight:600;color:#8b8fa3;text-transform:uppercase;letter-spacing:0.5px;background:#f9fafb;white-space:nowrap;">Created At</th>
                    </tr>
                </thead>
                <tbody>
                    ${data.data.map(r => `
                        <tr style="border-bottom:1px solid #f0f1f5;">
                            <td style="padding:12px 16px;font-family:monospace;font-size:12px;color:#6b7084;white-space:nowrap;">${r.refundId}</td>
                            <td style="padding:12px 16px;font-family:monospace;font-size:12px;color:#6b7084;white-space:nowrap;">${r.cardReference || '-'}</td>
                            <td style="padding:12px 16px;font-weight:600;font-size:14px;white-space:nowrap;">${r.currency ? r.currency.toUpperCase() : 'USD'} $${parseFloat(r.amount).toFixed(2)}</td>
                            <td style="padding:12px 16px;white-space:nowrap;">${statusLabel(r.status)}</td>
                            <td style="padding:12px 16px;white-space:normal;word-wrap:break-word;">${r.message || '-'}</td>
                            <td style="padding:12px 16px;color:#8b8fa3;white-space:nowrap;">${r.createdAt ? new Date(r.createdAt).toLocaleString() : '-'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
            </div>`;
    } catch (e) {
        body.innerHTML = '<div style="text-align:center;padding:20px 0;color:#d93025;font-size:13px;">Failed to load: ' + e.message + '</div>';
    }
}

function closeRefundLogsModal() {
    document.getElementById('refundLogsModal').style.display = 'none';
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
