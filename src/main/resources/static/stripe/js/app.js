// ===== SHARED STATE =====
let paymentIntentData = null;
let billingAddresses = [];
let selectedAddressId = null;
let showAllAddresses = false;
let selectedGatewayId = null;
let selectedGatewayTitle = null;
let stripe = null;
let elements = null;
let cardNumberEl = null;
let cardExpiryEl = null;
let cardCvcEl = null;

// ===== HELPERS =====
// Payment results are raised as the central notification only - the #card-msg area on this page
// is cleared and never filled in, so nothing on the card form shifts when a result arrives.
// `silent` is for Stripe's own field validation, which fires on every keystroke: it belongs to
// the field, not to the popup.
function setCardMsg(type, text, silent) {
    hideOtherMsgs();
    if (text && !silent) notify(type === 'success' ? 'success' : 'error', text);
}
function resetPaymentState() { sessionUser = null; paymentIntentData = null; selectedGatewayId = null; selectedGatewayTitle = null; stripe = null; elements = null; cardNumberEl = null; cardExpiryEl = null; cardCvcEl = null; }
// used when the session is gone or unusable: just bounce to the login page
function goToLogin() { resetPaymentState(); window.location.href = '/index.html'; }
// used by the Logout buttons: ends the session on the server first, so the cookie cannot be
// reused. Redirecting on its own left the session alive, which is not a sign-out.
function signOut() {
    resetPaymentState();
    fetch('/api/v1/auth/logout', { method: 'POST', headers: authHeaders() })
        .catch(() => {})
        .finally(() => { window.location.href = '/index.html'; });
}
function goToNoGateway() { hideAll(); document.getElementById('step-no-gateway').classList.remove('hidden'); }

// ===== PAY SESSION COUNTDOWN =====
// The "Pay now" step is live for three minutes. The clock is shown next to the card form, holds
// while a charge is actually in flight, and runs out by walking back to "Continue to pay" - so a
// card form left open never sits there forever looking usable.
const PAY_SESSION_MS = 3 * 60 * 1000;
const PAY_SESSION_URGE_MS = 30 * 1000;
let paySessionEndsAt = 0;
let paySessionTimer = null;

function startPaySession() {
    stopPaySession();
    paySessionEndsAt = Date.now() + PAY_SESSION_MS;
    const box = document.getElementById('payCountdown');
    if (box) box.hidden = false;
    renderPaySession();
    paySessionTimer = setInterval(renderPaySession, 250);
}

function stopPaySession() {
    if (paySessionTimer) { clearInterval(paySessionTimer); paySessionTimer = null; }
    paySessionEndsAt = 0;
    const box = document.getElementById('payCountdown');
    if (box) { box.hidden = true; box.classList.remove('is-urgent'); }
}

function renderPaySession() {
    const box = document.getElementById('payCountdown');
    const val = document.getElementById('payCountdownValue');
    if (!box || !val) { stopPaySession(); return; }
    // only the pay step expires: anywhere else (checkout, history, a redirect) ends the clock
    const step = document.getElementById('step-card');
    if (step && step.classList.contains('hidden')) { stopPaySession(); return; }

    const left = Math.max(0, paySessionEndsAt - Date.now());
    const secs = Math.ceil(left / 1000);
    val.textContent = String(Math.floor(secs / 60)).padStart(2, '0') + ':' + String(secs % 60).padStart(2, '0');
    box.classList.toggle('is-urgent', left <= PAY_SESSION_URGE_MS);
    if (left > 0) return;

    // a charge in flight is never interrupted: hold the clock until it settles
    const btn = document.getElementById('payBtn');
    if (btn && btn.disabled && btn.textContent.trim() === 'Processing...') {
        paySessionEndsAt = Date.now() + 15000;
        return;
    }

    stopPaySession();
    notifyError('Payment time is up. Nothing was charged - check the amount and continue to pay again.');
    goToCheckout();
}

// ===== INIT STRIPE WITH PUBLIC KEY =====
function initStripe(publicKey) {
    stripe = Stripe(publicKey);
    elements = stripe.elements();
    const onCardChange = (e) => { setCardMsg(e.error ? 'error' : '', e.error ? e.error.message : '', true); };
    cardNumberEl = elements.create('cardNumber');
    cardExpiryEl = elements.create('cardExpiry');
    cardCvcEl = elements.create('cardCvc');
    cardNumberEl.on('change', onCardChange);
    cardExpiryEl.on('change', onCardChange);
    cardCvcEl.on('change', onCardChange);
    cardNumberEl.on('ready', () => {
        if (typeof hideCardSkeleton === 'function') hideCardSkeleton();
    });
}

// ===== LOAD USER GATEWAY & GO TO CHECKOUT =====
async function loadUserGatewayAndCheckout() {
    const user = getUser();
    if (!user) { goToLogin(); return; }
    try {
        const res = await fetch('/api/v1/payment-gateways/user/' + user.userId + '/enabled', { headers: authHeaders() });
        if (res.status === 401 || res.status === 403) { goToLogin(); return; }
        const data = await res.json();
        if (!data.success || !data.data) { goToNoGateway(); return; }
        selectedGatewayId = data.data.gatewayId;
        selectedGatewayTitle = data.data.title;
        document.getElementById('headerGatewayBadge').textContent = selectedGatewayTitle;
        const publicKey = data.data.publicKey;
        if (!publicKey) { goToNoGateway(); return; }
        initStripe(publicKey);
        goToCheckout();
    } catch (e) {
        goToLogin();
    }
}

// ===== HANDLE WALLET RETURN URL =====
const urlParams = new URLSearchParams(window.location.search);
const returnedPiId = urlParams.get('payment_intent');
const returnedPiSecret = urlParams.get('payment_intent_client_secret');
const returnedStatus = urlParams.get('redirect_status');

if (returnedPiId && returnedPiSecret) {
    window.history.replaceState({}, '', window.location.pathname);

    const checkStripe = setInterval(() => {
        if (typeof stripe !== 'undefined' && stripe !== null) {
            clearInterval(checkStripe);
            stripe.retrievePaymentIntent(returnedPiSecret).then(({ error, paymentIntent }) => {
                if (error) {
                    console.error('[Return] Failed to retrieve PI:', error);
                    return;
                }
                console.log('[Return] PaymentIntent status:', paymentIntent.status);

                hideAll();
                document.getElementById('step-card').classList.remove('hidden');

                if (paymentIntent.status === 'succeeded') {
                    setCardMsg('success', 'Payment successful!');
                    confirmOnBackend(paymentIntent.id, 'wallet');
                } else {
                    setCardMsg('error', 'Payment status: ' + paymentIntent.status);
                }
            });
        }
    }, 100);
}

// ===== INIT =====
if (!returnedPiId && !returnedPiSecret) {
    loadSessionUser().then(user => {
        if (user) loadUserGatewayAndCheckout();
        else goToLogin();
    });
}
