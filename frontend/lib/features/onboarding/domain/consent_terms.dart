import 'codef_consent.dart';

/// 약관 본문의 한 칸 (예: 조회 경로 · 카드사 → CODEF → 팀 리치리치).
class TermsSection {
  const TermsSection(this.title, this.body);

  final String title;
  final String body;
}

/// 약관 상세 시트 내용 (ONB-02C1~C3). 첫 칸은 강조해서 보여준다.
///
/// 약관 본문은 앱에 두고, 서버에는 동의한 약관 버전만 기록한다 (온보딩 문서 Q3).
class ConsentTerms {
  const ConsentTerms({
    required this.title,
    required this.subtitle,
    required this.summary,
    required this.sections,
  });

  /// 시트 제목 (예: "[필수] 카드정보 조회 동의")
  final String title;
  final String subtitle;

  /// 전체 약관 보기(ONB-02C0)에 보여줄 한 줄 요약
  final String summary;
  final List<TermsSection> sections;

  /// 약관 버전. 문구를 바꾸면 함께 올린다.
  static const version = '2026-10-01';

  static ConsentTerms of(CodefConsent consent) => switch (consent) {
    CodefConsent.cardInfo => cardInfo,
    CodefConsent.transaction => transaction,
    CodefConsent.privacy => privacy,
  };

  static const cardInfo = ConsentTerms(
    title: '[필수] 카드정보 조회 동의',
    subtitle: 'CODEF를 통해 연결에 필요한 카드 정보만 조회해요.',
    summary: '보유카드 연결에 필요한 카드사·카드 식별·연결 상태 정보',
    sections: [
      TermsSection('조회 경로', '카드사 → CODEF → 팀 리치리치'),
      TermsSection('조회 항목', '카드사, 카드명, 카드 상품코드, 마스킹된 카드 식별정보, 카드 연결 상태'),
      TermsSection('이용 목적', '보유카드 확인 및 카드별 혜택·실적 분석 대상 연결'),
      TermsSection('조회·전송 기간', '카드 연결 해제 또는 동의 철회 시까지'),
      TermsSection('저장 기준', '카드사 로그인·인증 정보는 저장하지 않아요.'),
      TermsSection('거부 시 제한', '카드 연결과 맞춤 플랜·추천 기능을 이용할 수 없어요.'),
    ],
  );

  static const transaction = ConsentTerms(
    title: '[필수] 거래내역·실적 조회 동의',
    subtitle: '소비 분석과 혜택 계산에 필요한 데이터만 조회해요.',
    summary: '카드 이용내역과 실적·혜택 계산에 필요한 정보',
    sections: [
      TermsSection('조회 경로', '카드사 → CODEF → 팀 리치리치'),
      TermsSection(
        '조회 항목',
        '승인·취소 일시, 가맹점명, 이용 금액, 업종, 할부 여부, 카드 실적·혜택 관련 데이터',
      ),
      TermsSection('이용 목적', '소비 분석, 실적·혜택 계산, 월간 플랜 및 카드 추천'),
      TermsSection('조회 주기', '카드 연결 중 서비스 이용 시 최신 내역 동기화'),
      TermsSection('보유 기간', '연결 해제, 동의 철회 또는 회원 탈퇴 중 가장 빠른 시점까지'),
      TermsSection('거부 시 제한', '거래 분석과 맞춤 플랜·추천 기능을 이용할 수 없어요.'),
    ],
  );

  static const privacy = ConsentTerms(
    title: '[필수] 개인(신용)정보 수집·이용 동의',
    subtitle: '동국대학교 융합캡스톤디자인 프로젝트 · 팀 리치리치',
    summary: '팀 리치리치의 소비 분석·월간 플랜·맞춤 추천을 위한 수집·이용',
    sections: [
      TermsSection(
        'CODEF 연동',
        '카드 거래내역·실적 데이터만 불러오며, 추천 결과는 팀 리치리치의 내부 알고리즘으로 생성해요.',
      ),
      TermsSection('수집·이용 주체', '동국대학교 융합캡스톤디자인 프로젝트 팀 리치리치'),
      TermsSection('수집 항목', '카드 거래내역(승인·취소 일시, 가맹점, 금액, 업종) 및 카드 실적 관련 데이터'),
      TermsSection('이용 목적', '소비 분석, 카드 실적·혜택 계산, 월간 카드 플랜 및 맞춤형 카드 추천'),
      TermsSection('보유 기간', '카드 연결 해제, 동의 철회 또는 회원 탈퇴 중 가장 먼저 발생한 때까지'),
      TermsSection(
        '거부 및 철회',
        '동의를 거부하면 카드 연결·분석·추천 기능을 이용할 수 없어요.\n'
            '설정 > 연결된 카드 관리에서 언제든 동의를 철회할 수 있어요.',
      ),
    ],
  );
}
