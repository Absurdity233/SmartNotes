# 智趣笔记云服务 - Docker 镜像
# 说明：使用 Node 22（内置 node:sqlite），无任何第三方依赖

FROM node:22-alpine

WORKDIR /app

# 只有 server.js 与 package.json，没有依赖需要安装
COPY package.json ./
COPY server.js ./

# 数据与上传目录挂到卷上，容器重建不丢数据
ENV SMARTNOTES_DATA_DIR=/data
VOLUME ["/data"]

ENV PORT=8787
EXPOSE 8787

# 健康检查：容器编排可直接感知是否就绪
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:'+(process.env.PORT||8787)+'/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
