FROM node:22-alpine
WORKDIR /app
COPY apps/yunanexus-main/.output ./ 
EXPOSE 3000
ENV NITRO_HOST=0.0.0.0
ENV NITRO_PORT=3000
CMD ["node", "server/index.mjs"]
