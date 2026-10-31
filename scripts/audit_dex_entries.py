"""Reproducible candidate inventory. Counts are lexical, not unsafe-call counts.

A gate anywhere in a file does not prove coverage of every call. Review call
graphs and lazy result consumption before enabling native reclamation.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
patterns = {
    'raw_context': r'context\.(?:dexKitBridge|dexFinder|dexBridgeHolder)\s*\(',
    'query_candidate': r'\.find(?:Class|Method|Field)\s*\(',
    'data_candidate': r'\b(?:ClassData|MethodData|FieldData)\b|\.(?:getMethodInstance|getConstructorInstance|getFieldInstance)\s*\(',
    'gate_marker': r'\b(?:queryDexKit|queryDexKitTask|withCurrentBridge|queryCurrentBridge)\s*[{(]',
}
print('# DexKit candidate inventory\n')
print('Lexical candidates only: reflection false positives and existing gates require call-graph review.\n')
print('| File | Raw context | Query candidates | Data/conversion candidates | Gate markers |')
print('| --- | ---: | ---: | ---: | ---: |')
totals = dict.fromkeys(patterns, 0)
count = 0
for path in sorted((root / 'app/src/main/java/t/mma').rglob('*')):
    if path.suffix not in {'.java', '.kt'}:
        continue
    source = path.read_text()
    if 'dexkit' not in source.lower() and not re.search(patterns['raw_context'], source):
        continue
    values = {key: len(re.findall(pattern, source)) for key, pattern in patterns.items()}
    if not any(values.values()):
        continue
    count += 1
    for key, value in values.items():
        totals[key] += value
    print('| ' + str(path.relative_to(root)) + ' | ' + ' | '.join(map(str, values.values())) + ' |')
print('\nFiles:', count, '; category totals:', totals)
