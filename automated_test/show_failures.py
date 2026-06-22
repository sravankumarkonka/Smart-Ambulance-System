import json
data = json.load(open("automated_test/report.json"))
failures = [r for r in data if r["finding"]]
print(f"Total: {len(data)}, Failures: {len(failures)}")
for i, r in enumerate(failures):
    cat = r["test_category"]
    method = r["method"]
    ep = r["endpoint"]
    status = r["status"]
    expected = r["expected_status"]
    note = r["note"]
    print(f"\n--- Failure {i+1} ---")
    print(f"  Category: {cat}")
    print(f"  {method} {ep}")
    print(f"  Actual status: {status}")
    print(f"  Expected: {expected}")
    print(f"  Note: {note}")
