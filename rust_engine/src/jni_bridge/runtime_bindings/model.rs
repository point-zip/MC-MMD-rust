// 负责模型加载、基础查询与 VRM 类型识别。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(VERSION) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

// ============================================================================
// 模型相关函数
// ============================================================================

// 加载 PMX 模型
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_LoadModelPMX(
    mut env: JNIEnv,
    _class: JClass,
    filename: JString,
    _dir: JString,
    _layer_count: jlong,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match load_pmx(&filename_str) {
        Ok(mut model) => {
            // 自动初始化物理系统
            if !model.rigid_bodies.is_empty() {
                log::info!(
                    "模型包含 {} 个刚体, {} 个关节, 自动初始化物理",
                    model.rigid_bodies.len(),
                    model.joints.len()
                );
                model.init_physics();
            }
            register_model(model)
        }
        Err(e) => {
            log::error!("Failed to load PMX: {}", e);
            0
        }
    }
}

// 加载 PMD 模型（暂不支持）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_LoadModelPMD(
    _env: JNIEnv,
    _class: JClass,
    _filename: JString,
    _dir: JString,
    _layer_count: jlong,
) -> jlong {
    log::warn!("PMD format not supported yet");
    0
}

// 删除模型

// 更新模型
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_UpdateModel(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    delta_time: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        // 更新动画（内部已包含物理更新）
        model.tick_animation(delta_time);
    }
}

// ============================================================================
// 顶点数据函数
// ============================================================================

// 获取顶点数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetVertexCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().vertex_count() as jlong)
        .unwrap_or(0)
}

// ============================================================================
// 索引数据函数
// ============================================================================

// 获取索引元素大小

// 获取索引数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetIndexCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().index_count() as jlong)
        .unwrap_or(0)
}

// ============================================================================
// 材质相关函数
// ============================================================================

// 获取材质数量
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetMaterialCount(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jlong {
    let models = MODELS.read().unwrap();
    models
        .get(&model)
        .map(|m| m.lock().unwrap().material_count() as jlong)
        .unwrap_or(0)
}

// layer: 动画层ID（0-3），0为基础层，1-3为叠加层
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_LoadModelVRM(
    mut env: JNIEnv,
    _class: JClass,
    filename: JString,
    _dir: JString,
    _layer_count: jlong,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match load_vrm(&filename_str) {
        Ok(model) => register_model(model),
        Err(e) => {
            log::error!("Failed to load VRM: {}", e);
            0
        }
    }
}

// 查询模型是否为 VRM 格式
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_IsVrmModel(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(m) = models.get(&model) {
        let m = m.lock().unwrap();
        if m.is_vrm() {
            1
        } else {
            0
        }
    } else {
        0
    }
}
