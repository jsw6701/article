# 뉴스 이슈 카드 생성 시스템

RSS 피드에서 경제 뉴스를 자동 수집하고, 유사 기사를 클러스터링하여 AI가 요약 카드를 생성하는 백엔드 시스템입니다.

## 핵심 개념

```
기사(Article)  →  이슈(Issue)  →  카드(Card)
   원본 데이터       사건 묶음        AI 요약
```

- **기사**: RSS에서 수집된 개별 뉴스 (제목, 요약, 링크, 언론사, 발행일)
- **이슈**: 같은 사건을 다룬 기사들의 묶음 (키워드 기반 클러스터링)
- **카드**: 이슈에 대한 AI 생성 요약/결론

## 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       Pipeline (20분마다 자동 실행)                       │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────────┐ │
│  │ 1. RSS 수집   │ →  │ 2. 이슈 클러스터링 │ →  │ 3. 카드 생성 (Gemini)  │ │
│  └──────────────┘    └──────────────────┘    └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                              REST API                                    │
│  GET /api/cards          카드 리스트 (articleCount, publisherCount 포함) │
│  GET /api/cards/{id}     카드 상세 + 관련 기사 목록                       │
│  GET /api/cards/today    최근 24시간 카드                                 │
│  GET /api/trending       급상승 이슈                                      │
└─────────────────────────────────────────────────────────────────────────┘
```

## 파이프라인 상세

### 1단계: RSS 수집 (RssCollectorService)

경제 뉴스 RSS 피드에서 기사를 수집합니다.

- 48시간 이내 기사만 필터링
- HTML 엔티티 자동 디코딩 (`&#039;` → `'`)
- 중복 제거 후 DB 저장 (link가 unique key)

**수집 대상:**
| 매체 | RSS URL |
|------|---------|
| 매일경제 | https://www.mk.co.kr/rss/30100041/ |
| 한국경제 | https://www.hankyung.com/feed/economy |
| 이데일리 | http://rss.edaily.co.kr/economy_news.xml |
| 머니투데이 | http://rss.mt.co.kr/mt_news.xml |
| 연합뉴스 | http://www.yonhapnews.co.kr/RSS/economy.xml |

### 2단계: 이슈 클러스터링 (IssueClusterService)

키워드 기반으로 기사를 분류하여 이슈로 그룹핑합니다.

**분류 그룹:**
| 카테고리 | 키워드 예시 |
|----------|-------------|
| RATE (금리/통화정책) | 금리, 기준금리, 연준, fed, 파월, 한은, 인상, 인하 |
| FX (환율/외환) | 환율, 달러, 원화, 엔화, 외환, 강세, 약세 |
| STOCK (증시/주식) | 코스피, 코스닥, 주가, 증시, 상승, 하락, 급등 |
| REALESTATE (부동산) | 부동산, 아파트, 전세, 분양, 청약, 재건축 |
| MACRO (거시지표) | 물가, cpi, 고용, 실업, gdp, 수출, 무역수지 |
| POLICY (정책/제도) | 세금, 관세, 규제, 정책, 대책, 금융위, 기재부 |

**클러스터링 조건:**
- 같은 그룹 내 기사
- 공통 키워드 2개 이상
- 발행 시각 48시간 이내

**이슈 생성 조건:**
- 기사 2개 이상
- 출처 2개 이상 (언론사 다양성)

**fingerprint 생성:**
```
그룹:상위키워드3개정렬
예: "FX:달러,외환,환율"
```
→ 같은 fingerprint면 기존 이슈에 기사 추가 (UPDATE)

### 3단계: 카드 생성 (CardGenerationService)

Gemini API를 호출하여 이슈별 요약 카드를 생성합니다.

**카드 JSON 구조:**
```json
{
  "issue_title": "해외투자 급증과 고환율 장기화에 따른 내수 물가 압박 심화",
  "conclusion": "해외 주식 투자 열풍으로 인한 상시적 달러 수요와...",
  "why_it_matters": "환율 상승은 수입 원재료와 생필품 가격을 높여...",
  "evidence": [
    {"fact": "올해 국내 투자자 해외주식 순매수 약 315억 달러", "source": "머니투데이"},
    {"fact": "원/달러 환율 1400원대 초반 유지", "source": "연합뉴스"}
  ],
  "counter_scenario": "미 연준 금리 인하 시 달러 약세 전환 가능성",
  "impact": {"score": 4, "reason": "수입물가 상승으로 서민 경제 부담"},
  "action_guide": "외화 필요 시 환율 변동성 모니터링하며 분할 환전 고려"
}
```

## 급상승 이슈 (Trending)

최근 기사 수, 언론사 다양성, 최신성을 기반으로 "지금 터지고 있는" 이슈를 계산합니다.

**점수 계산 공식:**
```
score = velocity × 5.0 + publisherBonus × 2.0 + recencyBoost × 3.0

- velocity: 시간당 기사 수 (기사수 / 조회시간)
- publisherBonus: 언론사 다양성 (언론사수 - 1)
- recencyBoost: 최신성 (1 - 마지막기사분/60), 0~1
```

**예시:**
| 이슈 | 기사 수 | 시간 | 언론사 | 최신성 | 점수 |
|------|--------|------|--------|--------|------|
| KT 해킹 | 6건 | 3시간 | 4개 | 0.83 | **18.5** |
| 환율 이슈 | 20건 | 24시간 | 8개 | 0 | 18.15 |

→ 짧은 시간에 기사가 몰린 이슈가 상위로!

## API 엔드포인트

### 카드 API

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/cards | 카드 리스트 (articleCount, publisherCount 포함) |
| GET | /api/cards/{issueId} | 카드 상세 + 관련 기사 목록 (최대 15개) |
| GET | /api/cards/today | 최근 24시간 카드 |

**카드 상세 응답 예시:**
```json
{
  "issueId": 3,
  "issueTitle": "환율 관련 이슈: 환율/달러",
  "issueGroup": "FX",
  "issueArticleCount": 10,
  "issuePublisherCount": 5,
  "cardJson": "{ ... }",
  "articles": [
    {
      "title": "원/달러 환율 1400원 근접",
      "link": "https://...",
      "publisher": "연합뉴스",
      "publishedAt": "2025-12-23T11:52:00"
    }
  ]
}
```

### 급상승 API

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/trending | 급상승 이슈 조회 |

**파라미터:**
- `hours`: 최근 N시간 기준 (기본값: 3, 최대: 24)
- `limit`: 반환할 이슈 개수 (기본값: 10, 최대: 20)

**응답 예시:**
```json
{
  "items": [
    {
      "issueId": 101,
      "issueTitle": "정책 이슈: KT/소액결제",
      "issueGroup": "POLICY",
      "articleCount": 8,
      "publisherCount": 6,
      "lastPublishedAt": "2025-12-23T11:52:00",
      "score": 18.5,
      "conclusion": "KT 결제 시스템 해킹 의혹이 확산되며..."
    }
  ],
  "count": 1,
  "hours": 3
}
```

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin 2.x |
| Framework | Spring Boot 4.0 (WebFlux) |
| Database | MySQL + Exposed ORM 1.0 |
| LLM | Google Gemini API |
| RSS Parser | ROME 1.18 |
| Build | Gradle (Kotlin DSL) |

## 프로젝트 구조

```
src/main/kotlin/com/yourapp/news/
├── ArticleApplication.kt       # 메인 애플리케이션
├── api/                        # REST API 컨트롤러
│   ├── CardController.kt       # 카드 API
│   ├── TrendingController.kt   # 급상승 API
│   └── HealthController.kt
├── article/                    # 기사 도메인
│   ├── Article.kt
│   ├── Articles.kt (테이블)
│   └── ArticleStore.kt
├── card/                       # 카드 도메인
│   ├── Card.kt
│   ├── Cards.kt (테이블)
│   ├── CardStore.kt
│   ├── CardQuery.kt
│   ├── CardGenerationService.kt
│   ├── CardReadService.kt
│   └── PromptBuilder.kt
├── issue/                      # 이슈 도메인
│   ├── Issue.kt
│   ├── Issues.kt (테이블)
│   ├── IssueArticles.kt (테이블)
│   ├── IssueStore.kt
│   ├── IssueClusterService.kt
│   └── CategoryGroup.kt
├── trending/                   # 급상승 도메인
│   ├── TrendingService.kt
│   └── TrendingQuery.kt
├── llm/gemini/                 # Gemini API 클라이언트
│   ├── GeminiClient.kt
│   └── GeminiProperties.kt
├── pipeline/                   # 파이프라인 오케스트레이션
│   ├── PipelineOrchestrator.kt
│   ├── PipelineScheduler.kt
│   └── PipelineRun.kt
└── rss/                        # RSS 수집
    ├── RssCollectorService.kt
    └── RssProperties.kt
```

## 데이터베이스 스키마

```sql
-- 수집된 기사
articles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  link VARCHAR(500) UNIQUE,
  title VARCHAR(512),
  summary TEXT,
  publisher VARCHAR(255),
  published_at DATETIME,
  category VARCHAR(64)
)

-- 클러스터링된 이슈
issues (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fingerprint VARCHAR(128) UNIQUE,  -- 예: "FX:달러,외환,환율"
  group_name VARCHAR(32),
  title VARCHAR(512),
  keywords TEXT,
  article_count INT,
  publisher_count INT,
  first_published_at DATETIME,
  last_published_at DATETIME,
  status VARCHAR(32)
)

-- 이슈-기사 연결
issue_articles (
  issue_id BIGINT,
  article_link VARCHAR(500),
  published_at DATETIME,
  PRIMARY KEY (issue_id, article_link)
)

-- 생성된 카드
cards (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  issue_id BIGINT UNIQUE,
  issue_fingerprint VARCHAR(128),
  model VARCHAR(64),
  content_json TEXT,
  status VARCHAR(32),  -- ACTIVE, FAILED
  created_at DATETIME,
  updated_at DATETIME
)
```

## 설정

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/article
    username: root
    password: yourpassword

news:
  rss:
    feeds:
      - https://www.mk.co.kr/rss/30100041/
      - https://www.hankyung.com/feed/economy
      - http://rss.edaily.co.kr/economy_news.xml
      - http://rss.mt.co.kr/mt_news.xml
      - http://www.yonhapnews.co.kr/RSS/economy.xml
  pipeline:
    enabled: true
    cron: "0 */10 * * * *"  # 20분마다

gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-2.5-flash
  timeout-ms: 15000
  max-retries: 2
```

## 실행 방법

### 1. 데이터베이스 생성
```sql
CREATE DATABASE article CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 환경 변수 설정
```bash
set GEMINI_API_KEY=your-api-key
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 4. API 확인
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/api/health
- 카드 목록: http://localhost:8080/api/cards
- 급상승 이슈: http://localhost:8080/api/trending

## 데이터 흐름 요약

```
RSS 피드
    │
    ▼
┌─────────┐     ┌─────────────┐     ┌───────┐
│Articles │ ──▶ │IssueArticles│ ◀── │Issues │
│ (기사)  │     │  (매핑)     │     │(이슈) │
└─────────┘     └─────────────┘     └───┬───┘
                                       │
                                       ▼
                                   ┌───────┐
                                   │ Cards │
                                   │(카드) │
                                   └───────┘
                                       │
                                       ▼
                                   ┌──────────┐
                                   │ Trending │
                                   │(급상승)  │
                                   └──────────┘
```

## 핵심 특징

1. **기사 제목이 달라도 같은 이슈로 묶임** - 키워드 기반 클러스터링
2. **언론사 다양성 보장** - 출처 2개 이상이어야 이슈로 인정
3. **AI 결론 카드** - 이슈별 요약/결론/행동 가이드 제공
4. **급상승 계산** - 시간당 기사 수 정규화로 "지금 터지는" 이슈 감지
5. **카운트 자동 동기화** - 기사 추가 시 articleCount, publisherCount 갱신

## 라이선스

MIT License
