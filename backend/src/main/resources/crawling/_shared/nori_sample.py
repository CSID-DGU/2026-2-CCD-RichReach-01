"""Two-benefit format sample for original NORI. Never a complete card rule set."""
from lxml import html
from .team_core import empty_tables, identifier, row_common, digest


def section(text,start,end):
    a=text.index(start); b=text.index(end,a+len(start))
    return text[a:b].strip()


def build(card,meta,s,text):
    tables=empty_tables(); common=row_common(card)
    # Restrict extraction to this product's detail tabs, not site-wide popups.
    detail=section(text,'상세혜택 > 영화/외식/커피','상세혜택 > 놀이공원/금융')
    movie=section(detail,'영화\nCGV 35%','외식\n')
    cafe=detail[detail.index('커피\n스타벅스 20%'):].strip()
    global_text=section(text,'상세혜택 > 통합할인한도','준법감시인 심의필')
    # Fail closed if terms change. This is source-checked mapping, not an LLM guess.
    required=['노리 체크카드','20만원 이상','30만원 이상','50만원 이상','100만원 이상',
              '후불 교통이용금액, 해외이용금액','60일까지','1만원','2만원','3만원','5만원']
    for term in required:
        if term not in text: raise ValueError('Profile requires review; source anchor missing: '+term)
    expected_table='전월 이용실적\n20만원 이상\n30만원 이상\n50만원 이상\n100만원 이상\n월간 통합할인한도\n1만원\n2만원\n3만원\n5만원'
    if expected_table not in global_text:
        raise ValueError('Shared limit table changed; review the tier mapping')
    scope_ids={}
    for label,quote in [('cafe',cafe),('movie',movie),('common',global_text)]:
        sid=identifier(card,'s',label+':'+digest(quote));scope_ids[label]=sid
        tables['source_scopes'].append(dict(common,scope_id=sid,source_url=card['url'],
            source_html=meta['html_file'],scope_text=quote))
    for label,category,category_name,target,code,rate,maximum,quote in [
        ('cafe','CAFE','카페','스타벅스','STARBUCKS',20,4000,cafe),
        ('movie','MOVIE','영화','CGV','CGV',35,7000,movie)]:
        expected=f'1회 최대 할인금액 {maximum:,}원'
        if expected not in quote or '이용금액 건당 1만원~2만원까지 할인 적용' not in quote:
            raise ValueError('Changed transaction threshold/cap: '+label)
        bid=identifier(card,'b',label)
        conditions=('전월 20만원 이상. 건당 1만원 이상 결제 시 결제금액 중 2만원까지만 할인 적용. '
                    f'건당 최대 {maximum:,}원. 통합 월 한도는 전월 20/30/50/100만원 이상에 따라 1/2/3/5만원. '
                    '동일계열 카드와 한도 공유. 최초 60일 예외 및 공통 제외조건은 common 근거 참조.')
        tables['card_benefits_all'].append(dict(common,issuer=card['issuer'],source_url=card['url'],
            source_file=meta['html_file'],source_snapshot_date=meta['retrieved_at'][:10],
            benefit_id=bid,benefit_type='DISCOUNT',category=category,merchants=target,
            rate_percent=rate,reward_unit='KRW',per_transaction_min_krw=10000,
            previous_month_min_krw=200000,previous_month_requirement_status='MINIMUM',
            monthly_limit_status='LIMITED_AND_SHARED',cap_group_id='kb_nori_family_monthly',
            conditions=conditions,review_notes='형식 확인용 2개 혜택 발췌. 전체 카드 규칙 아님. 건당 인정금액 상한·구간별 통합한도는 기존 전용 숫자 컬럼이 없어 조건문에 보존.',
            review_status='SNAPSHOT_FIELDS_REVIEWED',rule_engine_ready=False,source_quote=quote,
            category_name=category_name,target_raw=target,targets_normalized=target,monthly_limit_unit='KRW',
            source_scope_id=scope_ids[label],extraction_method='SOURCE_ASSERTED_SAMPLE_PROFILE',
            amount_basis='건당 할인대상 결제금액 상한 20000원; 정액 할인 아님'))
        tables['target_names'].append(dict(benefit_id=bid,issuer_code=card['issuer_code'],card_id=card['card_id'],
            target_code=code,target_name=target,target_kind='MERCHANT',matching_status='LEXICAL_MATCH_REVIEW_SCOPE'))
    snippets=[('SPEND_AND_SHARED_CAP',section(global_text,'통합할인한도\n통합할인한도','카드이용\n')),
              ('EXCLUSION',section(global_text,'가맹점 정보가 해당 업체가 아닌','자세한 사항은 KB국민카드 고객센터')),
              ('PREVIOUS_MONTH_SPEND',section(global_text,'이용실적 기준\n','카드이용 시 제공되는 포인트'))]
    for kind,quote in snippets:
        tables['card_conditions_all'].append(dict(common,condition_id=identifier(card,'c',kind),
            condition_type=kind,condition_text=quote,source_scope_id=scope_ids['common'],scope_status='SCOPE_REVIEW_REQUIRED'))
    for kind,start,end,description in [
        ('NEW_CARD_GRACE','노리체크카드 및 노리체크 동일계열카드의 최초 발급 후','노리체크카드 및 노리체크 동일계열카드 중복 소지',
         '최초 사용등록 후 60일까지 전월실적 없이 통합한도 1만원. 교통·통신·금융수수료는 제외.'),
        ('CAP_RESTORE_DELAY','할인/적립이 적용된 매출 취소 시','각 할인서비스는 해당 가맹점',
         '취소전표의 매입 지연으로 할인한도 복원이 즉시 되지 않을 수 있음.')]:
        quote=section(global_text,start,end)
        tables['special_cases'].append(dict(common,exception_type=kind,description=description,
            source_scope_id=scope_ids['common'],source_quote=quote))
    for kind,detail in [('PARTIAL_FORMAT_SAMPLE','전체 혜택이 아닌 카페·영화 2개 혜택만 발췌. 형식 승인 후 전체 확장.'),
                        ('RULE_SCHEMA_REVIEW','건당 인정금액 상한·전월실적별 공통 한도·동일계열 카드 공유 조건은 별도 계산 규칙 설계 필요. rule_engine_ready=False.')]:
        tables['review_queue'].append(dict(common,issue_type=kind,detail=detail,status='OPEN'))
    tables['card_catalog_319'].append(dict(common,issuer=card['issuer'],card_type='check',
        source_summary='형식 확인용: 노리 체크카드 카페·영화 혜택 2개 발췌. 전체 카드 완성본 아님.',
        source_url=card['url'],source_snapshot_date=meta['retrieved_at'][:10],source_text_sha256=digest(text),
        source_html_count=1,source_scope_count=3,source_table_count=len(html.fromstring(s).xpath('//table')),
        benefit_row_count=2,categories='CAFE|MOVIE',category_names='카페 / 영화',benefit_types='DISCOUNT',
        review_status='SNAPSHOT_FIELDS_REVIEWED',product_class='PAYMENT_CARD',sample_owner='NOT_PROVIDED',mvp_selected='NOT_SELECTED'))
    return tables
