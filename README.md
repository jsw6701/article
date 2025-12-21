# 📰 뉴스 이슈 카드 생성 시스템

RSS 피드에서 경제 뉴스를 자동 수집하고, 유사 기사를 클러스터링하여 AI가 요약 카드를 생성하는 백엔드 시스템입니다.

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       Pipeline (10분마다 자동 실행)                       │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────────────┐ │
│  │ 1. RSS 수집   │ →  │ 2. 이슈 클러스터링 │ →  │ 3. 카드 생성 (Gemini)  │ │
│  └──────────────┘    └──────────────────┘    └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                              REST API                                    │
│    GET /api/cards    GET /api/cards/{id}    GET /api/cards/today        │
└─────────────────────────────────────────────────────────────────────────┘
```

## 🔄 파이프라인 상세

### 1단계: RSS 수집
- 5개 경제 뉴스 RSS 피드에서 기사 수집
- 48시간 이내 기사만 필터링
- 중복 제거 후 DB 저장

**수집 대상:**
| 매체 | RSS URL |
|------|---------|
| 매일경제 | https://www.mk.co.kr/rss/30100041/ |
| 한국경제 | https://www.hankyung.com/feed/economy |
| 이데일리 | http://rss.edaily.co.kr/economy_news.xml |
| 머니투데이 | http://rss.mt.co.kr/mt_news.xml |
| 연합뉴스 | http://www.yonhapnews.co.kr/RSS/economy.xml |

### 2단계: 이슈 클러스터링
키워드 기반으로 기사를 분류하여 이슈로 그룹핑

| 카테고리 | 키워드 예시 |
|----------|-------------|
| RATE (금리) | 금리, 기준금리, 인하, 인상, 동결 |
| FX (환율) | 환율, 달러, 원화, 외환 |
| REALESTATE (부동산) | 부동산, 주택, 아파트, 전세 |
| COMMODITY (원자재) | 유가, 금값, 원자재 |
| TRADE (무역) | 수출, 수입, 무역, 관세 |

**이슈 생성 조건:**
- 기사 3개 이상
- 출처 2개 이상

### 3단계: 카드 생성
Gemini API를 호출하여 이슈별 요약 카드 생성

**카드 JSON 구조:**
```json
{
  "issue_title": "기준금리 동결과 향후 통화정책 방향",
  "conclusion": "한국은행이 기준금리를 동결했으며...",
  "why_it_matters": "대출 이자, 예금 금리에 직접 영향...",
  "evidence": [
    {"fact": "한은 금통위 기준금리 3.0% 유지", "source": "연합뉴스"},
    {"fact": "총재 '인하 여지 검토 중' 발언", "source": "매일경제"}
  ],
  "counter_scenario": "글로벌 금융 불안 시 동결 장기화 가능성",
  "impact": {"score": 4, "reason": "대출자 이자 부담 변화"},
  "action_guide": "변동금리 대출자는 금리 추이 주시 필요"
}
```

## 🛠️ 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Kotlin 2.x |
| Framework | Spring Boot 4.0 (WebFlux) |
| Database | MySQL + Exposed ORM 1.0 |
| LLM | Google Gemini API (gemini-2.5-flash) |
| RSS Parser | ROME 1.18 |
| Build | Gradle (Kotlin DSL) |

## 📁 프로젝트 구조

```
src/main/kotlin/com/yourapp/news/
├── ArticleApplication.kt       # 메인 애플리케이션
├── api/                        # REST API 컨트롤러
│   ├── CardController.kt
│   └── HealthController.kt
├── article/                    # 기사 도메인
│   ├── Article.kt
│   ├── Articles.kt (테이블)
│   └── ArticleStore.kt
├── card/                       # 카드 도메인
│   ├── Card.kt
│   ├── Cards.kt (테이블)
│   ├── CardStore.kt
│   ├── CardGenerationService.kt
│   ├── CardReadService.kt
│   └── PromptBuilder.kt
├── config/                     # 설정
│   ├── CorsConfig.kt
│   ├── ExposedConfig.kt
│   ├── OpenApiConfig.kt
│   └── WebClientConfig.kt
├── issue/                      # 이슈 도메인
│   ├── Issue.kt
│   ├── Issues.kt (테이블)
│   ├── IssueStore.kt
│   ├── IssueClusterService.kt
│   └── CategoryGroup.kt
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

## 🗄️ 데이터베이스 스키마

```sql
-- 수집된 기사
articles (
  link VARCHAR(500) PRIMARY KEY,
  title, summary, publisher, published_at, category, created_at
)

-- 클러스터링된 이슈
issues (
  id BIGINT PRIMARY KEY,
  fingerprint VARCHAR(255) UNIQUE,  -- 예: "RATE:금리,기준금리,인하"
  group_name, title, keywords,
  article_count, publisher_count,
  first_published_at, last_published_at
)

-- 이슈-기사 연결
issue_articles (
  issue_id BIGINT,
  article_link VARCHAR(500),
  PRIMARY KEY (issue_id, article_link)
)

-- 생성된 카드
cards (
  id BIGINT PRIMARY KEY,
  issue_id BIGINT UNIQUE,
  issue_fingerprint, model, content_json,
  status ENUM('ACTIVE', 'FAILED'),
  created_at, updated_at
)
```

## ⚙️ 설정

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
      # ...
  pipeline:
    enabled: true
    cron: "0 */10 * * * *"  # 10분마다

gemini:
  api-key: ${GEMINI_API_KEY}
  model: gemini-2.5-flash
  timeout-ms: 15000
  max-retries: 2
```

## 🚀 실행 방법

### 1. 데이터베이스 생성
```sql
CREATE DATABASE article CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 환경 변수 설정 (선택)
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

## 📡 API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/cards | 오늘 생성된 카드 목록 |
| GET | /api/cards/{id} | 카드 상세 조회 |
| GET | /api/cards/today | 오늘자 카드 목록 |
| GET | /api/health | 서버 상태 |
| GET | /api/health/pipeline | 마지막 파이프라인 실행 정보 |

## 🔧 Gemini API 호출 최적화

- **ACTIVE 카드 스킵**: 이미 성공한 카드는 재생성하지 않음
- **FAILED 카드 재시도**: 실패한 카드는 다음 파이프라인에서 재시도
- **Rate Limit 대응**: 지수 백오프 재시도 (최대 2회)

## 📊 로깅

```
[RSS] Collection complete: totalFeeds=5, success=5, failed=0, totalSaved=122
[Cluster] Created 2, Updated 1, Skipped 96
[Card] Generated 2, Skipped 2, Failed 0
Pipeline finished: Status=SUCCESS, Duration=2899ms
```

## 📝 라이선스

MIT License
