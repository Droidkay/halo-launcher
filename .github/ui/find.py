"""Prints the centre "x y" of the first node in a uiautomator dump whose text/desc matches."""
import re, sys
xml, want = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "exact"
data = open(xml, encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', data):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    vals = [t.group(1) if t else "", d.group(1) if d else ""]
    ok = any(v == want for v in vals) if mode == "exact" else any(want in v for v in vals)
    if not ok:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if b:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2, y2)
        sys.exit(0)
sys.exit(1)
