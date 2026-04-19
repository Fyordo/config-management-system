FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

ARG VITE_API_URL=http://localhost:8081
ARG VITE_SERVER_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_SERVER_URL=${VITE_SERVER_URL}

RUN npm run build

FROM nginx:1.27-alpine

COPY .docker/node/prod/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

ENTRYPOINT ["nginx", "-g", "daemon off;"]
