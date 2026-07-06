/**
 * visual-qa Performance Scan Script — v1.0
 * Run via: browser_evaluate (paste full content as the function body)
 * Returns Core Web Vitals + resource analysis + image/script optimization checks.
 * Must run AFTER full page load (DOMContentLoaded + all resources settled).
 */
() => {
  const results = {};

  // ─── 1. NAVIGATION TIMING ────────────────────────────────────────────────────
  const nav = performance.getEntriesByType('navigation')[0];
  if (nav) {
    results.navigationTiming = {
      ttfb: Math.round(nav.responseStart - nav.requestStart),
      domInteractive: Math.round(nav.domInteractive - nav.startTime),
      domContentLoaded: Math.round(nav.domContentLoadedEventEnd - nav.startTime),
      loadComplete: Math.round(nav.loadEventEnd - nav.startTime),
      transferSizeKB: Math.round(nav.transferSize / 1024),
      encodedBodySizeKB: Math.round(nav.encodedBodySize / 1024),
    };
  }

  // ─── 2. PAINT TIMING (FCP, FP) ───────────────────────────────────────────────
  results.paintTiming = {};
  for (const p of performance.getEntriesByType('paint')) {
    const key = p.name.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
    results.paintTiming[key] = Math.round(p.startTime);
  }

  // ─── 3. LARGEST CONTENTFUL PAINT (LCP) ───────────────────────────────────────
  const lcpEntries = performance.getEntriesByType('largest-contentful-paint');
  if (lcpEntries.length > 0) {
    const lcp = lcpEntries[lcpEntries.length - 1];
    results.lcp = {
      value: Math.round(lcp.startTime),
      element: lcp.element
        ? lcp.element.tagName + (lcp.element.id ? '#' + lcp.element.id : '') + (lcp.element.className ? '.' + [...lcp.element.classList].slice(0, 2).join('.') : '')
        : null,
      sizeKB: lcp.size ? Math.round(lcp.size / 1024) : null,
      url: lcp.url || null,
    };
  }

  // ─── 4. CUMULATIVE LAYOUT SHIFT (CLS) ────────────────────────────────────────
  let clsTotal = 0;
  const clsEntries = performance.getEntriesByType('layout-shift');
  for (const e of clsEntries) {
    if (!e.hadRecentInput) clsTotal += e.value;
  }
  results.cls = {
    score: Math.round(clsTotal * 1000) / 1000,
    shiftCount: clsEntries.length,
    verdict: clsTotal < 0.1 ? 'GOOD' : clsTotal < 0.25 ? 'NEEDS_IMPROVEMENT' : 'POOR',
  };

  // ─── 5. LONG TASKS (INP proxy) ───────────────────────────────────────────────
  const longTasks = performance.getEntriesByType('longtask');
  results.longTasks = {
    count: longTasks.length,
    totalDurationMs: Math.round(longTasks.reduce((s, t) => s + t.duration, 0)),
    worst: longTasks
      .sort((a, b) => b.duration - a.duration)
      .slice(0, 3)
      .map(t => ({ durationMs: Math.round(t.duration), startMs: Math.round(t.startTime) })),
  };

  // ─── 6. RESOURCE BREAKDOWN ────────────────────────────────────────────────────
  const resources = performance.getEntriesByType('resource');
  const byType = {};
  let totalTransferBytes = 0;
  for (const r of resources) {
    const t = r.initiatorType;
    if (!byType[t]) byType[t] = { count: 0, sizeKB: 0 };
    byType[t].count++;
    byType[t].sizeKB += Math.round((r.transferSize || 0) / 1024);
    totalTransferBytes += r.transferSize || 0;
  }
  const largeResources = resources
    .filter(r => r.transferSize > 100_000)
    .sort((a, b) => b.transferSize - a.transferSize)
    .slice(0, 6)
    .map(r => ({
      url: r.name.split('?')[0].split('/').slice(-2).join('/'),
      sizeKB: Math.round(r.transferSize / 1024),
      type: r.initiatorType,
    }));
  results.resources = {
    totalCount: resources.length,
    totalTransferKB: Math.round(totalTransferBytes / 1024),
    byType,
    largeResources,
  };

  // ─── 7. IMAGE OPTIMIZATION ────────────────────────────────────────────────────
  const imgs = [...document.querySelectorAll('img')];
  results.imageOptimization = {
    totalImages: imgs.length,
    missingWidthHeight: imgs.filter(i => !i.getAttribute('width') || !i.getAttribute('height')).length,
    noLazyLoad: imgs.filter(i => i.loading !== 'lazy' && !i.hasAttribute('fetchpriority')).length,
    nonModernFormat: imgs.filter(i => {
      const s = i.currentSrc || i.src || '';
      return s && !s.startsWith('data:') && !s.includes('.webp') && !s.includes('.avif');
    }).length,
    oversized: imgs.filter(i => i.naturalWidth > i.clientWidth * 2.5 && i.clientWidth > 0).length,
  };

  // ─── 8. SCRIPT LOADING ────────────────────────────────────────────────────────
  const scripts = [...document.querySelectorAll('script[src]')];
  results.scriptLoading = {
    total: scripts.length,
    renderBlocking: scripts.filter(s => !s.defer && !s.async && !(s.type || '').includes('module')).length,
    deferred: scripts.filter(s => s.defer).length,
    async: scripts.filter(s => s.async).length,
    esModules: scripts.filter(s => (s.type || '').includes('module')).length,
  };

  // ─── 9. CORE WEB VITALS SUMMARY ──────────────────────────────────────────────
  const lcpVal = results.lcp?.value;
  const fcpVal = results.paintTiming?.firstContentfulPaint;
  const ttfbVal = results.navigationTiming?.ttfb;
  results.cwvSummary = {
    lcp: {
      value: lcpVal != null ? lcpVal + 'ms' : 'NOT_CAPTURED',
      verdict: lcpVal == null ? 'NOT_CAPTURED' : lcpVal < 2500 ? 'GOOD' : lcpVal < 4000 ? 'NEEDS_IMPROVEMENT' : 'POOR',
      target: '< 2500ms',
    },
    cls: {
      value: results.cls.score,
      verdict: results.cls.verdict,
      target: '< 0.10',
    },
    fcp: {
      value: fcpVal != null ? fcpVal + 'ms' : 'NOT_CAPTURED',
      verdict: fcpVal == null ? 'NOT_CAPTURED' : fcpVal < 1800 ? 'GOOD' : fcpVal < 3000 ? 'NEEDS_IMPROVEMENT' : 'POOR',
      target: '< 1800ms',
    },
    ttfb: {
      value: ttfbVal != null ? ttfbVal + 'ms' : 'NOT_CAPTURED',
      verdict: ttfbVal == null ? 'NOT_CAPTURED' : ttfbVal < 800 ? 'GOOD' : ttfbVal < 1800 ? 'NEEDS_IMPROVEMENT' : 'POOR',
      target: '< 800ms',
    },
    overallScore: (() => {
      // Count GOOD verdicts across LCP, CLS, FCP
      const verdicts = [
        results.lcp?.value != null ? (lcpVal < 2500 ? 'GOOD' : lcpVal < 4000 ? 'NI' : 'POOR') : null,
        results.cls.verdict !== 'NOT_CAPTURED' ? results.cls.verdict : null,
        fcpVal != null ? (fcpVal < 1800 ? 'GOOD' : fcpVal < 3000 ? 'NI' : 'POOR') : null,
      ].filter(Boolean);
      const goodCount = verdicts.filter(v => v === 'GOOD').length;
      if (verdicts.length === 0) return 'UNKNOWN';
      if (goodCount === verdicts.length) return 'ALL_GOOD';
      if (goodCount >= 2) return 'MOSTLY_GOOD';
      if (goodCount >= 1) return 'MIXED';
      return 'ALL_POOR';
    })(),
  };

  // ─── META ─────────────────────────────────────────────────────────────────────
  results._meta = {
    scannedAt: new Date().toISOString(),
    url: window.location.href,
    viewportWidth: window.innerWidth,
    viewportHeight: window.innerHeight,
    connectionType: navigator.connection?.effectiveType || 'unknown',
  };

  return results;
}
