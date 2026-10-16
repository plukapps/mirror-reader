#if os(iOS)
import Foundation
@preconcurrency import ReadiumNavigator
@preconcurrency import ReadiumShared
import ReaderDomain

/// Qué texto ancla la página visible (SYN-014), como `VisibleText` de Android.
enum VisibleText: Equatable {
    /// El texto del ancla anterior sigue en la página: se conserva, para no correrse al ir y volver entre dispositivos.
    case kept
    /// El primer carácter visible de la página.
    case found(TextAnchor)
    /// No se puede ubicar el texto (maquetación fija, derecha a izquierda, vertical, página sin texto).
    case none
}

extension EPUBNavigatorViewController {
    /// Busca el texto que ancla la página visible (SYN-014). Si `kept` (el ancla de la posición actual) sigue a la
    /// vista, se conserva; si no, el primer carácter visible. El script es el mismo que `FirstVisibleText.kt` de Android.
    func firstVisibleText(kept: Locator.Text?) async -> VisibleText {
        var args = "null, 0"
        if let highlight = kept?.highlight {
            let before = kept?.before ?? ""
            guard let quoted = try? String(decoding: JSONEncoder().encode(before + highlight), as: UTF8.self) else { return .none }
            args = "\(quoted), \(before.utf16.count)"
        }
        guard case let .success(value) = await evaluateJavaScript("(\(firstVisibleTextScript))(\(args))"),
              let result = value as? [String: Any]
        else { return .none }
        if result["kept"] as? Bool == true { return .kept }
        guard let window = result["window"] as? String,
              let start = (result["start"] as? NSNumber)?.intValue,
              let anchor = textAnchorAt(window: window, start: start)
        else { return .none }
        return .found(anchor)
    }
}

extension TextAnchor {
    var locatorText: Locator.Text { Locator.Text(after: after, before: before, highlight: highlight) }
}

/// Recibe `(kept, keptOffset)`: el texto del ancla actual (con su contexto previo) y dónde empieza el resaltado.
/// Devuelve `{kept: true}`, `{window, start}` (un tramo de `body.textContent` y el índice UTF-16 del primer carácter
/// visible) o `null`. "Visible" es lo que está dentro de la ventana; lo que queda a la izquierda o arriba es de
/// páginas anteriores.
private let firstVisibleTextScript = #"""
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
"""#
#endif
