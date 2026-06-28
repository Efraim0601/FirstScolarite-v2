# FirstPay Studio — Prompt Cursor : Implémentation Full Stack 1M+ tx/min

## CONTEXTE DU PROJET

Tu es un développeur Full Stack expert Angular 17 + Spring Boot 3.x.  
Implémente **FirstPay Studio** : plateforme de traitement de paiements multi-partenaires  
capables de **1 000 000 transactions par minute** (~16 667 TPS).

Contraintes absolues :
- **Angular 21+** (standalone components, Signals, NgRx Signal Store)
- **Spring Boot 3.x** (virtual threads, reactive WebFlux pour les endpoints critiques)
- **PostgreSQL 16** (partitionnement natif, PgBouncer, read replicas)
- **Apache Kafka** (event-driven, exactly-once semantics)
- **Redis Cluster** (cache L2, idempotency keys, rate limiting)
- **API REST + WebSocket** exposées aux partenaires
- **Multi-tenant** : isolation par `tenant_id` dans chaque requête/table
- **Docker + Kubernetes** (Helm charts inclus)

---

## STRUCTURE DU MONOREPO

```
firstpay-studio/
├── apps/
│   ├── frontend/              # Angular 17 (standalone)
│   └── api-gateway/           # Spring Cloud Gateway
├── services/
│   ├── transaction-service/   # CQRS + Event Sourcing (le cœur)
│   ├── partner-service/       # Multi-tenant onboarding
│   ├── payment-service/       # Orchestration paiements
│   └── reporting-service/     # Read models / projections
├── libs/
│   ├── shared-domain/         # DTOs, enums, value objects partagés
│   └── shared-security/       # JWT utils, tenant context
├── infrastructure/
│   ├── kafka/                 # Topics, partitions config
│   ├── postgresql/            # Migrations Flyway, partitions DDL
│   ├── redis/                 # Configuration cluster
│   └── k8s/                   # Helm charts, HPA, Istio
└── docker-compose.yml         # Dev local complet
```

---

## 1. BASE DE DONNÉES POSTGRESQL — SCHÉMA HAUTE PERFORMANCE

### 1.1 Tables principales (à créer avec Flyway)

```sql
-- V1__init_schema.sql

-- Extension UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_partman";

-- Tenants (partenaires)
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code            VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    config          JSONB NOT NULL DEFAULT '{}',
    api_key_hash    VARCHAR(255),
    rate_limit_tpm  INTEGER DEFAULT 10000,  -- transactions/minute
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transactions (table partitionnée par mois + tenant)
CREATE TABLE transactions (
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    external_ref    VARCHAR(100) NOT NULL,
    amount          NUMERIC(19,4) NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    type            VARCHAR(30) NOT NULL,
    metadata        JSONB,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Partitions mensuelles automatiques (pg_partman)
SELECT partman.create_parent(
    p_parent_table => 'public.transactions',
    p_control      => 'created_at',
    p_type         => 'range',
    p_interval     => 'monthly',
    p_premake      => 3
);

-- Index critiques
CREATE INDEX CONCURRENTLY idx_tx_tenant_status
    ON transactions (tenant_id, status, created_at DESC);
CREATE UNIQUE INDEX idx_tx_idempotency
    ON transactions (tenant_id, idempotency_key, created_at);

-- Events (Event Store pour CQRS)
CREATE TABLE domain_events (
    id              BIGSERIAL,
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   INTEGER NOT NULL DEFAULT 1,
    tenant_id       UUID NOT NULL,
    payload         JSONB NOT NULL,
    metadata        JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Outbox pattern pour Kafka (fiabilité)
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    tenant_id       UUID NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (status, created_at) WHERE status = 'PENDING';
```

### 1.2 Configuration PgBouncer (pool de connexions)

```ini
# pgbouncer.ini
[databases]
firstpay = host=postgres port=5432 dbname=firstpay pool_size=100

[pgbouncer]
pool_mode = transaction       # CRITIQUE: mode transaction pour haute perf
max_client_conn = 5000
default_pool_size = 100
reserve_pool_size = 20
server_idle_timeout = 600
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
```

---

## 2. SPRING BOOT — SERVICES BACKEND

### 2.1 transaction-service : le cœur du système

**`pom.xml` (dépendances critiques)**

```xml
<dependencies>
    <!-- Spring Boot WebFlux (réactif pour les endpoints API) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <!-- R2DBC pour accès réactif PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>
    <dependency>
        <groupId>io.r2dbc</groupId>
        <artifactId>r2dbc-postgresql</artifactId>
    </dependency>
    <!-- Kafka réactif -->
    <dependency>
        <groupId>io.projectreactor.kafka</groupId>
        <artifactId>reactor-kafka</artifactId>
    </dependency>
    <!-- Redis réactif -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
    <!-- Resilience4j circuit breaker -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>
    <!-- Micrometer pour métriques -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
</dependencies>
```

**`application.yml`**

```yaml
spring:
  application:
    name: transaction-service

  r2dbc:
    url: r2dbc:pool:postgresql://pgbouncer:6432/firstpay
    username: ${DB_USER}
    password: ${DB_PASS}
    pool:
      initial-size: 20
      max-size: 100
      max-idle-time: 30m
      validation-query: SELECT 1

  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    producer:
      acks: all                          # durabilité maximale
      batch-size: 65536                  # 64 KB
      linger-ms: 5                       # grouper les messages
      compression-type: lz4
      enable-idempotence: true
      max-in-flight-requests-per-connection: 5
    consumer:
      group-id: transaction-processors
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500

  data:
    redis:
      cluster:
        nodes: ${REDIS_NODES}
      timeout: 1000ms

  threads:
    virtual:
      enabled: true                      # Virtual threads (Java 21)

resilience4j:
  circuitbreaker:
    instances:
      payment-gateway:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 100
  ratelimiter:
    instances:
      transaction-api:
        limit-for-period: 1000           # 1000 req/s par instance
        limit-refresh-period: 1s
        timeout-duration: 0

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**`TransactionCommand.java`** — Commande CQRS

```java
package com.firstpay.transaction.command;

import java.math.BigDecimal;
import java.util.UUID;

public sealed interface TransactionCommand {

    record CreateTransaction(
        UUID tenantId,
        String externalRef,
        BigDecimal amount,
        String currency,
        String type,
        String idempotencyKey,
        Map<String, Object> metadata
    ) implements TransactionCommand {}

    record ProcessTransaction(UUID transactionId, UUID tenantId) implements TransactionCommand {}
    record RefundTransaction(UUID transactionId, UUID tenantId, BigDecimal amount) implements TransactionCommand {}
}
```

**`TransactionCommandHandler.java`** — Handler réactif avec idempotency

```java
@Service
@RequiredArgsConstructor
public class TransactionCommandHandler {

    private final TransactionRepository repository;
    private final ReactiveRedisTemplate<String, String> redis;
    private final OutboxEventPublisher outbox;

    @Transactional
    public Mono<TransactionId> handle(TransactionCommand.CreateTransaction cmd) {
        String idempotencyKey = "idempotency:%s:%s".formatted(
            cmd.tenantId(), cmd.idempotencyKey()
        );

        return redis.opsForValue()
            .setIfAbsent(idempotencyKey, "processing", Duration.ofMinutes(10))
            .flatMap(acquired -> {
                if (!acquired) {
                    // Requête dupliquée — retourner la transaction existante
                    return repository.findByIdempotencyKey(cmd.tenantId(), cmd.idempotencyKey())
                        .map(tx -> new TransactionId(tx.getId()));
                }
                return createNewTransaction(cmd);
            });
    }

    private Mono<TransactionId> createNewTransaction(TransactionCommand.CreateTransaction cmd) {
        var transaction = Transaction.create(
            cmd.tenantId(), cmd.externalRef(),
            cmd.amount(), cmd.currency(),
            cmd.type(), cmd.idempotencyKey()
        );

        return repository.save(transaction)
            .flatMap(saved -> outbox.publish(new TransactionCreatedEvent(saved.getId(), cmd.tenantId())))
            .map(saved -> new TransactionId(saved.getId()))
            .doOnError(e -> log.error("Erreur création transaction: {}", e.getMessage()));
    }
}
```

**`TransactionController.java`** — API REST réactive

```java
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionCommandHandler commandHandler;
    private final TransactionQueryHandler queryHandler;

    @PostMapping
    @RateLimiter(name = "transaction-api")
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "createFallback")
    public Mono<ResponseEntity<TransactionResponse>> create(
        @RequestHeader("X-Tenant-Id") UUID tenantId,
        @RequestHeader("X-Idempotency-Key") String idempotencyKey,
        @RequestBody @Valid CreateTransactionRequest req
    ) {
        return commandHandler.handle(new TransactionCommand.CreateTransaction(
            tenantId, req.externalRef(), req.amount(),
            req.currency(), req.type(), idempotencyKey, req.metadata()
        ))
        .map(id -> ResponseEntity.accepted()
            .header("Location", "/api/v1/transactions/" + id.value())
            .body(new TransactionResponse(id.value(), "PENDING")));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TransactionEvent>> streamTransactions(
        @RequestHeader("X-Tenant-Id") UUID tenantId
    ) {
        return queryHandler.streamForTenant(tenantId)
            .map(event -> ServerSentEvent.<TransactionEvent>builder()
                .id(event.id().toString())
                .event(event.type())
                .data(event)
                .build());
    }

    // SSE WebSocket pour le dashboard Angular en temps réel
    @GetMapping(value = "/live-stats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TenantStats>> liveStats(
        @RequestHeader("X-Tenant-Id") UUID tenantId
    ) {
        return Flux.interval(Duration.ofSeconds(1))
            .flatMap(_ -> queryHandler.getRealtimeStats(tenantId))
            .map(stats -> ServerSentEvent.builder(stats).event("stats").build());
    }
}
```

**`KafkaTransactionConsumer.java`** — Consumer haute perf

```java
@Service
@RequiredArgsConstructor
public class KafkaTransactionConsumer {

    private final TransactionProcessor processor;
    private final ReceiverOptions<String, TransactionEvent> receiverOptions;

    @PostConstruct
    public void startConsumer() {
        KafkaReceiver.create(receiverOptions.assignment(List.of(
            new TopicPartition("transactions.created", 0),
            new TopicPartition("transactions.created", 1)
        )))
        .receive()
        .groupBy(ConsumerRecord::partition)           // traitement parallèle par partition
        .flatMap(partitionFlux ->
            partitionFlux
                .concatMap(record ->
                    processor.process(record.value())
                        .doOnSuccess(_ -> record.receiverOffset().acknowledge())
                        .onErrorResume(e -> handleError(e, record))
                )
        )
        .subscribe();
    }
}
```

---

## 3. API GATEWAY (Spring Cloud Gateway)

**`GatewayConfig.java`**

```java
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("transaction-service", r -> r
                .path("/api/v1/transactions/**")
                .filters(f -> f
                    .filter(new TenantExtractorFilter())
                    .requestRateLimiter(c -> c
                        .setRateLimiter(redisRateLimiter())
                        .setKeyResolver(tenantKeyResolver())
                    )
                    .retry(config -> config.setRetries(3).setStatuses(HttpStatus.SERVICE_UNAVAILABLE))
                    .circuitBreaker(c -> c.setName("transaction-cb").setFallbackUri("forward:/fallback"))
                )
                .uri("lb://transaction-service")
            )
            .route("reporting-service", r -> r
                .path("/api/v1/reports/**")
                .filters(f -> f.filter(new TenantExtractorFilter()))
                .uri("lb://reporting-service")
            )
            .build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // 10 000 req/s par tenant par défaut (configurable par tenant)
        return new RedisRateLimiter(10000, 10000, 1);
    }
}
```

**`TenantExtractorFilter.java`** — Middleware multi-tenant

```java
@Component
public class TenantExtractorFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");

        return validateAndEnrichRequest(apiKey, tenantId)
            .flatMap(tenant -> {
                ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", tenant.getId().toString())
                    .header("X-Tenant-Rate-Limit", tenant.getRateLimitTpm().toString())
                    .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            })
            .onErrorMap(UnauthorizedException.class,
                e -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }
}
```

---

## 4. KAFKA — TOPICS ET CONFIGURATION

**`kafka-topics.yaml`** (à exécuter au démarrage)

```yaml
# Topics avec 12 partitions chacun (pour 1M tx/min)
# Règle: 1 partition ≈ 100k messages/min
topics:
  - name: transactions.created
    partitions: 12
    replication-factor: 3
    config:
      retention.ms: 604800000     # 7 jours
      compression.type: lz4
      min.insync.replicas: 2

  - name: transactions.processed
    partitions: 12
    replication-factor: 3

  - name: transactions.failed
    partitions: 6
    replication-factor: 3

  - name: payments.outbox
    partitions: 12
    replication-factor: 3
    config:
      cleanup.policy: compact      # Outbox pattern
```

---

## 5. FRONTEND ANGULAR 17+

### 5.1 Structure Angular

```
apps/frontend/src/
├── app/
│   ├── core/
│   │   ├── auth/              # JWT interceptor, guards
│   │   ├── tenants/           # Context tenant (Signal)
│   │   └── api/               # Services HTTP
│   ├── features/
│   │   ├── dashboard/         # Dashboard en temps réel
│   │   ├── transactions/      # Liste, détail, création
│   │   ├── partners/          # Gestion partenaires
│   │   └── reports/           # Analytics & exports
│   └── shared/
│       ├── components/        # Composants réutilisables
│       └── directives/
└── environments/
```

### 5.2 Composants clés

**`transaction-store.ts`** — NgRx Signal Store

```typescript
// transaction.store.ts
import { signalStore, withState, withMethods, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';

interface TransactionState {
  transactions: Transaction[];
  stats: TenantStats | null;
  loading: boolean;
  error: string | null;
}

export const TransactionStore = signalStore(
  withState<TransactionState>({
    transactions: [],
    stats: null,
    loading: false,
    error: null,
  }),
  withMethods((store, transactionService = inject(TransactionService)) => ({
    // Chargement des transactions
    loadTransactions: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() => transactionService.getAll()),
        tapResponse({
          next: (transactions) => patchState(store, { transactions, loading: false }),
          error: (error: string) => patchState(store, { error, loading: false }),
        })
      )
    ),

    // Streaming SSE temps réel
    connectLiveStats: rxMethod<void>(
      pipe(
        switchMap(() => transactionService.liveStats()),
        tapResponse({
          next: (stats) => patchState(store, { stats }),
          error: console.error,
        })
      )
    ),
  }))
);
```

**`dashboard.component.ts`** — Dashboard temps réel

```typescript
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, AsyncPipe, ChartComponent, MetricCardComponent],
  template: `
    <div class="dashboard-grid">
      <app-metric-card
        title="Transactions / min"
        [value]="store.stats()?.tpm ?? 0"
        [trend]="store.stats()?.tpmTrend ?? 0"
        color="blue"
      />
      <app-metric-card
        title="Taux de succès"
        [value]="store.stats()?.successRate ?? 0"
        unit="%"
        color="green"
      />
      <app-metric-card
        title="Latence P99"
        [value]="store.stats()?.p99LatencyMs ?? 0"
        unit="ms"
        color="amber"
      />
      <app-transaction-chart [data]="store.transactions()" />
      <app-live-feed [transactions]="store.transactions().slice(0, 20)" />
    </div>
  `,
})
export class DashboardComponent implements OnInit {
  store = inject(TransactionStore);

  ngOnInit() {
    this.store.loadTransactions();
    this.store.connectLiveStats();  // SSE stream
  }
}
```

**`transaction.service.ts`** — Service avec SSE

```typescript
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private http = inject(HttpClient);
  private tenantId = inject(TenantContextService).tenantId;

  getAll(): Observable<Transaction[]> {
    return this.http.get<Transaction[]>('/api/v1/transactions', {
      headers: { 'X-Tenant-Id': this.tenantId() }
    });
  }

  create(req: CreateTransactionRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/v1/transactions', req, {
      headers: {
        'X-Tenant-Id': this.tenantId(),
        'X-Idempotency-Key': crypto.randomUUID()
      }
    });
  }

  liveStats(): Observable<TenantStats> {
    return new Observable(observer => {
      const eventSource = new EventSource(
        `/api/v1/transactions/live-stats`,
        { withCredentials: true }
      );
      eventSource.addEventListener('stats', (event) => {
        observer.next(JSON.parse(event.data));
      });
      eventSource.onerror = (err) => observer.error(err);
      return () => eventSource.close();
    }).pipe(
      retry({ delay: 3000 })  // reconnexion auto
    );
  }
}
```

---

## 6. INFRASTRUCTURE KUBERNETES

**`transaction-service-deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transaction-service
spec:
  replicas: 5                    # Minimum 5 pods
  selector:
    matchLabels:
      app: transaction-service
  template:
    spec:
      containers:
        - name: transaction-service
          image: firstpay/transaction-service:latest
          resources:
            requests:
              cpu: "1"
              memory: "1Gi"
            limits:
              cpu: "4"
              memory: "4Gi"
          env:
            - name: JAVA_OPTS
              value: "-XX:+UseZGC -XX:+ZGenerational -Xmx3g -Xms1g"
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transaction-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: transaction-service
  minReplicas: 5
  maxReplicas: 50                # Scale jusqu'à 50 pods
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
    - type: Pods
      pods:
        metric:
          name: transactions_per_second
        target:
          type: AverageValue
          averageValue: "2000"   # Scale si >2000 TPS/pod
```

---

## 7. DOCKER COMPOSE (DÉVELOPPEMENT LOCAL)

```yaml
version: '3.9'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: firstpay
      POSTGRES_USER: firstpay
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  pgbouncer:
    image: pgbouncer/pgbouncer:latest
    ports:
      - "6432:6432"
    volumes:
      - ./infrastructure/postgresql/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_DEFAULT_REPLICATION_FACTOR: 1
      KAFKA_NUM_PARTITIONS: 12
    ports:
      - "9092:9092"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 2gb --maxmemory-policy allkeys-lru

  api-gateway:
    build: ./apps/api-gateway
    ports:
      - "8080:8080"
    depends_on:
      - transaction-service

  transaction-service:
    build: ./services/transaction-service
    environment:
      DB_URL: r2dbc:postgresql://pgbouncer:6432/firstpay
      KAFKA_BROKERS: kafka:9092
      REDIS_HOST: redis
    depends_on:
      - postgres
      - kafka
      - redis

  frontend:
    build: ./apps/frontend
    ports:
      - "4200:80"
    environment:
      API_URL: http://api-gateway:8080

volumes:
  postgres_data:
```

---

## ÉTAPES À SUIVRE DANS CURSOR

### Étape 1 — Initialisation du monorepo
```
Cursor prompt:
"Crée la structure de dossiers du monorepo firstpay-studio avec les 
sous-projets: apps/frontend (Angular 17), apps/api-gateway (Spring Boot), 
services/transaction-service, services/partner-service, services/payment-service, 
services/reporting-service. Initialise le pom.xml parent Maven avec les 
modules et les dépendances partagées."
```

### Étape 2 — Base de données
```
Cursor prompt:
"Dans services/transaction-service/src/main/resources/db/migration/, 
crée les fichiers Flyway V1 à V5 selon le schéma PostgreSQL fourni. 
Inclus le partitionnement de la table transactions par mois et tenant_id, 
les index composites, la table domain_events et outbox_events."
```

### Étape 3 — Domain model et CQRS
```
Cursor prompt:
"Implémente le domain model Transaction avec CQRS dans le package 
com.firstpay.transaction. Crée: Transaction.java (aggregate root), 
TransactionCommand.java (sealed interface), TransactionCommandHandler.java 
(réactif avec Reactor), TransactionQueryHandler.java, et les events de domaine.
Utilise R2DBC pour la persistance réactive."
```

### Étape 4 — API REST + SSE
```
Cursor prompt:
"Crée TransactionController.java avec: POST /api/v1/transactions (avec 
idempotency key Redis), GET /api/v1/transactions/{id}, GET /api/v1/transactions/stream 
(SSE), GET /api/v1/transactions/live-stats (SSE 1s). Ajoute @RateLimiter 
et @CircuitBreaker Resilience4j. Inclus les tests WebTestClient."
```

### Étape 5 — Kafka consumers
```
Cursor prompt:
"Implémente le consumer Kafka réactif KafkaTransactionConsumer.java 
qui traite les topics transactions.created et payments.outbox. 
Utilise reactor-kafka, exactly-once semantics, dead letter queue 
pour les messages en erreur, et acknowledgement manuel."
```

### Étape 6 — API Gateway
```
Cursor prompt:
"Dans apps/api-gateway, configure Spring Cloud Gateway avec: routing 
vers les microservices, RedisRateLimiter par tenant (configurable), 
TenantExtractorFilter pour l'authentification API key, circuit breaker 
par route, et retry avec backoff exponentiel."
```

### Étape 7 — Frontend Angular
```
Cursor prompt:
"Dans apps/frontend, crée l'application Angular 17 standalone avec: 
NgRx Signal Store pour l'état, TransactionStore avec méthodes rxMethod, 
DashboardComponent avec métriques temps réel via SSE, 
TransactionListComponent avec pagination virtuelle (CDK Virtual Scroll), 
et TransactionFormComponent avec validation réactive."
```

### Étape 8 — Infrastructure Docker + K8s
```
Cursor prompt:
"Génère le docker-compose.yml complet pour le développement local 
(PostgreSQL + PgBouncer + Kafka + Redis + tous les services). 
Puis crée les Helm charts Kubernetes dans infrastructure/k8s/ avec 
HPA pour chaque service, Istio VirtualService pour le traffic shaping, 
et PodDisruptionBudget pour zero-downtime deployments."
```

### Étape 9 — Observabilité
```
Cursor prompt:
"Configure l'observabilité complète: Micrometer + Prometheus dans 
chaque service, Grafana dashboard JSON pour les métriques FirstPay 
(TPS, latence P50/P99, taux d'erreur par tenant), OpenTelemetry 
pour le tracing distribué avec Jaeger, et alertes Prometheus 
pour latence >100ms et taux d'erreur >1%."
```

### Étape 10 — Tests de charge
```
Cursor prompt:
"Crée un script de test de charge Gatling en Scala dans 
tests/load-tests/ qui simule 1 000 000 transactions/minute en 
distribuant sur 10 partenaires. Inclus des scénarios: burst de 5x 
le volume normal, panne d'un service, et recovery test. 
Configure les seuils d'acceptation (P99 < 500ms, erreurs < 0.1%)."
```

---

## MÉTRIQUES CIBLES POUR VALIDATION

| Métrique | Cible |
|----------|-------|
| Débit maximum | ≥ 1 000 000 tx/min |
| Latence P50 | < 20 ms |
| Latence P99 | < 100 ms |
| Taux d'erreur | < 0.01% |
| Disponibilité | 99.99% |
| RTO (recovery) | < 30 secondes |
| Duplication transactions | 0% (idempotency) |

