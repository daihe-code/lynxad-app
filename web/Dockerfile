ARG NGINX_BASE_IMAGE=mirror.gcr.io/library/nginx:1.27-alpine
FROM ${NGINX_BASE_IMAGE}

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
