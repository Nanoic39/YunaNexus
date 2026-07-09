/**
 * 初始化 Mock 数据文件：从 .example.ts 复制为 .ts
 * 运行: node scripts/mock-init.cjs
 */
const fs = require('fs');
const path = require('path');

const mockDir = path.join(__dirname, '..', 'YunaNexusWeb', 'apps', 'yunanexus-main', 'app', 'mock');

if (!fs.existsSync(mockDir)) {
  console.log('Mock 目录不存在: ' + mockDir);
  process.exit(0);
}

const files = fs.readdirSync(mockDir).filter(f => f.endsWith('.example.ts'));

files.forEach(f => {
  const target = f.replace('.example.ts', '.ts');
  const srcPath = path.join(mockDir, f);
  const dstPath = path.join(mockDir, target);
  if (!fs.existsSync(dstPath)) {
    fs.copyFileSync(srcPath, dstPath);
    console.log('已创建: ' + target);
  } else {
    console.log('已存在: ' + target + ' (跳过)');
  }
});

console.log('Mock 数据初始化完成！');
