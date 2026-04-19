FROM node:22-alpine

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

EXPOSE 5173

ENTRYPOINT ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
