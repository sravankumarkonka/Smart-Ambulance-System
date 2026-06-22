import json
path = r"C:\Users\konka\.gemini\antigravity-ide\brain\fb9a331c-aa65-4515-98c8-b3dbb372ca17\.system_generated\steps\451\content.md"
with open(path) as f:
    txt = f.read()
idx = txt.index("{")
data = json.loads(txt[idx:], strict=False)
for job in data["jobs"]:
    print(f"Job: {job['name']}  Status: {job['status']}  Conclusion: {job['conclusion']}")
    for step in job.get("steps", []):
        conc = step.get("conclusion", "n/a")
        icon = "+" if conc == "success" else "X" if conc == "failure" else "-"
        print(f"  [{icon}] [{conc}] {step['name']}")
