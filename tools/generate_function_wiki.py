#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WIKI = ROOT / "wiki"
WIKI.mkdir(exist_ok=True)

JAVA_SIG = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\([^;{}]*\)\s*\{\s*$")
JAVA_BAD = {"if", "for", "while", "switch", "catch", "synchronized", "try", "else", "do", "new", "return"}
JS_DECL = [
    re.compile(r"^\s*function\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\("),
    re.compile(r"^\s*(?:const|let|var)\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>"),
    re.compile(r"^\s*([A-Za-z_$][A-Za-z0-9_$]*)\s*:\s*(?:async\s*)?function\s*\("),
    re.compile(r"^\s*(?:async\s+)?([A-Za-z_$][A-Za-z0-9_$]*)\s*\([^)]*\)\s*\{\s*$"),
]


def split_words(name: str) -> str:
    name = name.replace("_", " ")
    name = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name)
    return " ".join(name.lower().split())


def explain(name: str) -> str:
    low = name.lower()
    if low.startswith("get"):
        return f"Returns the {split_words(name[3:]) or 'requested value'}."
    if low.startswith("set"):
        return f"Updates the {split_words(name[3:]) or 'target value'}."
    if low.startswith(("is", "has", "can")):
        return f"Checks whether {split_words(name)}."
    if low.startswith(("create", "build")):
        return f"Constructs {split_words(name)}."
    if low.startswith(("render", "draw")):
        return f"Renders {split_words(name)}."
    if low.startswith("update"):
        return f"Updates {split_words(name[6:]) or 'internal state'}."
    if low.startswith("load"):
        return f"Loads {split_words(name[4:]) or 'required data'}."
    if low.startswith("save"):
        return f"Saves {split_words(name[4:]) or 'state'}."
    if low.startswith("on"):
        return f"Handles the {split_words(name[2:]) or 'event'} callback."
    if low.startswith("init"):
        return f"Initializes {split_words(name[4:]) or 'the component lifecycle'}."
    if low.startswith("to") and len(name) > 2:
        return f"Converts this value to {split_words(name[2:])}."
    return f"Performs {split_words(name) or 'function'} logic."


def collect_entries():
    entries = []
    for path in sorted((ROOT / "src").rglob("*.java")):
        rel = "/" + path.relative_to(ROOT).as_posix()
        class_name = path.stem
        for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            s = line.strip()
            if not s or s.startswith(("//", "*", "@")):
                continue
            if "(" not in s or ")" not in s or "{" not in s or ";" in s:
                continue
            if " class " in f" {s} " or s.startswith(("class ", "interface ", "enum ", "record ")):
                continue
            m = JAVA_SIG.search(s)
            if not m:
                continue
            name = m.group(1)
            if name in JAVA_BAD:
                continue
            explanation = f"Creates a new {split_words(name)} instance." if name == class_name else explain(name)
            entries.append(("Java", rel, name, line_no, explanation))

    for path in sorted((ROOT / "src").rglob("*.js")):
        rel = "/" + path.relative_to(ROOT).as_posix()
        for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            s = line.strip()
            if not s or s.startswith("//"):
                continue
            name = None
            for pattern in JS_DECL:
                m = pattern.match(s)
                if m:
                    candidate = m.group(1)
                    if candidate not in JAVA_BAD and candidate != "constructor":
                        name = candidate
                    break
            if name:
                entries.append(("JavaScript", rel, name, line_no, explain(name)))

    entries.sort(key=lambda x: (x[0], x[1], x[3], x[2]))
    return entries


def write_pages(entries):
    home = """# Combatant Function Wiki

This wiki provides a generated function-by-function reference for repository source code.

## Pages

- [Function Reference](Function-Reference.md) — generated list of discovered Java and JavaScript functions with short summaries.
- [Wiki Maintenance](Wiki-Maintenance.md) — regenerate/update instructions.

## Coverage

- `/src/main/java`, `/src/dev/java`, `/src/duplexCourier/java`
- `/src/main/resources/assets/**/*.js`

Entries include source path, line number, function name, and concise inferred purpose.
"""
    (WIKI / "Home.md").write_text(home, encoding="utf-8")

    maintenance = """# Wiki Maintenance

The function reference is generated from source code.

## Regeneration

From repository root run:

```bash
python /home/runner/work/combatant-client/combatant-client/tools/generate_function_wiki.py
```

## Notes

- This scans Java and JavaScript sources for function/method declarations.
- Summaries are inferred from function names.
- Re-run after function additions, removals, or renames.
"""
    (WIKI / "Wiki-Maintenance.md").write_text(maintenance, encoding="utf-8")

    lines = [
        "# Function Reference",
        "",
        "This page is auto-generated and lists discovered functions with short explanations.",
        "",
        f"Total entries: **{len(entries)}**",
        "",
    ]

    current = None
    for _lang, rel, name, line_no, explanation in entries:
        if rel != current:
            lines.extend([f"## `{rel}`", ""])
            current = rel
        lines.append(f"- `{name}` (line {line_no}) — {explanation}")

    (WIKI / "Function-Reference.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    entries = collect_entries()
    write_pages(entries)
    print(f"Generated wiki with {len(entries)} function entries.")


if __name__ == "__main__":
    main()
