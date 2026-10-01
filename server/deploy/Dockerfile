# BlitzBook sync server. Build from the repository root:
#   docker build -f server/deploy/Dockerfile -t blitzbook .
# Data lives in /data (mount a volume there so it survives updates).
FROM node:20-alpine
WORKDIR /app
COPY server/server.js server/notify.js server/
COPY webportal webportal
RUN rm -rf webportal/tools
ENV PORT=8080 BLITZBOOK_DATA=/data NODE_ENV=production
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "server/server.js"]
