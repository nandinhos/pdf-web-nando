# syntax=docker/dockerfile:1

# --- Build stage ---
FROM node:22-slim AS build
WORKDIR /app
ENV PNPM_CONFIG_STRICT_DEP_BUILDS=false
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# postinstall copia o worker do pdfjs para public/
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# --- Runtime stage: usa a saída standalone do Next ---
FROM node:22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN useradd -r -u 1001 appuser
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER appuser
EXPOSE 3000
CMD ["node", "server.js"]
