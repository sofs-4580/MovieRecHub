FROM node:22-alpine
WORKDIR /app
COPY --chown=node:node package.json server.mjs index.html styles.css ./
COPY --chown=node:node src ./src
USER node
ENV FRONTEND_HOST=0.0.0.0 FRONTEND_PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
    CMD ["node", "-e", "fetch('http://127.0.0.1:' + process.env.FRONTEND_PORT, { signal: AbortSignal.timeout(3000) }).then(response => process.exit(response.ok ? 0 : 1)).catch(() => process.exit(1))"]
CMD ["node", "server.mjs"]
