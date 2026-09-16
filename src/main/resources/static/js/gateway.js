// ===== INIT GATEWAY (on page refresh) =====
async function initGateway(userId) {
    try {
        const res = await fetch('/api/v1/payment-gateways/user/' + userId + '/details', { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { goToLogin(); return; }
        const data = await res.json();
        if (!data.success || !data.data || data.data.length === 0) {
            document.getElementById('gateway-list').innerHTML = '';
            document.getElementById('gateway-empty').classList.remove('hidden');
            return;
        }
        renderGateways(data.data);
    } catch (e) {
        document.getElementById('gateway-list').innerHTML = '';
        showMsg(document.getElementById('gateway-error'), 'Failed to load: ' + e.message);
    }
}

// ===== SELECT GATEWAY =====
async function goToGateway() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    hideAll();
    document.getElementById('step-gateway').classList.remove('hidden');
    document.getElementById('gateway-error').classList.remove('show');
    document.getElementById('gateway-list').innerHTML = '<div style="text-align:center;padding:20px 0;color:#8b8fa3;font-size:13px;">Loading...</div>';
    document.getElementById('gateway-empty').classList.add('hidden');

    try {
        const res = await fetch('/api/v1/payment-gateways/user/' + user.userId + '/details', { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { goToLogin(); return; }
        const data = await res.json();
        if (!data.success || !data.data || data.data.length === 0) {
            document.getElementById('gateway-list').innerHTML = '';
            document.getElementById('gateway-empty').classList.remove('hidden');
            return;
        }
        renderGateways(data.data);
    } catch (e) {
        document.getElementById('gateway-list').innerHTML = '';
        showMsg(document.getElementById('gateway-error'), 'Failed to load: ' + e.message);
    }
}

function renderGateways(gateways) {
    document.getElementById('gateway-list').innerHTML = gateways.map(g => {
        const disabled = !g.hasCredentials;
        return `
        <div class="address-card ${disabled ? 'gateway-disabled' : ''}"
             ${disabled ? '' : `onclick="selectGateway(${g.gatewayId}, '${g.title}')"`}
             style="${disabled ? 'cursor:not-allowed;opacity:0.5;' : 'cursor:pointer;'}">
            <div class="address-card-icon">${g.image ? '<img src="' + g.image + '" style="width:20px;height:20px;" alt="' + g.title + '">' : '&#128179;'}</div>
            <div class="address-card-info">
                <div class="address-card-name">${g.title}</div>
                <div class="address-card-line">${g.description || 'No description'}</div>
            </div>
            ${g.hasCredentials ? '<span class="address-card-tag">Configured</span>' : '<span style="font-size:10px;color:#b91c1c;">Setup needed</span>'}
        </div>`;
    }).join('');
}

function selectGateway(gatewayId, title) {
    selectedGatewayId = gatewayId;
    selectedGatewayTitle = title;
    document.getElementById('headerGatewayBadge').textContent = title;
    goToCheckout();
}

// ===== RUN INIT ON PAGE LOAD =====
(function() {
    const user = getUser();
    if (user && document.getElementById('step-gateway') && !document.getElementById('step-gateway').classList.contains('hidden')) {
        document.getElementById('gateway-error').classList.remove('show');
        document.getElementById('gateway-list').innerHTML = '<div style="text-align:center;padding:20px 0;color:#8b8fa3;font-size:13px;">Loading...</div>';
        document.getElementById('gateway-empty').classList.add('hidden');
        initGateway(user.userId);
    }
})();
