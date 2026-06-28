# FirstScolarité V2 — FirstPay Studio

Plateforme de paiement multi-partenaires **Afriland First Bank** (encaissement mobile,
interfaces de collecte, back-office banque).

## Contenu du dépôt

| Chemin | Description |
|--------|-------------|
| [`firstpay-studio/`](firstpay-studio/) | **Application principale** — monorepo (Angular 21, Spring Boot, Docker) |
| `firstpay_cursor_prompt (1).md` | Spécifications / prompt de conception |
| `FirstPay Studio (standalone) (1).html` | Maquette UI standalone |

## Démarrage rapide

```bash
cd firstpay-studio
export JWT_SECRET="firstpay-test-secret-min-32-chars!!"
docker compose up -d --build
```

- Frontend : http://localhost:14200  
- API Gateway : http://localhost:18080  

Documentation complète : [`firstpay-studio/docs/DEPLOIEMENT.md`](firstpay-studio/docs/DEPLOIEMENT.md)

## Déploiement production (firstsign.afbdei.com)

```bash
cd firstpay-studio
sudo ./infrastructure/scripts/deploy-production.sh
sudo ./infrastructure/scripts/update-production.sh   # mises à jour
```

## Stack technique

Angular 21 · Spring Boot 3 / WebFlux · PostgreSQL · Kafka · Redis · TrustPayWay (MTN / Orange)

Voir [`firstpay-studio/README.md`](firstpay-studio/README.md) pour l'architecture détaillée.
