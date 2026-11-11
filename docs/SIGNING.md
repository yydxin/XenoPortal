# 自己生成长期 release 签名

交付的 probe APK 只能作为诊断版本。正式维护时请自己生成、离线备份签名密钥；不要把 keystore、密码、keystore.properties 加进仓库、工单或日志。

在源码目录外创建密钥，keytool 会交互询问密码（避免命令行明文参数）：

```bash
mkdir -p "$HOME/.xeno-signing"
chmod 700 "$HOME/.xeno-signing"
keytool -genkeypair -keystore "$HOME/.xeno-signing/release.jks" \
  -alias xeno-release -keyalg RSA -keysize 3072 -validity 10000
```

设置构建所需环境变量；密码从交互输入读取，shell 不回显：

```bash
export XENO_KEYSTORE="$HOME/.xeno-signing/release.jks"
export XENO_KEY_ALIAS=xeno-release
read -r -s -p 'Store password: ' XENO_STORE_PASSWORD
export XENO_STORE_PASSWORD
read -r -s -p 'Key password: ' XENO_KEY_PASSWORD
export XENO_KEY_PASSWORD
./gradlew :app:assembleRelease
unset XENO_STORE_PASSWORD XENO_KEY_PASSWORD
```

将 ANDROID_HOME 指向本地 Android SDK 36，使用 JDK 17 或完整 JDK 21。当前 Gradle 配置仅在 XENO_KEYSTORE 非空时为 release 配置签名，未设置时输出 unsigned APK，不能将其称为签名完成。

release APK 路径 `app/build/outputs/apk/release/app-release.apk`。用 SDK build-tools 的 apksigner verify 验证，不要只根据文件名判断。

本轮诊断 APK 使用工作区外的独立测试证书构建；密钥与密码不在源码压缩包或日志中。该证书未登记小米平台，不产生超级岛授权。自行换签名后无法覆盖原签名安装，卸载会清空应用数据，安装前请先导出需保留的诊断日志。长期自用请尽早切换自己的密钥。
