#!/usr/bin/env python3
"""Import only the artwork dependencies used by MMDLib's vanilla extensions.
PNG bytes and animation metadata are copied unchanged; model IDs are namespaced for MMDLib.
Normal datagen consumes the imported assets and does not require the reference content folder.
"""
import argparse,hashlib,json,re,shutil
from pathlib import Path

def main():
    repo=Path(__file__).resolve().parents[1]
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,default=repo/'content');args=parser.parse_args()
    original=args.source;source=repo/'src/main/resources';assets=source/'assets/mmdlib';assets.mkdir(parents=True,exist_ok=True)
    provenance={};derived={};visited=set();untinted_items=set();untinted_blocks=set();block_textures={}
    def canonical(path):return re.sub(r'_powder$', '_dust',path)
    def write(relative,value):
        target=assets/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(value,indent=2)+'\n')
    def copied(relative,destination):
        target=assets/destination;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original/relative,target)
        provenance[destination]={'source':relative,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
    def texture(path):
        destination='textures/'+canonical(path)+'.png';relative='textures/'+path+'.png'
        if not (original/relative).is_file():raise FileNotFoundError(original/relative)
        copied(relative,destination)
        if (original/(relative+'.mcmeta')).is_file():copied(relative+'.mcmeta',destination+'.mcmeta')
        return 'mmdlib:'+canonical(path)
    def ref(value,kind):
        if value.startswith('basemetals:'):
            old=value.split(':',1)[1]
            return model(old) if kind=='model' else texture(old)
        return value
    def model(path):
        dest=canonical(path)
        if dest in visited:return 'mmdlib:'+dest
        visited.add(dest);file=original/'models'/f'{path}.json';data=json.loads(file.read_text())
        if 'parent' in data:data['parent']=ref(data['parent'],'model')
        if 'textures' in data:data['textures']={key:ref(value,'texture') for key,value in data['textures'].items()}
        for override in data.get('overrides',[]):override['model']=ref(override['model'],'model')
        # The old crossbow files inherited the bow grip. Use the actual crossbow display transforms.
        if path.endswith('_crossbow'):
            data['parent']='minecraft:item/crossbow'
            for suffix,firework in [('_arrow',False),('_firework',True)]:
                loaded=dest+suffix
                write('models/'+loaded+'.json',{'parent':'minecraft:item/crossbow','textures':{'layer0':'mmdlib:'+dest+'_pulling_2'}})
                derived['models/'+loaded+'.json']='Loaded-state model using supplied fully-drawn crossbow sprite'
                predicate={'charged':1}
                if firework:predicate['firework']=1
                data.setdefault('overrides',[]).append({'predicate':predicate,'model':'mmdlib:'+loaded})
        write('models/'+dest+'.json',data)
        provenance['models/'+dest+'.json']={'source':str(file.relative_to(original)),'sha256':hashlib.sha256((assets/'models'/f'{dest}.json').read_bytes()).hexdigest()}
        return 'mmdlib:'+dest
    for file in sorted((source/'mmdlib/material').glob('*.json')):
        material=file.stem;data=json.loads(file.read_text())
        for form in data['items'].split():
            old=material+'_'+('powder' if form=='dust' else form);name=material+'_'+form
            if (original/'models/item'/f'{old}.json').is_file():model('item/'+old)
            elif form in ['blend','smallblend']:
                sprite=texture('item/'+material+('_powder' if form=='blend' else '_smallpowder'))
                write('models/item/'+name+'.json',{'parent':'minecraft:item/generated','textures':{'layer0':sprite}})
                derived['models/item/'+name+'.json']='Pure-material blend using supplied dust sprite'
            else:raise FileNotFoundError('Missing example artwork: '+name)
            untinted_items.add('mmdlib:'+name)
        for form in data['blocks'].split():
            name=material+'_'+form;mapping={}
            if form=='bars':mapping={'bars':texture('block/'+name),'edge':texture('block/'+name)}
            elif form=='door':mapping={'top':texture('block/'+name+'_upper'),'bottom':texture('block/'+name+'_lower')}
            elif form=='trapdoor':mapping={'texture':texture('block/'+name)}
            elif form=='block' and (original/'textures/block'/f'{name}.png').is_file():mapping={'all':texture('block/'+name)}
            if mapping:block_textures['mmdlib:'+name]=mapping
            untinted_blocks.add('mmdlib:'+name)
            module=json.loads((source/'data/mmdlib/block'/f'{form}.json').read_text())
            if module.get('template',{}).get('has_item',True):
                if (original/'models/item'/f'{name}.json').is_file():model('item/'+name)
                untinted_items.add('mmdlib:'+name)
        if any(form in data['items'].split() for form in ['helmet','horse_armor']):
            for layer in [1,2]:texture(f'models/armor/{material}_layer_{layer}')
            texture('entity/horse/armor/horse_armor_'+material)
            data['runtime']['armor_texture']='mmdlib:'+material;data['runtime']['tinted_armor']=False
            file.write_text(json.dumps(data,indent=2)+'\n')
    texture('block/molten_metal_still');texture('block/molten_metal_flow')
    write('imported_assets.json',{'untinted_items':sorted(untinted_items),'untinted_blocks':sorted(untinted_blocks),'block_textures':block_textures,'sources':dict(sorted(provenance.items())),'derived':dict(sorted(derived.items()))})
    print(json.dumps({'source_assets':len(provenance),'derived_models':len(derived),'untinted_items':len(untinted_items),'untinted_blocks':len(untinted_blocks)}))
if __name__=='__main__':main()
