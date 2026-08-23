// 负责按骨骼名称设置/清除姿势覆盖（乐器演奏等程序化姿势入口）。
// include! 进 runtime_bindings.rs，共享其 use（MODELS / jni 类型等）。

// 设置单个骨骼姿势覆盖（按名称）。
// 与 ApplyVpdMorph 共用 vpd 覆盖管线：每帧动画评估后、物理之前应用，
// 因此会压过 VMD 动画层，同时不影响头发/裙摆物理。
// translation/rotation 为该骨骼局部空间增量（rotation 为 xyzw 四元数）。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetBoneOverrideByName(
    env: JNIEnv,
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
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        if let Some(idx) = model.bone_manager.find_bone_by_name(&name) {
            let translation = glam::Vec3::new(tx, ty, tz);
            let rotation = glam::Quat::from_xyzw(qx, qy, qz, qw).normalize();
            model.set_vpd_bone_override(idx, translation, rotation);
            return 1;
        }
    }
    0
}

// 清除全部骨骼姿势覆盖（停止演奏等场景调用）。
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ClearBoneOverrides(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.clear_vpd_bone_overrides();
    }
}
