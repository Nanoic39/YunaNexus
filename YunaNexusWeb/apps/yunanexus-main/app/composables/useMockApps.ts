/**
 * 开发环境共享的 OAuth 应用模拟数据。
 * 所有页面和插件共享同一个数组，确保创建/审核操作的数据一致性。
 */

export interface MockApp {
  uuid: string;
  clientName: string;
  clientType: number;
  grantTypes: string;
  scope: string;
  redirectUri: string;
  description: string;
  auditStatus: number; // 0=pending, 1=approved, 2=rejected
  auditOpinion: string | null;
  status: number; // 0=disabled, 1=enabled
  createdAt: string;
  updatedAt?: string;
  /** 审核锁定：当前正在审核该应用的管理员用户名，null 表示未锁定 */
  reviewingBy?: string | null;
  /** 提交者用户名 */
  applicantName?: string;
}

function createSeedApps(): MockApp[] {
  return [
    {
      uuid: "mock-app-001",
      clientName: "示例 Web 应用",
      clientType: 2,
      grantTypes: "authorization_code",
      scope: "read",
      redirectUri: "https://example.com/oauth/callback",
      description: "一个演示用的第三方应用，展示 OAuth 接入效果。",
      auditStatus: 1,
      auditOpinion: "审核通过",
      status: 1,
      createdAt: "2026-06-20 10:30:00",
      applicantName: "dev_user",
    },
    {
      uuid: "mock-app-002",
      clientName: "待审核应用",
      clientType: 2,
      grantTypes: "authorization_code,refresh_token",
      scope: "read,write",
      redirectUri: "https://demo.example.org/callback",
      description: "刚提交的新应用申请，等待管理员审核。",
      auditStatus: 0,
      auditOpinion: null,
      status: 0,
      createdAt: "2026-07-01 14:00:00",
      applicantName: "test_user",
    },
    {
      uuid: "mock-app-003",
      clientName: "被拒绝的测试应用",
      clientType: 2,
      grantTypes: "authorization_code",
      scope: "read",
      redirectUri: "http://localhost:3000/callback",
      description: "回调地址未使用 HTTPS，审核未通过。",
      auditStatus: 2,
      auditOpinion: "回调地址必须使用 HTTPS",
      status: 0,
      createdAt: "2026-06-25 09:15:00",
      applicantName: "new_dev",
    },
    {
      uuid: "mock-app-004",
      clientName: "官方客户端",
      clientType: 1,
      grantTypes: "authorization_code,refresh_token",
      scope: "read,write",
      redirectUri: "https://yunanexus.app/oauth/callback",
      description: "YunaNexus 官方桌面客户端。",
      auditStatus: 1,
      auditOpinion: "官方应用自动通过",
      status: 1,
      createdAt: "2026-06-15 08:00:00",
      applicantName: "system",
    },
  ];
}

/** 开发模式共享的 mock app 列表 (useState 全局单例) */
export function useMockApps(): Ref<MockApp[]> {
  const state = useState<MockApp[]>("mock-apps", () => createSeedApps());
  return state;
}

/** 根据 uuid 查找 mock app */
export function findMockApp(uuid: string): MockApp | undefined {
  if (!import.meta.dev) return undefined;
  const apps = useMockApps();
  return apps.value.find((a) => a.uuid === uuid);
}

/** 创建一个新的 mock app（模拟提交申请） */
export function addMockApp(data: {
  clientName: string;
  redirectUri: string;
  description: string;
  grantTypes: string;
  scope: string;
}): MockApp {
  const apps = useMockApps();
  const newApp: MockApp = {
    uuid: "mock-app-" + Math.random().toString(36).slice(2, 8),
    clientName: data.clientName,
    clientType: 2,
    grantTypes: data.grantTypes,
    scope: data.scope,
    redirectUri: data.redirectUri,
    description: data.description,
    auditStatus: 0,
    auditOpinion: null,
    status: 0,
    createdAt: new Date().toISOString().replace("T", " ").substring(0, 19),
    applicantName: "current_user",
  };
  apps.value.unshift(newApp);
  return newApp;
}
