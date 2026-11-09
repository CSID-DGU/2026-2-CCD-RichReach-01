/// provider가 실패했을 때 자동으로 다시 시도하지 않는다.
///
/// Riverpod 3은 기본으로 최대 10번(약 40초) 다시 시도해서, 그동안 화면에 로딩만 보이고 실패 안내가 늦게 나온다.
/// 조회 화면은 모두 "다시 시도"·"다시 불러오기" 버튼이 있으므로 실패하면 바로 안내한다.
/// `ProviderScope(retry: noProviderRetry)`처럼 쓴다.
Duration? noProviderRetry(int retryCount, Object error) => null;
