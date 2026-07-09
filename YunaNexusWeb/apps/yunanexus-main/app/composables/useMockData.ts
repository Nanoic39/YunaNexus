/**
 * 安全加载 Mock 数据的 composable。
 * - 生产环境直接返回空数组/对象，不影响系统运行。
 * - 开发环境尝试从 ~/mock/ 加载数据，若文件不存在则返回空值。
 * - Mock 数据文件（~/mock/*.ts）被 .gitignore 排除，需手动从 .example.ts 复制。
 */

interface MockModules {
  users: typeof import('~/mock/users.example')
  roles: typeof import('~/mock/roles.example')
  resources: typeof import('~/mock/resources.example')
  endpoints: typeof import('~/mock/endpoints.example')
  files: typeof import('~/mock/files.example')
  apps: typeof import('~/mock/apps.example')
}

const emptyCache: Record<string, any> = {}

function getEmpty<K extends keyof MockModules>(key: K): MockModules[K] {
  return emptyCache[key] as MockModules[K]
}

export async function useMockData<K extends keyof MockModules>(key: K): Promise<MockModules[K]> {
  if (!import.meta.dev) {
    return getEmpty(key)
  }
  try {
    const mod = await import(`~/mock/${key}`)
    return mod as MockModules[K]
  } catch {
    console.warn(`[Mock] ~/mock/${key}.ts 未找到，使用空数据。运行 "pnpm mock:init" 初始化 Mock 数据。`)
    return getEmpty(key)
  }
}
