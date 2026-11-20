const nextConfig = {
  poweredByHeader: false,
  agentRules: false,
  output: "standalone",
  ...(process.env.CINENEST_BUILD_CPUS === "2" ? { experimental: { cpus: 2 } } : {}),
  ...(process.env.CINENEST_BUILD_ID ? { generateBuildId: async () => process.env.CINENEST_BUILD_ID } : {}),
};
export default nextConfig;
