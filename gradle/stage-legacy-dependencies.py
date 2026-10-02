#!/usr/bin/env python3
"""Stage exact, checksum-verified original jars in an isolated Maven mirror."""
import hashlib
import json
from pathlib import Path
import sys
import urllib.request


def stage(destination):
    specs = json.loads(Path('gradle/legacy-dependencies.json').read_text(encoding='utf-8'))
    for spec in specs:
        group, module, version = spec['coordinate'].split(':')
        target = destination / group.replace('.', '/') / module / version / (module + '-' + version + '.jar')
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest().upper() != spec['sha256']:
            request = urllib.request.Request(spec['url'], headers={'User-Agent': 'MMDLib-build-maintenance'})
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest().upper() != spec['sha256']:
                raise RuntimeError('Checksum mismatch for ' + spec['name'])
            temporary = target.with_suffix('.download')
            temporary.write_bytes(data)
            temporary.replace(target)
        print(spec['name'] + ': ' + spec['sha256'])


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: stage-legacy-dependencies.py <destination-maven-repository>')
    stage(Path(sys.argv[1]))
