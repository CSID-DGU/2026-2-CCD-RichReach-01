"""Collect public source evidence and export the agreed eight-file CSV contract.

Only kb/01664 currently has a reviewed FORMAT SAMPLE profile. Other adapters
collect source evidence for subsequent rule mapping; they never infer rates
from unrelated numbers or pretend a whole card is calculation-ready.
"""
import argparse
import csv
import hashlib
import json
import re
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

from lxml import html

HERE = Path(__file__).resolve().parent
UA = 'RichReachResearch/1.0 (academic public card benefit research)'
from .settings import SCHEMA, MANIFEST
DATE = lambda: datetime.now(timezone(timedelta(hours=9))).isoformat(timespec='seconds')


def digest(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def identifier(card, kind, key):
    return f"mvp_{card['issuer_code']}_{card['card_id']}_{kind}_{digest(key)[:16]}"


def decode(b):
    for encoding in ('utf-8-sig', 'cp949', 'euc-kr'):
        try:
            return b.decode(encoding)
        except UnicodeDecodeError:
            pass
    raise ValueError('Lossless decoding failed; no replacement-character export')


def lines_from_html(s):
    doc = html.fromstring(s)
    for n in doc.xpath('//script|//style'):
        n.drop_tree()
    return '\n'.join(re.sub(r'\s+', ' ',x).strip() for x in doc.text_content().splitlines() if x.strip())


def json_strings(value, path='$'):
    if isinstance(value, dict):
        for k,v in value.items():
            yield from json_strings(v, path + '.' + str(k))
    elif isinstance(value, list):
        for i,v in enumerate(value):
            yield from json_strings(v, f'{path}[{i}]')
    elif isinstance(value, str):
        yield path, value


def extract(s, card):
    doc = html.fromstring(s)
    title = doc.xpath('string(//title)').strip()
    text = lines_from_html(s)
    if card.get('embedded_json'):
        nodes = doc.xpath('//script[@id="__NEXT_DATA__"]')
        if not nodes:
            raise ValueError('Expected public __NEXT_DATA__ missing')
        data = json.loads(nodes[0].text)
        # Other cards' promotion templates also occur in this JSON document.
        # Select only the requested product, never flatten the whole document.
        matches=[]
        for query in data.get('props',{}).get('pageProps',{}).get('dehydratedState',{}).get('queries',[]):
            candidate=query.get('state',{}).get('data',{})
            if isinstance(candidate,dict):
                product=candidate.get('result',{})
                if isinstance(product,dict) and product.get('cardProductCode')==card['card_id']:
                    matches.append(product)
        if len(matches)!=1:
            raise ValueError('Expected exactly one matching product in public JSON')
        data=matches[0]
        fragments = []
        for path, value in json_strings(data):
            if re.search('[가-힣]', value) and not value.startswith(('http','/')):
                fragments.append(path + '\n' + (lines_from_html(value) if '<' in value else value))
        text += '\n' + '\n'.join(fragments)
    return title, text


def collect(card, output, offline=None):
    stem = card['issuer_code'] + '_' + card['card_id']
    raw = output / 'sources'
    raw.mkdir(parents=True, exist_ok=True)
    meta = {'issuer_code':card['issuer_code'], 'card_id':card['card_id'],
            'requested_name':card['requested_name'], 'url':card['url'], 'retrieved_at':DATE()}
    if card.get('product_notice_url'):
        meta['product_notice_url'] = card['product_notice_url']
    try:
        if offline:
            b = Path(offline).read_bytes()
            meta.update(mode='offline_replay', http_status=None, final_url=card['url'])
        else:
            req = urllib.request.Request(card['url'], headers={'User-Agent':UA})
            with urllib.request.urlopen(req, timeout=40) as response:
                b = response.read()
                meta.update(mode='live', http_status=response.status, final_url=response.url)
        (raw / (stem+'.html')).write_bytes(b)
        s = decode(b)
        title, text = extract(s, card)
        (raw / (stem+'.txt')).write_text(text, encoding='utf-8')
        meta.update(title=title, bytes=len(b), html_sha256=hashlib.sha256(b).hexdigest(),
                    text_sha256=digest(text), text_length=len(text))
        missing = [token for token in card['identity_tokens'] if token not in text and token not in title]
        if card.get('dynamic_detail') and meta.get('http_status') == 200 and missing:
            meta.update(
                status='DYNAMIC_CONTENT_REQUIRES_RENDERED_PAGE',
                detail=('공식 모바일 상품 화면은 HTTP 200이나 이 HTTP 수집 결과는 초기 HTML만 포함하고 '
                        '상품 상세는 JavaScript 조회 후 브라우저 DOM에 표시됩니다. '
                        '자동 혜택 추출에는 렌더링된 본문 또는 공식 상품설명서가 필요합니다. '
                        '로그인/CAPTCHA 차단으로 판정하지 않습니다.'),
            )
        elif missing or '/error' in meta['final_url']:
            meta.update(status='BODY_MISSING_OR_WRONG_PRODUCT', detail='Missing identity: '+', '.join(missing))
        elif not re.search(r'\d+(?:\.\d+)?\s*%|\d[\d,]*\s*원',text):
            meta.update(status='BENEFIT_BODY_MISSING', detail='Product name present; benefit amounts not present')
        else:
            meta.update(status='SOURCE_COLLECTED', detail=card.get('note',''))
        meta['text_file'] = 'sources/'+stem+'.txt'
        meta['html_file'] = 'sources/'+stem+'.html'
        result = (meta, s, text)
    except (OSError, ValueError, etree_error()) as error:
        meta.update(status='FETCH_OR_PARSE_FAILED', detail=str(error))
        result = (meta, '', '')
    (raw / (stem+'.json')).write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding='utf-8')
    return result


def etree_error():
    from lxml.etree import LxmlError
    return LxmlError


def empty_tables():
    return {name:[] for name in SCHEMA}


def row_common(card):
    return {k:card[k] for k in ('issuer_code','card_id','card_name')}


def write_tables(output, tables):
    output.mkdir(parents=True, exist_ok=True)
    for name, header in SCHEMA.items():
        with (output/(name+'.csv')).open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.DictWriter(f,fieldnames=header,extrasaction='raise')
            w.writeheader()
            w.writerows(tables[name])


def validate(output, legacy=None):
    tables={}
    for name, header in SCHEMA.items():
        p=output/(name+'.csv')
        assert p.read_bytes().startswith(b'\xef\xbb\xbf'), 'BOM missing: '+name
        with p.open(encoding='utf-8-sig',newline='') as f:
            r=csv.DictReader(f)
            assert r.fieldnames==header, 'Header/order mismatch: '+name
            tables[name]=list(r)
        for row in tables[name]:
            assert None not in row and all(v is not None for v in row.values()), 'Malformed row'
            if 'issuer_code' in row: assert re.fullmatch('[a-z]+',row['issuer_code'])
    all_ids=set()
    for table,field in [('card_benefits_all','benefit_id'),('card_conditions_all','condition_id'),('source_scopes','scope_id')]:
        for row in tables[table]:
            key=row[field]
            assert key and key not in all_ids, 'Duplicate/empty identifier: '+key
            all_ids.add(key)
    cards={(r['issuer_code'],r['card_id']) for r in tables['card_catalog_319']}
    assert len(cards)==len(tables['card_catalog_319']), 'Duplicate card key'
    scopes={r['scope_id']:r for r in tables['source_scopes']}
    benefits={r['benefit_id']:r for r in tables['card_benefits_all']}
    for table in ['card_benefits_all','card_conditions_all','source_scopes','target_names','special_cases']:
        for row in tables[table]:
            assert (row['issuer_code'],row['card_id']) in cards, 'Missing card FK'
            if 'source_scope_id' in row:
                scope=scopes[row['source_scope_id']]
                assert scope['issuer_code']==row['issuer_code'] and scope['card_id']==row['card_id'], 'Cross-card scope'
                if row.get('source_quote'): assert row['source_quote'] in scope['scope_text'], 'Quote not in source'
                if row.get('condition_text'): assert row['condition_text'] in scope['scope_text'], 'Condition not in source'
            if table=='target_names': assert row['benefit_id'] in benefits, 'Missing benefit FK'
    if legacy:
        old_ids=set()
        for table, field in [('card_benefits_all','benefit_id'),('card_conditions_all','condition_id'),('source_scopes','scope_id')]:
            with (Path(legacy)/(table+'.csv')).open(encoding='utf-8-sig',newline='') as f:
                old_ids.update(r[field] for r in csv.DictReader(f))
        assert not (all_ids & old_ids), 'ID collision with legacy batch'
    return {'passed':True,'row_counts':{k:len(v) for k,v in tables.items()},
            'legacy_ids_checked':bool(legacy),'database_import_tested':False}


def run(issuer=None, card_type=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path)
    parser.add_argument('--mode',choices=['probe','sample','raw'],default='probe')
    parser.add_argument('--card-id')
    parser.add_argument('--offline',type=Path,help='Sample replay using saved KB HTML only')
    parser.add_argument('--legacy',type=Path,help='Legacy 8-CSV folder for collision validation')
    args=parser.parse_args()
    cards=[c for c in MANIFEST if (not issuer or c['issuer_code']==issuer) and
           (not card_type or c['runner_type']==card_type) and (not args.card_id or c['card_id']==args.card_id)]
    if not cards: parser.error('No configured products for this issuer/card type')
    if args.mode=='sample':
        cards=[c for c in cards if c['issuer_code']=='kb' and c['card_id']=='01664']
        if not cards: parser.error('Reviewed format sample is kb debit 01664 only')
    if args.offline and args.mode!='sample': parser.error('--offline is sample-only')
    if args.out.exists() and any(args.out.iterdir()): parser.error('Choose a new empty output directory; existing files are preserved')
    args.out.mkdir(parents=True,exist_ok=True)
    tables=empty_tables(); results=[]
    for card in cards:
        meta,s,text=collect(card,args.out,args.offline)
        results.append(meta)
        if args.mode=='sample':
            if meta['status']!='SOURCE_COLLECTED': raise ValueError('Sample source unavailable: '+meta['detail'])
            from .nori_sample import build
            tables=build(card,meta,s,text)
        elif args.mode=='raw':
            if meta['status']=='SOURCE_COLLECTED':
                scope_id=identifier(card,'s','whole-page:'+digest(text))
                tables['source_scopes'].append(dict(row_common(card),scope_id=scope_id,source_url=card['url'],
                    source_html=meta['html_file'],scope_text=text))
                tables['card_catalog_319'].append(dict(row_common(card),issuer=card['issuer'],card_type=card['card_type'],
                    source_url=card['url'],source_snapshot_date=meta['retrieved_at'][:10],source_text_sha256=digest(text),
                    source_html_count=1,source_scope_count=1,source_table_count=len(html.fromstring(s).xpath('//table')),
                    benefit_row_count=0,review_status='AUTO_EXTRACTED_NEEDS_REVIEW',product_class=card['product_class']))
            tables['review_queue'].append(dict(row_common(card),issue_type=meta['status'],
                detail='원문 수집 단계. 혜택·조건 구조화 미완료. '+meta.get('detail',''),status='OPEN'))
        if not args.offline: time.sleep(1)
    (args.out/'access_results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
    if args.mode!='probe':
        write_tables(args.out,tables)
        result=validate(args.out,args.legacy)
        (args.out/'validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(result,ensure_ascii=False))
    print(json.dumps([{'card':r['requested_name'],'status':r['status']} for r in results],ensure_ascii=False))


if __name__=='__main__':
    run()
