"""Author reviewed-source terms into the unchanged eight-CSV contract.

The numeric columns are authoritative. The existing conditions TEXT column holds
card-benefit-v1 JSON for predicates that the legacy DTO could not express.
This script does not access a database, mark human approval, or contain transactions.
"""
import argparse
import csv
import json
from pathlib import Path

FILES = ['card_catalog_319','card_benefits_all','card_conditions_all','source_scopes','target_names','review_queue','special_cases','common_standards']
NH_EX = ['TUITION','SCHOOL_FEE','RENT','TAX','UTILITIES','FINE','POSTAGE','SOCIAL_INSURANCE','MANAGEMENT','GIFT_CARD','PREPAID','LOAN','FEE','INTEREST','ANNUAL_FEE','CANCEL','POINT_PAYMENT']
BC_EX = ['TUITION','SCHOOL_FEE','TAX','UTILITIES','SOCIAL_INSURANCE','MANAGEMENT','GIFT_CARD','PREPAID','CANCEL']
SH_EX = ['SCHOOL_FEE','TAX','UTILITIES','SOCIAL_INSURANCE','MANAGEMENT','GIFT_CARD','PREPAID','LOAN','FEE','INTEREST','ANNUAL_FEE','CANCEL','POINT_PAYMENT','INTEREST_FREE','OTHER_CARD_DISCOUNT','TMONEY']
SH_PERF = ['GIFT_CARD','PREPAID','LOAN','FEE','INTEREST','ANNUAL_FEE','TMONEY','CANCEL']

def clause(names=None, **facts):
    return dict(**({'merchants': names} if names else {}), **({'facts': facts} if facts else {}))

AREAS = [
    [clause(['11번가','G마켓','지마켓','옥션','쿠팡','농협몰','배달의민족','요기요'])],
    [clause(['이마트','홈플러스','롯데마트','하나로마트']),clause(['올리브영','다이소'],standalone_store='true')],
    [clause(['SKT','KT','LGU','에스케이텔레콤','케이티','유플러스'],autopay='true'),clause(['유튜브프리미엄','넷플릭스','웨이브','멜론'],direct_subscription='true')],
    [clause(['택시']),clause(None,transport_rf='true')],
    [clause(['스타벅스','이디야','투썸플레이스','GS25','CU','세븐일레븐'],standalone_store='true')],
    [clause(None,domestic='false')],
]

def definitions():
    rows=[]
    def add(issuer, card, key, family, description, rate='', amount='', kind='MONEY', category='ALL', **kw):
        spec=dict(schema='card-benefit-v1',family=family,description=description,kind=kind,event='PURCHASE')
        spec.update(kw.pop('spec',{}))
        rows.append(dict(issuer_code=issuer,card_id=card,benefit_id=key,benefit_type='POINT' if issuer=='nh' else 'CASHBACK',
                         category=category,rate_percent=str(rate),amount=str(amount),reward_unit='POINT' if issuer=='nh' else 'KRW',
                         rule_mode='BASE',conditions=spec,**kw))
    nh='90010036'; sh='1196867'; bc='1000027768'
    for rank,key,rate in [(1,'A001','0.4'),(2,'A003','0.2')]:
        add('nh',nh,'nh_'+nh+'_'+key,'NH_SMART',f'월간 영역 {rank}위 추가 적립',rate,
            previous_month_min_krw='200000',shared_monthly_limit='5000',cap_group_id='NH_SMART',
            spec=dict(rank=rank,areas=AREAS,exclusions=NH_EX,performance_exclusions=NH_EX,new_card_grace=True,posted_basis_for_special=True))
    add('nh',nh,'nh_'+nh+'_A002','NH_BASE','국내외 일반 결제 기본 적립','0.2',spec=dict(exclusions=NH_EX,performance_exclusions=NH_EX))
    add('nh',nh,'nh_'+nh+'_A004','NH_LOUNGE','국제공항 대상 라운지 연 1회',kind='ENTITLEMENT',
        previous_month_min_krw='500000',annual_count_limit='1',spec=dict(event='LOUNGE',facts=dict(eligible_lounge='true',same_day_boarding_pass='true',after_registration_month='true')))
    add('nh',nh,'nh_'+nh+'_A005','NH_TRANSFER','농협 전자금융 이체수수료 월 5회 면제',kind='FEE_WAIVER',
        previous_month_min_krw='300000',monthly_count_limit='5',spec=dict(event='TRANSFER',facts=dict(nh_electronic_banking='true',own_account='true')))
    baseid='mvp_bc_1000027768_b_fbfc72d6b0b9bf48'; timeid='mvp_bc_1000027768_b_38494369b964cc5c'
    add('bc',bc,baseid,'BC_BASE','국내외 일반 결제 기본 캐시백','0.2',spec=dict(exclusions=BC_EX,performance_exclusions=BC_EX,exclusive_group='BC_REWARD',priority=0))
    add('bc',bc,timeid,'BC_TIME','국내 점심·저녁 총 0.6%, 기본과 중복 금지','0.6',previous_month_min_krw='300000',
        spec=dict(exclusions=BC_EX,performance_exclusions=BC_EX,facts=dict(domestic='true'),windows=[[720,840],[1080,1200]],new_card_grace=True,exclusive_group='BC_REWARD',priority=10))
    for family,event,desc,count,kind,rate in [('BC_TRANSFER','TRANSFER','IBK 전자금융·ATM 타행 이체수수료 면제','','FEE_WAIVER',''),('BC_ATM','ATM_DOMESTIC','국내 은행 ATM 출금수수료 월 10회 면제','10','FEE_WAIVER',''),('BC_EXCHANGE','FX','영업점 USD/JPY/EUR 환전 수수료 70% 우대','','FX_FEE_DISCOUNT','70')]:
        req=dict(ibk_payment_account='true')
        if family=='BC_TRANSFER':req['ibk_transfer_channel']='true'
        if family=='BC_EXCHANGE':req.update(branch_exchange='true',eligible_fx_currency='true')
        add('bc',bc,'v2_'+family,family,desc,rate,kind=kind,previous_month_min_krw='300000',monthly_count_limit=count,spec=dict(event=event,new_card_grace=True,facts=req))
    domestic=[
        ('SH_TRANSIT','후불교통 5%','5','',0,'3000','','',[clause(None,transport_rf='true',eligible_public_transit='true')]),
        ('SH_MOBILE','이동통신 자동납부 2천원','','2000',50000,'','','1',[clause(['SKT','KT','LGU','에스케이텔레콤','케이티','유플러스'],autopay='true',pure_mobile_bill='true')]),
        ('SH_CGV','CGV 영화 5천원','','5000',20000,'','','1',[clause(['CGV'],movie_ticket='true',direct_or_onsite='true')]),
        ('SH_CONVENIENCE','편의점 1천원','','1000',10000,'','1','5',[clause(['GS25','CU','이마트24'],offline='true',standalone_store='true')]),
        ('SH_CAFE','카페 1천원','','1000',10000,'','1','5',[clause(['스타벅스','투썸플레이스','이디야','폴바셋','메가MGC','메가엠지씨','빽다방','커피빈','엔제리너스'],standalone_store='true')]),
        ('SH_FASTFOOD','패스트푸드 1천원','','1000',10000,'','1','5',[clause(['KFC','크리스피크림','배스킨라빈스'],offline='true',standalone_store='true')]),
        ('SH_DELIVERY','땡겨요 배달 1천원','','1000',10000,'','1','5',[clause(['땡겨요'],direct_delivery_purchase='true')]),
        ('SH_LIFE','올리브영·다이소 1천원','','1000',10000,'','1','5',[clause(['올리브영'],standalone_store='true'),clause(['다이소'],standalone_store='true',offline='true')]),
        ('SH_OTT','공식 OTT 정기결제 1천원','','1000',10000,'','1','5',[clause(['넷플릭스','디즈니플러스','티빙'],direct_subscription='true')]),
        ('SH_ONLINE','대상 온라인쇼핑 1천원','','1000',20000,'','1','5',[clause(['쿠팡','무신사','지그재그','에이블리','크림'],direct_merchant_checkout='true')]),
        ('SH_PAY','대상 간편결제 1%','1','',20000,'5000','','',[clause(None,pay_provider='SHINHAN_SOL'),clause(None,pay_provider='SAMSUNG'),clause(None,pay_provider='HEY_YOUNG')]),
    ]
    for family,desc,rate,amount,minimum,cap,day,month,alternatives in domestic:
        add('shinhan',sh,'v2_'+family,family,desc,rate,amount,previous_month_min_krw='200000',
            per_transaction_min_krw=str(minimum),monthly_limit=cap,daily_count_limit=day,monthly_count_limit=month,cap_group_id='SH_DOMESTIC',
            spec=dict(minimum_amount=minimum,any=alternatives,facts=dict(domestic='true'),exclusions=SH_EX,performance_exclusions=SH_PERF,
                new_card_grace=True,cap_tiers=[[200000,5000],[500000,10000],[800000,20000],[1000000,30000]],grace_spend=200000,
                exclusive_group='SH_DOMESTIC_EVENT',priority=0 if family=='SH_PAY' else 10))
    add('shinhan',sh,'v2_SH_OVERSEAS','SH_OVERSEAS','해외 일시불 1.2%','1.2',spec=dict(facts=dict(domestic='false',visa_card='true',overseas_installment_conversion='false'),exclusions=SH_EX,performance_exclusions=SH_PERF))
    add('shinhan',sh,'v2_SH_ATM','SH_ATM','해외 ATM 건당 3 USD',amount='3',daily_count_limit='1',monthly_limit='10000',spec=dict(event='ATM_OVERSEAS',facts=dict(visa_card='true',visa_atm='true')))
    rows[-1]['reward_unit']='USD'
    add('shinhan',sh,'v2_SH_TMONEY','SH_TMONEY','협약 대학 학생증 선불 기능',kind='ENTITLEMENT',spec=dict(event='TMONEY',facts=dict(partner_student_card='true')))
    add('shinhan',sh,'v2_SH_PLATINUM','SH_PLATINUM','선택한 국제브랜드 플래티늄 부가서비스',kind='ENTITLEMENT',spec=dict(event='BRAND_SERVICE',facts=dict(eligible_brand_service='true')))
    categories={'SH_TRANSIT':'PUBLIC_TRANSIT','SH_MOBILE':'TELECOM','SH_CGV':'MOVIE','SH_CONVENIENCE':'CONVENIENCE',
                'SH_CAFE':'CAFE','SH_FASTFOOD':'DINING','SH_DELIVERY':'DELIVERY_APP','SH_LIFE':'SHOPPING_OTHER','SH_OTT':'SUBSCRIPTION',
                'SH_ONLINE':'ONLINE_SHOPPING','SH_PAY':'PAYMENT','SH_OVERSEAS':'OVERSEAS','NH_SMART':'MULTI_CATEGORY'}
    for r in rows:
        s=r['conditions'];r['category']=categories.get(s['family'],'ALL_MERCHANTS' if r['category']=='ALL' else r['category'])
        if s['kind']!='MONEY':
            r['benefit_type']='FREE_SERVICE' if s['kind']=='ENTITLEMENT' else 'FEE_WAIVER'
            r['category']='FINANCIAL_SERVICE' if s['kind']!='ENTITLEMENT' else 'ADDITIONAL_SERVICE'
        if r['issuer_code']=='nh' and s['family']=='NH_TRANSFER':s['new_card_grace']=True
    return rows

def main():
    p=argparse.ArgumentParser();p.add_argument('--input-dir',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True);args=p.parse_args()
    headers={};tables={}
    for name in FILES:
        with (args.input_dir/(name+'.csv')).open(encoding='utf-8-sig',newline='') as f:
            reader=csv.DictReader(f);headers[name]=reader.fieldnames;tables[name]=list(reader)
    cards={(r['issuer_code'],r['card_id']):r for r in tables['card_catalog_319']}
    scopes={}
    for s in tables['source_scopes']:scopes.setdefault((s['issuer_code'],s['card_id']),s['scope_id'])
    tables['card_benefits_all']=[];tables['card_conditions_all']=[];tables['target_names']=[];tables['special_cases']=[];tables['review_queue']=[]
    for r in definitions():
        key=r['issuer_code'],r['card_id'];card=cards[key];spec=r['conditions']
        r.update(card_name=card['card_name'],issuer=card['issuer'],source_url=card['source_url'],source_snapshot_date=card['source_snapshot_date'],
            source_scope_id=scopes[key],review_status='MANUALLY_STRUCTURED_SOURCE_REVIEW',rule_engine_ready='False',
            extraction_method='OFFICIAL_TERMS_STRUCTURED_V2',source_quote='',review_notes='2026-11-10 공식 페이지 기준 구조화·테스트 대상. 과거 적용 약관 및 사람 승인 전; 거래 사실 미확인은 UNKNOWN으로 처리.',
            previous_month_requirement_status='MINIMUM' if r.get('previous_month_min_krw') else 'NONE')
        r['conditions']=json.dumps(spec,ensure_ascii=False,separators=(',',':'))
        tables['card_benefits_all'].append({h:r.get(h,'') for h in headers['card_benefits_all']})
        tables['card_conditions_all'].append(dict(condition_id='cond_'+r['benefit_id'],issuer_code=key[0],card_id=key[1],card_name=card['card_name'],condition_type='CALCULATION_RULE_V1',condition_text=r['conditions'],source_scope_id=scopes[key],scope_status='LINKED'))
        tables['review_queue'].append(dict(issuer_code=key[0],card_id=key[1],card_name=card['card_name'],issue_type='HUMAN_APPROVAL_REQUIRED',detail=r['benefit_id']+': '+spec['description'],status='OPEN'))
        if spec['kind']!='MONEY': tables['special_cases'].append(dict(issuer_code=key[0],card_id=key[1],card_name=card['card_name'],exception_type=spec['kind'],description=spec['description']+'; 실제 이용 이벤트·금전가치 확인 필요',source_scope_id=scopes[key],source_quote=spec['description']))
        names=sorted({name for c in spec.get('any',[]) for name in c.get('merchants',[])})
        for name in names:tables['target_names'].append(dict(benefit_id=r['benefit_id'],issuer_code=key[0],card_id=key[1],target_code='',target_name=name,target_kind='MERCHANT',matching_status='NAME_RULE_REQUIRES_CONTEXT'))
    for key,card in cards.items():
        own=[r for r in tables['card_benefits_all'] if (r['issuer_code'],r['card_id'])==key]
        card.update(benefit_row_count=str(len(own)),categories='|'.join(sorted({r['category'] for r in own})),benefit_types='|'.join(sorted({r['benefit_type'] for r in own})),source_summary='공식 혜택 항목을 거래별 규칙으로 구조화. conditions는 card-benefit-v1 JSON. 신규 거래 계산기 필요.',review_status='MANUALLY_STRUCTURED_SOURCE_REVIEW')
    known={r['code'] for r in tables['common_standards'] if r['standard_kind']=='CATEGORY'}
    added={'PAYMENT':'간편결제','OVERSEAS':'해외 거래','MULTI_CATEGORY':'여러 혜택 영역','FINANCIAL_SERVICE':'금융 수수료','ADDITIONAL_SERVICE':'부가 서비스'}
    for code,label in added.items():
        if code not in known:
            tables['common_standards'].append(dict(standard_kind='CATEGORY',code=code,name=label,source_alias_pattern='',definition='거래별 카드 혜택 규칙 확장 코드'))
    args.output_dir.mkdir(parents=True,exist_ok=True)
    for name in FILES:
        with (args.output_dir/(name+'.csv')).open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.DictWriter(f,fieldnames=headers[name]);w.writeheader();w.writerows(tables[name])
    print('rows',len(tables['card_benefits_all']),'families',len({json.loads(r['conditions'])['family'] for r in tables['card_benefits_all']}))

if __name__=='__main__':main()
