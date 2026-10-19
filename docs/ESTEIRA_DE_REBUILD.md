# Esteira Canônica de Rebuild & Ciclo de Mudanças

Este documento descreve a esteira determinística de desenvolvimento, validação, rebuild de containers e verificação pós-deploy do **pdf-web-nando**.

---

## 1. Visão Geral do Ciclo de Mudança

Toda alteração de funcionalidade no projeto segue o fluxo rigoroso de feedback em 4 fases:

```
[ALTERAÇÃO DE CÓDIGO]
         │
         ▼
[FASE 1: TESTES CANÔNICOS LOCAIS]
├── Backend: mvn test (Java 21 / PDFBox 3)
└── Frontend: pnpm test + pnpm run typecheck (Vitest + TypeScript)
         │
         ▼
[FASE 2: REBUILD E DEPLOY LOCAL DOS CONTAINERS]
└── docker compose -f infra/docker-compose.yml up -d --build backend frontend nginx
         │
         ▼
[FASE 3: AUDITORIA PÓS-DEPLOY (SMOKE TESTS)]
├── Backend Health: curl http://localhost:8090/api/v1/actuator/health -> {"status":"UP"}
└── Frontend UI: curl -sI http://localhost:8090/ -> HTTP/1.1 200 OK
         │
         ▼
[FASE 4: REGISTRO GIT E AUDITORIA DE DIFF]
└── Commits semânticos e rastreabilidade total
```

---

## 2. Comandos Canônicos da Esteira

### Fase 1: Validação de Código e Testes Unitários

1. **Backend (Spring Boot + PDFBox 3):**
   ```bash
   # Execução via container Maven oficial (sem necessidade de Java no host)
   docker run --rm -v "$PWD/backend":/app -w /app maven:3.9-eclipse-temurin-21 mvn test
   ```

2. **Frontend (Next.js + Vitest + TypeScript):**
   ```bash
   cd frontend
   pnpm test
   pnpm run typecheck
   ```

---

### Fase 2: Rebuild dos Containers Docker

Para que alterações em código, layouts, novas rotas da API ou novos componentes se tornem efetivos na instância em execução, os containers devem ser reconstruídos:

```bash
# Rebuild dos serviços atualizados e subida em modo daemon
docker compose -f infra/docker-compose.yml up -d --build backend frontend nginx
```

> **Nota sobre volumes:** O volume persistente `pdfweb-data` é preservado entre rebuilds, garantindo que uploads e artefatos de teste em `/tmp/pdfweb` não sejam perdidos.

---

### Fase 3: Validação de Saúde (Smoke Tests)

Imediatamente após a conclusão do build, valide a saúde dos serviços:

1. **Checar status dos containers:**
   ```bash
   docker compose -f infra/docker-compose.yml ps
   ```

2. **Validar Backend via Nginx:**
   ```bash
   curl -s http://localhost:8090/api/v1/actuator/health
   # Resposta esperada: {"status":"UP"}
   ```

3. **Validar Frontend via Nginx:**
   ```bash
   curl -sI http://localhost:8090/
   # Resposta esperada: HTTP/1.1 200 OK
   ```

---

### Fase 4: Solução de Problemas Rápidos

- **Visualizar logs em tempo real:**
  ```bash
  docker compose -f infra/docker-compose.yml logs -f backend
  docker compose -f infra/docker-compose.yml logs -f frontend
  ```
- **Reiniciar sem rebuild (apenas reinício rápido):**
  ```bash
  docker compose -f infra/docker-compose.yml restart
  ```
- **Reset completo de cache de build (se houver artefatos desatualizados):**
  ```bash
  docker compose -f infra/docker-compose.yml build --no-cache backend frontend
  docker compose -f infra/docker-compose.yml up -d
  ```
