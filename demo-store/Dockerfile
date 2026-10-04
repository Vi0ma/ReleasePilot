FROM node:22.23.3-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY tsconfig.json vite.config.mjs index.html ./
COPY src/ src/
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine AS storefront
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

FROM node:22.23.3-alpine AS bff
WORKDIR /app
# No npm packages are needed by the native Node BFF.
COPY --chown=node:node server/ server/
USER node
ENV DEMO_BFF_HOST=0.0.0.0
EXPOSE 3001
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD node -e "fetch('http://127.0.0.1:3001/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "server/index.mjs"]
