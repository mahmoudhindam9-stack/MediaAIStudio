FROM node:22-bookworm-slim

ENV NODE_ENV=production
WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev && npm cache clean --force

COPY src ./src
RUN mkdir -p /app/temp_media && chown -R node:node /app
USER node

EXPOSE 3000
CMD ["npm", "start"]
