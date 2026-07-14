# MC-MMD 领域上下文

本文档固定 1.21.5 客户端渲染重构使用的领域语言。代码、测试和 ADR 应使用这些名称，避免同一概念出现多套称呼。

## 核心概念

**Model Key**

一个模型实例的结构化身份，由模型资源标识、实例所有者和使用场景组成。它不是字符串拼接出的缓存键。

**Model Instance**

一次可独立播放和渲染的模型生命周期。它拥有 native model handle、动画状态以及对应的 GPU 资源，并负责确定性释放。Model Instance 不负责选择玩家模型或决定是否替换原版渲染。

**Model Lease**

Model Repository 发出的帧级或会话级租约。持有租约的调用方可以使用 Model Instance；最后一个租约释放后，Repository 才能回收实例。

**Render Snapshot**

完成原版 `EntityRenderState` 提取后创建的不可变帧数据。它只保存 UUID、Model Key、姿态、变换、可见性、发光和渲染上下文，不保存实体对象或可复用的原版 render state。

**Frame Update**

一个 Model Instance 在一个 Minecraft 渲染帧内唯一的一次动画、Morph、物理和 VR 输入推进。不同 draw pass 共享同一次更新结果。

**Draw Request**

把 Model Instance 的某个 Render Snapshot 提交到指定 pipeline 变体的不可变绘制请求。它不触发动画或物理更新。

**Frame Queue**

实体、第一人称和场景 Module 提交 Draw Request 的有序队列。世界实体阶段结束后统一 flush；物品栏预览使用独立的局部 flush。

**Render Data**

Rust CPU 更新后提供给 Java 的受检数据：CPU 回退使用 28 字节交错顶点，GPU 蒙皮使用 36 字节 palette 顶点；另包含静态索引、`f32 mat4` 骨骼 palette、静态网格描述和紧凑 draw command。Render Data 不包含裸指针或 GPU handle。

**Pipeline Variant**

不可变的 `RenderPipeline` 配置组合，表达透明、双面、Toon 主通道、Toon 描边和发光等 GPU 状态差异。

**Loader Adapter**

Fabric 或 NeoForge 在加载器事件、Mixin 和资源注册点上的实现。Loader Adapter 只把平台事件映射到公共运行时 Interface，不拥有模型或 GPU 生命周期。

**Compatibility Fallback**

Iris 阴影 pass、Vivecraft/YSM 冲突、pipeline 失败或模型未就绪时继续原版渲染的确定性结果。Fallback 不得留下半提交的队列项或修改全局 GPU 状态。

## Module 所有权

- `MmdClientRenderRuntime` 是客户端 composition root，实例化并组合各 Module。
- `Model Repository` 拥有异步模型加载、失败退避、Model Lease 和 native handle 生命周期。
- `Animation Repository` 拥有动画资源加载、去重和租约。
- `Texture Repository` 拥有 RGBA8 解码结果对应的 GPU texture、引用租约和 TTL/LRU。
- `Frame Updater` 保证每个 Model Instance 每帧最多推进一次。
- `Frame Queue` 负责排序和批量提交，不负责创建 Model Instance。
- `Native Bridge` 是所有 JNI/native 调用的唯一 Seam。
- `GPU Draw Adapter` 是 `GpuBuffer`、`GpuTexture`、`RenderPipeline`、`CommandEncoder` 和 `RenderPass` 的唯一所有者。

## 不变量

- native 解析只在有界工作线程执行；GPU 创建、上传和关闭只在渲染线程执行。
- 静态 mesh/index/material 数据每个 Model Instance 只读取一次。
- 动态顶点仅在 native revision 改变时上传，每个 revision 最多上传一次。
- 一个 Render Pass 打开期间不执行 buffer 或 texture 上传。
- Render Snapshot 不跨帧引用实时实体或原版 render state。
- JNI 不返回 Rust 集合的裸地址，不接受未校验容量的写目标。
- 接管原版主体后仍保留名称标签、拴绳、手持物以及 dispatcher 负责的效果。
