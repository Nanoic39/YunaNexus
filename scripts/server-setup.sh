#!/bin/bash
set -euo pipefail

GITHUB_REPO="https://github.com/Nanoic39/YunaNexus" # 改为你自己的仓库路径
PROJECT_DIR="/yunanexus/YunaNexusPackage" # 项目根目录（绝对路径）
JENKINS_PORT=8080 # Jenkins前端端口

# --- 一些提示信息 ---
echo "=== YunaNexus Server Setup ==="
echo "Target: Ubuntu 24.04" # 我自己的版本是Ubuntu 24.04，给自己看的
echo ""

# --- 更新源 ---
apt-get update -y

# --- Docker (阿里云镜像) ---
if ! command -v docker &>/dev/null; then
  echo ">>> Installing Docker (Aliyun mirror)..."
  apt-get install -y ca-certificates curl gnupg lsb-release
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  mkdir -p /etc/docker
  cat > /etc/docker/daemon.json << 'DOCKEREOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://dockerpull.org",
    "https://docker.xuanyuan.me",
    "https://hub.rat.dev"
  ],
  "max-concurrent-downloads": 3,
  "max-concurrent-uploads": 2
}
DOCKEREOF
  systemctl restart docker
  systemctl enable docker --now
fi

# --- JDK (Amazon Corretto 22.0.2.9.1) ---
if ! command -v java &>/dev/null || ! java -version 2>&1 | grep -q '22\.'; then
  echo ">>> Installing Amazon Corretto 22.0.2.9.1..."
  apt-get install -y wget
  CORRETTO_URL="https://corretto.aws/downloads/resources/22.0.2.9.1/amazon-corretto-22.0.2.9.1-linux-x64.tar.gz"
  echo "Installing... Not Printing Anything, Please Wait..."
  wget -qO /tmp/corretto22.tar.gz "$CORRETTO_URL" || {
    echo ">>> Primary mirror failed, trying GitHub..."
    wget -qO /tmp/corretto22.tar.gz "https://github.com/corretto/corretto-22/releases/download/22.0.2.9.1/amazon-corretto-22.0.2.9.1-linux-x64.tar.gz"
  }
  mkdir -p /usr/lib/jvm
  tar -xzf /tmp/corretto22.tar.gz -C /usr/lib/jvm
  JDK_DIR=$(ls -d /usr/lib/jvm/amazon-corretto-22* | head -1)
  update-alternatives --install /usr/bin/java java "$JDK_DIR/bin/java" 1
  update-alternatives --install /usr/bin/javac javac "$JDK_DIR/bin/javac" 1
  update-alternatives --set java "$JDK_DIR/bin/java"
  update-alternatives --set javac "$JDK_DIR/bin/javac"
  rm -f /tmp/corretto22.tar.gz
fi

# --- Maven ---
if ! command -v mvn &>/dev/null; then
  echo ">>> Installing Maven..."
  apt-get install -y maven
  mkdir -p /root/.m2 /var/lib/jenkins/.m2
  cat > /root/.m2/settings.xml << 'MVNEOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
MVNEOF
  cp /root/.m2/settings.xml /var/lib/jenkins/.m2/settings.xml
  chown -R jenkins:jenkins /var/lib/jenkins/.m2
fi

# --- Node.js 22 (nvm + 淘宝镜像) ---
if ! command -v node &>/dev/null || ! node -v | grep -q 'v22'; then
  echo ">>> Installing Node.js 22 via nvm (npmmirror)..."
  export NVM_NODEJS_ORG_MIRROR=https://npmmirror.com/mirrors/node
  export NVM_DIR="$HOME/.nvm"
  if [ ! -d "$NVM_DIR" ]; then
    curl -fsSL https://gitee.com/mirrors/nvm/raw/v0.40.1/install.sh | bash
  else
    git -C "$NVM_DIR" remote set-url origin https://gitee.com/mirrors/nvm.git 2>/dev/null || true
  fi
  [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
  nvm install 22
  nvm use 22
  nvm alias default 22
  echo 'export NVM_DIR="$HOME/.nvm"' >> /root/.bashrc
  echo '[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"' >> /root/.bashrc
fi

if ! command -v pnpm &>/dev/null; then
  echo ">>> Installing pnpm..."
  npm install -g pnpm --registry=https://registry.npmmirror.com
fi

# --- Node.js + pnpm for jenkins user ---
if ! sudo -u jenkins bash -c 'command -v node &>/dev/null && node -v | grep -q v22' 2>/dev/null; then
  echo ">>> Installing Node.js 22 for jenkins user..."
  sudo -u jenkins bash -c "
    export NVM_DIR=\"\$HOME/.nvm\"
    [ ! -d \"\$NVM_DIR\" ] && curl -fsSL https://gitee.com/mirrors/nvm/raw/v0.40.1/install.sh | bash
    git -C \"\$NVM_DIR\" remote set-url origin https://gitee.com/mirrors/nvm.git 2>/dev/null || true
    export NVM_NODEJS_ORG_MIRROR=https://npmmirror.com/mirrors/node
    [ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\"
    nvm install 22
    nvm use 22
    nvm alias default 22
    npm install -g pnpm --registry=https://registry.npmmirror.com
  "
fi

# --- Jenkins (清华 DEB 直装) ---
if ! systemctl is-active --quiet jenkins 2>/dev/null; then
  echo ">>> Installing Jenkins (Tsinghua DEB)..."
  if [ ! -f /usr/share/java/jenkins.war ]; then
    JENKINS_DEB_URL="https://mirrors.tuna.tsinghua.edu.cn/jenkins/debian-stable/jenkins_2.555.2_all.deb"
    wget -qO /tmp/jenkins.deb "$JENKINS_DEB_URL" || {
      echo ">>> Tsinghua DEB failed, trying Aliyun..."
      wget -qO /tmp/jenkins.deb "https://mirrors.aliyun.com/jenkins/debian-stable/jenkins_2.555.2_all.deb"
    } || {
      echo ">>> All mirrors failed, trying official..."
      wget -qO /tmp/jenkins.deb "https://pkg.jenkins.io/debian-stable/binary/jenkins_2.555.2_all.deb"
    }
    apt-get install -y fontconfig daemon
    dpkg -i /tmp/jenkins.deb || apt-get install -f -y
    rm -f /tmp/jenkins.deb
    usermod -aG docker jenkins
  else
    echo ">>> Jenkins already installed, starting..."
  fi
  sed -i 's/#Environment="JENKINS_OPTS="/Environment="JENKINS_OPTS=--enable-future-java"/' /usr/lib/systemd/system/jenkins.service 2>/dev/null || true
  systemctl daemon-reload
  systemctl start jenkins
fi

# --- Project directory ---
mkdir -p "$PROJECT_DIR"/docker/nginx "$PROJECT_DIR"/YunaNexusCore "$PROJECT_DIR"/YunaNexusWeb
chown -R jenkins:jenkins "$PROJECT_DIR"

echo ""
echo "=== Setup Complete ==="
echo "Jenkins: http://$(hostname -I | awk '{print $1}'):${JENKINS_PORT}"

if [ -f /var/lib/jenkins/secrets/initialAdminPassword ]; then
  echo "Initial Admin Password: $(cat /var/lib/jenkins/secrets/initialAdminPassword)"
fi