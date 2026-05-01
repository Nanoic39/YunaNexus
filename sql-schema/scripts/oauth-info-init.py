import bcrypt, uuid, string, secrets

"""
用于初始化OAuth表的脚本
"""

DEFAULT_REDIRECT_URI = "http://localhost:3000/oauth/callback"
SECRET_LENGTH = 48
ALPHABET = string.ascii_letters + string.digits + "-_"

def generateSecret(length: int = SECRET_LENGTH) -> str:
    return "".join(secrets.choice(ALPHABET) for _ in range(length))

def initByPlain(clientName: str, plainSecret: str, redirectUri: str = "localhost:3000/oauth/callback") -> str | None:
    # 生成UUID
    uid = str(uuid.uuid4())
    # bcrypt加密密钥
    bcryptSecret = bcrypt.hashpw(plainSecret.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    # 拼接输出命令(我不再嵌套一层返回值了，有需要自己改)
    print(f"INSERT INTO oauth_clients (uuid, client_type, audit_status, audit_opinion, redirect_whitelist, client_secret, client_name, authorized_grant_types, scope, redirect_uri, access_token_validity, refresh_token_validity, auto_approve, default_role_id, status) "
          # 这里设置的是YunaNexusCore的数据，所以authorized_grant_types为password,refresh_token
          + f"VALUES ('{uid}', 1, 1, '官方应用【SystemInit】', '{redirectUri}', '{bcryptSecret}', '{clientName}, 'password,refresh_token', 'all', '{redirectUri}', 7200, 604800, 0, 1);")

if __name__ == "__main__":
    name = input("请输入客户端名称：")
    secret = generateSecret() # 可以自定义密钥长度
    redirect = input(f"请输入回调地址(默认为\"{DEFAULT_REDIRECT_URI}\")：")

    print(f"====================")
    print(f"数据库指令：\n{initByPlain(clientName=name, plainSecret=secret, redirectUri=redirect)}")
    print(f"\n=== 密钥明文（仅出现这一次，请妥善保存）===")
    print(f"{secret}")
    print(f"========================")

    input(f"请确保已记录所有必要的信息，密钥明文仅会展示这一次！")
