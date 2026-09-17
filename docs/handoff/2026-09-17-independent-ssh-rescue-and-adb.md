# DSH Mobile｜独立 SSH 救援通道与按需无线 ADB：实施记录及接入手册

状态（2026-09-17）：**橙派侧密钥/脚本已准备；手机 SSH 未连通、ADB 未连通；未进行 APK 安装，不应称终端连续性已经通过实测。** 本文补充 `2026-09-16-ui-remediation-and-acceptance-plan.md` 的 G0/真机门禁及 `docs/adr/0003-local-adb-primary-enhanced-provider.md`、ADR 0007；不改变 App 自身执行域/权限模型，也不替换现有云→橙派 SSH 隧道。

## 1. 经过核查的基线及安全约束

- 橙派 OpenSSH 22 正监听；现存 `orangepi-azure-tunnel@22022` service active/running，**必须保持不动**。既有手机 Termux SSH alias `orange` 据旧交接可通过云跳板连接橙派；本轮尚未从手机重新验证这一事实。
- 新的橙派本地 TCP `127.0.0.1:22023`（反向 SSH 到手机 `127.0.0.1:8022`）、`127.0.0.1:25555`（可选 ADB 连接）与 `127.0.0.1:25554`（可选 ADB 临时配对）目前均没有监听。之前手机 LAN 地址不可达，`adb devices` 为零设备。
- 新设备 SSH identity 在橙派项目 `.private/ssh/id_ed25519_dshmobile_device`，私钥 `0600`、目录 `0700`、`.private/` 由 `.gitignore` 排除；仅公钥放手机账号的 `authorized_keys`。不得将私钥、ADB pairing code、Wi-Fi pairing QR、用户私密文件放入 Git/MCP 日志/聊天。可在橙派上 `ssh-keygen -lf .private/ssh/id_ed25519_dshmobile_device.pub -E sha256` 核对公钥指纹。
- 手机端 sshd/tmux 必须属于**与 DSH Mobile 不同的 Termux 或独立沙箱包 UID/进程树**，不能由正在覆盖安装的 App 自己启动并持有。APK 升级只设计为不主动停止救援通道；网络掉线、OriginOS 杀后台、手机重启、Termux 自身更新无法做零中断承诺。tmux 保留的是其宿主进程运行期间的 session，不提供重启后进程持久性。
- 所有 SSH 端口只绑定**回环地址**，不得开放云端/公网 ADB；保留 SSH 密钥鉴权和校验过的主机指纹，不使用 `StrictHostKeyChecking=no`、`sshpass` 或全局修改 `/etc/ssh/sshd_config`。手机普通 UID ≠ Android shell UID，只有独立 ADB TLS 配对成功才能开展 shell-UID 的真机调试。

## 2. 手机需要完成的一次性操作（推荐使用已有 Termux 而不是 DSH App 终端）

以下命令须在**手机 Termux** 中运行，手机仍由用户持有、未接入时橙派侧不能代做；如果实际使用 Ubuntu 环境，要把 `pkg`、`$PREFIX`、`8022` 改成该环境真实值，且确认 Android 宿主不依赖 DSH Mobile。

1. 安装独立工具：`pkg install openssh tmux`。检查旧版 Termux `ssh orange 'printf "ORANGEPI_OK\\n"'` 仍能用；先确认旧隧道，不重建它。
2. 安全安装橙派**公钥**到手机（经过既有已校验 SSH alias `orange`，不传私钥）：

   ```bash
   mkdir -p "$HOME/.ssh"; chmod 700 "$HOME/.ssh"
   ssh orange 'cat ~/workspace/DeepSeek-Harness-Mobile/.private/ssh/id_ed25519_dshmobile_device.pub' \
     | sed 's/^/from="127.0.0.1,::1" /' >> "$HOME/.ssh/authorized_keys"
   chmod 600 "$HOME/.ssh/authorized_keys"
   ```

   `from=` 只授权经手机本机回环 SSH 转发过来的该公钥，不能误以为可以用它从外网直连手机。不要重复执行公钥追加命令；如需核对可查看手机 authorized_keys 的对应注释与橙派公钥指纹（不要共享整个文件）。
3. 如果手机上尚没有独立 sshd，启动仅回环的密钥 SSH server（不要重启/覆盖正在工作的 sshd）：

   ```bash
   sshd -p 8022 -o ListenAddress=127.0.0.1 \
     -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no \
     -o PubkeyAuthentication=yes
   ssh-keygen -lf "$PREFIX/etc/ssh/ssh_host_ed25519_key.pub" -E sha256
   whoami
   ```

   记下**host-key SHA256 指纹**和手机用户名（这不是新的客户端公钥指纹），回复给协助调试的助手；绝不发送私钥。若已有 sshd 使用不同端口，只需 `PHONE_SSH_PORT=实际端口`，不要干扰旧服务。手机安全设置中保证 Termux 可以持续后台运行，先在通电/同网下做长时间断线重连试验；勿修改系统权限或 Root。
4. 经既有 SSH 从橙派取得**不含秘密**的隧道脚本，在手机 Termux 中执行：

   ```bash
   ssh orange 'cat ~/workspace/DeepSeek-Harness-Mobile/scripts/rescue/phone-reverse-ssh.sh' \
     > "$HOME/phone-reverse-ssh.sh"
   chmod 700 "$HOME/phone-reverse-ssh.sh"
   tmux new-session -d -s dsh-rescue "$HOME/phone-reverse-ssh.sh" run
   tmux capture-pane -pt dsh-rescue | tail -12
   ```

   目标是让手机 SSH 主动经**已有** `orange` alias 登录橙派，只在**橙派的本机**开放 `127.0.0.1:22023`，回连手机独立 sshd；自动重连只重建此*新*救援 tunnel，不停止其他会话、服务或 App。出现 `remote port forwarding failed` 必须先检查占用/sshd AllowTcpForwarding，**不得**擅改全局防火墙或 GatewayPorts。

## 3. 橙派的双向认证/救援验收（由助手在手机连上后完成）

1. `ss -lnt | grep ':22023'` 检查只有 `127.0.0.1:22023`，不应出现 `0.0.0.0:22023`。
2. 通过手机本机 `ssh-keygen -lf "$PREFIX/etc/ssh/ssh_host_ed25519_key.pub" -E sha256` 的**独立渠道**指纹，执行 `scripts/rescue/pi-pin-phone-host.sh 'SHA256:...'`。脚本通过回环 ssh-keyscan 抓取 ED25519 key、核对指纹后才写 `.private/ssh/known_hosts`；不同指纹/旧 pin 冲突会拒绝，不自动接受新 key。
3. `scripts/rescue/pi-phone-ssh.sh 手机Termux用户名 --check` 必须输出 `RESCUE_SSH_OK` 和一个普通 UID，然后 `--shell` 测试进入独立手机 shell。断掉 Pi→手机的一个交互 SSH 会话，再经同一已 pin 主机密钥登录，确认 phone 侧 tmux session 仍可 attach；没有实时证据就不能称“终端不间断”。
4. 安装前后分别记录三个独立状态：①手机→橙派的原 `orange` 通道，②橙派→手机救援 SSH 连通和 tmux，③ADB serial/授权。覆盖安装只能针对 `com.stevebrilien.dshmobile`，必须先通过 APK SHA、签名、备份/回退门禁；不得卸载、清数据或把 Termux 纳入 `am force-stop`。如救援 SSH 掉线或旧隧道掉线，**立即中止安装/回滚动作**，先恢复控制面。

## 4. 按需 ADB，和救援 SSH **分离**

- Android 11 开发者选项开启 Wireless debugging，用户在系统 UI 读出当次「连接地址:端口」（不是配对端口）；是否监听 `127.0.0.1` 不预设。手机端独立 shell 用 `phone-adb-reverse.sh 连接端口 [可达的手机IP或127.0.0.1] [配对端口]`。脚本先检测实际 TCP 端口，再在**另一条 SSH 会话**上建立仅橙派回环 `:25555`，可选配对 `:25554`；不重启主救援 tunnel。连接失败不可手动将 ADB 端口暴露公网。
- 设备尚未配对时，橙派用受控 ADB 客户端在 `127.0.0.1:25554` 输入系统显示的一次性 pairing code，接着 `adb connect 127.0.0.1:25555` 并核对设备 serial、`getprop ro.product.model`、`id` 和 SDK；不能把 SSH `id` 结果冒充 shell UID。端口改变后按当前地址更新**可选**隧道；断 ADB 不影响主 SSH。
- 当前 MCP `adb_devices` 可只读检查；若 `adb pair/connect` 还没有受控结构化 HostCapability/TaskProfile，须先创建最小参数白名单的能力并审核，不通过修改 `adb_policy` 放开任意 adb shell 来走捷径。设备连接后先做**只读** UI / IME / 多选数量诊断，再决定是否安装 Preview 候选。

## 5. 目前完成与不完成

橙派侧已经：独立 Ed25519 生成、权限/ignore 验证，手机和橙派脚本已通过 `bash -n`，原旧隧道保持 active。**还未**证明 phone sshd/tmux 是否正常、手机 host key 指纹、reverse SSH 链路、Android wireless adbd、掉线重连/后台保活及更新前后终端持续可用。本任务未修改 App 源码、APK、签名、OTA、SSH 系统服务及既有 tunnel。若手机无法使用 Termux/Ubuntu 之外的独立 sshd，必须先停在基础设施门禁，不在 DSH App 中伪造救援终端。
