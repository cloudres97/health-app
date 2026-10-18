"""Dependency-free bundled-plan consistency check."""
import json
from pathlib import Path
p = json.loads((Path(__file__).resolve().parents[1] / 'app/src/main/assets/plan.json').read_text())
ids = {e['id'] for e in p['exercises']}
assert len(ids) == len(p['exercises'])
assert p['trainingDays'] == [3, 5, 7]
for r in p['routines']:
    assert len(r['exerciseIds']) == 4
    assert set(r['exerciseIds']) <= ids
assert len(p['meals']) == 4
assert len(p['dailyTasks']) == 3
assert all(1 <= e['sets'] <= 6 and e['steps'] for e in p['exercises'])
print('Bundled plan: references, counts, schedule and steps OK')
