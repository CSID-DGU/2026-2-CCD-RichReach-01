# 거래별 혜택 규칙 실험

`build_three_card_rules.py`는 기존 8종 CSV의 **컬럼 이름과 순서**를 그대로 유지하면서 세 카드의 공식 혜택 항목을 거래별 규칙으로 만듭니다. DB에 연결하거나 적재하지 않습니다. 입력은 기존 8종 CSV 중 농협 올바른 NEW HAVE 체크, 신한 Hey Young 체크, BC/IBK 이지캐시백 체크 행만 추린 사본입니다.

```powershell
python backend/scripts/build_three_card_rules.py --input-dir <기존_8종_CSV_폴더> --output-dir <새_8종_CSV_폴더>
```

출력은 카드 3종, 혜택 행 25개(혜택 종류 24개)입니다. `card_benefits_all.csv`의 `conditions`는 `card-benefit-v1` JSON입니다. 기존 숫자 컬럼에 할인율·정액·전월실적·한도·횟수를 두고, JSON에는 시간·가맹점·결제 방법·제외조건·월간 순위·중복금지 등을 둡니다. `card_conditions_all.csv`은 동일한 JSON을 `source_scope_id`로 원문과 연결합니다. 원본 CSV와 병합할 때는 **세 카드 기존 행을 새 행으로 교체**해야 합니다. 단순 추가 시 기존 규칙과 중복됩니다.

`rule_engine_ready=false`를 유지합니다. 숫자 결과를 시험할 때만 거래별 계산기에 `allowUnverified=true`를 전달하며, 운영 계산이나 자동 승인은 하지 않습니다. `BenefitRuleQueryService.findForTransactions`가 새 형식을 읽습니다. 기존 월간 업종 합계용 `findForCalculation`에 이 JSON 규칙을 보내면 명시적으로 오류가 납니다. 거래 시각·가맹점·결제 방법을 요구하는 규칙을 월간 총액만으로 계산할 수 없기 때문입니다.

`TransactionBenefitCalculator` 한 곳이 기존 사용 내역, 즉시 혜택 선택, CP-SAT 목적값을 평가합니다. 거래별 CP-SAT은 현재 거래 1건과 최대 7개 **예측 거래 표본**만 고려합니다. 최적 상태가 나와도 그 입력 범위의 최적해이며, 실제 한 달 전체 또는 미래 소비를 미리 아는 결과가 아닙니다. 결제 금액을 늘려 실적을 억지로 채우지 않습니다. `HistoricalTransactionForecast`는 이미 관측된 직전 달의 거래만 사용하며, 예측 행에 출처와 표시를 남깁니다.

CSV의 `source_scope_id`는 근거 원문 블록과 연결됩니다. 다만 카드사의 현재 페이지를 기준으로 수동 구조화했으므로 2026년 7~10월 적용 약관과 일치하는지, 카드 발급일·국제브랜드·실제 가맹점 업종·매출 접수일 등은 사람이 별도 확인해야 합니다. 비금전 서비스(라운지·Tmoney·브랜드 서비스)는 이용 이벤트가 없으면 금전 결과에 합산되지 않습니다.

실제 거래 파일과 재생 결과는 저장소에 두지 않습니다. 로컬 시험용 자료는 별도 비공개 폴더에서 관리합니다.
