.DEFAULT_GOAL := help

COMPOSE := docker compose

.PHONY: help up down down-v ps logs restart validate health

help:
	@echo "LedgerPulse Platform - Comandos de Infraestrutura"
	@echo "  make validate  - Valida sintaxe e interpolacao do docker-compose.yml"
	@echo "  make up        - Sobe a stack em background (docker compose up -d)"
	@echo "  make down      - Para e remove os containers mantendo volumes"
	@echo "  make down-v    - Para containers e remove volumes locais (reset limpo)"
	@echo "  make ps        - Lista status dos containers"
	@echo "  make health    - Exibe estado de saude (healthcheck) dos containers"
	@echo "  make logs      - Acompanha logs em tempo real (--tail=100)"
	@echo "  make restart   - Reinicia os containers da stack"

validate:
	$(COMPOSE) config --quiet

up:
	$(COMPOSE) up -d

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
