/* OmniPage reflow reader runtime. Talks to Kotlin through window.OmniBridge. */
(function () {
  'use strict';

  var root = document.documentElement;
  var B = window.OmniBridge;
  if (!B) { return; }

  var mode = 'scroll';      // 'scroll' | 'paged'
  var pages = 1;
  var page = 0;
  var pendingFraction = 0;  // where the reader wants to be; survives relayouts
  var ready = false;
  var raf = 0;
  var VW = 0, VH = 0;       // viewport size in CSS px, supplied by Android (authoritative when > 0)

  function vw() { return VW > 50 ? VW : (window.innerWidth || root.clientWidth || 1); }
  function vh() { return VH > 50 ? VH : (window.innerHeight || root.clientHeight || 1); }
  function span() { return Math.max(0, root.scrollHeight - vh()); }

  function hasSelection() {
    var s = window.getSelection && window.getSelection();
    return !!(s && !s.isCollapsed && String(s).length > 0);
  }

  /** Pixel sizes for the CSS, so layout never depends on the browser's own idea of the viewport. */
  function syncViewport() {
    root.style.setProperty('--o-vw', vw() + 'px');
    root.style.setProperty('--o-vh', vh() + 'px');
  }

  function measure() {
    if (mode === 'paged') {
      var total = Math.max(root.scrollWidth, document.body ? document.body.scrollWidth : 0);
      pages = Math.max(1, Math.ceil((total - 4) / vw()));
      if (page > pages - 1) { page = pages - 1; }
    } else {
      pages = 1;
      page = 0;
    }
  }

  function currentFraction() {
    if (mode === 'paged') { return pages > 1 ? page / (pages - 1) : 0; }
    var s = span();
    return s > 0 ? Math.min(1, Math.max(0, window.scrollY / s)) : 0;
  }

  function schedule() {
    if (!raf) { raf = requestAnimationFrame(report); }
  }

  function report() {
    raf = 0;
    if (!ready) { return; }
    var atStart, atEnd;
    if (mode === 'paged') {
      atStart = page <= 0;
      atEnd = page >= pages - 1;
    } else {
      var y = window.scrollY;
      atStart = y <= 2;
      atEnd = y >= span() - 2;
    }
    var f = currentFraction();
    pendingFraction = f;
    try { B.onPosition(f, page, pages, atStart, atEnd); } catch (e) { console.error('bridge onPosition failed: ' + e); }
  }

  function goPage(p) {
    page = Math.max(0, Math.min(pages - 1, p));
    window.scrollTo(page * vw(), 0);
    schedule();
  }

  function goFraction(f) {
    f = Math.max(0, Math.min(1, f || 0));
    if (mode === 'paged') {
      goPage(Math.round(f * (pages - 1)));
    } else {
      window.scrollTo(0, f * span());
      schedule();
    }
  }

  function layoutNow() {
    syncViewport();
    measure();
    verifyLayout();
    goFraction(pendingFraction);
  }

  /**
   * Paged mode needs a body that is (almost) as tall as the screen. If the browser hands us a collapsed
   * page (it would show one line per page), fall back to continuous scroll rather than ship a broken page.
   */
  function verifyLayout() {
    if (mode !== 'paged' || !document.body) { return; }
    var bodyH = document.body.getBoundingClientRect().height;
    console.log('omni layout: viewport=' + vw() + 'x' + vh() + ' inner=' + window.innerWidth + 'x' + window.innerHeight +
      ' body=' + Math.round(bodyH) + ' pages=' + pages);
    if (bodyH < vh() * 0.5) {
      console.error('paged layout collapsed (body ' + Math.round(bodyH) + 'px of ' + vh() + 'px); using scroll mode');
      mode = 'scroll';
      root.classList.remove('o-paged');
      syncViewport();
      measure();
    }
  }

  function applyConfig(c) {
    if (c.vw > 0) { VW = c.vw; }
    if (c.vh > 0) { VH = c.vh; }
    var s = root.style;
    s.setProperty('--o-bg', c.bg);
    s.setProperty('--o-fg', c.fg);
    s.setProperty('--o-link', c.link);
    s.setProperty('--o-fs', c.fs + 'px');
    s.setProperty('--o-lh', String(c.lh));
    s.setProperty('--o-mx', c.mx + 'px');
    s.setProperty('--o-my', Math.max(14, Math.round(c.mx * 0.7)) + 'px');
    s.setProperty('--o-ff', c.ff);
    root.classList.toggle('o-paged', !!c.paged);
    root.classList.toggle('o-justify', !!c.justify);
    mode = c.paged ? 'paged' : 'scroll';
    syncViewport();
  }

  // ------------------------------------------------------------------------------------------
  // Highlights. A highlight is a [start, end) character range over the body's text nodes, which is
  // stable for a given chapter, so it can be stored and re-applied on every load.
  // ------------------------------------------------------------------------------------------

  function skipNode(n) {
    var p = n.parentNode;
    return !!(p && (p.nodeName === 'SCRIPT' || p.nodeName === 'STYLE'));
  }

  function clearHighlights() {
    var spans = document.querySelectorAll('span.o-hl');
    for (var i = 0; i < spans.length; i++) {
      var sp = spans[i];
      var parent = sp.parentNode;
      if (!parent) { continue; }
      while (sp.firstChild) { parent.insertBefore(sp.firstChild, sp); }
      parent.removeChild(sp);
      parent.normalize();
    }
  }

  function applyHighlight(h) {
    if (!document.body || !(h.end > h.start)) { return; }
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var pos = 0, node, todo = [];
    while ((node = walker.nextNode())) {
      var len = node.nodeValue.length;
      var from = pos, to = pos + len;
      if (to > h.start && from < h.end && !skipNode(node)) {
        todo.push({ node: node, a: Math.max(h.start, from) - from, b: Math.min(h.end, to) - from });
      }
      pos = to;
      if (pos >= h.end) { break; }
    }
    for (var i = 0; i < todo.length; i++) {
      var t = todo[i];
      if (t.b <= t.a) { continue; }
      var r = document.createRange();
      r.setStart(t.node, t.a);
      r.setEnd(t.node, t.b);
      var span = document.createElement('span');
      span.className = 'o-hl';
      span.setAttribute('data-hl', String(h.id));
      try { r.surroundContents(span); } catch (e) { console.error('highlight failed: ' + e); }
    }
  }

  function selectionInfo() {
    var sel = window.getSelection();
    if (!sel || sel.rangeCount === 0 || sel.isCollapsed) { return null; }
    var text = String(sel).replace(/\s+/g, ' ').trim();
    if (!text) { return null; }
    var r = sel.getRangeAt(0);
    var pre = document.createRange();
    pre.selectNodeContents(document.body);
    pre.setEnd(r.startContainer, r.startOffset);
    var start = pre.toString().length;
    return { start: start, end: start + r.toString().length, text: text };
  }

  window.omni = {
    /** Called once per chapter load. */
    start: function (cfg, fraction, anchorId) {
      pendingFraction = fraction || 0;
      ready = false;
      applyConfig(cfg);

      var finished = false;
      var finish = function () {
        if (finished) { return; }
        finished = true;
        try {
          layoutNow();
          if (anchorId) { window.omni.anchor(anchorId); }
        } catch (e) {
          console.error('layout failed: ' + e);
        }
        ready = true;
        try { B.onReady(); } catch (e) { console.error('bridge onReady failed: ' + e); }
        schedule();
      };

      // Wait for the bundled fonts so page breaks are computed with the final glyph metrics.
      if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(function () { requestAnimationFrame(finish); }, finish);
      } else {
        requestAnimationFrame(finish);
      }
      setTimeout(finish, 1500);
    },

    /** Live settings change (font size, theme, mode…). Keeps the reader at the same place. */
    configure: function (cfg) {
      if (ready) { pendingFraction = currentFraction(); }
      applyConfig(cfg);
      layoutNow();
      schedule();
    },

    /** The Android view changed size (rotation, split screen…). */
    resize: function (w, h) {
      VW = w; VH = h;
      syncViewport();
      if (ready) { layoutNow(); }
    },

    seek: function (f) { goFraction(f); },

    step: function (dir) {
      if (mode === 'paged') {
        if (dir > 0) {
          if (page >= pages - 1) { B.onEdge(1); } else { goPage(page + 1); }
        } else {
          if (page <= 0) { B.onEdge(-1); } else { goPage(page - 1); }
        }
      } else {
        var s = span();
        var y = window.scrollY;
        if (dir > 0) {
          if (y >= s - 2) { B.onEdge(1); } else { window.scrollTo(0, Math.min(s, y + vh() * 0.88)); }
        } else {
          if (y <= 2) { B.onEdge(-1); } else { window.scrollTo(0, Math.max(0, y - vh() * 0.88)); }
        }
      }
    },

    anchor: function (id) {
      var el = document.getElementById(id);
      if (!el) { el = document.getElementsByName(id)[0]; }
      if (!el) { return false; }
      var r = el.getBoundingClientRect();
      if (mode === 'paged') {
        goPage(Math.floor((r.left + window.scrollX + 2) / vw()));
      } else {
        window.scrollTo(0, Math.max(0, r.top + window.scrollY - 8));
        schedule();
      }
      return true;
    },

    /** Replaces every highlight on the page with [{id, start, end}, ...]. */
    setHighlights: function (list) {
      clearHighlights();
      for (var i = 0; i < list.length; i++) { applyHighlight(list[i]); }
    },

    /** {start, end, text} for the current selection, or null. */
    selectionInfo: function () { return selectionInfo(); },

    clearSelection: function () {
      var s = window.getSelection();
      if (s) { s.removeAllRanges(); }
    }
  };

  window.addEventListener('scroll', function () {
    if (mode === 'scroll' && ready) { schedule(); }
  }, { passive: true });

  window.addEventListener('resize', function () {
    if (ready) { layoutNow(); }
  });

  if (document.fonts && document.fonts.addEventListener) {
    document.fonts.addEventListener('loadingdone', function () {
      if (ready) { layoutNow(); }
    });
  }

  // Taps. Links are left to the WebView so Kotlin can resolve them; highlights open their own menu.
  document.addEventListener('click', function (e) {
    if (hasSelection()) { return; }
    var el = e.target;
    var a = el && el.closest ? el.closest('a[href]') : null;
    if (a) { return; }
    var hl = el && el.closest ? el.closest('span.o-hl') : null;
    if (hl) {
      try { B.onHighlightTap(Number(hl.getAttribute('data-hl'))); } catch (err) { console.error('bridge onHighlightTap failed: ' + err); }
      return;
    }
    B.onTap(e.clientX / vw(), e.clientY / vh());
  }, true);

  // Horizontal swipe turns pages in paged mode.
  var startX = 0, startY = 0, startT = 0, multi = false;
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) { multi = true; return; }
    multi = false;
    startX = e.touches[0].clientX;
    startY = e.touches[0].clientY;
    startT = Date.now();
  }, { passive: true });
  document.addEventListener('touchend', function (e) {
    if (multi || mode !== 'paged' || hasSelection() || !e.changedTouches.length) { return; }
    var t = e.changedTouches[0];
    var dx = t.clientX - startX;
    var dy = t.clientY - startY;
    if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy) * 1.5 && Date.now() - startT < 800) {
      window.omni.step(dx < 0 ? 1 : -1);
    }
  }, { passive: true });
})();
