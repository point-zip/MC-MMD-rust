# TaCZ 第三人称绝对姿态双臂 IK 对比（2026-08-04 已实现，待实机验收）

## 实机坐标修正

首个对比包中直接使用了 `TaCZ rotation * +Y`，遗漏 Minecraft `ModelPart` 与 Rust/MMD 模型空间的
基变换。实机表现为腕挂点和枪械整体落到角色左后方。第三人称方向现统一执行局部 X 轴 180° 转换：
`(x, y, z) -> (x, -y, -z)`；这会修正上下与前后方向，同时保持左右手 X 轴不互换。该修正只作用于
第三人称方向重建，不改变第一人称真实矩阵目标、VMD 肩腕距离、肘弯平面或枪械 Z +45° 补偿。

用户要求腰射也不再保持 v4 VMD 的最终手臂位置。本轮因此把 TaCZ 当前绝对上臂姿态转换为方向驱动的
MMD 腕点，并复用现有双骨 IK。实机确认沿用弯曲 VMD 的肩腕距离会让右手停在目标前，因此腕点距离改为
模型自身总臂长的 95%；剩余 5% 保持肘弯平面稳定，手首 roll 仍继承当前 VMD。该实验可以直接对比
“纯 v4”与“TaCZ 方向驱动 IK”，但 TaCZ 仍未提供逐枪握把/护木锚点，因此不能视为精确贴枪解算。

性能边界保持不变：Java 只复用一个 ThreadLocal ModelPart 集合，反射句柄和 Rust 骨链索引均缓存；
左右姿态一次 JNI 提交、一次消费，不增加网络同步，不输出逐帧诊断。第一人称真实锚点 IK 和已确认的
第三人称枪械 Hand_Attach_R 局部 Z +45° 补偿不变。

# TaCZ 第三人称目标语义纠正（2026-08-04 已实现，待实机验收）

## 新证据与根因

实机截图显示第三人称一条手臂被拉直到相机前。核对 TaCZ 1.20.1 源码后确认，默认
`IThirdPersonAnimation.animateGunHold/animateGunAim` 只写左右 `ModelPart` 上臂的 `xRot/yRot`，
没有肘、腕、握把或护木目标。此前把该上臂方向扩展为“肩到手首方向”，再用当前肩腕距离构造绝对
腕点，会强制双骨 IK 把整条手臂拉向错误方向；这与截图一致。

## 已实施修正

1. Java 为同一枪械分别采样腰射基线与当前姿态，计算左右上臂的相对局部四元数。
2. Rust 只把相对旋转叠加到 MMD 左右上臂，保留 VMD 的肘弯、腕位与手掌方向。
3. 删除第三人称“单位上臂方向 -> 绝对腕目标 -> 双骨 IK”路径；第一人称真实 TaCZ 手部锚点 IK 不变。
4. PlayerAnimator 枪包继续使用已验证的原版姿态旁路；反射句柄、临时 `ModelPart` 与骨索引继续缓存。
5. 保留已实机确认的第三人称主手枪械 `Hand_Attach_R` 局部 `Z +45°` 修正。

该修正只能利用 TaCZ 实际提供的上臂状态变化，不能宣称从缺失的数据中恢复每把枪的精确双腕位置。
若未来需要第三人称精确贴合握把/护木，必须新增枪械侧真实锚点来源，而不是再次从上臂角度伪造腕点。

# 眼球追踪多模型兼容方案

# TaCZ 第三人称持枪位置双臂 IK 修正（待审批）

## 问题结论

当前第三人称没有复用第一人称的双臂 IK。Java 只从 TaCZ 的 `thirdPersonAnimation` 采样左右上臂
四元数，Rust 再把相对旋转叠加到统一的 `tacz_hold_rifle_ads_v4.vmd`。静态持枪时相对旋转接近
单位旋转，因此最终仍主要表现为 v4 姿势，MMD 手首不会主动到达 TaCZ 的第三人称持枪位置。

第一人称 `lefthand_pos` / `righthand_pos` 属于相机枪模空间，只在本地第一人称枪械渲染过程中存在，
不能直接作为世界第三人称或远端玩家目标。第三人称权威输入应是 TaCZ 已依据枪包
`thirdPersonAnimation`、ADS progress 和实体同步状态算出的原版左右臂最终几何。

## 目标语义

复用现有 Rust 双骨 IK 求解器，但不复用第一人称相机坐标：

```text
TaCZ thirdPersonAnimation 最终 ModelPart 姿态
-> 原版左右肩、肘、腕的局部持枪几何
-> 按当前 MMD 左右肩位置和各自臂长重建模型空间目标
-> 现有 TaczArmTargets 双骨 IK
-> MMD 手首/可选 Hand_Attach_L/R 到达对应持枪位置
```

目标重建必须保持以下约束：

1. 原版玩家固定像素尺寸只提供方向、屈肘比例和左右手相对布局，不直接作为 MMD 世界距离。
2. 每侧按当前 MMD 的上臂长、前臂长归一化，避免不同身高、肩宽模型出现不可达目标。
3. 肩根保持当前 VMD/基础动画位置；IK 只写上臂、肘和手首。
4. 手首 roll 默认保留 VMD 方向；TaCZ 原版 `ModelPart` 没有可直接等价为 MMD 手掌 roll 的语义。
5. 本地第三人称与远端玩家使用同一条实体同步状态路径，不读取本地第一人称 operator。

## 实现范围

1. Java `TaczThirdPersonPoseSampler`：由“相对上臂四元数”改为采样 TaCZ 最终左右腕链几何；继续通过
   反射保持 TaCZ 可选依赖，并保留 PlayerAnimator 独立动画的明确回退。
2. Java/native 数据包：第三人称提交归一化肩、肘、腕几何或已重建的模型局部目标；不得与第一人称
   瞬态相机矩阵混用。接口命名明确区分 `FIRST_PERSON_ANCHOR` 与 `THIRD_PERSON_GEOMETRY`。
3. Rust：复用 `tacz_arm_targets.rs` 的骨链缓存、目标校验、双骨解算和诊断，不再走
   `tacz_third_person_arms.rs` 的纯上臂旋转叠加。共享数学函数下沉到小模块，避免复制求解器。
4. 更新顺序：基础 VMD和普通动画评估完成后，按同一帧左右目标执行 IK，再生成蒙皮矩阵；目标包
   一次性消费，丢帧时回退 v4，不跨玩家或跨帧复用。
5. `ItemRenderHelper`：保留已经实机确认正确的主手 TaCZ 枪械局部 `Z +45°` 补偿。该补偿只修正枪体
   相对 `Hand_Attach_R` 的局部朝向，不参与双臂 IK 目标重建，也不按枪械分类重复叠加。

## 性能约束

1. 复用 `WorldRenderPolicy.Decision.shouldUpdate()`：只有当前世界动画更新帧才采样并提交第三人称目标；
   中远距离继续服从现有动画 LOD，不额外创建独立逐帧调度器。
2. Java 热路径不得逐帧扫描类、方法或字段。TaCZ 反射句柄初始化一次后缓存；每次采样只读取当前
   `ModelPart` 数值并写入固定长度数据包，避免流、装箱、临时集合和矩阵对象分配。
3. Rust 按模型实例缓存左右骨链索引、静止长度和挂点信息。骨名解析只在模型首次需要 TaCZ IK 时
   执行一次；每帧求解只使用栈上定长向量和四元数，不调用 `find_bone_by_name`。
4. 左右手数据合并为一次 JNI 提交和一次性消费；无有效目标时立即走 VMD，不进入 IK。单侧有效时只
   求解该侧，避免为缺失手臂执行完整双侧计算。
5. 正式路径不逐帧格式化诊断字符串或输出日志。详细误差、钳制距离等诊断仅在显式调试开关开启时
   生成，并采用限频输出。
6. 不增加逐帧网络同步。远端玩家继续消费 TaCZ 已同步的实体状态，并在本地渲染线程重建目标。

## TaCZ 版本兼容边界

1. 当前工程与构建产物仍限定 Minecraft 1.20.1，不宣称兼容其他 Minecraft 大版本。
2. 已用实际发布 JAR 核对 TaCZ 1.1.5 与 1.1.6-hotfix：两版均提供相同签名的
   `InnerThirdPersonManager.setRotationAnglesHead(...)`，且该入口内部负责按实体同步状态选择腰射或 ADS。
3. 第三人称采样只绑定 `getGunDisplay` 与上述完整姿态入口，不再额外依赖
   `ThirdPersonManager.getAnimation`、`getThirdPersonAnimation` 或 `animateGunHold`，减少版本耦合和重复计算。
4. PlayerAnimator 检测属于可选绑定；附加兼容类缺失时继续采样 TaCZ 原版手臂。核心入口缺失或签名
   变化时整条程序化手臂安全回退 VMD，不因反射异常中断渲染。
5. 1.1.5 与 1.1.6-hotfix 之外的 TaCZ 版本尚未实机验证，发布时必须把“接口安全回退”和“视觉兼容”
   分开记录。

## 验证门槛

1. Java 测试覆盖 TaCZ hold/ADS 几何采样、左右手缺失、反射失败、PlayerAnimator 回退和矩阵有限性。
2. Rust 测试覆盖不同 MMD 臂长的比例重建、可达目标、超距钳制、左右侧独立失败和单位旋转稳定性。
3. 本地第三人称腰射与 ADS：双腕到达 TaCZ 对应持枪位置，切枪、开火和换弹不闪烁或跨帧跳变。
4. 远端玩家：使用同步 ADS 状态得到一致姿势，不依赖本地第一人称相机节点。
5. 回归第一人称 IK、普通物品、库存、阴影、VR、未安装 TaCZ 的启动和 Forge/Fabric Mixin 审核。
6. 构建通过只证明接口与测试成立；最终仍需在水平视线下对比原版 TaCZ 第三人称和 MMD 第三人称。
7. 性能验收记录同屏多玩家时的采样次数、骨链缓存命中和 IK 执行次数；关闭诊断后不得出现逐帧
   TaCZ 日志，并确认中远距离实体严格服从现有动画 LOD。

审批后先实现“TaCZ 最终腕链几何 -> MMD 比例化目标 -> 现有 IK”的最小闭环，不在同一阶段增加
枪械分类 VMD、逐枪偏移或除已实机确认 `Z +45°` 以外的新固定角度补偿。

# TaCZ 第三人称与多人程序化手臂阶段（已审批）

## 已确认边界

本阶段把现有 TaCZ 双臂求解机制扩展到世界第三人称，并覆盖本地与远端玩家。第一人称
`lefthand_pos/righthand_pos` 捕获矩阵仍只属于第一人称渲染入口，不能直接用于世界渲染。
第三人称以 TaCZ 对实体最终计算出的 `PlayerModel` 手臂姿态为输入，远端玩家继续使用 TaCZ 已同步的
`getSynAimingProgress()`，不增加逐帧网络数据包。

TaCZ 标准第三人称数据没有通用左手护木末端锚点，`thirdperson_hand` 又是枪械对齐主手的节点。因此
本阶段不从枪械节点反推双手，也不猜每把枪的护木坐标：VMD 继续提供身体与基础握枪姿态，程序化层
只应用 TaCZ 已经权威计算出的手臂姿态；无法取得可靠一侧时，该侧保留 VMD。

## 实现结构

1. Forge/Fabric 使用可选 TaCZ Mixin，在 `InnerThirdPersonManager` 完成原版第三人称手臂动画后复制
   左右 `ModelPart` 的最终姿态参数；平台层只采集，不执行 native 求解。
2. Common 建立按玩家 UUID 保存的短生命周期姿态快照。快照带实体身份和消费生命周期，切枪、退出
   世界渲染或缺失本帧采样时不复用陈旧状态。
3. 玩家 MMD 世界渲染在动画状态更新后、native 模型更新前消费快照，将原版上臂局部旋转转换为
   独立 TaCZ 双臂姿态输入；本地与远端走同一条 Common 路径。
4. Rust 将骨链名称解析、索引、臂长和挂点偏移缓存到模型实例持有的独立模块，求解帧不再逐次
   `find_bone_by_name`。`runtime.rs` 只保留薄接线，不继续增加 TaCZ 算法。
5. 复用 `WorldRenderPolicy.Decision.shouldUpdate()`：近距离按正常更新频率应用，中距离随现有动画 LOD
   降频，未更新帧和远距离只保留 VMD，并清除一次性目标。
6. 第一人称详细矩阵和 24-float JNI 诊断仅保留为受限诊断能力，正式逐帧路径不读取或输出日志。

## 验证门槛

1. 单元测试覆盖快照按 UUID 隔离、单次消费、过期清理、缺少单侧姿态回退和非有限数据拒绝。
2. Rust 测试覆盖骨链缓存只解析一次、左右侧独立缺失、目标应用和模型实例销毁清理。
3. Common、Forge、Fabric 构建通过；TaCZ 未安装时可选 Mixin 不产生类加载硬依赖。
4. 游戏内分别验收本地第三人称、两个远端玩家、ADS/腰射切换、切枪、实体离开视野和远距离 LOD；
   构建成功与游戏内动作位置正确分别记录。

---

## 问题结论

已确认关闭 GPU 蒙皮后问题仍可复现，因此主因不在 GPU Compute Skinning 或 CPU/GPU 蒙皮矩阵分叉。

当前 PMX 眼球追踪只会旋转命名命中的左右眼骨。部分模型的瞳孔顶点并未绑定到这些眼骨，或通过四方向 Morph 驱动瞳孔移动，导致眼球整体转动时瞳孔区块保持不动。这应定义为“模型数据与现有追踪策略不匹配”的兼容性问题，而不是直接判定模型损坏。

## 改造目标

支持不同 PMX/VRM 眼睛制作方式，并让运行时能明确说明最终使用了哪种策略：

1. `Auto`：优先使用完整的方向 Morph；未找到有效 Morph 时回退到眼骨。
2. `Bone`：只使用眼骨旋转，保持当前模型的兼容行为。
3. `Morph`：只使用方向 Morph；缺失时报告不可用，不偷偷混用眼骨。

`Auto` 模式不得同时叠加眼骨与方向 Morph，避免双重运动造成瞳孔过度偏移。

## 实施范围

1. 新增 Rust 眼球追踪策略模块，承载策略选择、Morph 别名识别和诊断数据，避免继续扩展超长的 `rust_engine/src/model/runtime.rs`。
2. 调整 Rust 运行时：根据选定策略写入眼骨旋转或方向 Morph 权重，并保持 CPU/GPU 使用同一份最终蒙皮和 Morph 状态。
3. 扩展 JNI/Java 配置桥接：传递 `Auto / Bone / Morph` 选择并读取运行时诊断。
4. 在模型设置界面提供模式选择与当前检测结果，包含眼骨是否命中、四方向 Morph 是否完整、最终策略及不能自动适配时的原因。
5. 补齐中英文语言文本及聚焦单元测试。

## Morph 识别和回退

方向 Morph 采用别名表匹配，覆盖常见日文、中文、英文名称；仅在左、右、上、下四方向均有效时才视为完整方案。

`Auto` 的优先级为：

1. 找到完整方向 Morph：使用 Morph。
2. 未找到完整方向 Morph，且左右眼骨命中：使用 Bone。
3. 两者皆不可用：关闭眼球追踪效果并输出诊断，提示模型需要在 PMX 编辑器中修正瞳孔顶点权重或补齐方向 Morph。

## 验证

1. 单元测试覆盖策略选择、别名匹配、Morph 不完整回退和禁止 Bone/Morph 叠加。
2. 对现有骨骼驱动模型验证行为不回归。
3. 对 Morph 驱动模型验证瞳孔随视线移动。
4. 在 CPU 与 GPU 蒙皮两种设置下验证相同模型的追踪结果一致。

---

# 通用手持物品末端挂点与 TaCZ 朝向回退方案（待审批）

## 目标与边界

本方案建立在现有第一人称修复和 TaCZ ADS 动作接入之上，只调整物品挂载矩阵，不修改 VMD 四元数，也不恢复已经取消的 TaCZ 完整第一人称渲染流程。

第一阶段目标：

1. 普通物品默认放在左右手首的末端，而不是手首骨根部。
2. 模型存在可识别的手部 Dummy 骨时，优先使用该 Dummy 根部的完整全局矩阵。
3. TaCZ 枪械复用同一套挂点选择，并在通用挂点仍不匹配时叠加专用 `+45°` 局部旋转回退。
4. 不要求模型作者统一新增 `Hand_Attach_L/R`，但保留现有专用挂点作为可选候选。
5. 本阶段仅保证本地第一人称；第三人称、远端玩家和多人同步继续留在第二阶段。

## 坐标定义

“手首末端”只决定挂点位置，不能单独决定完整朝向，因为绕骨轴的 roll 仍缺少一个自由度。因此通用末端矩阵定义为：

```text
位置 = PMX 手首末端的当前动画后位置
旋转 = 手首当前全局旋转
缩放 = 单位缩放
```

若使用 Dummy，则直接采用 Dummy 根部当前动画后的完整全局矩阵，不再额外推导末端方向。

TaCZ 回退矩阵在选定挂点之后、Minecraft 物品显示变换之前叠加：

```text
最终矩阵 = 挂点矩阵 × TaCZ 局部修正矩阵 × 物品显示矩阵
```

`+45°` 必须明确为某个局部轴上的旋转。实施时不把屏幕空间角度直接写进骨骼或 VMD；先用可切换的 X/Y/Z 诊断候选确认正确轴和正负号，再固定默认值。

## 挂点选择顺序

左右手分别解析，不把左手名称用于主手：

```text
1. 模型配置显式指定的手持挂点（后续可配置入口，第一版可只保留解析接口）
2. 对应侧 Dummy 的严格别名匹配，并验证它位于对应手首的子树中
3. Hand_Attach_L / Hand_Attach_R
4. 对应手首的 PMX 末端
5. 对应手首骨根部
6. 对应手臂骨根部
7. 单位矩阵
```

Dummy 不使用模糊的全模型包含匹配，避免把头发、衣服或其他同名辅助骨误认为手部挂点。候选骨必须是对应手首的后代，并记录最终命中的挂点类型供诊断。

## PMX 末端数据

当前 `BoneLink` 没有保存 PMX 原始末端连接信息，不能可靠地从现有运行时结构直接恢复所有模型的手首末端。实现需要在 PMX 加载时保存以下二选一信息：

1. 骨骼通过索引连接到目标骨：保存目标骨索引，末端位置取目标骨当前全局位置。
2. 骨骼通过偏移定义末端：保存经过 MMD 左手系到引擎右手系转换后的局部末端偏移，并随手首当前全局旋转变换。

如果输入格式或模型没有有效末端数据，则按对应手首的直属子骨选择稳定候选；优先使用手掌/Dummy 类子骨，不以第一根手指的朝向直接替代手掌朝向。仍无法确定时回退手首根部。

## 预计改动职责

1. Rust 骨骼结构与 PMX loader：保存末端连接信息，并提供“指定侧手持挂点矩阵”的解析函数。新增独立小模块承载候选与末端计算，避免继续膨胀 `runtime.rs`。
2. JNI/Java 矩阵桥：让调用方能够取得解析后的挂点矩阵和挂点类型；原左右手矩阵接口保留兼容或在同一语义下内部升级。
3. `ItemRenderHelper`：普通物品使用通用挂点；识别到 TaCZ 枪械时叠加独立的局部旋转回退，不影响剑、工具和其他模组物品。
4. 测试：覆盖 Dummy 存在、Dummy 缺失、PMX 目标骨末端、PMX 偏移末端、无有效末端、TaCZ 与普通物品分流，以及左右手镜像选择。

所有新增代码使用中文注释。任何单文件接近 1000 行时拆分职责，不继续向超长文件堆叠逻辑。

## 验证顺序

1. 普通方块/工具：确认物品从手首根部移动到手掌末端，旋转不发生无关回归。
2. 有 Dummy 的截图模型：确认命中对应侧 Dummy 根部，并与手指动画同步。
3. 无 Dummy 的另一模型：确认自动回退到 PMX 手首末端，不坍缩到原点。
4. TaCZ A/B：依次测试无专用修正、局部轴 `+45°`、局部轴 `-45°`，在视线水平且 ADS 的固定场景截图比较。
5. 回归：确认先前修复的 VAO/EBO 状态污染不复发，动画方块、普通 MMD 动画和库存预览正常。
6. 通过后再构建 Fabric/Forge 1.20.1 正式包，并记录 JAR 哈希。

## 当前待审批项

认可“Dummy 根部优先，PMX 手首末端回退，TaCZ 再叠加局部 `+45°` 修正”的总体方案后开始实现。`+45°` 的具体局部轴需要通过一次诊断构建确认，不能仅凭斜向截图猜定。

---

# TaCZ 兼容与通用手持挂点模块化方案（待审批）

## 当前判断

测试截图显示枪械已经跟随新挂点，但枪身纵轴近似竖直，右手掌姿态也随挂点一起翻转。结合 `tacz_hold_rifle_ads_v3.vmd` 中 `Hand_Attach_R` 约 `Z +42°` 的关键帧，当前方向异常优先按动作/挂点局部旋转方向处理，不再额外添加代码侧固定 `+45°`。

Rust 当前新增的候选顺序是通用模型挂点能力，本身不依赖也不识别 TaCZ，因此不能命名为 `tacz.rs`。TaCZ 专属识别和动作选择继续留在 Java `compat/tacz` 层，防止 Rust 模型运行时与某个 Minecraft 模组耦合。

## 重构目标

1. 新建 `rust_engine/src/model/hand_attachment.rs`，承载左右手侧别、严格骨骼候选、矩阵解析以及聚焦单元测试。
2. `runtime.rs` 的 `get_right_hand_matrix()` / `get_left_hand_matrix()` 只保留薄委托，JNI 函数签名和 Java 调用方式保持不变。
3. 候选顺序保持当前测试版本不变：`Hand_Attach` → 对应侧 `ダミー` → 手首 → 手臂 → 英文别名。
4. Java 的 TaCZ 接口名反射识别继续由 `compat/tacz/TaczGunDetector.java` 独立负责；`AnimationStateManager` 只保留通用动画层切换，TaCZ 动作选择后续若继续增长，再拆为 `TaczAnimationController`，本次不为只有一个动作的逻辑过度拆分。
5. 新 VMD 由用户反转手持组件后另行导入并版本化；模块化提交不修改动作数值，避免同时改变代码结构和视觉基线。

## 文件范围

```text
新增 rust_engine/src/model/hand_attachment.rs
修改 rust_engine/src/model/mod.rs
修改 rust_engine/src/model/runtime.rs
```

只迁移本次加入的挂点解析函数和两项测试，不移动 `runtime.rs` 中其他历史功能，不做 PMX 末端数据结构改造。

## 验证与提交顺序

1. 重构前后运行同一组挂点优先级测试，矩阵结果必须一致。
2. 运行 `cargo fmt --check` 与 Rust 全量测试。
3. 运行 Common 单元测试以及 Fabric/Forge 1.20.1 构建。
4. 检查 diff 只表现为代码迁移和模块声明，不夹带 `.gradle-tacz`、`.tmp-tacz` 或旧 VMD。
5. 单独提交模块化重构；用户反转后的 VMD 作为下一次独立动作提交，便于必要时只回退动作。

---

# TaCZ 本地第一人称 ADS 同步方案（待审批）

## 源码核对结论

目标基线为 Minecraft 1.20.1。已核对 TaCZ 官方仓库固定提交
`b43eb84c38e9768d8e73c8b14f0b845669704b38` 以及对应 Fabric 移植仓库接口：

1. 本地玩家通过 `com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator`
   暴露 `isAim()` 和 `getClientAimingProgress(float partialTicks)`；后者返回已经按枪械
   `aimTime` 插值的 `0..1` 进度。
2. TaCZ 的第一人称枪模不是通过移动 Minecraft 世界相机来瞄准，而是在
   `FirstPersonRenderGunEvent` 中读取枪械机瞄节点、镜座节点和瞄具视点节点，计算其逆矩阵，
   再按 aiming progress 把枪模平滑移到相机中心。
3. 当前 MMDSkin 的 `ItemRenderHelper` 使用 `THIRD_PERSON_RIGHT_HAND` 将枪械挂到 MMD 手骨。
   这条路径不会执行 TaCZ 的第一人称瞄具定位，因此只靠一份通用 VMD 无法精确兼容所有枪械、
   内置机瞄和可更换瞄具。
4. 已验证的 `tacz_hold_rifle_ads_v4.vmd` 只负责人物举枪姿态和手部挂点方向；它不应承担
   每把枪的瞄具坐标，也不再加入固定 `+45°` 代码补偿。

## 第一阶段设计

仅处理本地玩家、桌面第一人称。第三人称、远端玩家、多人同步与 VR 保持原逻辑，留到第二阶段。

第一阶段的动作状态不是简单的“普通待机 / ADS”二态，而是明确拆成三类：

```text
普通物品状态
    ↓ 主手切换为 TaCZ 枪械
TaCZ 默认持枪状态（Hip Fire / 未瞄准）
    ↓ ADS 进度开始上升
TaCZ 瞄准状态（ADS）
```

TaCZ 默认持枪状态使用独立的自定义 VMD，不从 ADS 动作倒放或依赖代码旋转临时生成。默认动作负责
枪械处于腰射/预备位置时的肩、臂、肘和双手基础姿态；`tacz_hold_rifle_ads_v4.vmd` 继续只负责
举枪瞄准姿态。两份动作可以共用相同的 `Hand_Attach_R/L` 与 `ダミー.R/L` 约定，但不得把瞄具
相机位置写入这些手持挂点。

默认持枪动作采用版本化资源名，例如 `tacz_hold_rifle_idle_v1.vmd`。在用户提供并验证该动作前，
代码回退到当前普通物品持有姿态，而不是错误地常驻 ADS v4。第一阶段先实现步枪默认动作；手枪、
机枪等标签可以复用同一状态机，后续分别增加动作资源，不在第一版通过猜测骨骼角度自动派生。

引入一个无 TaCZ 编译期依赖的 `TaczFirstPersonCompat`：

1. 通过名称反射取得本地玩家实现的 `IClientPlayerGunOperator`，读取 `isAim()` 和
   `getClientAimingProgress(partialTicks)`；方法句柄按运行时类缓存，避免逐帧重复扫描。
2. 反射失败、TaCZ 未安装、接口版本不匹配或返回非有限数时，统一安全回退为“未瞄准、进度 0”，
   并且只记录一次诊断，不影响普通物品和游戏启动。
3. 动画状态只对“本地第一人称 + 主手 TaCZ 枪械”生效：未瞄准时使用自定义默认持枪动作；瞄准
   输入开始且 ADS 进度上升后切换到 v4；退出 ADS 后回到默认持枪，而不是回到普通物品待机。
   以 TaCZ 的进度决定 ADS 切换边界，不再以“只要持枪”作为 ADS 条件。
4. ADS 枪械与瞄具定位交还 TaCZ 原生第一人称渲染流程。MMDSkin 在该状态下只渲染 MMD 身体/手臂，
   跳过自身主手枪械绘制，避免同一把枪出现两份；副手及普通物品不受影响。
5. 不修改 `FirstPersonManager` 的眼部相机锚点，不把相机绑定到 `Hand_Attach_R`，也不读取或复制
   TaCZ 私有的 Bedrock 模型节点矩阵。TaCZ 继续负责 FOV、机瞄、瞄具倍率、瞄具视点切换及枪模插值。

## 分阶段落地与文件范围

先实现“ADS 状态同步”，再实现“渲染所有权切换”，两者分开提交和回归，避免再次把渲染故障与
动作问题混在一起。

第一提交预计：

```text
新增 common/src/main/java/com/shiroha/mmdskin/compat/tacz/TaczFirstPersonCompat.java
修改 common/src/main/java/com/shiroha/mmdskin/player/animation/AnimationStateManager.java
新增 common/src/main/resources/assets/mmdskin/default_anim/tacz_hold_rifle_idle_v1.vmd（动作提供后）
修改 common/src/main/java/com/shiroha/mmdskin/MmdClientResourceBootstrap.java（动作提供后）
新增 common/src/test/java/com/shiroha/mmdskin/compat/tacz/TaczFirstPersonCompatTest.java
修改 common/src/test/java/com/shiroha/mmdskin/player/animation/AnimationStateManagerTest.java
```

第二提交需要先确认现有第一人称手部渲染事件没有取消 TaCZ 原生事件，再选择最小加载器桥：

```text
修改 common/src/main/java/com/shiroha/mmdskin/player/render/ItemRenderHelper.java
必要时分别新增 fabric / forge 的薄事件桥或 Mixin（仅当现有钩子阻断 TaCZ 原生枪模时）
补充对应渲染决策单元测试
```

不会在本阶段修改 Rust、JNI、`runtime.rs`、VMD 内容或通用挂点优先级。`runtime.rs` 模块化仍作为
独立重构处理，不与 ADS 功能提交混合。

## 验证门槛

1. 未安装 TaCZ：Fabric/Forge 1.20.1 均能启动，普通物品和原动画行为不变。
2. 持枪未 ADS：播放自定义默认持枪动作，不播放 v4 ADS 动作，不发生枪械重复绘制。
3. 按下 ADS：从默认持枪平滑切换到 v4；机械瞄具由 TaCZ 原生定位到画面中心。
4. 安装并更换瞄具：瞄具视点和倍率继续由 TaCZ 数据驱动，无每枪硬编码偏移。
5. 松开 ADS：动作与枪模平滑退出并回到默认持枪，不闪现、不残留、不切换到错误手持方向。
6. 切走枪械：默认持枪层被清除，恢复普通物品/空手状态，不残留手臂姿态。
7. 回归动画方块、库存预览、第三人称和关闭第一人称模型的场景，确认不会重新污染 OpenGL 状态。

## 当前待审批项

审批后先只实现第一提交的 ADS 状态同步并构建测试包；通过后再检查和接入第二提交的渲染所有权切换。
若实际运行证明 TaCZ 原生第一人称枪模已被现有 MMDSkin 渲染钩子完全阻断，则停止继续猜测，先做
一份只记录事件触发和渲染次数的诊断构建，再决定 Fabric/Forge 的具体桥接点。

---

# TaCZ 枪械锚点驱动 MMD 第一人称与本地第三人称动作方案（已审批）

## 回归结论

`bbd68e9` 同时放行了 TaCZ 原生第一人称枪械，并保留原坐标空间中的完整 MMD 第一人称模型。实测中
TaCZ 瞄具能够正确对准屏幕中心，但 MMD 衣袖、手臂和身体大幅侵入镜头。这不是
`tacz_hold_rifle_ads_v4.vmd` 的朝向回归，而是两套互不关联的第一人称坐标空间被同时绘制。

短期撤回 TaCZ 原生渲染只能恢复旧基线，无法满足所有枪械和瞄具的数据驱动 ADS。最终职责改为：

```text
TaCZ 原生枪械：枪械动画、瞄具定位、ADS 插值、后坐力与左右手目标锚点
MMD VMD 动作：肩、上臂、肘、腕和身体的持枪/举枪姿态
同步桥：将动画后的 MMD 手部锚点对齐 TaCZ 本帧最终手部锚点
```

两者必须同时生效。TaCZ 锚点不能替代 MMD 动作，VMD 也不能替代每把枪的数据节点。

## 已核对的 TaCZ 1.20.1 调用链

以 `C:\tmp\tacz-1.20.1-source` 的 `1.20.1` 分支源码为准：

1. `GunItemRendererWrapper.renderFirstPerson` 更新枪械动画状态机。
2. 随后依次应用视角延滞、Bedrock 模型翻转、
   `FirstPersonRenderGunEvent.applyFirstPersonGunTransform` 的 Idle/机瞄/瞄具逆矩阵及动画约束。
3. `gunModel.render(...)` 遍历功能节点；`RightHandRender` 与 `LeftHandRender` 此时取得的
   `PoseStack` 已包含上述全部本帧变换。
4. 两个功能节点原本在对应位置绘制原版玩家手臂，因此它们比枪根节点更适合作为握把和护木的
   语义目标锚点。

不得复制 `getPositioningNodeInverse` 等 TaCZ 私有瞄具算法，也不得把枪根节点误当作握把位置。

## 第一人称同帧渲染设计

本地桌面第一人称且主手为 TaCZ 枪械时，普通玩家渲染阶段不再直接绘制未校正的完整 MMD 模型；
TaCZ 原生枪械流程继续运行。在 TaCZ 手部功能节点已经产生最终矩阵后，兼容桥取消对应的原版手臂，
并在同一帧触发 TaCZ 专用 MMD 绘制。

右手作为第一阶段的主约束，校正矩阵定义为：

```text
MMD 动画后右手锚点 = MmdRightHand
TaCZ 本帧右手目标 = TaczRightHand
模型校正矩阵 Δ = TaczRightHand × inverse(MmdRightHand)
最终 MMD 模型矩阵 = Δ × 原 MMD 模型矩阵
```

矩阵必须在同一 `PoseStack` 约定下完成坐标系转换，并校验所有分量有限、矩阵可逆、帧与当前本地玩家
及当前枪械一致。任何校验失败都不得复用陈旧矩阵或单位矩阵强行绘制。

左手功能节点在第一切片中至少需要被捕获并用于误差诊断。若现有 Rust 运行时没有稳定的局部左臂 IK
入口，则不在首个测试包中猜测骨骼旋转；先由 v4 负责左臂姿态。后续通过
`TaCZ_LeftHand × inverse(MMD_LeftHand)` 的误差验证，再决定加入左臂 IK 或受限骨链校正。

为避免明显的一帧延迟，正式路径优先采用同帧延后绘制，不采用“上一帧 TaCZ 矩阵驱动下一帧 MMD”。
若加载器或 TaCZ 功能节点无法提供稳定的同帧钩子，停止并输出诊断，不悄悄退化为跨帧同步。

## 动作状态与视角范围

动画状态分为普通物品、TaCZ 默认持枪和 TaCZ ADS。当前尚无独立默认持枪 VMD，因此首版在 ADS
进度为零时安全露出原普通持物动作；不得让 v4 常驻，也不得用倒放 v4 伪造默认动作。

本轮支持范围：

1. 本地第一人称：TaCZ 原生枪械渲染、v4 ADS 动作和手部锚点同步同时生效。
2. 本地第三人称：继续由 MMD 手部挂载枪械，但真实 TaCZ ADS 进度仍驱动 v4，使切换视角时动作连续。
3. 远端玩家与多人同步：保留第二阶段。不得用本地 `IClientPlayerGunOperator` 读取远端状态。
4. VR：保持现状，不进入本轮 TaCZ 桌面第一人称路径。

切枪、退出 ADS、关闭第一人称模型、TaCZ 未安装、接口反射失败或模型缺少手功能节点时，必须清除
当前 TaCZ 帧状态并恢复原 MMDSkin 行为，不能残留动画层权重或旧矩阵。

## 模块与文件职责

1. `common/compat/tacz`：保存 TaCZ 可选兼容的状态、矩阵快照与严格的本帧生命周期；保持无 TaCZ
   编译期硬依赖的公共调用边界。
2. Fabric/Forge：使用各自最薄的 Mixin 或事件桥接 TaCZ 1.20.1 的左右手功能节点；只负责采集最终
   `PoseStack`、抑制原版手臂并调用 Common 入口，不承载矩阵算法。
3. `PlayerModelRenderCoordinator`：只负责在本地 TaCZ 第一人称状态延后普通 MMD 绘制，并提供受控的
   专用绘制入口；普通物品、世界第三人称、库存和阴影 pass 不受影响。
4. `AnimationStateManager`：将本地第三人称纳入 TaCZ 动作状态，但远端玩家仍走原状态机。
5. 不修改 v4 VMD、Rust 通用挂点顺序、OpenGL EBO 状态修复和动画方块修复。

所有新增代码使用中文注释。`rust_engine/src/model/runtime.rs` 已超过 1000 行，本轮不得继续向其中加入
TaCZ 专属逻辑；若后续确需 Rust IK，必须先拆为独立模块。

## 分步实现与验证门槛

第一切片先建立同帧右手主锚定、原版手臂抑制、左手矩阵诊断和本地第三人称动作门控。通过后再决定
是否加入左臂 IK，避免把矩阵捕获、模型根校正和双臂求解一次混入同一测试包。

自动验证：

1. `:common:test` 覆盖本地第一/第三人称状态边界、切枪清理、矩阵有效性和 TaCZ 缺失回退。
2. `:fabric:build` 与 `:forge:build` 均通过，且产物不硬链接不存在的 TaCZ 类。
3. 检查 diff 不包含 `.gradle-tacz`、`.tmp-tacz`、临时计划、旧 VMD 或 Bullet 依赖目录。

游戏内验收：

1. 腰射时 TaCZ 枪械仍由原生流程显示，MMD 不以旧空间闯入镜头。
2. ADS 时瞄具保持数据驱动居中，v4 同时形成举枪姿态，右手稳定贴合握把。
3. 开火、移动、跳跃和切换倍率时，MMD 锚定继承 TaCZ 本帧变化且无一帧拖影。
4. 本地第三人称能看到 ADS 动作；切回第一人称时不闪现、不重复绘枪。
5. 切换至少两把不同尺寸步枪和一个可更换瞄具，确认没有每枪硬编码偏移。
6. 回归普通物品、关闭第一人称、库存、第三人称、动画方块和已有 OpenGL 修复。

---

# TaCZ 第一人称锚点渲染返工方案（待审批）

## 失败结论与废弃设计

首次测试包在拿起 TaCZ 枪械时触发 `MixinTransformerError`。实机日志确认，直接故障来自
`@ModifyArg` 使用 `Object`，而 TaCZ 1.1.6-hotfix 的真实参数描述符为
`IFunctionalRenderer`，Mixin 在 `RightHandRender` 延迟加载时拒绝应用。

即使只修正该描述符，原方案仍不可继续使用：它通过 TaCZ 的 `delegateRender` 回调同步进入完整
MMD native render，形成嵌套渲染；同时把完整人物网格当作手部渲染，无法可靠避免躯干和衣袖侵入
第一人称视锥。因此正式废弃以下设计：

1. 不再使用动态代理替换 `IFunctionalRenderer`。
2. 不再使用 `@ModifyArg` 接管 `delegateRender` 参数。
3. 不在 `RightHandRender`、`LeftHandRender` 或 TaCZ 枪体 batch 内调用完整 MMD render。
4. 不以“编译通过”代替 TaCZ 目标类实际加载时的 Mixin 验证。

## 新的渲染生命周期

返工采用“准备姿态、只采样、退出枪渲染、安全后置消费”的单向流程：

```text
MMDSkin 本地玩家第一人称准备阶段
  → 更新一次 VMD/物理并保存 prepared pose
  → TaCZ GunItemRendererWrapper.renderFirstPerson 开始
  → RightHandRender / LeftHandRender 只复制最终 PoseStack 矩阵
  → 对应节点通过可取消注入跳过 TaCZ 原版手臂，不绘制 MMD
  → TaCZ 枪械、瞄具及其 buffer/render state 全部完成
  → GunItemRendererWrapper.renderFirstPerson 返回点
  → 独立后置入口校验并消费本帧锚点
  → 只提交 MMD 第一人称所需网格
  → finally 清除本帧状态
```

Common 桥保存严格的一帧状态：本地玩家身份、当前枪物品标识、帧序号、右/左手矩阵、prepared pose
是否已消费。任何异常、节点缺失、切枪、切视角、VR、阴影或库存 pass 都立即清理；禁止上一帧矩阵
驱动下一帧，也禁止单位矩阵强行回退。

## 第一人称可见范围与手臂校正

源码复核确认，现有 `first_person_mesh.rs` 并不是“只保留双臂”的局部网格：它保留人物身体，仅剔除
头部，并按本帧视锥动态剔除靠近镜头的头颈边界。因此仍复用这条已经验证的第一人称全身可见路径，
但禁止用右手误差矩阵整体移动 MMD 模型，否则躯干会跟随枪械后坐力和瞄具定位一起移动。

第一人称改为双层驱动：

```text
MMD 模型根/躯干 = MMDSkin 现有第一人称镜头空间
MMD 左右臂骨链 = TaCZ 本帧 LeftHandRender / RightHandRender 目标
```

VMD v4 提供肩、肘、腕的基础持枪外形；TaCZ 的最终左右手矩阵只作为局部双臂目标。兼容层把两个目标
从 TaCZ 当前 `PoseStack` 转换回 MMD 模型局部空间，再在动画求值后、蒙皮矩阵生成前执行受限双臂
校正。右手对齐握把，左手对齐护木；身体、腿、头部和模型根不继承枪械节点变换。

现有 `NativeBoneOverridePort` 接收的是 VMD 局部平移/旋转覆盖，并且会跨帧保存，不能直接表达同帧的
相机空间手部目标。现有 VR IK 虽可参考骨链求解方式，但输入是世界空间头显/控制器，不能直接复用。
若诊断切片确认左右手矩阵稳定，则新增独立的 TaCZ 双臂目标模块和一次性 JNI 入口；不得继续向已超过
1000 行的 `runtime.rs` 堆入实现，也不得把 TaCZ 状态塞进通用 VPD 覆盖表。

## Mixin 边界与版本安全

Forge 和 Fabric 分别使用薄 Mixin，但不得在 Common 普通启动路径硬链接 TaCZ 类型：

1. 功能节点采用字符串 `targets`、`@Pseudo` 和允许缺失的注入约束，只复制 `PoseStack` 或取消原版手臂。
2. 后置消费钩子位于 `GunItemRendererWrapper.renderFirstPerson` 的返回点；它只能调用 Common 的安全入口。
3. 所有注入回调签名必须按实际 TaCZ 1.20.1 / 1.1.6-hotfix 字节码描述符核对，不能用 `Object`
   规避类型确认。
4. 构建后必须针对实际 TaCZ jar 运行 Mixin 审核或启动测试，确认目标类真正完成 transformation；
   仅执行 `compileJava`、`build` 不算通过。

## 动作规则（按最新试玩目标收敛）

ADS 与腰射不再使用两套 VMD 状态。只要任意 `AbstractClientPlayer` 的主手被识别为 TaCZ 枪械，且未
进入睡眠、受伤等更高优先级状态，就以完整权重循环播放
`tacz_hold_rifle_ads_v4.vmd`。本地第一人称、本地第三人称和远端玩家都使用同一通用持枪外形；切走
枪械时清理动画层并恢复普通物品逻辑。

真实 ADS progress 不再控制 VMD 的出现或权重。它仍由 TaCZ 原生第一人称渲染内部用于枪体、瞄具、
FOV、后坐力和镜头动画。远端玩家不得读取 `IClientPlayerGunOperator.fromLocalPlayer(...)`；第二阶段
再同步远端 ADS、开火、换弹等精确状态。

## 实现切片

切片 A：把当前尚未提交的本地 ADS progress 门控改为“所有持枪玩家恒定播放 v4”，并更新聚焦测试；
不接触渲染器。

切片 B：只实现一帧状态容器、TaCZ 左右手矩阵捕获、`renderFirstPerson` 返回点清理/诊断，以及原版手臂
抑制。此切片不绘制 MMD，用来验证 TaCZ 1.1.6-hotfix 的 Mixin 描述符、调用次数和帧生命周期。

切片 C：延后本地第一人称 MMD 绘制，复用既有去头第一人称网格；在 TaCZ 返回后的安全阶段消费左右
手目标，并通过独立的受限双臂求解模块校正手臂。不得整体移动模型根，也不得回退为功能节点内嵌套
整模渲染。

切片 D：游戏内通过两把步枪、瞄具切换、开火/换弹/跳跃和第一/第三人称切换后，再评估左手 IK。
默认持枪 VMD 仍作为独立资源任务，不用 ADS v4 倒放代替。

## 返工验证门槛

1. TaCZ 未安装时 Forge/Fabric 均可启动，optional Mixin 不造成类加载失败。
2. 安装 TaCZ 1.1.6-hotfix 后，实际拿枪能触发左右手目标类 transformation，无
   `MixinApplyError` 或 `InvalidInjectionException`。
3. 功能节点阶段只记录矩阵，不出现 MMD native render 调用。
4. 后置阶段每帧最多消费一次 prepared pose，所有退出路径清理状态。
5. 第一人称只显示预期手/前臂，不显示完整躯干，不重复绘枪；TaCZ 瞄具仍居中。
6. 本地第三人称和远端持枪玩家均以完整权重播放 v4，不读取本地 ADS operator。
7. 普通物品、库存、阴影、VR、动画方块以及既有 EBO/OpenGL 修复全部回归通过。

审批后先实施切片 B，并提供带诊断日志的测试包；切片 B 实机稳定后才进入局部网格与后置绘制，
不再把未经运行时验证的整套方案一次性交给用户测试。

# GPU 主路径、跨平台 SIMD 与 CPU 回退方案（待审批）

## 结论与目标

“大运算量交给 GPU、CPU 使用 SIMD”方向正确，但应按数据并行度划分职责，而不是把整套动画运行时
迁入计算着色器。现有工程已经有 OpenGL 4.3 Compute Shader 蒙皮、顶点 Morph 和 UV Morph，当前主要
问题是 GPU 路径仍在 CPU 物化完整 Morph 顶点、Morph 使用高显存的稠密布局、能力探测不足，以及
第一人称裁剪仍需要 CPU 顶点结果。

目标职责如下：

```text
CPU：动画采样与混合、Group/Flip 权重展开、骨骼层级、IK、Bullet 物理、材质 Morph
GPU：顶点/UV Morph 累加、顶点蒙皮、法线变换，以及后续可选的第一人称三角形分类
CPU 回退：与 GPU 同语义的 Morph + 蒙皮，自动向量化/SIMD，并保留标量实现
```

第一阶段不启用 `PhysicsComputeShader`。当前 GPU 物理只有 Verlet 弹簧近似实现，尚未证明与 Bullet 的
刚体、关节、碰撞和更新顺序等价；若物理结果还要回读 CPU 继续 IK，会引入同步停顿。它应作为后续
独立实验后端，不影响本方案落地。

## 后端能力与兼容矩阵

新增统一的运行时能力快照 `RenderComputeCapabilities`，在 OpenGL 上下文创建后探测一次，不用操作系统
名称推断 GPU 能力。至少记录 OpenGL/GLSL 版本、compute shader、SSBO、最大 SSBO 尺寸、最大绑定数、
最大 work group 数，以及着色器编译自检结果。

运行模式按以下优先级选择：

```text
AUTO
  1. OpenGL 4.3+ 且 compute/SSBO/尺寸/着色器自检全部通过 -> GPU_COMPUTE
  2. 否则 -> CPU_VECTORIZED
  3. 向量能力不可用或自检失败 -> CPU_SCALAR
```

平台预期而非硬编码规则：

1. Windows/Linux x86-64 通常进入 GPU Compute；CPU 回退至少有 SSE2，可在运行时选择 AVX2。
2. Windows/Linux ARM64 的 CPU 回退使用 NEON；不能把 Windows 等同于 SSE。
3. macOS 原生 OpenGL 通常仅到 4.1，不支持 OpenGL Compute Shader，默认进入 CPU 向量化回退。
4. Linux LoongArch64、RISC-V 等先依赖 `glam`/LLVM 自动向量化；没有经过验证的显式 SIMD 时走标量或
   自动向量化实现，禁止编译进 x86 intrinsic。
5. Android 当前继续禁用 GPU 蒙皮工厂，除非以后单独实现 OpenGL ES/Vulkan 后端。

配置由两个布尔开关收敛为 `AUTO / GPU_COMPUTE / CPU_VECTORIZED / CPU_SCALAR`。显式选择
`GPU_COMPUTE` 但能力不满足时仍安全回退，并只输出一次包含原因的诊断；不能因驱动或 shader 编译失败
导致模型不可见。旧配置在迁移时映射到新模式，保持用户配置兼容。

## GPU 主路径

### 1. 拆分 CPU Morph 语义

Rust 将当前 `update_morph_animation()` 拆为三个职责：

1. 展开 Group/Flip Morph，复用固定缓冲得到最终有效权重。
2. CPU 始终处理骨骼 Morph、材质 Morph和必要的运行时表达式。
3. 仅 CPU 回退或第一人称 CPU 裁剪确实需要时，才物化完整 `update_positions/update_uvs`。

GPU 模型的普通世界渲染每帧只跨 JNI 上传骨骼矩阵、有效 Morph 权重和材质结果，不再在 CPU 遍历并
重置全部顶点。所有上传使用 revision：骨骼、顶点 Morph、UV Morph、材质 Morph 各自独立标记，未变化
的数据不重复复制。

### 2. Morph 改为稀疏 GPU 数据

当前 `morph_count * vertex_count` 稠密数组会让显存和每顶点循环量同时快速增长。改为加载期建立稀疏
邻接布局：

```text
vertexMorphRanges[vertex] = {offset, count}
vertexMorphEntries[]       = {morphIndex, dx, dy, dz}
uvMorphRanges[vertex]      = {offset, count}
uvMorphEntries[]           = {morphIndex, du, dv}
effectiveWeights[]         = 当前帧权重
```

compute shader 每个 invocation 只遍历当前顶点实际关联的 Morph，不再扫描所有 Morph，也不再硬截断
顶点 Morph 128 个、UV Morph 32 个。权重判断统一为 `abs(weight) > epsilon`，修复当前 GPU 路径忽略负
顶点 Morph 权重的问题。若稀疏数据或 SSBO 超过驱动限制，该模型单独回退 CPU，不拖累其他模型。

### 3. 蒙皮与输出

单次 compute dispatch 完成 Morph、位置蒙皮、法线变换和 UV 输出。静态原始顶点、权重、索引与稀疏
Morph 数据只在模型创建时上传；每帧仅上传小型动态数据。输出 SSBO 直接作为 VBO 使用，禁止 GPU 到
CPU 回读。对同一更新 revision 的阴影、世界、描边等多个 pass 只 dispatch 一次。

首版保持每模型 dispatch，以降低改造风险；确认 CPU 提交成为瓶颈后，再评估把多个同布局实例批处理，
不在第一阶段引入全局大缓冲和复杂生命周期。

### 4. 第一人称处理

第一阶段保留现有 CPU 动态索引裁剪，但只对裁剪需要的 `dynamic_vertices` 计算 Morph 与蒙皮，不能因此
恢复整模型 CPU 顶点物化。稳定 body/head 索引预分段，仅重建边界三角形区间。

第二阶段可增加纯 GPU 三角形分类与 indirect draw，但前提是目标平台支持相应能力，且不能用 GPU
readback 获取索引数量。macOS 及不支持该能力的平台继续使用第一阶段 CPU 局部裁剪。

## CPU SIMD 回退

先保持一个可验证的标量参考实现，再提供向量化实现。公共入口在进程启动时选择一次函数表，帧循环内
不反复做 CPU feature detection：

```text
x86/x86-64：SSE2 基线；运行时检测 AVX2，支持时处理更宽批次
aarch64：NEON
其他架构：glam + LLVM 自动向量化；必要时标量
```

Rust 使用 `cfg(target_arch)` 隔离架构代码；x86 使用 `is_x86_feature_detected!("avx2")`，所有
`#[target_feature]` 函数由安全分派层调用。不得全局设置 `target-cpu=native`，否则发布产物可能在较老
CPU 上触发非法指令。Windows x86-64 的 SSE2 是架构基线，但 Windows ARM64 必须走 NEON 分支。

CPU 数据改为适合连续批处理的 SoA 或紧凑平铺布局，并按顶点块并行。Rayon 只在顶点数超过基准测得的
阈值时启用，避免小模型的线程调度成本；外层多模型并行和单模型内部 Rayon 不同时无限扩张。骨骼层级、
IK 和 Bullet 仍以正确性为先，先消除热路径 `clone()` 和堆分配，再评估局部数学的 SIMD 收益。

## 回退与故障隔离

回退必须按模型实例生效，而不是让一个异常模型关闭整局 GPU：

1. 创建模型前检查全局 GPU 能力；不满足则直接创建 CPU 实例。
2. 创建 GPU 资源时检查尺寸、GL error 和 shader 状态；失败则清理已创建资源，使用同一个 native model
   handle 创建 CPU 实例，避免重新解析模型。
3. 运行期出现 context loss、dispatch 错误或非有限输出诊断时，将实例标记为待重建，在安全帧边界切换
   CPU；渲染调用中不边画边替换资源。
4. CPU 向量实现启动时运行小规模参考自检；结果超差或 CPU 特性不匹配则退回标量。
5. HUD/日志显示实际后端和回退原因，例如 `GPU_COMPUTE`、`CPU_AVX2`、`CPU_SSE2`、`CPU_NEON`、
   `CPU_SCALAR`，便于收集不同平台问题。

## 预计模块边界

为避免继续扩大已超过 1000 行的 `runtime.rs`，实施时按职责拆分：

```text
rust_engine/src/model/morph_runtime.rs       CPU Morph 权重与选择性顶点物化
rust_engine/src/skinning/scalar.rs           标量参考实现
rust_engine/src/skinning/vectorized.rs       安全分派与通用向量路径
rust_engine/src/skinning/x86.rs              SSE2/AVX2（仅 x86 编译）
rust_engine/src/skinning/aarch64.rs          NEON（仅 aarch64 编译）
common/.../render/capability/                OpenGL 能力快照与选择策略
common/.../render/backend/gpu/               稀疏 Morph 缓冲、dispatch 与实例回退
common/.../render/shader/                     新 compute shader 和编译自检
```

JNI 新接口使用批量 DirectBuffer，不增加逐顶点调用。静态 GPU 数据只复制一次，动态权重和矩阵继续复用
已分配缓冲。已有 `RenderModeManager` 保留为工厂回退入口，但 `isAvailable()` 必须依赖真实 capability，
不能再只排除 Android 后就返回 true。

## 分阶段实施

阶段 A：建立基准与语义测试。固定若干无 Morph、正/负 Morph、Group/Flip、UV Morph、超 128 Morph、
不同骨权重和第一人称模型，记录 CPU 标量结果、帧时间、上传字节和显存。

阶段 B：先修 CPU 热路径。消除骨骼数组 clone、Morph 临时 Vec 和渲染临时对象；建立标量与
SSE2/AVX2/NEON 分派及一致性测试。该阶段本身即可改善所有平台和 GPU 回退。

阶段 C：重构 GPU Morph。引入稀疏布局、负权重和无硬上限 shader，拆开 CPU Morph 物化；GPU 普通世界
路径不再执行完整 CPU 顶点遍历。

阶段 D：能力探测与自动回退。接入 `AUTO` 模式、逐模型资源上限检查、失败原因和旧配置迁移；在
Windows/Linux/macOS 与 x86-64/ARM64 构建矩阵验证。

阶段 E：优化第一人称。先完成 CPU 局部边界裁剪和索引分段；有数据证明它仍是瓶颈后，再做 GPU
classification/indirect draw。GPU 物理继续作为独立 RFC，不并入本轮。

## 验收门槛

1. CPU 标量、各 SIMD 后端和 GPU 的位置/法线/UV结果在约定容差内一致；材质、骨骼、Group/Flip、
   正负权重和超过旧 shader 上限的模型均有覆盖。
2. GPU 普通世界路径的 CPU 顶点遍历为零，且同一 animation revision 多 pass 只 dispatch 一次。
3. GPU 初始化失败、SSBO 超限、shader 编译失败和显式关闭 GPU 均能自动显示 CPU 结果，不丢模型。
4. Windows x86-64 验证 AVX2 与 SSE2 强制回退；Windows ARM64/Linux ARM64 验证 NEON；macOS OpenGL
   4.1 验证 CPU 回退。LoongArch64/RISC-V 至少完成交叉编译和标量路径测试。
5. 基准至少覆盖 1/10/30 个模型、10k/50k/100k 顶点和不同 Morph 密度；分别报告 CPU update、JNI
   上传、GPU dispatch、GPU draw、RAM/VRAM 和 P95/P99 帧时间，不只比较平均 FPS。
6. `cargo test --lib`、Common 测试、Fabric/Forge 构建通过，并完成实际 Minecraft + Iris/常见驱动
   回归；图形驱动测试与单元测试分开记录。

审批后建议先实施阶段 A 与 B，再进入稀疏 GPU Morph。这样先得到跨平台、可对照的 CPU 基线，后续每次
GPU 改动都能检测数值或视觉回归，而不是直接替换整条渲染链。

## 调试 HUD 与渲染遥测补充

现有 `PerformanceHud` 已能展示模型、纹理、RAM 和 VRAM，`RenderPerformanceProfiler` 也已记录
`nativeModelUpdate`、骨骼/Morph 上传、Compute 提交和 Draw 的 CPU 墙钟时间。实施时复用这些入口，
补充无阻塞 GPU 计时、滑动窗口统计和后端诊断，不在 HUD 刷新时临时遍历或同步等待 GPU。

HUD 分为紧凑摘要和可展开明细，默认展示最近 120 帧的统计：

```text
Frame
  Frame time        current / avg / P95 / P99
  MMD CPU time      update / JNI+upload / submit / total
  MMD GPU time      morph+skinning / draw / total
  Visible models    count / vertices / draw calls / compute dispatches

Backend
  Active            GPU_COMPUTE / CPU_AVX2 / CPU_SSE2 / CPU_NEON / CPU_SCALAR
  CPU work          animation / bones+IK / physics / material morph / CPU skinning
  GPU work          vertex morph / UV morph / skinning+normal / raster draw
  Transfer          bytes uploaded per frame / avoided uploads / readback bytes
```

“每帧渲染用时”需区分整帧与 MMD 自身开销。整帧时间在客户端帧边界用单调时钟采样；MMD CPU 时间只覆盖
本模组动画更新、JNI、上传和 GL 命令提交，不能标成整个 Minecraft 的 CPU 渲染时间。GPU 时间使用
OpenGL timer query，分别包围 Morph+Skinning Compute 和实际 Draw；查询结果延迟若干帧异步读取，维护
查询对象环形池。结果尚未就绪时沿用上一份样本，禁止调用会阻塞渲染线程的等待或 `glFinish()`。

骨骼、UV 等“占用 CPU/GPU 比重”不显示成一个看似能严格相加到 100% 的百分比，因为 CPU 与 GPU 可并行，
CPU 提交时间也不代表 GPU 执行时间。HUD 同时提供两种口径：

1. `CPU stage share`：各 CPU 阶段耗时占 MMD CPU 总耗时的比例，来源于真实墙钟采样。
2. `GPU stage share`：Compute 与 Draw 占 MMD GPU 总耗时的比例，来源于 timer query。
3. `Work placement`：骨骼、UV、Morph、蒙皮当前在哪个后端执行，以标签或分段条显示；这是职责分布，
   不伪装成硬件利用率。

骨骼项进一步拆成 CPU 动画采样、层级/IK/物理和 GPU 骨骼矩阵消费；UV 项拆成 CPU 权重准备/上传和 GPU
稀疏 UV Morph。只有支持 timer query 的平台显示 GPU 分段耗时，不支持或 query 失败时显示 `N/A`，不以
CPU dispatch 时间冒充 GPU 时间。GPU 全局利用率、显存带宽和功耗依赖厂商接口，不作为跨平台核心指标。

统计实现使用固定大小无分配环形缓冲，保存 current、EMA、P95 和 P99；P95/P99 低频计算并随 HUD 的
500ms 刷新节奏更新，渲染热路径只累计原始纳秒和计数。Profiler 关闭且 HUD 隐藏时保持近零开销；HUD
打开时启用轻量采样，详细 GPU 分段计时可单独配置采样频率，避免每个模型、每个 pass 都创建查询对象。

新增验收要求：HUD 开关前后进行基准对照；关闭时性能变化在噪声范围内，打开摘要时 CPU 开销目标低于
0.1ms/帧，且不得产生 GPU 同步停顿。CPU/GPU 强制回退测试必须同步更新后端标签和阶段列表；多 pass、
多模型场景中的计数和 revision 去重一致。最终以 RenderDoc/厂商 profiler 抽样核对 timer query 区间，
确保 HUD 数值没有重复计时或漏计。
