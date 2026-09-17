// 负责按骨骼名称设置/清除乐器演奏姿势覆盖，并提供静息方向查询。
// include! 进 runtime_bindings.rs，共享其 use（MODELS / jni 类型等）。

// 设置单个骨骼姿势覆盖（按名称，乐器通道）。
// 每帧动画评估后、物理之前应用，因此会压过 VMD 动画层，同时不影响头发/裙摆物理；
// 写入时会禁用控制该骨骼的 IK 解算器，避免腕IK 等把覆盖结果拉回。
// translation/rotation 为该骨骼局部空间增量（rotation 为 xyzw 四元数）。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetBoneOverrideByName(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    bone_name: JString,
    tx: jfloat,
    ty: jfloat,
    tz: jfloat,
    qx: jfloat,
    qy: jfloat,
    qz: jfloat,
    qw: jfloat,
) -> jboolean {
    let name: String = match env.get_string(&bone_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    if !qw.is_finite() || !qx.is_finite() || !qy.is_finite() || !qz.is_finite() {
        return 0;
    }
    let translation = glam::Vec3::new(tx, ty, tz);
    let rotation = glam::Quat::from_xyzw(qx, qy, qz, qw).normalize();
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        if let Some(idx) = model.bone_manager.find_bone_by_name(&name) {
            model.set_instrument_bone_override(idx, translation, rotation);
            return 1;
        }
    }
    0
}

// 清除全部乐器演奏姿势覆盖（停止演奏等场景调用）。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ClearBoneOverrides(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.clear_instrument_bone_overrides();
    }
}

// 查询"骨骼A → 骨骼B"的静息方向（模型空间单位向量），写入 out[3]。
// 用于程序化姿势映射：静息方向因模型而异（T-pose / A-pose），不能硬编码。
// 返回 1 表示成功；0 表示骨骼缺失或两点重合。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetBoneRestDirection(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    from_name: JString,
    to_name: JString,
    out: jni::objects::JFloatArray,
) -> jboolean {
    let from: String = match env.get_string(&from_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let to: String = match env.get_string(&to_name) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(dir) = model.bone_rest_direction(&from, &to) {
            let buf: [f32; 3] = [dir.x, dir.y, dir.z];
            let _ = env.set_float_array_region(&out, 0, &buf);
            return 1;
        }
    }
    0
}
