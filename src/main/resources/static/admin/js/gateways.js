// ===== GATEWAYS SECTION =====
let allGateways = [];
let allUserGateways = [];

async function loadGateways() {
    const body = document.getElementById('gatewaysBody');
    body.innerHTML = '<tr><td colspan="5" class="admin-loading">Loading...</td></tr>';
    try {
        const [gwRes, ugRes, uRes] = await Promise.all([
            fetch('/api/v1/admin/gateways', { headers: authHeaders() }),
            fetch('/api/v1/admin/user-gateways', { headers: authHeaders() }),
            fetch('/api/v1/admin/users', { headers: authHeaders() }),
        ]);
        const gw = await gwRes.json();
        const ug = await ugRes.json();
        const u = await uRes.json();

        allGateways = gw.success && gw.data ? gw.data : [];
        allUserGateways = ug.success && ug.data ? ug.data : [];

        const usersMap = {};
        if (u.success && u.data) u.data.forEach(x => { usersMap[x.id] = x.email || x.name || 'User #' + x.id; });

        if (allGateways.length === 0) {
            body.innerHTML = '<tr><td colspan="5" class="admin-empty">No gateways configured.</td></tr>';
            return;
        }

        body.innerHTML = allGateways.map(g => {
            const assigned = allUserGateways
                .filter(a => a.paymentGatewayId === g.id)
                .map(a => usersMap[a.userId] || 'User #' + a.userId)
                .join(', ') || '-';
            return `
                <tr>
                    <td>${g.id}</td>
                    <td style="font-weight:600;">${esc(g.title) || '-'}</td>
                    <td><span class="status-badge ${g.status === 'ACTIVE' ? 'badge-green' : 'badge-red'}">${g.status === 'ACTIVE' ? 'Active' : 'Inactive'}</span></td>
                    <td>${esc(assigned)}</td>
                    <td style="color:#8b8fa3;">${g.createdAt ? new Date(g.createdAt).toLocaleDateString() : '-'}</td>
                </tr>`;
        }).join('');
    } catch (e) {
        body.innerHTML = '<tr><td colspan="5" class="admin-empty">Failed to load: ' + esc(e.message) + '</td></tr>';
    }
}
