import json, os

path = os.path.join(os.path.dirname(__file__), "report.json")
data = json.load(open(path, encoding="utf-8"))

total   = len(data)
passed  = sum(1 for r in data if not r.get("finding", False))
failed  = sum(1 for r in data if r.get("finding", False))

cats = {}
for r in data:
    c = r.get("test_category", "unknown")
    if c not in cats:
        cats[c] = {"pass": 0, "fail": 0}
    if r.get("finding", False):
        cats[c]["fail"] += 1
    else:
        cats[c]["pass"] += 1

print(f"Total tests : {total}")
print(f"PASS        : {passed}")
print(f"FAIL/finding: {failed}")
pct = (passed / total * 100) if total else 0
print(f"Pass rate   : {pct:.1f}%")
print()
for cat, v in sorted(cats.items()):
    status = "OK" if v["fail"] == 0 else "!! FINDINGS"
    print(f"  {cat:35s}  pass={v['pass']:3d}  fail={v['fail']:3d}  {status}")
