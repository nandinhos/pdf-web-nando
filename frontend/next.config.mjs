/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  // Dev local ("pnpm dev" no host, sem nginx): faz proxy de /api/v1 para o
  // backend publicado em localhost:8081 pelo docker-compose. No fluxo Docker
  // completo o nginx intercepta /api/ antes do frontend, entao esta rewrite
  // nao e acionada. Sobrescreva com API_PROXY_TARGET se necessario.
  async rewrites() {
    const target = process.env.API_PROXY_TARGET ?? "http://localhost:8081";
    return [{ source: "/api/v1/:path*", destination: `${target}/api/v1/:path*` }];
  },
};

export default nextConfig;
