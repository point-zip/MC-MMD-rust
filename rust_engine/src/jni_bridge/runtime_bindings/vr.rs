// 负责 VR 追踪数据、启停和 IK 参数入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetVRTrackingData(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    tracking_data: jni::objects::JFloatArray,
) {
    let mut buf = [0.0f32; 21];
    if env
        .get_float_array_region(&tracking_data, 0, &mut buf)
        .is_err()
    {
        return;
    }
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.set_vr_tracking_data(&buf);
    }
}

// 启用/禁用 VR 模式

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetVREnabled(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    enabled: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.set_vr_enabled(enabled != 0);
    }
}

// 设置 VR IK 参数
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetVRIKParams(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    arm_ik_strength: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut m = model_arc.lock().unwrap();
        m.set_vr_ik_strength(arm_ik_strength);
    }
}

// 设置 VR 手部渲染模式（0=全身, 1=仅左手, 2=仅右手）

// 加载 VRM 模型
