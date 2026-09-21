// ===== GO TO CHECKOUT =====
async function goToCheckout() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    hideAll();
    document.getElementById('step-checkout').classList.remove('hidden');
    document.getElementById('selectedGatewayTitle').textContent = selectedGatewayTitle || 'Unknown';
    document.getElementById('userAvatar').textContent = (user.name || 'U').charAt(0).toUpperCase();
    document.getElementById('userName').textContent = user.name || 'Unknown';
    document.getElementById('userEmailDisplay').textContent = user.email || '';
    const btn = document.getElementById('checkoutBtn');
    btn.disabled = false;
    btn.textContent = 'Continue to Pay';
    document.getElementById('checkout-error').classList.remove('show');
    paymentIntentData = null;
    showAllAddresses = false;
    await loadBillingAddresses();
}

// ===== GO TO PAY PAGE =====
document.getElementById('checkoutBtn').addEventListener('click', () => {
    const amount = document.getElementById('amountInput').value.trim();
    const err = document.getElementById('checkout-error');
    if (!amount || parseFloat(amount) < 0.50) { showMsg(err, 'Minimum amount is $0.50.'); return; }
    if (!selectedAddressId) { showMsg(err, 'Please add a billing address.'); return; }
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    if (!addr) { showMsg(err, 'Selected address not found.'); return; }
    goToCard(amount);
});
