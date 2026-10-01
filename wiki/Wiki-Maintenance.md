# Wiki Maintenance

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
