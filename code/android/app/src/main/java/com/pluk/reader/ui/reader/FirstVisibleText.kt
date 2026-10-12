package com.pluk.reader.ui.reader

import com.pluk.reader.domain.TextAnchor
import com.pluk.reader.domain.textAnchorAt
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

/** Qué texto ancla la página visible (SYN-014). */
internal sealed interface VisibleText {
    /** El texto del ancla anterior sigue en la página: se conserva, para no correrse al ir y volver entre dispositivos. */
    data object Kept : VisibleText

    /** El primer carácter visible de la página. */
    data class Found(val anchor: TextAnchor) : VisibleText

    /** No se puede ubicar el texto (maquetación fija, derecha a izquierda, vertical, página sin texto). */
    data object None : VisibleText
}

/**
 * Busca el texto que ancla la página visible (SYN-014). Si [kept] (el ancla de la posición actual) sigue a la vista,
 * se conserva; si no, el primer carácter visible. El script es el mismo que `FirstVisibleText.swift` de Apple.
 */
internal suspend fun EpubNavigatorFragment.firstVisibleText(kept: Locator.Text?): VisibleText {
    val highlight = kept?.highlight
    val before = kept?.before.orEmpty()
    val args = if (highlight == null) "null, 0" else "${JSONObject.quote(before + highlight)}, ${before.length}"
    val raw = withTimeoutOrNull(SCRIPT_TIMEOUT_MS) { evaluateJavascript("($FIRST_VISIBLE_TEXT_SCRIPT)($args)") }
    val result = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return VisibleText.None
    if (result.optBoolean("kept")) return VisibleText.Kept
    val anchor = textAnchorAt(result.optString("window"), result.optInt("start", -1)) ?: return VisibleText.None
    return VisibleText.Found(anchor)
}

internal fun TextAnchor.toLocatorText(): Locator.Text = Locator.Text(before = before, highlight = highlight, after = after)

/** Si la página no responde en este tiempo se guarda la posición sin texto, como antes. */
private const val SCRIPT_TIMEOUT_MS = 500L

/**
 * Recibe `(kept, keptOffset)`: el texto del ancla actual (con su contexto previo) y dónde empieza el resaltado. Devuelve
 * `{kept: true}`, `{window, start}` (un tramo de `body.textContent` y el índice UTF-16 del primer carácter visible) o
 * `null`. "Visible" es lo que está dentro de la ventana; lo que queda a la izquierda o arriba es de páginas anteriores.
 */
private val FIRST_VISIBLE_TEXT_SCRIPT = """
    (function (kept, keptOffset) {
      var body = document.body;
      if (!body) return null;
      var css = getComputedStyle(body);
      if (css.direction === 'rtl' || (css.writingMode || '').indexOf('vertical') === 0) return null;
      var w = window.innerWidth, h = window.innerHeight;
      var range = document.createRange();
      function isBefore(r) { return r.right <= 0 || r.bottom <= 0; }
      function isAfter(r) { return r.left >= w || r.top >= h; }
      function charRect(node, i) {
        var code = node.data.charCodeAt(i);
        range.setStart(node, i);
        range.setEnd(node, code >= 0xd800 && code < 0xdc00 ? i + 2 : i + 1);
        return range.getBoundingClientRect();
      }
      function rectAt(offset) {
        var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT), node, seen = 0;
        while ((node = walker.nextNode())) {
          if (offset < seen + node.data.length) return charRect(node, offset - seen);
          seen += node.data.length;
        }
        return null;
      }
      var text = body.textContent;
      if (kept) {
        for (var at = text.indexOf(kept); at >= 0; at = text.indexOf(kept, at + 1)) {
          var r0 = rectAt(at + keptOffset);
          if (r0 && !isBefore(r0) && !isAfter(r0)) return { kept: true };
        }
      }
      var walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
      var offset = 0, node;
      while ((node = walker.nextNode())) {
        var data = node.data;
        var candidates = [];
        var visibleChar = /[^\s­​-‍⁠﻿]/g, m;
        while ((m = visibleChar.exec(data))) candidates.push(m.index);
        if (candidates.length > 0) {
          range.selectNodeContents(node);
          var rects = range.getClientRects();
          var seen = false, after = false;
          for (var k = 0; k < rects.length; k++) {
            var r = rects[k];
            if (r.width === 0 && r.height === 0) continue;
            if (isAfter(r)) after = true;
            else if (!isBefore(r)) seen = true;
          }
          if (seen) {
            var lo = 0, hi = candidates.length - 1;
            while (lo < hi) {
              var mid = (lo + hi) >> 1;
              if (isBefore(charRect(node, candidates[mid]))) lo = mid + 1; else hi = mid;
            }
            var start = offset + candidates[lo];
            var from = Math.max(0, start - 200);
            return { window: text.substring(from, start + 200), start: start - from };
          }
          if (after) return null;
        }
        offset += data.length;
      }
      return null;
    })
""".trimIndent()
