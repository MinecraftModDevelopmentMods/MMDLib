#!/usr/bin/env python3
"""Regenerate gameplay data (recipes, tags, loot, unlocks) from maintained material JSON.
Visual assets are static in src/main/resources/assets/mmdlib and are never rewritten.
"""
import argparse,json
from pathlib import Path

def main():
    repo=Path(__file__).resolve().parents[1]
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-assets',type=Path,help='Deprecated compatibility option; no longer used for gameplay-data generation')
    parser.add_argument('--output', type=Path, help='Generate into another directory (used by the NeoForge provider)')
    parser.add_argument('--check', action='store_true', help='Fail if generated resources differ; do not write files')
    args=parser.parse_args(); source=repo/'src/main/resources'; output=args.output or repo/'src/generated/resources'
    def write(path,data):
        target=output/path; text=json.dumps(data,indent=2)+'\n'
        if args.check:
            if not target.is_file() or target.read_text()!=text: raise SystemExit(f'Out-of-date generated resource: {target}')
        else:
            target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text)
    modules={section:{p.stem:json.loads(p.read_text()) for p in (source/f'data/mmdlib/{section}').glob('*.json')} for section in ['item','block']}
    def select(value,section):
        values=value if isinstance(value,list) else value.split();yes=set();no=set()
        aliases={alias:key for key,d in modules[section].items() for alias in d.get('part',{}).get('aliases',[])}
        for token in values:
            target=no if token.startswith('!') else yes;name=token.lstrip('!').removeprefix('mmdlib:');name=aliases.get(name,name)
            if name=='all':target.update(modules[section])
            elif name in modules[section]:target.add(name)
            else:raise ValueError(f'Unknown {section}: {token}')
        return sorted(yes-no)
    ORE_TAGS={'coal','copper','diamond','emerald','gold','iron','lapis','netherite_scrap','quartz','redstone'};RAW_TAGS={'copper','gold','iron'}
    tags={};counts={'materials':0,'items':0,'blocks':0,'recipes':0}
    def tag(kind,name,value):tags.setdefault((kind,name),set()).add(value)
    def itemid(name):return 'mmdlib:'+name
    def recipe(name,data):
        counts['recipes']+=1;write(Path(f'data/mmdlib/recipe/{name}.json'),data)
        write(Path(f'data/mmdlib/advancement/recipes/{name}.json'),{'parent':'minecraft:recipes/root','criteria':{'has_material':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':[repair]}]}}},'requirements':[['has_material']],'rewards':{'recipes':[itemid(name)]}})
    def shaped(name,pattern,key,count=1,result=None):
        recipe(name,{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':v} for k,v in key.items()},'result':{'id':result or itemid(name),'count':count}})
    def shapeless(name,ingredients,count=1,result=None):
        recipe(name,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[{'item':v} for v in ingredients],'result':{'id':result or itemid(name),'count':count}})
    def smelt(name,ingredient,result):recipe(name,{'type':'minecraft:smelting','category':'misc','ingredient':{'item':ingredient},'result':{'id':result,'count':1},'experience':.1,'cookingtime':200})
    for file in sorted((source/'mmdlib/material').glob('*.json')):
        d=json.loads(file.read_text());name=file.stem;counts['materials']+=1;its=select(d.get('items','all'),'item');bs=select(d.get('blocks','all'),'block')
        repair=d['runtime']['repair_item'];base=d['runtime']['base_block'];metal=itemid(name+'_ingot') if 'ingot' in its else repair
        rod=itemid(name+'_rod') if 'rod' in its else 'minecraft:stick'
        for form in its:
            full=name+'_'+form;counts['items']+=1
            if form in ['bow','crossbow','fishing_rod']:
                tag('item','minecraft:enchantable/'+{'bow':'bow','crossbow':'crossbow','fishing_rod':'fishing'}[form],itemid(full))
            if form in ['bow','crossbow','fishing_rod','shears','shield','axe','pickaxe','shovel','hoe','sword','crackhammer','scythe','helmet','chestplate','leggings','boots']:
                tag('item','minecraft:enchantable/durability',itemid(full))
            if form in ['axe','pickaxe','shovel','hoe','crackhammer','scythe']:
                tag('item','minecraft:enchantable/mining',itemid(full));tag('item','minecraft:enchantable/mining_loot',itemid(full))
            if form in ['sword','axe','scythe','crackhammer']:
                tag('item','minecraft:enchantable/weapon',itemid(full));tag('item','minecraft:enchantable/sharp_weapon',itemid(full))
            if form=='sword':tag('item','minecraft:enchantable/sword',itemid(full))
            if form in ['arrow','bolt']:tag('item','minecraft:arrows',itemid(full));tag('item','c:arrows',itemid(full))
            if form in ['pickaxe','axe','shovel','hoe','sword']:tag('item','minecraft:'+form+'s',itemid(full))
            if form in ['helmet','chestplate','leggings','boots']:tag('item','minecraft:enchantable/armor',itemid(full));tag('item','minecraft:enchantable/'+('leg_armor' if form=='leggings' else 'foot_armor' if form=='boots' else 'head_armor' if form=='helmet' else 'chest_armor'),itemid(full))
            if form in ['dust','ingot','nugget','rod','gear']:tag('item','c:'+{'dust':'dusts','ingot':'ingots','nugget':'nuggets','rod':'rods','gear':'gears'}[form]+'/'+name,itemid(full))
            if form=='gear':tag('item','c:gears','#c:gears/'+name)  # Umbrella tag: any material's gear.
            patterns={'axe':['MM','MR',' R'],'pickaxe':['MMM',' R ',' R '],'shovel':['M','R','R'],'hoe':['MM',' R',' R'],'sword':['M','M','R'],'crackhammer':['MMM','MRM',' R '],'scythe':['MMM','  R','  R'],'helmet':['MMM','M M'],'chestplate':['M M','MMM','MMM'],'leggings':['MMM','M M','M M'],'boots':['M M','M M'],'shears':[' M','M '],'gear':[' M ','M M',' M '],'rod':['M','M']}
            if form in patterns:
                pattern=patterns[form];key={'M':metal}
                if any('R' in line for line in pattern):key['R']=rod if form not in ['rod'] else 'minecraft:stick'
                shaped(full,pattern,key,4 if form=='rod' else 1)
            elif form=='horse_armor':shaped(full,['M M','MLM','M M'],{'M':metal,'L':'minecraft:leather'})
            elif form=='bow':shaped(full,[' RS','R S',' RS'],{'R':rod,'S':'minecraft:string'})
            elif form=='crossbow':shaped(full,['MRM','STS',' R '],{'M':metal,'R':rod,'S':'minecraft:string','T':'minecraft:tripwire_hook'})
            elif form=='fishing_rod':shaped(full,['  R',' RS','R S'],{'R':rod,'S':'minecraft:string'})
            elif form=='shield':shaped(full,['MMM','MMM',' M '],{'M':metal})
            elif form in ['arrow','bolt']:shaped(full,['M','R','F'],{'M':metal,'R':'minecraft:stick' if form=='arrow' else rod,'F':'minecraft:feather'},4)
            elif form=='nugget':
                shapeless(full,[metal],9);shaped(name+'_from_nuggets',['NNN']*3,{'N':itemid(full)},result=metal)
            elif form=='dust':
                # Dust is obtained with the crackhammer, not a single-input crafting recipe
                # that would shadow nugget conversion. Remove only this generator's obsolete files.
                for obsolete in [output/f'data/mmdlib/recipe/{full}.json',output/f'data/mmdlib/advancement/recipes/{full}.json']:
                    if args.check:
                        if obsolete.exists(): raise SystemExit(f'Obsolete generated recipe: {obsolete}')
                    else: obsolete.unlink(missing_ok=True)
                smelt(full+'_smelting',itemid(full),repair)
                def crushing(suffix,ingredient,count):
                    recipe(name+'_dust_from_'+suffix+'_crushing',{'type':'mmdlib:crushing','ingredient':ingredient,'result':{'id':itemid(full),'count':count}})
                recipe(full+'_crushing',{'type':'mmdlib:crushing','ingredient':{'item':repair},'result':{'id':itemid(full),'count':1}})
                # Ores and raw ores give 2 dust; storage blocks give back the dust they hold
                # (special.storage_block_dust, default 9). Only tags NeoForge actually defines are used.
                if name in ORE_TAGS:crushing('ore',{'tag':'c:ores/'+name},2)
                if name in RAW_TAGS:
                    crushing('raw',{'tag':'c:raw_materials/'+name},2)
                    crushing('raw_block',{'tag':'c:storage_blocks/raw_'+name},18)
                storage=itemid(name+'_block') if 'block' in bs else base
                if storage!=repair:crushing('block',{'item':storage},int(d.get('special',{}).get('storage_block_dust',9)))
            elif form=='smallpowder':
                dust=itemid(name+'_dust') if 'dust' in its else repair
                shapeless(full,[dust],9);shaped(name+'_from_smallpowder',['NNN']*3,{'N':itemid(full)},result=dust)
            elif form=='blend':shapeless(full,[itemid(name+'_dust')]*2,2);smelt(full+'_smelting',itemid(full),repair)
            elif form=='smallblend':shapeless(full,[itemid(name+'_blend')],9);shaped(name+'_from_smallblend',['NNN']*3,{'N':itemid(full)},result=itemid(name+'_blend'))
            elif form=='ingot':smelt(full,repair,itemid(full))
        for form in bs:
            full=name+'_'+form;counts['blocks']+=1
            definition=modules['block'][form];has_item=definition.get('template',{}).get('has_item',True)
            if has_item:counts['items']+=1
            for tagged in definition.get('template',{}).get('tags',[]):tag('block',tagged,itemid(full))
            for tagged in d.get('harvest_tags',[]):tag('block',tagged,itemid(full))
            tag('block','minecraft:mineable/pickaxe',itemid(full))
            level='diamond' if d['hardness']>=10 else 'iron' if d['hardness']>=8 else 'stone' if d['hardness']>=5 else None
            if level:tag('block','minecraft:needs_'+level+'_tool',itemid(full))
            target=itemid(name+'_slab') if form=='double_slab' else itemid(full)
            functions=[]
            if form=='double_slab':functions=[{'function':'minecraft:set_count','count':2}]
            elif form=='slab':functions=[{'function':'minecraft:set_count','count':2,'conditions':[{'condition':'minecraft:block_state_property','block':itemid(full),'properties':{'type':'double'}}]}]
            pool={'rolls':1,'entries':[{'type':'minecraft:item','name':target,**({'functions':functions} if functions else {})}],'conditions':[{'condition':'minecraft:survives_explosion'}]}
            if form=='door':pool['conditions'].append({'condition':'minecraft:block_state_property','block':itemid(full),'properties':{'half':'lower'}})
            write(Path(f'data/mmdlib/loot_table/blocks/{full}.json'),{'type':'minecraft:block','pools':[pool]})
            if not has_item:continue
            patterns={'bars':(['MMM','MMM'],16,metal),'door':(['MM','MM','MM'],3,metal),'trapdoor':(['MM','MM'],1,metal),'button':(['M'],1,base),'slab':(['MMM'],6,base),'pressure_plate':(['MM'],1,metal),'stairs':(['M  ','MM ','MMM'],4,base),'wall':(['MMM','MMM'],6,base),'plate':(['MMM'],3,metal),'block':(['MMM']*3,1,repair)}
            if form in patterns:
                pattern,count,ingredient=patterns[form];shaped(full,pattern,{'M':ingredient},count)
            elif form=='lever':shaped(full,['R','M'],{'R':rod,'M':base})
            if form=='block':shapeless(name+'_from_block',[itemid(full)],9,repair)
        if d.get('has_fluid',True):
            fluid=name+'_molten'
            tag('fluid','c:molten_'+name,itemid(fluid));tag('fluid','c:molten_'+name,'mmdlib:flowing_'+fluid)
            counts['items']+=1
    # Standalone (material-less) blocks: tags from their template (or their module's), and a plain self-drop.
    for f in sorted((source/'mmdlib/standalone/block').rglob('*.json')):
        d=json.loads(f.read_text());full=f.relative_to(source/'mmdlib/standalone/block').with_suffix('').as_posix()
        module=d['module'].removeprefix('mmdlib:');template=d.get('template',modules['block'][module]['template'])
        counts['standalone_blocks']=counts.get('standalone_blocks',0)+1
        for tagged in template.get('tags',[]):tag('block',tagged,itemid(full))
        pool={'rolls':1,'entries':[{'type':'minecraft:item','name':itemid(full)}],'conditions':[{'condition':'minecraft:survives_explosion'}]}
        write(Path(f'data/mmdlib/loot_table/blocks/{full}.json'),{'type':'minecraft:block','pools':[pool]})
    for (kind,key),values in sorted(tags.items()):
        namespace,path=key.split(':',1);write(Path(f'data/{namespace}/tags/{kind}/{path}.json'),{'replace':False,'values':sorted(values)})
    write(Path('data/mmdlib/example_manifest.json'),counts)
    print(json.dumps(counts))
if __name__=='__main__':main()
