// 负责 Morph 目录、权重和 VPD 应用入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ApplyVpdMorph(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    filename: JString,
) -> jint {
    use crate::animation::VpdFile;

    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return -1,
    };

    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();

        match VpdFile::load(&filename_str) {
            Ok(vpd) => {
                let mut morph_count = 0i32;
                let mut bone_count = 0i32;

                // 1. 应用 Morph 表情
                for morph_data in &vpd.morphs {
                    if let Some(idx) = model.morph_manager.find_morph_by_name(&morph_data.name) {
                        model.morph_manager.set_morph_weight(idx, morph_data.weight);
                        morph_count += 1;
                    }
                }

                // 2. 设置 VPD 骨骼姿势覆盖（会在每帧动画评估后自动应用）
                model.clear_vpd_bone_overrides();
                for bone_data in &vpd.bones {
                    if let Some(idx) = model.bone_manager.find_bone_by_name(&bone_data.name) {
                        model.set_vpd_bone_override(idx, bone_data.translation, bone_data.rotation);
                        bone_count += 1;
                    }
                }

                // 返回编码值: 高16位骨骼数，低16位 Morph 数
                return ((bone_count & 0xFFFF) << 16) | (morph_count & 0xFFFF);
            }
            Err(_) => {
                return -1;
            }
        }
    }
    -2
}

// 重置所有 Morph 权重和 VPD 骨骼姿势覆盖
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ResetAllMorphs(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.morph_manager.reset_all_weights();
        model.clear_vpd_bone_overrides();
    }
}

// 设置单个 Morph 权重（通过名称）

// 获取 Morph 数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetMorphCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        return model.morph_manager.morph_count() as jlong;
    }
    0
}

// 获取 Morph 名称（通过索引）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetMorphName(
    env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
) -> jstring {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if let Some(morph) = model.morph_manager.get_morph(index as usize) {
            if let Ok(s) = env.new_string(&morph.name) {
                return s.into_raw();
            }
        }
    }
    env.new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

// 获取 Morph 权重（通过索引）

// 设置 Morph 权重（通过索引）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetMorphWeight(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    index: jint,
    weight: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.morph_manager.set_morph_weight(index as usize, weight);
    }
}

// 物理运行时配置入口。

// 设置全局物理配置（Bullet3，实时调整）
