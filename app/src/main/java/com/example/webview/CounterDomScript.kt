package com.example.webview

import org.json.JSONObject

data class CounterCandidate(
    val selector: String,
    val value: String,
    val numericValue: Long?,
    val label: String,
    val kind: String // "TOTAL", "COUNT", "TIMER", "PINNED"
)

data class DomScanPayload(
    val url: String,
    val title: String,
    val primary: CounterCandidate?,
    val totalCounter: CounterCandidate?,
    val candidates: List<CounterCandidate>
)

data class PickedElementPayload(
    val selector: String,
    val value: String,
    val numericValue: Long?,
    val label: String
)

object CounterDomScript {

    fun buildInstallScript(pinnedSelector: String): String {
        val escapedSelector = JSONObject.quote(pinnedSelector)
        return """
            (function() {
                window.__redCounterPinnedSelector = $escapedSelector;
                if (window.__redCounterInstalled) {
                    if (typeof window.__redCounterScanNow === 'function') {
                        window.__redCounterScanNow();
                    }
                    return;
                }
                window.__redCounterInstalled = true;
                window.__redCounterPickerActive = false;

                function toLatinDigits(str) {
                    if (!str) return '';
                    var map = {'٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9',
                               '۰':'0','۱':'1','۲':'2','۳':'3','۴':'4','۵':'5','۶':'6','۷':'7','۸':'8','۹':'9'};
                    return str.replace(/[٠-٩۰-۹]/g, function(ch) { return map[ch] || ch; });
                }

                // Extracts the TOTAL numeric value when text is like "12 of 340", "1 - 10 / 250", or "12 من 340"
                function parseTotalNumeric(rawText) {
                    if (!rawText) return null;
                    var normalized = toLatinDigits(rawText).replace(/\s+/g, ' ').trim();

                    // Check timer pattern first so timers are parsed as seconds
                    var hms = normalized.match(/\b(\d{1,2})\s*:\s*(\d{1,2})\s*:\s*(\d{1,2})\b/);
                    if (hms) {
                        return parseInt(hms[1], 10) * 3600 + parseInt(hms[2], 10) * 60 + parseInt(hms[3], 10);
                    }
                    var ms = normalized.match(/\b(\d{1,2})\s*:\s*(\d{1,2})\b/);
                    if (ms) {
                        return parseInt(ms[1], 10) * 60 + parseInt(ms[2], 10);
                    }

                    // Pattern: "X of Y", "X / Y", "X من Y", "1-12 of 340" -> capture total Y!
                    var totalOfMatch = normalized.match(/(?:\d[\d,]*\s*(?:of|out of|from|\/|من|أصل)\s*)(\d[\d,]*)/i);
                    if (totalOfMatch) {
                        var totalParsed = parseInt(totalOfMatch[1].replace(/,/g, ''), 10);
                        if (!isNaN(totalParsed)) return totalParsed;
                    }

                    // Otherwise if multiple numbers appear (e.g. "Total: 340" vs "Showing 10"), prefer largest non-phone number
                    var allNums = normalized.match(/\d[\d,]*/g);
                    if (allNums && allNums.length > 0) {
                        var parsedVals = allNums.map(function(n) {
                            return parseInt(n.replace(/,/g, ''), 10);
                        }).filter(function(v) { return !isNaN(v); });
                        if (parsedVals.length > 0) {
                            return parsedVals[parsedVals.length - 1];
                        }
                    }
                    return null;
                }

                function cssEscapeSafe(val) {
                    if (window.CSS && CSS.escape) return CSS.escape(val);
                    return val.replace(/([^\w-])/g, '\\$1');
                }

                function getUniqueSelector(el) {
                    if (!el || el.nodeType !== 1) return '';
                    if (el.id) {
                        return '#' + cssEscapeSafe(el.id);
                    }
                    var testId = el.getAttribute('data-testid') || el.getAttribute('data-test-id');
                    if (testId) {
                        return '[data-testid="' + testId.replace(/"/g, '\\"') + '"]';
                    }
                    var parts = [];
                    var curr = el;
                    while (curr && curr.nodeType === 1 && curr.tagName.toLowerCase() !== 'html') {
                        var tag = curr.tagName.toLowerCase();
                        if (curr.id) {
                            parts.unshift('#' + cssEscapeSafe(curr.id));
                            break;
                        }
                        var sibIndex = 1;
                        var sib = curr.previousElementSibling;
                        while (sib) {
                            if (sib.tagName === curr.tagName) sibIndex++;
                            sib = sib.previousElementSibling;
                        }
                        var needsNth = false;
                        var nextSib = curr.nextElementSibling;
                        if (sibIndex > 1) {
                            needsNth = true;
                        } else {
                            while (nextSib) {
                                if (nextSib.tagName === curr.tagName) { needsNth = true; break; }
                                nextSib = nextSib.nextElementSibling;
                            }
                        }
                        parts.unshift(needsNth ? (tag + ':nth-of-type(' + sibIndex + ')') : tag);
                        curr = curr.parentElement;
                        if (parts.length >= 6) break;
                    }
                    return parts.join(' > ');
                }

                function isVisible(el) {
                    if (!el || el.nodeType !== 1) return false;
                    var style = window.getComputedStyle(el);
                    if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
                        return false;
                    }
                    var rect = el.getBoundingClientRect();
                    return rect.width > 0 && rect.height > 0;
                }

                function extractLabel(el) {
                    var aria = el.getAttribute('aria-label');
                    if (aria && aria.trim()) return aria.trim().slice(0, 48);
                    var prev = el.previousElementSibling;
                    if (prev && prev.innerText && prev.innerText.trim().length > 0 && prev.innerText.trim().length <= 48) {
                        return prev.innerText.trim().replace(/\s+/g, ' ');
                    }
                    var parent = el.parentElement;
                    if (parent) {
                        var pt = (parent.innerText || '').replace(/\s+/g, ' ').trim();
                        if (pt.length > 0 && pt.length <= 64) return pt;
                    }
                    var cls = (typeof el.className === 'string') ? el.className.trim().split(/\s+/)[0] : '';
                    if (el.id) return el.id;
                    if (cls) return cls;
                    return el.tagName.toLowerCase();
                }

                function collectCandidates() {
                    var results = [];
                    var seenSelectors = new Set();
                    var all = document.querySelectorAll('span, div, p, strong, b, time, h1, h2, h3, h4, small, label, button');
                    var timerRegex = /\b\d{1,2}\s*:\s*\d{2}(?:\s*:\s*\d{2})?\b/;
                    var totalMetaRegex = /(total|overall|all-?numbers|results?-?count|total-?count|numbers?-?count|available-?count|found|إجمالي|الكلي)/i;
                    var totalTextRegex = /(total|of\s+\d|\/\s*\d|من\s+\d|results|numbers\s+available|available\s+numbers|إجمالي|المجموع|الكلي|نتيجة|رقم\s+متاح|أرقام\s+متاحة)/i;
                    var secondaryTextRegex = /(per\s*page|page\s*\d|cart|bag|selected|egp|le\b|جنيه|ج\.م|monthly|month|شهرياً|سعر)/i;
                    var counterMetaRegex = /(timer|countdown|counter|clock|remain|expire|hold|count|stock|avail|numbers|total|result|badge|qty)/i;

                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        if (el.children.length > 5) continue;

                        var quickText = (el.textContent || '').replace(/\s+/g, ' ').trim();
                        if (!quickText || quickText.length > 64) continue;
                        if (!/\d|[٠-٩۰-۹]/.test(quickText)) continue;

                        if (!isVisible(el)) continue;

                        var raw = (el.innerText || quickText).replace(/\s+/g, ' ').trim();
                        if (!raw || raw.length > 64) continue;

                        var norm = toLatinDigits(raw);
                        // Skip phone numbers (e.g. 010xxxxxxxx or +20...)
                        if (/^(?:\+?2)?01\d{8,9}$/.test(norm.replace(/[\s-]/g, ''))) continue;
                        if (/\b01[0125]\d{8}\b/.test(norm.replace(/[\s-]/g, ''))) continue;

                        var hasTimer = timerRegex.test(norm);
                        var meta = (el.id || '') + ' ' +
                                   (typeof el.className === 'string' ? el.className : '') + ' ' +
                                   (el.getAttribute('data-testid') || '') + ' ' +
                                   (el.getAttribute('aria-label') || '');
                        var parentMeta = el.parentElement ? ((el.parentElement.id || '') + ' ' + (typeof el.parentElement.className === 'string' ? el.parentElement.className : '')) : '';
                        var parentText = el.parentElement ? toLatinDigits((el.parentElement.innerText || '').replace(/\s+/g, ' ').trim()).slice(0, 80) : '';

                        var hasTotalMeta = totalMetaRegex.test(meta) || totalMetaRegex.test(parentMeta);
                        var hasTotalText = totalTextRegex.test(norm) || totalTextRegex.test(parentText);
                        var hasCounterMeta = counterMetaRegex.test(meta);
                        var hasPureDigits = /^\d{1,6}$/.test(norm);
                        var isSecondary = secondaryTextRegex.test(norm) || secondaryTextRegex.test(parentText);

                        if (!hasTimer && !hasTotalMeta && !hasTotalText && !(hasCounterMeta && /\d/.test(norm)) && !(hasPureDigits && el.children.length === 0)) {
                            continue;
                        }

                        var numeric = parseTotalNumeric(raw);
                        if (numeric === null) continue;

                        var sel = getUniqueSelector(el);
                        if (!sel || seenSelectors.has(sel)) continue;
                        seenSelectors.add(sel);

                        var score = 0;
                        var kind = 'COUNT';
                        if (hasTimer) {
                            kind = 'TIMER';
                            score += 20;
                        } else {
                            if (hasTotalMeta) score += 160;
                            if (hasTotalText) score += 140;
                            if (hasCounterMeta) score += 50;
                            if (el.children.length === 0) score += 15;
                            if (hasTotalMeta || hasTotalText) {
                                kind = 'TOTAL';
                            }
                        }
                        if (isSecondary) {
                            score -= 90;
                            if (kind === 'TOTAL') kind = 'COUNT';
                        }

                        var displayVal = (kind === 'TOTAL' && /(?:\d+\s*(?:of|\/|من)\s*)(\d[\d,]*)/i.test(norm))
                            ? String(numeric)
                            : raw;

                        results.push({
                            selector: sel,
                            value: displayVal,
                            numericValue: numeric,
                            label: extractLabel(el),
                            kind: kind,
                            score: score
                        });
                    }

                    results.sort(function(a, b) { return b.score - a.score; });
                    return results.slice(0, 12);
                }

                window.__redCounterScanNow = function() {
                    try {
                        var candidates = collectCandidates();
                        var totalCounter = null;
                        for (var i = 0; i < candidates.length; i++) {
                            if (candidates[i].kind === 'TOTAL') {
                                totalCounter = candidates[i];
                                break;
                            }
                        }
                        if (!totalCounter) {
                            for (var j = 0; j < candidates.length; j++) {
                                if (candidates[j].kind !== 'TIMER') {
                                    totalCounter = candidates[j];
                                    break;
                                }
                            }
                        }

                        var primary = null;
                        var pinned = window.__redCounterPinnedSelector;
                        if (pinned && pinned.length > 0) {
                            var target = document.querySelector(pinned);
                            if (target) {
                                var raw = (target.innerText || target.textContent || '').replace(/\s+/g, ' ').trim();
                                var num = parseTotalNumeric(raw);
                                primary = {
                                    selector: pinned,
                                    value: raw || '--',
                                    numericValue: num,
                                    label: extractLabel(target),
                                    kind: 'PINNED'
                                };
                            }
                        }
                        if (!primary) {
                            primary = totalCounter || (candidates.length > 0 ? candidates[0] : null);
                        }

                        var payload = {
                            url: window.location.href,
                            title: document.title || '',
                            primary: primary,
                            totalCounter: totalCounter,
                            candidates: candidates
                        };
                        if (window.CounterBridge && typeof window.CounterBridge.onCounterUpdate === 'function') {
                            window.CounterBridge.onCounterUpdate(JSON.stringify(payload));
                        }
                    } catch (e) {}
                };

                window.__redCounterSetPinned = function(sel) {
                    window.__redCounterPinnedSelector = sel || '';
                    window.__redCounterScanNow();
                };

                window.__redCounterSetPicker = function(enabled) {
                    window.__redCounterPickerActive = !!enabled;
                    document.body.style.cursor = enabled ? 'crosshair' : '';
                };

                document.addEventListener('click', function(ev) {
                    if (!window.__redCounterPickerActive) return;
                    ev.preventDefault();
                    ev.stopPropagation();
                    var el = ev.target;
                    if (!el || el.nodeType !== 1) return;

                    var prevOutline = el.style.outline;
                    el.style.outline = '3px solid #E60000';
                    setTimeout(function() { el.style.outline = prevOutline; }, 1200);

                    var sel = getUniqueSelector(el);
                    var raw = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
                    var picked = {
                        selector: sel,
                        value: raw.slice(0, 64) || '--',
                        numericValue: parseTotalNumeric(raw),
                        label: extractLabel(el)
                    };
                    window.__redCounterPickerActive = false;
                    window.__redCounterPinnedSelector = sel;
                    document.body.style.cursor = '';

                    if (window.CounterBridge && typeof window.CounterBridge.onElementPicked === 'function') {
                        window.CounterBridge.onElementPicked(JSON.stringify(picked));
                    }
                    window.__redCounterScanNow();
                }, true);

                var observerTimeout = null;
                var observer = new MutationObserver(function() {
                    if (observerTimeout) return;
                    observerTimeout = setTimeout(function() {
                        observerTimeout = null;
                        window.__redCounterScanNow();
                    }, 500);
                });
                if (document.body) {
                    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
                }
                setInterval(function() {
                    window.__redCounterScanNow();
                }, 1500);

                window.__redCounterScanNow();
            })();
        """.trimIndent()
    }

    fun parseDomPayload(jsonString: String): DomScanPayload? {
        return runCatching {
            val root = JSONObject(jsonString)
            val url = root.optString("url", "")
            val title = root.optString("title", "")
            val primaryObj = root.optJSONObject("primary")
            val primary = primaryObj?.let { parseCandidate(it) }
            val totalObj = root.optJSONObject("totalCounter")
            val totalCounter = totalObj?.let { parseCandidate(it) }
            val candidatesArr = root.optJSONArray("candidates")
            val candidates = buildList {
                if (candidatesArr != null) {
                    for (i in 0 until candidatesArr.length()) {
                        candidatesArr.optJSONObject(i)?.let { add(parseCandidate(it)) }
                    }
                }
            }
            DomScanPayload(
                url = url,
                title = title,
                primary = primary,
                totalCounter = totalCounter,
                candidates = candidates
            )
        }.getOrNull()
    }

    fun parsePickedPayload(jsonString: String): PickedElementPayload? {
        return runCatching {
            val obj = JSONObject(jsonString)
            PickedElementPayload(
                selector = obj.optString("selector", ""),
                value = obj.optString("value", "--"),
                numericValue = if (obj.isNull("numericValue")) null else obj.optLong("numericValue"),
                label = obj.optString("label", "")
            )
        }.getOrNull()
    }

    private fun parseCandidate(obj: JSONObject): CounterCandidate {
        return CounterCandidate(
            selector = obj.optString("selector", ""),
            value = obj.optString("value", "--"),
            numericValue = if (obj.isNull("numericValue")) null else obj.optLong("numericValue"),
            label = obj.optString("label", ""),
            kind = obj.optString("kind", "COUNT")
        )
    }
}
