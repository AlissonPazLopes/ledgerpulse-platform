.DEFAULT_GOAL := help

COMPOSE := docker compose
PYTHON ?= python3

.PHONY: help up down down-v ps logs restart validate health e2e chaos clean-boot audit

help:
	@echo "LedgerPulse Platform - Comandos de Infraestrutura & QA"
	@echo "  make validate    - Valida sintaxe e interpolacao do docker-compose.yml"
	@echo "  make up          - Sobe toda a stack (9 containers) em background"
	@echo "  make clean-boot  - Reset completo de volumes (down -v) + rebuild e subida"
	@echo "  make down        - Para e remove os containers mantendo volumes"
	@echo "  make down-v      - Para containers e remove volumes locais (reset limpo)"
	@echo "  make ps          - Lista status dos containers"
	@echo "  make health      - Exibe estado de saude (healthcheck) dos containers"
	@echo "  make logs        - Acompanha logs em tempo real (--tail=100)"
	@echo "  make restart     - Reinicia os containers da stack"
	@echo "  make e2e         - Executa fluxo E2E (Pagamento -> Ledger -> DLQ -> Replay)"
	@echo "  make chaos       - Executa suite Load & Chaos (idempotencia, fallback, DLQ, metricas)"
	@echo "  make audit       - Executa validacoes offline (Compose, self-tests E2E/Chaos e JSON Grafana)"

validate:
	$(COMPOSE) config --quiet

up:
	$(COMPOSE) up -d

clean-boot:
	$(COMPOSE) down -v
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

down-v:
	$(COMPOSE) down -v

ps:
	$(COMPOSE) ps

health:
	$(COMPOSE) ps --format "table {{.Name}}\t{{.State}}\t{{.Status}}"

logs:
	$(COMPOSE) logs -f --tail=100

restart:
	$(COMPOSE) restart

e2e:
	$(PYTHON) infra/scripts/e2e_payment_dlq_flow.py

chaos:
	$(PYTHON) infra/scripts/chaos_load_test.py

audit:
	$(COMPOSE) config --quiet
	$(PYTHON) infra/scripts/e2e_payment_dlq_flow.py --self-test
	$(PYTHON) infra/scripts/chaos_load_test.py --self-test
	$(PYTHON) -c "import json; json.load(open('infra/grafana/dashboards/ledgerpulse-overview.json', encoding='utf-8'))"
