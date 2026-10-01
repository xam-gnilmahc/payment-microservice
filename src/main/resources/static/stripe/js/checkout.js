// ===== GO TO CHECKOUT =====
const money = (v) => Number(v).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function updateCheckoutSummary() {
    const raw = parseFloat(document.getElementById('amountInput').value);
    const shown = Number.isFinite(raw) && raw > 0 ? '$' + money(raw) : '$0.00';
    ['summaryAmount', 'summaryTotal'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = shown;
    });
}

async function goToCheckout() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    hideAll();
    document.getElementById('step-checkout').classList.remove('hidden');
    document.getElementById('selectedGatewayTitle').textContent = selectedGatewayTitle || 'Unknown';
    ['summaryGateway', 'summaryGateway2'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = selectedGatewayTitle || 'Unknown';
    });
    document.getElementById('userAvatar').textContent = (user.name || 'U').charAt(0).toUpperCase();
    document.getElementById('userName').textContent = user.name || 'Unknown';
    document.getElementById('userEmailDisplay').textContent = user.email || '';
    const btn = document.getElementById('checkoutBtn');
    btn.disabled = false;
    btn.textContent = 'Continue to Pay';
    document.getElementById('checkout-error').classList.remove('show');
    paymentIntentData = null;
    showAllAddresses = false;
    updateCheckoutSummary();
    await loadBillingAddresses();
}

document.getElementById('amountInput').addEventListener('input', updateCheckoutSummary);

// ===== GO TO PAY PAGE =====
const MIN_AMOUNT = 0.50;
const MAX_AMOUNT = 999999.99;

document.getElementById('checkoutBtn').addEventListener('click', () => {
    const raw = document.getElementById('amountInput').value.trim();
    const err = document.getElementById('checkout-error');
    if (!raw) { showMsg(err, 'Please enter an amount.'); return; }
    const amount = Number(raw);
    if (!Number.isFinite(amount) || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
        showMsg(err, 'Enter an amount between $' + MIN_AMOUNT.toFixed(2) + ' and $' + MAX_AMOUNT.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '.');
        return;
    }
    if (!selectedAddressId) { showMsg(err, 'Please add a billing address.'); return; }
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    if (!addr) { showMsg(err, 'Selected address not found.'); return; }
    goToCard(amount.toFixed(2));
});
