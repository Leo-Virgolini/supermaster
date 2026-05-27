import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  output: "standalone",
  experimental: {
    // Router Cache: mantener las pages visitadas en memoria para que el back del
    // navegador sea instantáneo y no dispare el loading.tsx.
    // dynamic = rutas con data dinámica; static = rutas prerenderizadas.
    staleTimes: {
      dynamic: 300,  // 5 minutos
      static: 600,   // 10 minutos
    },
  },
};

export default nextConfig;
