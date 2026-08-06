# CySpring 参数语义与 Bullet 约束修复计划

## 目标

修复当前 Rust 物理中同源的三类基础错误：零刚度旋转弹簧仍被启用、FollowBone 运动学目标与约束锚点不同步、PMX 关节坐标和旋转限位可能没有正确转换。目标现象是减少裙摆外撑和穿腿，以及头发、袖子、头饰的高频抖动；角色切换后不能继承上一角色的物理历史状态。

CySpring 只用于参考参数语义、初始化与重置时序、父骨骼驱动和碰撞选择。Unity/CySpring 的坐标转换不直接移植，PMX 到 Bullet 的左右手转换由独立数学测试和 Bullet 回读结果决定。

## 已确认的证据

- CySpring 使用固定 Native 布局，并把参数值、启用标志、上一帧目标、父级姿态索引分别保存，避免零值或字段偏移被误解释。
- CySpring 初始化时令 `TargetPosition == PrevTargetPosition`，重置时同时刷新 `SelfPosition`、目标历史、父旋转和最终旋转。
- Rust 的平移弹簧会用 `spring_is_enabled` 判断是否启用，但旋转弹簧无条件 `enable_spring(true)`；受害关节又大量使用零旋转刚度。
- Rust 的 FollowBone 每帧只写 MotionState。现有测试只验证 Bullet 能计算运动学速度，没有验证它带动受约束动态子刚体时的锚点连续性。
- 关节 frame 和限位目前直接使用 PMX 坐标，而刚体和骨骼路径使用 `inv_z`；镜像后的旋转轴、上下限交换尚无测试证明正确。
- 运行日志中的深穿透、巨大冲量、锚点偏移和角限位越界能够形成“碰撞向外推、失效约束向内拉”的持续外撑与抖动循环。

## 阶段 B1：建立 Bullet 最小回归测试，不改运行行为

1. 增加零刚度旋转轴测试，回读 `spring_enabled`，并在受限双刚体场景中比较 enabled/disabled 的运动结果。
2. 增加运动学父刚体与动态子刚体的 6DOF 测试，连续移动父体，记录每步锚点误差、限位和速度；另测角色重置式瞬移，确保不会把旧目标变成巨大速度。
3. 增加关节镜像数学测试：验证 `M * frame` 的世界锚点在 `inv_z` 前后保持镜像等价；通过 Bullet diagnostic 回读 frame、当前角和 lower/upper。
4. 这些测试先复现当前错误，再作为后续行为修改的验收基线。

## 阶段 B2：规范化关节参数并修复零弹簧

1. 新增小型 `joint_parameters` 模块，将 PMX 参数规范化为显式的线性/旋转轴配置：上下限、刚度、是否启用、有限值校验。
2. 平移和旋转轴统一使用同一启用规则；零值、非有限值和极小值不启用 spring motor，但关节 limit 保持独立生效。
3. 不修改 PMX 原始重力、阻尼、碰撞半径或模型数据，不增加裙摆特判。
4. 日志补充每模型启用/禁用的弹簧轴统计，确认参数实际进入 Bullet。
5. 对有限的反向上下限逐轴排序；Bullet 将 `lower > upper` 解释为自由轴，Teio 横向裙摆的 X/Z 反向区间必须恢复为实际受限区间。

## Rust 日志 JNI 转发补充

1. 新增独立的有界 Rust logger，仅在 JNI 首次调用时安装，不抢占 viewer/demo 的 `env_logger`。
2. Rust 日志按级别、target 和消息写入全局队列，通过无模型句柄的 JNI 接口一次性排空。
3. Java 沿现有 native backend port 接收日志，并映射到 Log4j 的 INFO/WARN/ERROR；队列固定容量，避免未及时消费时无限增长。
4. 保留现有模型级物理聚合诊断，两条通道分别负责通用 Rust 日志和高频物理窗口摘要。

## 阶段 B3：修复 FollowBone 同步和角色切换重置

1. 将“连续动画目标更新”和“物理状态硬重置”拆为两个明确接口。
2. 连续更新保留 Bullet 计算运动学速度所需的上一帧状态，同时确保约束求解看到的 world/interpolation/MotionState 属于一致时序。
3. 首次加载、角色切换、物理重建和大跨度跳变使用硬重置：同步 world transform、interpolation transform、MotionState，并清零速度和残余力。
4. 在进入第一步模拟前检查关节 frame 的两个世界锚点重合；角色切换测试要求首帧不产生继承速度或大锚点误差。

## 阶段 B4：独立验证并按证据修复坐标转换

1. 推导 Z 镜像对平移、关节姿态和 XYZ 旋转限位的影响，不采用 CySpring/Unity 坐标代码。
2. 若测试证明关节 frame 缺少镜像，则统一转换关节 position/rotation；若某旋转轴符号翻转，则同时交换并取反对应 lower/upper。
3. 用对称与非对称限位样例验证，防止仅靠对称的 `+-0.1745` 数据掩盖轴向错误。
4. 对真实 PMX 抽样回读袖子、头饰、头发和裙摆关节，要求初始锚点误差接近零且 current angle 位于预期限位附近。

## 阶段 C：通用修复验证后再评估裙摆控制器

只有当阶段 B 已消除头发、袖子、头饰的同源抖动，而奔跑时裙摆仍会穿腿，才实现 CySpring `SkirtController` 风格的保护层：从标准姿态和腿部几何计算有限根骨外翻角，不通过扩大碰撞半径或持续冲量把裙摆撑开。该阶段需要单独计划和审批。

## 预计修改范围

- `rust_engine/src/physics/mmd_joint.rs`
- 新增 `rust_engine/src/physics/joint_parameters.rs`
- `rust_engine/src/physics/bullet_ffi.rs`
- `rust_engine/bullet_wrapper/bw_api.cpp`
- `rust_engine/bullet_wrapper/bw_api.h`
- `rust_engine/src/physics/mmd_physics.rs`
- `rust_engine/src/physics/mmd_physics_tests.rs`
- 新增 `rust_engine/src/jni_log.rs`
- `rust_engine/src/lib.rs`
- `rust_engine/src/jni_bridge/native_func.rs`
- `common/src/main/java/com/shiroha/mmdskin/NativeFunc.java`
- `common/src/main/java/com/shiroha/mmdskin/bridge/runtime/NativeRenderBackendPort.java`
- `common/src/main/java/com/shiroha/mmdskin/bridge/runtime/NativeRuntimeBridge.java`
- `common/src/main/java/com/shiroha/mmdskin/render/backend/BaseModelInstance.java`

实现时按 B1 到 B4 分批进行，每批先测试再进入下一批；不会回退工作区已有改动。

## 验收

- `cargo fmt --manifest-path rust_engine/Cargo.toml -- --check`
- `cargo test --manifest-path rust_engine/Cargo.toml --lib`
- 构建并复制三端 Native DLL，核对哈希一致。
- 用户在 Forge 中分别测试站立、奔跑、角色切换；日志对比关节锚点误差、角限位越界、角速度、接触深度和冲量峰值。
- 通过标准：不再整体外撑一圈；头发、袖子、头饰无持续高频抖动；切换角色首帧无爆炸；奔跑穿模显著下降。若仅剩裙摆局部穿腿，再进入阶段 C。
