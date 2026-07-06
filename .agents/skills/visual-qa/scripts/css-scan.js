/**
 * visual-qa CSS Scan Script — v1.0
 * Run via: browser_evaluate (paste full content as the function body)
 * Returns a structured object with all 14 audit metrics.
 * Adapt automatically: skips metrics that have no relevant DOM/CSS nodes.
 */
() => {
  const results = {};
  const allElements = [...document.querySelectorAll('*')];
  const computedStyles = el => window.getComputedStyle(el);

  // ─── HELPER ────────────────────────────────────────────────────────────────
  const safeSheet = fn => {
    const out = [];
    for (const sheet of document.styleSheets) {
      try { fn(sheet, out); } catch (_) { /* cross-origin, skip */ }
    }
    return out;
  };

  // ─── 1. TYPOGRAPHY ──────────────────────────────────────────────────────────
  const fontFamilies = new Set();
  const fontSizes = {};
  for (const el of allElements) {
    const cs = computedStyles(el);
    const ff = cs.fontFamily;
    if (ff) fontFamilies.add(ff.split(',')[0].trim().replace(/['"]/g, ''));
    const fs = cs.fontSize;
    if (fs) fontSizes[fs] = (fontSizes[fs] || 0) + 1;
  }
  results.typography = {
    fontFamiliesUsed: [...fontFamilies],
    fontSizeDistribution: fontSizes,
    uniqueFontSizesCount: Object.keys(fontSizes).length,
  };

  // ─── 2. GLOW AUDIT ──────────────────────────────────────────────────────────
  let glowCount = 0;
  const glowElements = [];
  for (const el of allElements) {
    const cs = computedStyles(el);
    const bs = cs.boxShadow || '';
    const ts = cs.textShadow || '';
    const isGlow = /\d+px\s+\d+px\s+\d+px/.test(bs) || /\d+px\s+\d+px\s+\d+px/.test(ts);
    // Neon glow = shadow with large blur and no spread (0 0 Xpx)
    const isNeonGlow = /0(?:px)?\s+0(?:px)?\s+\d+px/.test(bs) || /0(?:px)?\s+0(?:px)?\s+\d+px/.test(ts);
    if (isNeonGlow) {
      glowCount++;
      if (glowElements.length < 10) glowElements.push(el.tagName + (el.className ? '.' + [...el.classList].slice(0, 2).join('.') : ''));
    }
  }
  results.glowAudit = { neonGlowCount: glowCount, affectedElements: glowElements };

  // ─── 3. ANIMATION AUDIT ─────────────────────────────────────────────────────
  const easings = {};
  const durations = {};
  let infiniteAnimCount = 0;
  let hasReducedMotion = false;
  let hasHoverHover = false;

  safeSheet((sheet, _) => {
    const rules = [...sheet.cssRules || []];
    const walkRules = rules => {
      for (const rule of rules) {
        if (rule.cssText) {
          if (rule.cssText.includes('prefers-reduced-motion')) hasReducedMotion = true;
          if (rule.cssText.includes('hover: hover') || rule.cssText.includes('hover:hover')) hasHoverHover = true;
          if (rule.cssText.includes('infinite')) infiniteAnimCount++;
          const easing = rule.style?.transitionTimingFunction || rule.style?.animationTimingFunction;
          if (easing) easings[easing] = (easings[easing] || 0) + 1;
          const dur = rule.style?.transitionDuration || rule.style?.animationDuration;
          if (dur) durations[dur] = (durations[dur] || 0) + 1;
        }
        if (rule.cssRules) walkRules([...rule.cssRules]);
      }
    };
    walkRules(rules);
  });

  results.animationAudit = {
    hasReducedMotion,
    hasHoverHover,
    infiniteAnimationCount: infiniteAnimCount,
    easingDistribution: easings,
    durationDistribution: durations,
  };

  // ─── 4. SURFACE AUDIT ───────────────────────────────────────────────────────
  const bgColors = new Set();
  for (const el of allElements) {
    const bg = computedStyles(el).backgroundColor;
    if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') bgColors.add(bg);
  }
  results.surfaceAudit = {
    uniqueBackgroundColors: [...bgColors],
    depthLadderCount: bgColors.size,
  };

  // ─── 5. Z-INDEX MAP ─────────────────────────────────────────────────────────
  const zIndices = {};
  for (const el of allElements) {
    const z = computedStyles(el).zIndex;
    if (z && z !== 'auto') {
      const num = parseInt(z, 10);
      if (!isNaN(num)) {
        const key = String(num);
        if (!zIndices[key]) zIndices[key] = [];
        if (zIndices[key].length < 3) zIndices[key].push(el.tagName + (el.id ? '#' + el.id : ''));
      }
    }
  }
  results.zIndexMap = {
    zIndexValues: zIndices,
    maxZIndex: Math.max(...Object.keys(zIndices).map(Number), 0),
    uniqueZIndexCount: Object.keys(zIndices).length,
  };

  // ─── 6. CANVAS AUDIT (conditional) ─────────────────────────────────────────
  const canvases = document.querySelectorAll('canvas');
  if (canvases.length > 0) {
    results.canvasAudit = [...canvases].map(c => ({
      cssWidth: c.clientWidth,
      cssHeight: c.clientHeight,
      internalWidth: c.width,
      internalHeight: c.height,
      pixelRatioCssMatch: c.width === c.clientWidth * window.devicePixelRatio,
      imageRendering: computedStyles(c).imageRendering,
    }));
  }

  // ─── 7. ACTIVE STATES ───────────────────────────────────────────────────────
  let hasActiveScale = false;
  safeSheet((sheet, _) => {
    for (const rule of [...(sheet.cssRules || [])]) {
      if (rule.selectorText && rule.selectorText.includes(':active') && rule.cssText.includes('scale')) {
        hasActiveScale = true;
      }
    }
  });
  results.activeStates = { hasActiveScaleTransform: hasActiveScale };

  // ─── 8. BORDER-RADIUS DISTRIBUTION ─────────────────────────────────────────
  const borderRadii = {};
  for (const el of allElements) {
    const br = computedStyles(el).borderRadius;
    if (br && br !== '0px') borderRadii[br] = (borderRadii[br] || 0) + 1;
  }
  results.borderRadiusDistribution = borderRadii;

  // ─── 9. FOCUS STYLES ────────────────────────────────────────────────────────
  let hasFocusVisible = false;
  let hasFocusOutlineNone = false;
  safeSheet((sheet, _) => {
    for (const rule of [...(sheet.cssRules || [])]) {
      const t = rule.selectorText || '';
      if (t.includes(':focus-visible')) hasFocusVisible = true;
      if (t.includes(':focus') && rule.cssText.includes('outline: none') || rule.cssText.includes('outline:none')) hasFocusOutlineNone = true;
    }
  });
  results.focusStyles = { hasFocusVisible, hasFocusOutlineNoneRisk: hasFocusOutlineNone };

  // ─── 10. ARIA AUDIT ──────────────────────────────────────────────────────────
  results.ariaAudit = {
    ariaLabelCount: document.querySelectorAll('[aria-label]').length,
    ariaRoleCount: document.querySelectorAll('[role]').length,
    altTextCount: document.querySelectorAll('img[alt]').length,
    imgWithoutAlt: document.querySelectorAll('img:not([alt])').length,
    buttonWithoutLabel: document.querySelectorAll('button:not([aria-label]):not([aria-labelledby])').length,
  };

  // ─── 11. TOUCH AUDIT ────────────────────────────────────────────────────────
  const touchActions = {};
  for (const el of allElements) {
    const ta = computedStyles(el).touchAction;
    if (ta && ta !== 'auto') touchActions[ta] = (touchActions[ta] || 0) + 1;
  }
  results.touchAudit = { touchActionDistribution: touchActions };

  // ─── 12. TOKEN AUDIT ────────────────────────────────────────────────────────
  const rootStyle = getComputedStyle(document.documentElement);
  const cssVars = {};
  for (const prop of rootStyle) {
    if (prop.startsWith('--')) {
      cssVars[prop] = rootStyle.getPropertyValue(prop).trim();
    }
  }
  results.tokenAudit = {
    customPropertiesCount: Object.keys(cssVars).length,
    customProperties: cssVars,
    emptyTokensCount: Object.values(cssVars).filter(v => !v).length,
  };

  // ─── 13. EASING DISTRIBUTION (top-level summary) ────────────────────────────
  results.easingSummary = results.animationAudit.easingDistribution;

  // ─── 14. LAYOUT AUDIT ────────────────────────────────────────────────────────
  let textAlignCenterCount = 0;
  let flexCenterCount = 0;
  let marginAutoCount = 0;
  for (const el of allElements) {
    const cs = computedStyles(el);
    if (cs.textAlign === 'center') textAlignCenterCount++;
    if (cs.justifyContent === 'center' && cs.display === 'flex') flexCenterCount++;
    if (cs.marginLeft === 'auto' && cs.marginRight === 'auto') marginAutoCount++;
  }
  results.layoutAudit = {
    textAlignCenterCount,
    flexJustifyCenterCount: flexCenterCount,
    marginAutoCount,
    centerBiasRisk: textAlignCenterCount > 20 || flexCenterCount > 15,
  };

  // ─── META ─────────────────────────────────────────────────────────────────
  results._meta = {
    scannedAt: new Date().toISOString(),
    totalElementsScanned: allElements.length,
    devicePixelRatio: window.devicePixelRatio,
    viewportWidth: window.innerWidth,
    viewportHeight: window.innerHeight,
  };

  return results;
}
