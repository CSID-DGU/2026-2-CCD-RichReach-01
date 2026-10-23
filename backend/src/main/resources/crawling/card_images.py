"""Download card artwork referenced by the collected official product HTML.

No image generation or guessing of image paths. The original bytes are kept.
"""
import argparse
import hashlib
import html as html_std
import json
import re
import time
import urllib.request
from pathlib import Path
from urllib.parse import urljoin
from lxml import html
from _shared.team_core import MANIFEST, UA, DATE, decode


def candidates(card, source):
    doc=html.fromstring(decode(source.read_bytes()))
    issuer=card['issuer_code']
    if issuer=='kb':
        return [(n.get('src'),n.get('alt'),'상품 페이지 이미지') for n in doc.xpath('//img[@src]')
                if '노리 체크카드' in n.get('alt','')]
    if issuer=='shinhan':
        if card['card_id']=='1225543':
            # Product HTML cardPlate uses BOADG4; JSON-LD incorrectly points at
            # Discount Plan+ POEDR2. This exact BOADG4 URL is also published in
            # https://www.shinhancard.com/ ItemList JSON-LD.
            plate=decode(source.read_bytes())
            if 'BOADG4_E5_v_f_d.webp' not in plate:
                raise ValueError('Modu card plate changed; review before downloading')
            return [('https://cdn.www.shinhancard.com/pconts/static/images/card/plate/BOADG4_E5_v_f_d.webp',
                     card['card_name'],'상품 cardPlate와 공식 메인 ItemList의 BOADG4 일치. 잘못 연결된 Discount Plan+ JSON-LD 사진 제외')]
        # Some old og:image paths serve an HTML error page (HTTP 200).
        # Prefer the Product image explicitly published in JSON-LD.
        for node in doc.xpath('//script[@type="application/ld+json"]'):
            try:
                product=json.loads(node.text)
                if isinstance(product,dict) and product.get('@type') in ('Product','PaymentCard','CreditCard') and isinstance(product.get('image'),str):
                    return [(product['image'],card['card_name'],'공식 상품 JSON-LD 대표 이미지')]
            except (ValueError,TypeError):
                continue
        og=[n.get('content') for n in doc.xpath('//meta') if n.get('property')=='og:image']
        return [(u,card['card_name'],'상품 페이지 대표 이미지') for u in og]
    if issuer=='bc' and card['card_id']=='1000027768':
        return [(n.get('src'),n.get('alt'),'상품 페이지 이미지') for n in doc.xpath('//img[@src]')
                if '이지캐시백' in n.get('alt','')]
    if issuer=='bc' and card['card_id']=='nf-card-02':
        nodes=[n for n in doc.xpath('//img[@src]') if n.get('alt')=='카드']
        return [(n.get('src'),card['card_name'],'공식 페이지 대표 이미지') for n in nodes[:1]]
    if issuer=='hana':
        nodes=doc.xpath('//script[@id="__NEXT_DATA__"]')
        data=json.loads(nodes[0].text)
        for query in data['props']['pageProps']['dehydratedState']['queries']:
            result=query.get('state',{}).get('data',{})
            if not isinstance(result,dict): continue
            product=result.get('result',{})
            if product.get('cardProductCode')==card['card_id']:
                return [(i['cardImageUrl'],card['card_name'],f'공식 제휴 상세 디자인 {j+1}')
                        for j,i in enumerate(product['cardImageUrls'])]
    if issuer=='kakaobank':
        result=[]
        for n in doc.xpath('//img[@src]'):
            parent=n.getparent().getparent().text_content()
            if 'K-패스' in parent:
                result.append((n.get('src'),card['card_name'],'K-패스 디자인 안내 영역 이미지'))
                break
        return result
    return []


def detect_extension(data):
    if data.startswith(b'\x89PNG\r\n\x1a\n'): return '.png'
    if data.startswith(b'\xff\xd8\xff'): return '.jpg'
    if data.startswith((b'GIF87a',b'GIF89a')): return '.gif'
    if data[:4]==b'RIFF' and data[8:12]==b'WEBP': return '.webp'
    raise ValueError('Response is not a recognized image')


def run():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    if args.out.exists() and any(args.out.iterdir()): parser.error('Use a new empty output directory')
    args.out.mkdir(parents=True,exist_ok=True)
    records=[]
    for card in MANIFEST:
        stem=card['issuer_code']+'_'+card['card_id']
        source=args.sources/(stem+'.html')
        items=candidates(card,source) if source.exists() else []
        if not items:
            records.append(dict(issuer_code=card['issuer_code'],card_id=card['card_id'],
                card_name=card['card_name'],status='NOT_FOUND',source_page=card['url'],
                note='요청 상품 이미지가 원문에 없음. 로고나 다른 상품 이미지를 대신 저장하지 않음.'))
        for idx,(url,name,note) in enumerate(items,1):
            url=urljoin(card['url'],url)
            record=dict(issuer_code=card['issuer_code'],card_id=card['card_id'],card_name=name,
                source_page=card['url'],image_url=url,note=note,retrieved_at=DATE())
            try:
                req=urllib.request.Request(url,headers={'User-Agent':UA,'Referer':card['url']})
                with urllib.request.urlopen(req,timeout=40) as response:
                    data=response.read()
                    record.update(http_status=response.status,final_image_url=response.url)
                ext=detect_extension(data)
                filename=stem+'_'+str(idx)+ext
                (args.out/filename).write_bytes(data)
                record.update(status='DOWNLOADED',file=filename,bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
            except Exception as error:
                record.update(status='FAILED',error=str(error))
            records.append(record)
            time.sleep(0.6)
    (args.out/'image_manifest.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
    blocks=[]
    for row in records:
        esc=html_std.escape
        picture=f'<img src="{esc(row["file"])}">' if row.get('file') else '<div class="missing">이미지 미확보</div>'
        blocks.append(f'<article>{picture}<h2>{esc(row["card_name"])}</h2><p>{esc(row["note"])}</p>'
                      f'<p><a href="{esc(row["source_page"])}">상품 출처</a></p></article>')
    page='''<!doctype html><html lang="ko"><meta charset="utf-8"><title>MVP 카드 이미지 모음</title>
<style>body{font-family:Malgun Gothic,sans-serif;background:#f5f5fa;margin:32px;color:#252135}h1{font-size:26px}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:20px}
article{padding:22px;background:white;border-radius:16px;border:1px solid #e4e1ee}img,.missing{width:100%;height:220px;object-fit:contain}
h2{font-size:17px}p{font-size:13px;line-height:1.6;color:#514c65}a{color:#6247a8}.missing{display:grid;place-items:center;background:#eee}</style>
<h1>MVP 추가 카드 이미지</h1><p>공식 상품 페이지에서 내려받은 원본 이미지. 원본 해시와 출처는 image_manifest.json에 기록했습니다.</p>
<p>카카오뱅크는 K-패스 표기가 있는 춘식이 이미지를 사용했습니다. 다운로드 성공이 디자인별 혜택 동일성을 뜻하지 않습니다.</p><main>'''
    (args.out/'카드사진_한눈에보기.html').write_text(page+''.join(blocks)+'</main></html>',encoding='utf-8')
    print(json.dumps([{k:r[k] for k in ('card_name','status')} for r in records],ensure_ascii=False))


if __name__=='__main__':
    run()
