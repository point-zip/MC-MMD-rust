// 负责模型物理状态与全局物理配置入口。


// 重置物理系统

// 启用/禁用物理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetPhysicsEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_physics_enabled(enabled != 0);
    }
}

// 获取物理是否启用

// 获取物理是否已初始化

// 获取物理调试信息（返回 JSON 字符串）

// ============================================================================
// 材质可见性控制（用于脱外套等功能）
// ============================================================================

// 获取材质是否可见
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetPhysicsConfig(
    _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
    gravity_y: jfloat,
    physics_fps: jfloat,
    max_substep_count: jint,
    inertia_strength: jfloat,
    max_linear_velocity: jfloat,
    max_angular_velocity: jfloat,
    joints_enabled: jboolean,
    kinematic_filter: jboolean,
    debug_log: jboolean,
) {
    use crate::physics::config::{set_config, PhysicsConfig};

    let config = PhysicsConfig {
        enabled: enabled != 0,
        gravity_y,
        physics_fps,
        max_substep_count,
        inertia_strength,
        max_linear_velocity,
        max_angular_velocity,
        joints_enabled: joints_enabled != 0,
        kinematic_filter: kinematic_filter != 0,
        debug_log: debug_log != 0,
    };

    set_config(config);

    if debug_log != 0 {
        log::info!(
            "[Bullet3 物理配置] 重力={}, FPS={}, 惯性={}",
            gravity_y,
            physics_fps,
            inertia_strength
        );
    }
}

// ========== 第一人称模式相关 ==========

// 设置第一人称模式（启用时自动隐藏头部子网格，禁用时恢复）
