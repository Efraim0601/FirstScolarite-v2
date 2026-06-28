# Tests de charge Gatling — FirstPay Studio

Valide la cible **1M tx/min (~16 667 TPS)** et les SLO (`docs/ARCHITECTURE.md §1`).

## Prérequis

- Stack locale : `docker compose up -d` (gateway exposée sur `:8080`)
- JDK 21+
- Pour la cible pleine échelle : plusieurs injecteurs (machines/pods Gatling)

## Simulations

| Simulation | But |
|------------|-----|
| `TransactionLoadSimulation` | charge soutenue paramétrable (débit cible, ramp, hold) |
| `BurstAndRecoverySimulation` | nominal → pic ×5 → retour nominal (backpressure + recovery) |

## Exécution

```bash
cd tests/load-tests

# Charge soutenue (dev) : ~200 TPS
./gradlew gatlingRun-com.firstpay.load.TransactionLoadSimulation \
  -DbaseUrl=http://localhost:8080 -DapiKey=demo-soft-key \
  -DtargetTps=200 -DrampSeconds=60 -DholdSeconds=120

# Burst & recovery
./gradlew gatlingRun-com.firstpay.load.BurstAndRecoverySimulation \
  -DnominalTps=200 -DburstFactor=5
```

### Paramètres (`-D...`)

| Propriété | Défaut | Description |
|-----------|--------|-------------|
| `baseUrl` | `http://localhost:8080` | URL de l'API Gateway |
| `apiKey` | `demo-soft-key` | clé API du tenant injecté |
| `targetTps` | `200` | débit cible (req/s) — simulation soutenue |
| `rampSeconds` / `holdSeconds` | `60` / `120` | durée de montée / palier |
| `nominalTps` / `burstFactor` | `200` / `5` | burst : débit nominal et multiplicateur de pic |

## Atteindre 1M tx/min (runbook)

1. **Pré-chauffe** : `docker compose up -d` (ou cluster K8s via `infrastructure/helm`), vérifier
   que les HPA sont actifs (`kubectl get hpa -n firstpay`) et que `transactions_per_second`
   remonte (`prometheus-adapter`, cf. `infrastructure/k8s/prometheus-adapter-values.yaml`).
2. **Répartir la cible** : avec `K` injecteurs, lancer chacun avec `targetTps = 16667 / K`
   (p.ex. 10 injecteurs → `-DtargetTps=1667`). Utiliser 10 clés API partenaires distinctes
   pour solliciter le rate-limit par tenant de façon réaliste.
3. **Observer en direct** : Grafana (`:3000`) — TPS, P50/P99, erreurs 5xx, lag Kafka,
   circuit breakers ; Jaeger (`:16686`) pour le tracing ; Alertmanager (`:9093`) pour les alertes.
4. **Injection de panne** (chaos) : pendant le palier, couper un pod `transaction-service`
   (`kubectl delete pod ...`) ou un broker Kafka et vérifier la **récupération (RTO < 30 s)**
   et l'absence de duplication (idempotence Redis + index DB unique).
5. **Recette SLO** : les assertions Gatling échouent si P99 ≥ 500 ms ou erreurs ≥ 0.1 %.
   Archiver le rapport HTML (`build/reports/gatling/...`).

## Seuils (Phase 10)

| Métrique | Cible dev | Cible prod |
|----------|-----------|------------|
| P99 | < 500 ms | < 100 ms |
| Erreurs | < 0.1 % | < 0.01 % |
| RTO (recovery) | < 30 s | < 30 s |
| Duplication | 0 % | 0 % |
| Débit | paramétrable | 1M tx/min (cluster, multi-injecteurs) |
