#!/usr/bin/env python3
"""Offline APK/privacy and native SQLite audit. Does not replace Android/Room/UI tests.

Only synthetic in-memory rows are used. Never prints keys, BuildConfig, prompts or user data.
Run after building: python3 scripts/verify_private_demo.py
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.example.habit.data.local.HabitDatabase"
OUTPUT = ROOT / "app/build/reports/chunk13-verification/offline-audit"


def run(*args):
    return subprocess.check_output(args, cwd=ROOT).decode()


def database(version):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in json.loads((SCHEMAS / f"{version}.json").read_text())["database"]["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db


def shape(db, table):
    return (db.execute(f'PRAGMA table_info("{table}")').fetchall(),
            sorted(db.execute(f'PRAGMA foreign_key_list("{table}")').fetchall()),
            sorted((row[1], row[2], tuple(db.execute(f'PRAGMA index_info("{row[1]}")').fetchall()))
                   for row in db.execute(f'PRAGMA index_list("{table}")')))


def migration_sql_audit():
    # The actual v2→v3 SQL is additive; execute those statements rather than a rewritten migration.
    source = (ROOT / "app/src/main/java/com/example/habit/data/local/HabitMigrations.kt").read_text()
    section = source.split("val MIGRATION_1_2:")[0]
    sql = re.findall(r'db\.execSQL\("(CREATE[^"\n]+)"\)', section)
    assert len(sql) == 7, "Review changed migration extraction before using this audit"
    old, expected = database(2), database(3)
    try:
        # Preserve legacy metadata, an archived binary habit, a quantity partial and pending expectations.
        for habit_id, archived, frequency, mask, goal in [(1, None, "WEEKLY", 5, 99), (2, 200, "DAILY", 127, 1), (3, None, "WEEKLY", 127, 1)]:
            old.execute("INSERT INTO habits (id,name,icon_key,color_key,frequency,scheduled_days,goal,created_at,archived_at,created_epoch_day,archived_epoch_day) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        (habit_id, "Synthetic", "book", "green", frequency, mask, goal, 100, archived, 20000, 20002 if archived else None))
            old.execute("INSERT INTO schedule_history VALUES (?,?,?,?,?)", (habit_id, 20000, "CUSTOM" if habit_id == 1 else "DAILY", mask, None))
            old.execute("INSERT INTO tracking_history VALUES (?,?,?,?,?)", (habit_id, 20000, "QUANTITY" if habit_id == 3 else "BINARY", "5.00" if habit_id == 3 else None, "pages" if habit_id == 3 else None))
        old.execute("INSERT INTO schedule_history VALUES (3,20005,'WEEKLY',127,3)")
        old.execute("INSERT INTO tracking_history VALUES (3,20003,'QUANTITY','2.00','pages')")
        old.execute("INSERT INTO completions VALUES (1,20000,17,100,'BINARY',NULL,NULL)")
        old.execute("INSERT INTO completions VALUES (2,20000,1,100,'BINARY',NULL,NULL)")
        old.execute("INSERT INTO completions VALUES (3,20001,1,101,'QUANTITY','2.50','pages')")
        original_tables = ["habits", "completions", "schedule_history", "tracking_history"]
        before = {table: old.execute(f'SELECT * FROM "{table}" ORDER BY 1,2').fetchall() for table in original_tables}
        for statement in sql:
            old.execute(statement)
        for table, rows in before.items():
            assert old.execute(f'SELECT * FROM "{table}" ORDER BY 1,2').fetchall() == rows, "Migration changed original facts"
        tables = [entity["tableName"] for entity in json.loads((SCHEMAS / "3.json").read_text())["database"]["entities"]]
        for table in tables:
            assert shape(old, table) == shape(expected, table), f"Native schema mismatch: {table}"
        old.execute("INSERT INTO coach_messages VALUES (1,3,'USER','synthetic',1,'exchange')")
        old.execute("INSERT INTO coach_caches VALUES ('exchange',3,'{}','{}','{}','digest',1)")
        old.execute("INSERT INTO coach_actions VALUES ('exchange:0',3,'card_01','{}',1,'ADVICE','synthetic')")
        old.execute("INSERT INTO habit_field_versions VALUES (3,'cue',1)")
        old.execute("DELETE FROM habits WHERE id=3")
        for table in ["completions", "schedule_history", "tracking_history", "coach_messages", "coach_caches", "coach_actions", "habit_field_versions"]:
            assert old.execute(f'SELECT COUNT(*) FROM "{table}" WHERE habit_id=3').fetchone()[0] == 0, "Missing dependent cascade"
        assert old.execute("SELECT COUNT(*) FROM habits").fetchone()[0] == 2
        assert old.execute("PRAGMA foreign_key_check").fetchall() == []
        return "passed: actual v2→v3 SQL preserves raw facts, matches exported v3 table/index/FK shapes and cascades only the deleted fixture"
    finally:
        old.close(); expected.close()


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    sdk = next(line.partition("=")[2].strip().replace("\\ ", " ") for line in (ROOT / "local.properties").read_text().splitlines() if line.startswith("sdk.dir="))
    aapt = sorted(Path(sdk).glob("build-tools/*/aapt2"))[-1]
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    badging = run(str(aapt), "dump", "badging", str(apk))
    assert "package: name='com.example.habit'" in badging and "application-label:'Habit Companion'" in badging, "Packaged identity/brand changed"
    manifest = run(str(aapt), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk))
    permissions = set(re.findall(r'E: uses-permission[^\n]*\n[^\n]*:name[^=]*="([^"]+)"', manifest))
    assert permissions == {"android.permission.ACCESS_NETWORK_STATE", "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS",
                           "android.permission.RECEIVE_BOOT_COMPLETED", "com.example.habit.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}, "Unexpected packaged permission"
    assert "allowBackup(0x01010280)=false" in manifest and "usesCleartextTraffic(0x010104ec)=false" in manifest
    assert "ExportTestProvider" not in manifest and "ExportQaControlReceiver" not in manifest, "Test component leaked into demo APK"
    assert "minSdkVersion(0x0101020c)=24" in manifest and "targetSdkVersion(0x01010270)=36" in manifest
    domains = {"root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref"}
    for name, count in [("backup_rules", 1), ("data_extraction_rules", 2)]:
        tree = run(str(aapt), "dump", "xmltree", "--file", f"res/xml/{name}.xml", str(apk))
        compiled_domains = re.findall(r'A: domain="([^"]+)"', tree)
        assert len(compiled_domains) == len(domains) * count and all(compiled_domains.count(domain) == count for domain in domains)
        assert len(re.findall(r'A: path="\."', tree)) == len(domains) * count
        (OUTPUT / f"packaged-{name}.txt").write_text(tree)
    (OUTPUT / "packaged-manifest.txt").write_text(manifest)
    source = (ROOT / "resources/coach_cards.json").read_bytes()
    with zipfile.ZipFile(apk) as archive:
        assert source == archive.read("assets/coach_cards.json"), "Packaged strategy library changed"
    assert len(json.loads(source)) == 60
    # Check both possible credential inputs without disclosing their values or hashes.
    local = ROOT / ".env"
    key = next((line.partition("=")[2].strip() for line in local.read_text().splitlines() if line.startswith("GEMINI_API_KEY=")), "") if local.exists() else ""
    keys = {value.encode() for value in [key, os.environ.get("GEMINI_API_KEY", "").strip()] if value}
    if local.exists():
        subprocess.run(["git", "check-ignore", "-q", ".env"], cwd=ROOT, check=True)
        assert local.stat().st_mode & 0o777 == 0o600, "Private key file permissions changed"
    for name in run("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0"):
        path = ROOT / name
        if name and path.is_file():
            data = path.read_bytes()
            assert not any(value in data for value in keys), "Credential found in a tracked/nonignored file"
    result = {"apkSha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "catalogSha256": hashlib.sha256(source).hexdigest(),
              "packagedIdentityPermissionsBackupCleartextCatalog": "passed", "credentialAudit": "passed (configured key values not included)",
              "nativeSqliteV2ToV3": migration_sql_audit(),
              "limits": "Offline artifact/native SQLite audit only. Does not execute Room migration, Android transport, Compose, OEM backup/reminders or Gemini."}
    (OUTPUT / "audit.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
