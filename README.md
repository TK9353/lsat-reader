# LSAT Reader

LSAT 대비용 영문 독해 앱 (Android, Kotlin + Jetpack Compose). 개인 학습용.

- 피드: 릴스처럼 세로로 넘기는 글. 체류시간과 ♥ 기록으로 주제·공급원 가중치를 학습함(ε-greedy 15%)
- 검색: 키워드, 한국어 문장(AI가 영어 검색어로 변환), URL 직접 열기
- 공급원: Wikipedia, The Guardian API, RSS(싱크탱크·매체·칼럼, 기기에서 본문 추출), AI 재구성 지문(문장별 인용 대조 + 별도 호출로 사실 검증)
- 학습: 단어 탭 영영사전과 문맥 속 뜻(AI), LSAT RC 유형 문제, 논지 구조 하이라이트
- 빌드: main에 push하면 GitHub Actions가 APK를 빌드해 Releases에 올림
