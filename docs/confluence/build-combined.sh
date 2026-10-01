#!/usr/bin/env bash
# Erzeugt aus den Einzelseiten eine Gesamtdatei (z. B. für Pandoc/Markdown-Import in Confluence).
# Aufruf: docs/confluence/build-combined.sh   → docs/confluence/QCC-Komplettdokumentation.md
set -euo pipefail
cd "$(dirname "$0")"
out="QCC-Komplettdokumentation.md"
: > "$out"
for f in [0-9][0-9]-*.md; do
  printf '\n\n<!-- Seite: %s -->\n\n' "$f" >> "$out"
  cat "$f" >> "$out"
done
echo "geschrieben: $out ($(wc -l < "$out") Zeilen)"
