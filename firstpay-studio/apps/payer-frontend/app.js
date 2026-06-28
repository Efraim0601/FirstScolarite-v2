/*
 * Page payeur publique FirstPay — app statique autonome (sans framework).
 * Flux : résout l'URL /{shortCode}/{slug} via l'API publique, rend le parcours conçu par le
 * partenaire, initie le paiement puis interroge le statut jusqu'à l'état final.
 * Toute la validation faisant autorité est côté serveur ; ici on ne fait qu'assister la saisie.
 */
(function () {
  'use strict';

  var METHOD_LABELS = { orange: 'Orange Money', mtn: 'MTN MoMo', card: 'Carte bancaire', transfer: 'Virement' };
  var bodyEl = document.getElementById('body');
  var merchantEl = document.getElementById('merchant');
  var logoEl = document.getElementById('logo');

  // --- Routing : extrait shortCode + slug des deux derniers segments du chemin ---
  var segs = window.location.pathname.split('/').filter(Boolean);
  var shortCode = segs[segs.length - 2];
  var slug = segs[segs.length - 1];

  var state = { data: null, method: null, presetId: null };

  if (!shortCode || !slug) { renderError('Lien de paiement invalide.', 'Vérifiez l’adresse reçue.'); return; }

  // --- 1) Résolution de la config publique ---
  fetch('/public/p/' + encodeURIComponent(shortCode) + '/' + encodeURIComponent(slug))
    .then(function (r) {
      if (r.status === 404) throw { handled: true };
      if (!r.ok) throw new Error('http ' + r.status);
      return r.json();
    })
    .then(function (data) { state.data = data; renderForm(); })
    .catch(function (e) {
      if (e && e.handled) renderError('Page de paiement introuvable', 'Ce lien n’existe pas ou n’est plus actif.');
      else renderError('Service indisponible', 'Réessayez dans un instant.');
    });

  function money(n) { return Number(n).toLocaleString('fr-FR'); }

  function applyBrand(d) {
    merchantEl.textContent = d.merchant.name || 'FirstPay';
    if (d.merchant.brandColor) document.documentElement.style.setProperty('--fp', d.merchant.brandColor);
    if (d.merchant.logoUrl) { logoEl.innerHTML = ''; var img = document.createElement('img'); img.src = d.merchant.logoUrl; img.alt = ''; logoEl.appendChild(img); }
    else logoEl.textContent = (d.merchant.shortCode || 'FP').slice(0, 4).toUpperCase();
    document.title = 'Payer · ' + (d.name || d.merchant.name);
  }

  // --- 2) Rendu du parcours ---
  function renderForm() {
    var d = state.data;
    applyBrand(d);

    var h = '';
    h += '<div class="iname"></div>';
    if (d.description) h += '<div class="idesc"></div>';

    // Montant
    h += '<div class="lbl">Montant à payer</div>';
    if (d.amountType === 'fixed') {
      h += '<div class="big" id="amtFixed">' + money(d.fixedAmount || 0) + ' <small>' + d.currency + '</small></div>';
    } else if (d.amountType === 'preset') {
      h += '<div class="presets" id="presets">';
      (d.presets || []).forEach(function (p) {
        h += '<div class="preset" data-id="' + p.id + '"><span>' + esc(p.label || '') + '</span><b>' + money(p.amount) + ' ' + d.currency + '</b></div>';
      });
      h += '</div>';
    } else { // free
      h += '<div class="field"><input type="number" id="freeAmount" inputmode="numeric" min="0" placeholder="Saisir le montant (' + d.currency + ')"></div>';
      var hint = [];
      if (d.minAmount) hint.push('min ' + money(d.minAmount));
      if (d.maxAmount) hint.push('max ' + money(d.maxAmount));
      if (hint.length) h += '<div class="rsub" style="margin-top:6px;font-size:12px">' + hint.join(' · ') + ' ' + d.currency + '</div>';
    }

    // Champs personnalisés
    (d.customFields || []).forEach(function (f) {
      h += '<div class="field"><label>' + esc(f.label) + (f.required ? ' <span class="req">*</span>' : '') + '</label>';
      if (f.type === 'select') {
        h += '<select data-fid="' + f.id + '"><option value="">Choisir…</option>';
        (f.options || []).forEach(function (o) { h += '<option value="' + esc(o) + '">' + esc(o) + '</option>'; });
        h += '</select>';
      } else {
        h += '<input type="text" data-fid="' + f.id + '" placeholder="Saisir…">';
      }
      h += '</div>';
    });

    // Téléphone (mobile money)
    h += '<div class="field" id="phoneField"><label>Téléphone <span class="req">*</span></label>'
       + '<input type="tel" id="phone" inputmode="tel" placeholder="+237 6XX XX XX XX"></div>';

    // Moyens de paiement
    h += '<div class="lbl">Moyen de paiement</div><div class="methods" id="methods">';
    Object.keys(d.methods || {}).filter(function (k) { return d.methods[k]; }).forEach(function (k) {
      h += '<div class="m ' + k + '" data-m="' + k + '">' + (METHOD_LABELS[k] || k) + '</div>';
    });
    h += '</div>';

    h += '<button class="pay" id="payBtn">Payer maintenant</button>';
    h += '<div class="err" id="err"></div>';
    h += '<div class="secured">🔒 Paiement sécurisé · Afriland First Bank</div>';

    bodyEl.innerHTML = h;
    bodyEl.querySelector('.iname').textContent = d.name || '';
    if (d.description) bodyEl.querySelector('.idesc').textContent = d.description;

    // Sélecteur de presets
    if (d.amountType === 'preset') {
      bindClicks('#presets .preset', function (el) {
        bodyEl.querySelectorAll('#presets .preset').forEach(function (x) { x.classList.remove('on'); });
        el.classList.add('on'); state.presetId = Number(el.getAttribute('data-id'));
      });
    }
    // Sélecteur de moyen — présélectionne le premier
    bindClicks('#methods .m', function (el) {
      bodyEl.querySelectorAll('#methods .m').forEach(function (x) { x.classList.remove('on'); });
      el.classList.add('on'); state.method = el.getAttribute('data-m'); syncPhoneVisibility();
    });
    var firstM = bodyEl.querySelector('#methods .m');
    if (firstM) firstM.click();

    document.getElementById('payBtn').addEventListener('click', submit);
  }

  function syncPhoneVisibility() {
    var mm = state.method === 'orange' || state.method === 'mtn';
    document.getElementById('phoneField').classList.toggle('hide', !mm);
  }

  // --- 3) Initiation du paiement ---
  function submit() {
    var d = state.data, err = document.getElementById('err');
    err.style.display = 'none';

    var payload = { method: state.method, fields: {} };
    if (d.amountType === 'preset') {
      if (!state.presetId) return showErr('Veuillez choisir un montant.');
      payload.presetId = state.presetId;
    } else if (d.amountType === 'free') {
      payload.amount = (document.getElementById('freeAmount').value || '').trim();
      if (!payload.amount) return showErr('Veuillez saisir un montant.');
    }
    var missing = null;
    (d.customFields || []).forEach(function (f) {
      var el = bodyEl.querySelector('[data-fid="' + f.id + '"]');
      var v = el ? el.value.trim() : '';
      if (v) payload.fields[f.id] = v;
      if (f.required && !v && !missing) missing = f.label;
    });
    if (missing) return showErr('Champ requis : ' + missing);

    var mm = state.method === 'orange' || state.method === 'mtn';
    if (mm) {
      var phone = (document.getElementById('phone').value || '').trim();
      if (phone.replace(/\D/g, '').length < 8) return showErr('Numéro de téléphone invalide.');
      payload.phone = phone;
    }

    var btn = document.getElementById('payBtn');
    btn.disabled = true; btn.textContent = 'Traitement…';

    fetch('/public/p/' + encodeURIComponent(shortCode) + '/' + encodeURIComponent(slug) + '/pay', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
      .then(function (res) {
        if (!res.ok) { btn.disabled = false; btn.textContent = 'Payer maintenant';
          return showErr(res.j && res.j.message ? res.j.message : 'Le paiement a été refusé.'); }
        renderPending(res.j);
      })
      .catch(function () { btn.disabled = false; btn.textContent = 'Payer maintenant'; showErr('Erreur réseau. Réessayez.'); });
  }

  // --- 4) Attente + polling du statut ---
  function renderPending(pay) {
    bodyEl.innerHTML = '<div class="center"><div class="spin"></div>'
      + '<div class="rtitle">Paiement en cours…</div>'
      + '<div class="rsub">Validez la demande sur votre téléphone si demandé.</div>'
      + '<div class="ref">Réf. ' + esc(pay.reference) + '</div></div>';
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      fetch('/public/tx/' + encodeURIComponent(pay.transactionId))
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (tx) {
          if (tx && (tx.status === 'SUCCESS' || tx.status === 'FAILED')) { clearInterval(timer); renderResult(tx); }
          else if (tries >= 30) { clearInterval(timer); renderResult({ status: 'PENDING', reference: pay.reference, amount: '', currency: '' }); }
        })
        .catch(function () {});
    }, 2000);
  }

  function renderResult(tx) {
    var d = state.data || { currency: tx.currency };
    var amt = tx.amount ? money(tx.amount) + ' ' + (tx.currency || d.currency || '') : '';
    var h = '<div class="center">';
    if (tx.status === 'SUCCESS') {
      h += '<div class="ico ok">✓</div><div class="rtitle">Paiement réussi</div>'
        + '<div class="rsub">Votre paiement' + (amt ? ' de <b>' + amt + '</b>' : '') + ' a été confirmé.</div>';
    } else if (tx.status === 'FAILED') {
      h += '<div class="ico ko">✕</div><div class="rtitle">Paiement échoué</div>'
        + '<div class="rsub">La transaction n’a pas abouti. Aucun montant n’a été débité.</div>';
    } else {
      h += '<div class="ico" style="background:#FFF7E6;color:#B7791F">⏳</div><div class="rtitle">Paiement en attente</div>'
        + '<div class="rsub">Le traitement prend plus de temps que prévu. Vous recevrez une confirmation.</div>';
    }
    if (tx.reference) h += '<div class="ref">Réf. ' + esc(tx.reference) + '</div>';
    if (tx.status === 'FAILED') h += '<br><button class="ghost" onclick="location.reload()">Réessayer</button>';
    h += '</div>';
    bodyEl.innerHTML = h;
  }

  function renderError(title, sub) {
    merchantEl.textContent = 'FirstPay';
    bodyEl.innerHTML = '<div class="center"><div class="ico ko">!</div><div class="rtitle">' + esc(title)
      + '</div><div class="rsub">' + esc(sub) + '</div></div>';
  }

  function showErr(msg) { var e = document.getElementById('err'); e.textContent = msg; e.style.display = 'block'; }
  function bindClicks(sel, fn) { bodyEl.querySelectorAll(sel).forEach(function (el) { el.addEventListener('click', function () { fn(el); }); }); }
  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
})();
