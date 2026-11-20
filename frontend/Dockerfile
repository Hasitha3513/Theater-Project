FROM node:24.22.0-alpine@sha256:429bdfbc204b4eabc6438e6d34e4e9d8dd2ca1df8289bcd78cdd3c5db387b7a5 AS build
ARG SOURCE_DATE_EPOCH
ARG CINENEST_BUILD_ID
ENV CINENEST_BUILD_ID=$CINENEST_BUILD_ID CINENEST_BUILD_CPUS=2 TOKIO_WORKER_THREADS=2 RAYON_NUM_THREADS=2
WORKDIR /source/frontend
ENV NEXT_TELEMETRY_DISABLED=1
COPY frontend/package.json frontend/package-lock.json ./
RUN npm install --global npm@11.19.1 && test "$(node --version)" = v24.22.0 && test "$(npm --version)" = 11.19.1 && npm ci
COPY frontend/ ./
COPY knowledge/styles/ /source/knowledge/styles/
COPY knowledge/tokens/ /source/knowledge/tokens/
RUN test -n "$SOURCE_DATE_EPOCH" && test -n "$CINENEST_BUILD_ID" && npm run build && mkdir -p .next/standalone/public && cp -R public/. .next/standalone/public/ && cp -R .next/static .next/standalone/.next/static && test -z "$(find .next/standalone -name '.env*' -print -quit)"
FROM node:24.22.0-alpine@sha256:429bdfbc204b4eabc6438e6d34e4e9d8dd2ca1df8289bcd78cdd3c5db387b7a5
ARG CINENEST_BUILD_ID
LABEL com.cinenest.packaging.snapshot=$CINENEST_BUILD_ID
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0
COPY --from=build --chown=10001:10001 /source/frontend/.next/standalone/ ./
COPY --chown=10001:10001 <<'JS' /app/start.mjs
try {
  const port = Number(process.env.PORT);
  const backend = new URL(process.env.CINENEST_AUTH_ORIGIN);
  if (!Number.isInteger(port) || port < 1024 || port > 65535 || process.env.HOSTNAME !== '0.0.0.0'
      || backend.protocol !== 'http:' || !['127.0.0.1','localhost','[::1]'].includes(backend.hostname)
      || !backend.port || Number(backend.port) < 1024 || backend.pathname !== '/'
      || backend.username || backend.password || backend.search || backend.hash) throw Error();
  for (const [key,value] of Object.entries(process.env)) {
    if (/^CINENEST_.*_(ENABLED|EXECUTION)$/.test(key) && value.toLowerCase() === 'true') throw Error();
  }
} catch {
  console.error('Cloud Run frontend configuration unavailable or denied');
  process.exit(1);
}
await import('./server.js');
JS
USER 10001:10001
CMD ["node","start.mjs"]
