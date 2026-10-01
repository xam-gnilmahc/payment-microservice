// ===== STATE =====
let payAmount = 0;
let lastAvailableMethods = [];
let expressCheckoutElements = null;
let expressCheckoutEl = null;

// ===== HELPERS =====
function getWalletContainer() {
    return document.getElementById('pr-button-container');
}

function disableAllPaymentButtons() {
    document.getElementById('payBtn').disabled = true;
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = 'none'; wc.style.opacity = '0.5'; }
}

function enableAllPaymentButtons() {
    document.getElementById('payBtn').disabled = false;
    document.getElementById('payBtn').textContent = 'Pay Now';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = ''; wc.style.opacity = ''; }
}

function setProcessing() {
    disableAllPaymentButtons();
    document.getElementById('payBtn').textContent = 'Processing...';
}

// ===== SKELETON LOADING =====
function showCardSkeleton() {
    const skel = document.getElementById('card-skeleton');
    const el = document.getElementById('card-fields');
    if (skel) skel.classList.add('show');
    if (el) el.style.display = 'none';
}

function hideCardSkeleton() {
    const skel = document.getElementById('card-skeleton');
    const el = document.getElementById('card-fields');
    if (skel) skel.classList.remove('show');
    if (el) el.style.display = '';
}

// ===== WALLET PREFERENCE TOGGLE (wallets load in background, reveal on click) =====
// The toggle bar ALWAYS stays visible — it never auto-hides.
let walletMethodsReady = false;
let walletAnyAvailable = false;

function toggleWalletPanel() {
    const panel = document.getElementById('wallet-panel');
    const toggle = document.getElementById('walletToggle');
    if (!panel || !toggle) return;
    const opening = panel.classList.toggle('open') === true;
    toggle.setAttribute('aria-expanded', opening ? 'true' : 'false');
    const hint = toggle.querySelector('.wallet-toggle-hint');
    if (hint) hint.textContent = opening ? 'Hide options' : 'Show options';
    refreshWalletPanel();
}

function refreshWalletPanel() {
    const panel = document.getElementById('wallet-panel');
    if (!panel || !panel.classList.contains('open')) return;
    const loading = document.getElementById('wallet-loading');
    const wc = getWalletContainer();
    const inner = panel.querySelector('.wallet-panel-inner');
    const startH = inner ? inner.getBoundingClientRect().height : 0;
    if (walletAnyAvailable) {
        if (loading) loading.style.display = 'none';
        if (wc) wc.style.display = 'block';
    } else {
        if (wc) wc.style.display = 'none';
        if (loading) {
            loading.style.display = '';
            loading.textContent = walletMethodsReady
                ? 'No wallet options available for this payment — please pay with card below.'
                : 'Loading wallet options…';
        }
    }
    // smooth height change when content swaps while already open (skip opening frame — grid anim covers it)
    if (inner && inner.animate && startH > 1) {
        const endH = inner.getBoundingClientRect().height;
        if (Math.abs(endH - startH) > 1) {
            inner.animate([{ height: startH + 'px' }, { height: endH + 'px' }], { duration: 250, easing: 'ease' });
        }
    }
}

// ===== GO TO CARD PAGE =====
function goToCard(amount) {
    payAmount = parseFloat(amount);
    paymentIntentData = null;
    hideAll();
    document.getElementById('step-card').classList.remove('hidden');
    const amountText = '$' + payAmount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    document.getElementById('payAmount').textContent = amountText;
    const totalEl = document.getElementById('payTotal');
    if (totalEl) totalEl.textContent = amountText;
    updateSummaryAddress();
    setCardMsg('', '');
    document.getElementById('payBtn').disabled = false;
    document.getElementById('payBtn').textContent = 'Pay Now';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = ''; wc.style.opacity = ''; wc.style.display = 'none'; }

    // wallet: reset to collapsed; cards are usable immediately regardless
    walletMethodsReady = false;
    walletAnyAvailable = false;
    const toggle = document.getElementById('walletToggle');
    if (toggle) {
        toggle.style.display = '';
        toggle.setAttribute('aria-expanded', 'false');
        const hint = toggle.querySelector('.wallet-toggle-hint');
        if (hint) hint.textContent = 'Show options';
    }
    const panel = document.getElementById('wallet-panel');
    if (panel) panel.classList.remove('open');
    const loading = document.getElementById('wallet-loading');
    if (loading) { loading.style.display = ''; loading.textContent = 'Loading wallet options…'; }
    const sep = document.getElementById('payOrSeparator');
    if (sep) sep.style.display = 'flex';

    showCardSkeleton();

    if (cardNumberEl) { cardNumberEl.unmount(); cardNumberEl.mount('#card-number'); }
    if (cardExpiryEl) { cardExpiryEl.unmount(); cardExpiryEl.mount('#card-expiry'); }
    if (cardCvcEl) { cardCvcEl.unmount(); cardCvcEl.mount('#card-cvc'); }
    setupExpressCheckout(amount);

    setTimeout(hideCardSkeleton, 3000);
}

// ===== CREATE PAYMENT INTENT ON DEMAND =====
async function createPaymentIntent() {
    const user = getUser();
    const addr = billingAddresses.find(a => a.id === selectedAddressId);
    const res = await fetch('/api/v1/payments', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({
            customerId: parseInt(user.userId),
            amount: payAmount,
            name: addr ? addr.name : user.name,
            email: addr ? addr.email : user.email
        })
    });
    const data = await res.json();
    if (!data.success) throw new Error(data.message || 'Failed to create payment');
    paymentIntentData = data.data;
    return paymentIntentData;
}

// ===== CONFIRM ON BACKEND (fire-and-forget) =====
async function confirmOnBackend(paymentIntentId, paymentMethod) {
    const user = getUser();
    try {
        const res = await fetch('/api/v1/payments/confirm', {
            method: 'POST',
            headers: authHeaders(),
            body: JSON.stringify({ paymentIntentId, customerId: parseInt(user.userId), paymentMethod })
        });
        return await res.json();
    } catch (e) { return null; }
}

// ===== UI STATES =====
function showPaymentSuccess(paymentIntent) {
    setCardMsg('success', 'Payment successful!');
    document.getElementById('payBtn').disabled = true;
    document.getElementById('payBtn').textContent = 'Paid';
    const wc = getWalletContainer();
    if (wc) { wc.style.pointerEvents = 'none'; wc.style.opacity = '0.5'; }
}

function showPaymentError(message) {
    setCardMsg('error', message);
}

// ===== EXPRESS CHECKOUT ELEMENT (Apple Pay, Google Pay, Link) =====
// Runs strictly in this order:
//   STEP 1  cleanup old element
//   STEP 2  create new element (invisible, nothing on page yet)
//   STEP 3  attach events — they are only REGISTERED here, they RUN after STEP 4
//   STEP 4  mount — element goes live, Stripe starts its wallet check
//   STEP 5  safety timeout — note slow loading inside the panel (toggle bar never hides)
// After STEP 4 Stripe fires: availablepaymentmethodschange -> user taps wallet -> confirm
function setupExpressCheckout(amount) {
    // ---- STEP 1: remove previous element (fresh one every visit to this step) ----
    if (expressCheckoutEl) {
        expressCheckoutEl.unmount();
        expressCheckoutEl = null;
        expressCheckoutElements = null;
    }

    // ---- STEP 2: create element (memory only, nothing visible yet) ----
    const totalInCents = Math.round(parseFloat(amount) * 100);

    expressCheckoutElements = stripe.elements({
        mode: 'payment',
        amount: totalInCents,
        currency: 'usd',
    });

    expressCheckoutEl = expressCheckoutElements.create('expressCheckout', {
        paymentMethods: {
            googlePay: 'always',
            applePay: 'always'
        },
        buttonType: { googlePay: 'checkout', applePay: 'check-out' },
        buttonHeight: 40,
        layout: { maxColumns: 3, maxRows: 2, overflow: 'auto' }
    });

    // NOTE: both events below are registered HERE (before mount),
    // but they only TRIGGER after STEP 4 (mount) — Stripe's reply and
    // the user's tap both happen after the element goes live.

    // ---- STEP 3a: REGISTER event — RUNS after STEP 4, when Stripe replies with wallet list ----
    expressCheckoutEl.on('availablepaymentmethodschange', ({ paymentMethods }) => {
        console.log('[ExpressCheckout] available methods:', paymentMethods);
        lastAvailableMethods = paymentMethods || {};
        walletMethodsReady = true;
        walletAnyAvailable = paymentMethods && typeof paymentMethods === 'object' &&
            Object.values(paymentMethods).some(m => m && m.available);
        refreshWalletPanel();
    });

    // ---- STEP 3b: REGISTER event — RUNS after STEP 4, when user taps a wallet button ----
    expressCheckoutEl.on('confirm', async (event) => {
        console.log('[ExpressCheckout] confirm event fired', event);
        const walletType = event.expressPaymentType || 'wallet';
        console.log('[ExpressCheckout] wallet type:', walletType);
        setProcessing();

        try {
            if (!paymentIntentData || !paymentIntentData.clientSecret) {
                await createPaymentIntent();
            }

            const { error: submitError } = await expressCheckoutElements.submit();
            if (submitError) {
                console.error('[ExpressCheckout] submit error:', submitError);
                showPaymentError(submitError.message);
                enableAllPaymentButtons();
                return;
            }

            const { error, paymentIntent } = await stripe.confirmPayment({
                elements: expressCheckoutElements,
                clientSecret: paymentIntentData.clientSecret,
                confirmParams: { return_url: window.location.origin + '/payment.html' },
                redirect: 'if_required'
            });

            console.log('[ExpressCheckout] confirmPayment result:', { error, status: paymentIntent?.status });

            if (error) {
                console.error('[ExpressCheckout] confirmPayment error:', error);
                showPaymentError(error.message);
                enableAllPaymentButtons();
                return;
            }

            if (paymentIntent.status === 'succeeded') {
                showPaymentSuccess(paymentIntent);
                confirmOnBackend(paymentIntent.id, walletType);
            } else if (paymentIntent.status === 'requires_action') {
                console.log('[ExpressCheckout] requires_action — Stripe will handle redirect');
            } else {
                console.log('[ExpressCheckout] unexpected status:', paymentIntent.status);
            }
        } catch (err) {
            console.error('[ExpressCheckout] catch error:', err);
            showPaymentError('Server error: ' + err.message);
            enableAllPaymentButtons();
        }
    });

    // ---- STEP 4: MOUNT — element goes live; STEP 3 events fire AFTER this ----
    // container stays hidden until Stripe replies AND the user opens the wallet panel
    expressCheckoutEl.mount('#pr-button-container');

    // ---- STEP 5: safety net — if Stripe is slow, say so inside the panel (bar stays) ----
    setTimeout(() => {
        if (!walletMethodsReady) {
            const loading = document.getElementById('wallet-loading');
            const panel = document.getElementById('wallet-panel');
            if (loading && panel && panel.classList.contains('open')) {
                loading.textContent = 'Wallet options are taking longer than usual…';
            }
        }
    }, 4000);
}

// ===== PAY WITH CARD =====
document.getElementById('payBtn').addEventListener('click', async () => {
    const user = getUser();
    setCardMsg('', '');
    setProcessing();

    try {
        if (!paymentIntentData || !paymentIntentData.clientSecret) {
            await createPaymentIntent();
        }

        const addr = billingAddresses.find(a => a.id === selectedAddressId);
        const billingDetails = addr ? {
            name: addr.name,
            email: addr.email,
            address: {
                line1: addr.addressLine1,
                line2: addr.addressLine2 || '',
                city: addr.city,
                state: addr.state,
                postal_code: addr.zipCode,
                country: addr.country
            }
        } : {};

        const { error, paymentIntent } = await stripe.confirmCardPayment(paymentIntentData.clientSecret, {
            payment_method: { card: cardNumberEl, billing_details: billingDetails }
        });

        if (error) {
            setCardMsg('error', error.message);
            enableAllPaymentButtons();
            return;
        }
        showPaymentSuccess(paymentIntent);
        confirmOnBackend(paymentIntent.id, 'card');
    } catch (e) {
        setCardMsg('error', e.message);
        enableAllPaymentButtons();
    }
});
