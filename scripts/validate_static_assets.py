#!/usr/bin/env python3
"""Validate static model dependencies, overrides, textures, and animation metadata without launching a GUI."""
import json,struct,zipfile
from pathlib import Path

def main():
    repo=Path(__file__).resolve().parents[1];root=repo/'src/main/resources';assets=root/'assets/mmdlib'
    jars={name:zipfile.ZipFile(repo/path) for name,path in {'minecraft':'.gradle/repositories/ng_dummy_ng/net/minecraft/client/1.21.1/client-1.21.1-client-extra.jar','neoforge':'.gradle/repositories/ng_dummy_ng/net/neoforged/neoforge/21.1.247/neoforge-21.1.247.jar'}.items()}
    def parts(ref):return ref.split(':',1) if ':' in ref else ('minecraft',ref)
    def read_model(ref):
        ns,path=parts(ref)
        if path.startswith('builtin/'):return {}
        relative=f'assets/{ns}/models/{path}.json';file=root/relative
        if file.is_file():return json.loads(file.read_text())
        if ns in jars and relative in jars[ns].namelist():return json.loads(jars[ns].read(relative))
        raise AssertionError('Missing model: '+ref)
    def texture(ref):
        ns,path=parts(ref);relative=f'assets/{ns}/textures/{path}.png'
        assert (root/relative).is_file() or ns in jars and relative in jars[ns].namelist(), 'Missing texture: '+ref
    def inherited(ref,chain=()):
        assert ref not in chain, 'Model parent cycle: '+str(chain+(ref,))
        value=read_model(ref);base=inherited(value['parent'],chain+(ref,)) if value.get('parent') else {}
        return {**base,**value,'textures':{**base.get('textures',{}),**value.get('textures',{})}}
    models=list((assets/'models').rglob('*.json'))
    for file in models:
        value=json.loads(file.read_text());assert 'basemetals:' not in json.dumps(value),file
        if value.get('parent'):read_model(value['parent'])
        for override in value.get('overrides',[]):read_model(override['model'])
        for ref in value.get('textures',{}).values():
            if not ref.startswith('#'):texture(ref)
    rules=json.loads((assets/'imported_assets.json').read_text())
    roots={'mmdlib:item/'+identifier.split(':',1)[1] for identifier in rules['untinted_items']}
    roots.update('mmdlib:item/'+p.stem for p in (assets/'models/item').glob('*_bucket.json'))
    pending=list(roots);checked=set()
    while pending:
        ref=pending.pop()
        if ref in checked:continue
        checked.add(ref);data=inherited(ref);mapping=data.get('textures',{})
        pending.extend(value['model'] for value in data.get('overrides',[]))
        def resolved(value):
            seen=set()
            while value.startswith('#'):
                key=value[1:];assert key not in seen and key in mapping,(ref,'missing/cyclic texture',value)
                seen.add(key);value=mapping[key]
            texture(value)
        for element in data.get('elements',[]):
            for face in element.get('faces',{}).values():resolved(face['texture'])
    for file in (assets/'blockstates').glob('*.json'):
        def walk(value):
            if isinstance(value,dict):
                for k,v in value.items():
                    if k=='model':read_model(v)
                    else:walk(v)
            elif isinstance(value,list):
                for v in value:walk(v)
        walk(json.loads(file.read_text()))
    pngs=list((assets/'textures').rglob('*.png'))
    for file in pngs:
        raw=file.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n',file
        width,height=struct.unpack('>II',raw[16:24]);assert width>0 and height>0,file
        metadata=file.with_name(file.name+'.mcmeta')
        if metadata.is_file():
            meta=json.loads(metadata.read_text())
            # Animated textures declare 'animation'; GUI sprites declare 'gui' scaling (nine_slice/tile/stretch).
            assert isinstance(meta.get('animation'),dict) or isinstance(meta.get('gui'),dict),metadata
    assert not any('Zone.Identifier' in p.name for p in assets.rglob('*'))
    print(f'Static assets validated: {len(models)} models, {len(pngs)} textures; no legacy namespace dependencies')
if __name__=='__main__':main()
