// ===== PAYMENT LOGS =====
async function goToLogs() {
    hideAll();
    document.getElementById('step-logs').classList.remove('hidden');
    document.getElementById('logs-error').classList.remove('show');
    document.getElementById('logsTable').innerHTML = '<div style="text-align:center;padding:20px 0;color:#8b8fa3;font-size:13px;">Loading...</div>';
    document.getElementById('logsEmpty').classList.add('hidden');
    try {
        const res = await fetch('/api/v1/payments', { headers: authHeaders() });
        const data = await res.json();
        if (!data.success || !data.data || data.data.length === 0) {
            document.getElementById('logsTable').innerHTML = '';
            document.getElementById('logsEmpty').classList.remove('hidden');
            return;
        }
        renderLogs(data.data);
    } catch (e) {
        document.getElementById('logsTable').innerHTML = '';
        showMsg(document.getElementById('logs-error'), 'Failed to load: ' + e.message);
    }
}

function renderLogs(logs) {
    const statusBadge = (s) => {
        if (s === 'PROCESSING' || s === '1') return '<span class="status-badge pending">Processing</span>';
        if (s === 'INITIATED' || s === '0') return '<span class="status-badge pending">Initiated</span>';
        return '<span class="status-badge error">Failed</span>';
    };
    const fmt = (d) => {
        if (!d) return '-';
        const dt = new Date(d);
        return dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) + ' ' + dt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
    };
    document.getElementById('logsTable').innerHTML = `
        <div style="overflow-x:auto;">
            <table class="logs-table">
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>Method</th>
                        <th>Card</th>
                        <th>Transaction ID</th>
                        <th>Message</th>
                    </tr>
                </thead>
                <tbody>
                    ${logs.map(l => `
                        <tr>
                            <td>${fmt(l.createdAt)}</td>
                            <td class="amt">${l.currency ? l.currency.toUpperCase() : 'USD'} $${parseFloat(l.amount).toFixed(2)}</td>
                            <td>${statusBadge(l.status)}</td>
                            <td>${l.paymentMethod || '-'}</td>
                            <td class="mono">${l.cardBrand ? l.cardBrand + ' ****' + l.cardLast4 : '-'}</td>
                            <td class="mono">${l.transactionId || '-'}</td>
                            <td class="msg" title="${l.message || ''}">${l.message || '-'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>`;
}
