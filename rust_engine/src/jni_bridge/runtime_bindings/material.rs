// 负责材质可见性与材质目录查询入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_IsMaterialVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_material_visible(index as usize) {
            1
        } else {
            0
        }
    } else {
        1 // 默认可见
    }
}

// 设置材质可见性
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetMaterialVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
    visible: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_material_visible(index as usize, visible != 0);
    }
}

// 根据材质名称设置可见性（支持部分匹配，返回匹配的材质数量）

// 设置所有材质可见性
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetAllMaterialsVisible(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    visible: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_all_materials_visible(visible != 0);
    }
}

// 获取材质名称
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetMaterialName(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(name) = model.get_material_name(index as usize) {
            if let Ok(s) = env.new_string(name) {
                return s.into_raw();
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// 获取所有材质名称（JSON 数组格式）

// 骨骼与 VPD 姿态入口。

// 获取骨骼数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetBoneCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jint {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().bone_manager.bone_count() as jint)
        .unwrap_or(0)
}

// 应用 VPD 中的 Morph 与骨骼姿态，返回两者的应用数量。
