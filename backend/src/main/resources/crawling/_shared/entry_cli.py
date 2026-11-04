"""Shared CLI for the issuer/credit.py and issuer/debit.py entry points."""
import argparse
import importlib
import json
import sys
from pathlib import Path
from .settings import MANIFEST

CATALOG_ISSUERS={'kb','hyundai','nh','citi','woori'}
CODE_ROOT=Path(__file__).resolve().parents[1]

def plan(issuer,kind,mode,card_id=None):
    cards=[c for c in MANIFEST if c['issuer_code']==issuer and c['runner_type']==kind and (not card_id or c['card_id']==card_id)]
    return {'catalog':issuer in CATALOG_ISSUERS and mode in ('all','catalog'),
            'team':cards if mode in ('all','team','sample') else [],'sample':mode=='sample'}

def output_path(value):
    output=Path(value).expanduser().resolve()
    if output==CODE_ROOT or output.is_relative_to(CODE_ROOT):
        raise ValueError('Write results outside src/main/resources/crawling; code and data must stay separate')
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError('Choose a new empty --out directory; existing files will not be deleted')
    return output

def run(issuer,kind):
    p=argparse.ArgumentParser(description=f'{issuer} {kind} public-source crawler. No DB writes. Dependencies: beautifulsoup4, lxml.')
    p.add_argument('--out',help='New empty output directory outside the crawling code folder')
    p.add_argument('--mode',choices=['all','catalog','team','sample'],default='all')
    p.add_argument('--list',action='store_true',help='Show configured scope without network or writes')
    p.add_argument('--card-id')
    p.add_argument('--offline',type=Path,help='Saved KB NORI HTML; sample mode only')
    p.add_argument('--limit',type=int,help='Catalog detail limit only; list discovery still runs')
    p.add_argument('--legacy',type=Path,help='Optional legacy eight-CSV directory for ID collision checks')
    args=p.parse_args()
    selected=plan(issuer,kind,args.mode,args.card_id)
    if args.list:
        print(json.dumps({'issuer_code':issuer,'entry_type':kind,'catalog_supported':issuer in CATALOG_ISSUERS,
              'team_products':[{'card_id':c['card_id'],'name':c['card_name'],'card_type':c['card_type']} for c in selected['team']],
              'scope':'Catalog: existing 5 issuer adapters. Team: configured products only. No complete benefit-rule extraction.'},ensure_ascii=False,indent=2))
        return
    if args.limit is not None and args.limit<1:p.error('--limit must be positive')
    if args.card_id and args.mode not in ('team','sample'):p.error('--card-id requires --mode team or sample')
    if args.offline and args.mode!='sample':p.error('--offline requires --mode sample')
    if args.offline and not args.offline.is_file():p.error('--offline file does not exist')
    if args.mode=='sample' and (issuer!='kb' or kind!='debit' or args.card_id not in (None,'01664')):
        p.error('Only Kookmin/debit.py --card-id 01664 has a sample profile')
    if args.mode=='catalog' and issuer not in CATALOG_ISSUERS:p.error('Full catalog adapter not implemented for this issuer; use --mode team')
    if not selected['catalog'] and not selected['team']:p.error('No configured products for this issuer/type. No empty success file will be produced.')
    if not args.out:p.error('--out is required')
    try:out=output_path(args.out)
    except ValueError as exc:p.error(str(exc))
    out.mkdir(parents=True,exist_ok=True)
    results=[];failed=False
    if selected['catalog']:
        try:
            common=importlib.import_module('_shared.catalog_common')
            common.OUT_ROOT=out/'catalog'
            module=importlib.import_module('_shared.catalog_'+issuer)
            module.main(card_kind=kind,limit=args.limit)
            summary=json.loads((common.OUT_ROOT/issuer/'summary.json').read_text(encoding='utf-8'))
            ok=summary['unique_cards']>0 and summary['errors']==0 and summary['detail_failed']==0
            results.append({'mode':'catalog','status':'COLLECTED' if ok else 'REVIEW_REQUIRED','summary':summary})
            failed|=not ok
        except Exception as exc:
            results.append({'mode':'catalog','status':'FAILED','error':f'{type(exc).__name__}: {exc}'});failed=True
    if selected['team']:
        previous_argv=sys.argv
        try:
            core=importlib.import_module('_shared.team_core')
            sys.argv=[previous_argv[0],'--mode','sample' if selected['sample'] else 'raw','--out',str(out/'team')]
            if args.card_id:sys.argv+=['--card-id',args.card_id]
            if args.offline:sys.argv+=['--offline',str(args.offline)]
            if args.legacy:sys.argv+=['--legacy',str(args.legacy)]
            core.run(issuer,kind)
            access=json.loads((out/'team/access_results.json').read_text(encoding='utf-8'))
            ok=bool(access) and all(r['status']=='SOURCE_COLLECTED' for r in access)
            results.append({'mode':'sample' if selected['sample'] else 'team','status':'COLLECTED' if ok else 'REVIEW_REQUIRED',
                            'card_statuses':[{'card_id':r['card_id'],'status':r['status']} for r in access],
                            'rule_scope':'Two-benefit partial sample only' if selected['sample'] else 'Raw evidence only; structured benefit rows not populated'})
            failed|=not ok
        except Exception as exc:
            results.append({'mode':'team','status':'FAILED','error':f'{type(exc).__name__}: {exc}'});failed=True
        finally:sys.argv=previous_argv
    report={'issuer_code':issuer,'entry_type':kind,'requested_mode':args.mode,'results':results,'database_written':False,'exit_code':1 if failed else 0}
    (out/'run_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))
    if failed:raise SystemExit(1)
