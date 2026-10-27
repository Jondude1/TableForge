"""Fails the build if a hand-written migration doesn't match the schema Room generated.

A mismatch would crash the app on upgrade (Room validates the table), so catch it in CI instead.
"""
import json, pathlib, re, sys

root = pathlib.Path(__file__).resolve().parent.parent
schema_dir = root / "app/schemas/com.jondude.tableforge.data.AppDatabase"
kt = (root / "app/src/main/java/com/jondude/tableforge/data/AppDatabase.kt").read_text()

def const(name):
    m = re.search(rf'const val {name} =\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', kt)
    return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)))

latest = max(schema_dir.glob("*.json"), key=lambda p: int(p.stem))
ents = {e["tableName"]: e for e in json.loads(latest.read_text())["database"]["entities"]}
checks = {
    "MONSTERS_TABLE_SQL": ents["monsters"]["createSql"].replace("${TABLE_NAME}", "monsters"),
    "MONSTERS_INDEX_SQL": ents["monsters"]["indices"][0]["createSql"].replace("${TABLE_NAME}", "monsters"),
}
bad = False
for name, expected in checks.items():
    got = const(name)
    if got != expected:
        bad = True
        print(f"::error::{name} does not match Room's schema\n  expected: {expected}\n  got:      {got}")
    else:
        print(f"OK {name}")
print(f"Checked against schema version {latest.stem}")
sys.exit(1 if bad else 0)
