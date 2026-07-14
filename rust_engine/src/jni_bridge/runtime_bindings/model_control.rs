// 负责模型变换、视线、眨眼与基础控制入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetGpuSkinningEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) -> jboolean {
    catch_bridge(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(model)?)?;
        let mut model = model.lock()?;
        Ok(model.set_gpu_skinning_enabled(enabled != 0))
    })
    .unwrap_or(false) as jboolean
}


// 设置模型位置和朝向（简化版，用于惯性计算）
// pos_x, pos_y, pos_z: 模型位置（已缩放）
// yaw: 人物朝向角度（弧度）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetModelPositionAndYaw(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    pos_x: jfloat,
    pos_y: jfloat,
    pos_z: jfloat,
    yaw: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_model_position_and_yaw(pos_x, pos_y, pos_z, yaw);
    }
}

// 设置头部角度

// 设置眼球追踪角度（眼睛看向摄像头）
// eye_x: 上下看的角度（弧度，正值向上）
// eye_y: 左右看的角度（弧度，正值向左）

// 设置眼球最大转动角度
// max_angle: 最大角度（弧度），默认 0.35（约 20 度）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetEyeMaxAngle(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    max_angle: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_eye_max_angle(max_angle);
    }
}

// 启用/禁用眼球追踪
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetEyeTrackingEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_eye_tracking_enabled(enabled != 0);
    }
}

// 获取眼球追踪是否启用

// 启用/禁用自动眨眼
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetAutoBlinkEnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_auto_blink_enabled(enabled != 0);
    }
}

// 获取自动眨眼是否启用

// 设置眨眼参数
// interval: 眨眼间隔（秒），默认 4.0
// duration: 眨眼持续时间（秒），默认 0.15

// ============================================================================
// 动画层控制函数（新增）
// ============================================================================

// 播放指定层的动画
// layer: 动画层ID（0-3）
