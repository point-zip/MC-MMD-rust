// 负责动画资源、舞台检查、相机轨与合并入口。

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ChangeModelAnim(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    anim: jlong,
    layer: jlong,
) {
    // 先读取动画并释放锁，避免同时持有 MODELS+ANIMATIONS 双锁
    let anim_opt = {
        let animations = ANIMATIONS.read().unwrap();
        animations.get(&anim)
    };
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let layer_id = layer as usize;
        model.set_layer_animation(layer_id, anim_opt);
        model.play_layer(layer_id);
    }
}

// 重置物理
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_ResetModelPhysics(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.reset_physics();
    }
}

// 加载动画
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_LoadAnimation(
    mut env: JNIEnv,
    _class: JClass,
    model_handle: jlong,
    filename: JString,
) -> jlong {
    let filename_str: String = match env.get_string(&filename) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    // 支持 "path.fbx#StackName" 语法选择指定 AnimationStack
    let (file_path, stack_name) = if let Some(pos) = filename_str.rfind('#') {
        let lower = filename_str[..pos].to_ascii_lowercase();
        if lower.ends_with(".fbx") {
            (&filename_str[..pos], Some(&filename_str[pos + 1..]))
        } else {
            (filename_str.as_str(), None)
        }
    } else {
        (filename_str.as_str(), None)
    };

    let lower = file_path.to_ascii_lowercase();
    let is_fbx = lower.ends_with(".fbx");

    // FBX 手臂校正需要模型骨骼位置，先提取后释放 MODELS 锁
    let arm_positions = if is_fbx {
        let models = MODELS.read().unwrap();
        if let Some(model_arc) = models.get(&model_handle) {
            let model = model_arc.lock().unwrap();
            let mut pos = std::collections::HashMap::new();
            for name in &[
                "左肩",
                "左腕",
                "左ひじ",
                "左手首",
                "右肩",
                "右腕",
                "右ひじ",
                "右手首",
            ] {
                if let Some(idx) = model.bone_manager.find_bone_by_name(name) {
                    if let Some(bone) = model.bone_manager.get_bone(idx) {
                        pos.insert(name.to_string(), bone.initial_position);
                    }
                }
            }
            pos
        } else {
            std::collections::HashMap::new()
        }
    } else {
        std::collections::HashMap::new()
    };

    let result = if is_fbx {
        // 使用 FBX 缓存避免重复解析大文件
        let cache = {
            let cache_map = FBX_CACHE.read().unwrap();
            cache_map.get(file_path)
        };
        let cache = match cache {
            Some(c) => c,
            None => match fbx_loader::FbxCache::load(file_path) {
                Ok(c) => {
                    let arc = Arc::new(c);
                    let mut cache_map = FBX_CACHE.write().unwrap();
                    cache_map.insert(file_path.to_string(), arc.clone());
                    arc
                }
                Err(e) => {
                    log::error!("FBX 解析失败: {}", e);
                    return 0;
                }
            },
        };
        cache.load_animation(stack_name).map(|mut anim| {
            fbx_loader::apply_arm_retarget_correction_with_reference(
                &mut anim,
                &arm_positions,
                Some(cache.arm_reference_dirs()),
            );
            register_animation(anim)
        })
    } else {
        VmdFile::load(file_path).map(|vmd| register_animation(VmdAnimation::from_vmd_file(vmd)))
    };

    match result {
        Ok(handle) => handle,
        Err(e) => {
            log::error!("Failed to load animation: {}", e);
            0
        }
    }
}

// 预加载 FBX 文件到缓存（避免首次加载动画时阻塞）

// 列出 FBX 文件中所有 AnimationStack 名称（JSON 数组）

// 清除 FBX 文件缓存

// 删除动画

// 查询动画是否包含相机数据
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_HasCameraData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_camera() {
            1u8
        } else {
            0u8
        }
    } else {
        0u8
    }
}

// 获取动画最大帧数（包含相机轨道）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetAnimMaxFrame(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jfloat {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        animation.max_frame() as jfloat
    } else {
        0.0
    }
}

// 获取相机变换数据，写入 ByteBuffer
// 布局: pos_x, pos_y, pos_z (3×f32) + rot_x, rot_y, rot_z (3×f32) + fov (f32) + is_perspective (i32) = 32 字节
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_GetCameraTransform(
    env: JNIEnv,
    _class: JClass,
    anim: jlong,
    frame: jfloat,
    buffer: JByteBuffer,
) {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        let transform = animation.get_camera_transform(frame);

        let dst = match env.get_direct_buffer_address(&buffer) {
            Ok(p) => p,
            Err(_) => return,
        };
        let capacity = env.get_direct_buffer_capacity(&buffer).unwrap_or(0);
        if capacity < 32 {
            log::error!("GetCameraTransform: 缓冲区容量 {} < 32 字节", capacity);
            return;
        }
        unsafe {
            let ptr = dst as *mut f32;
            // position (3 × f32)
            *ptr.add(0) = transform.position.x;
            *ptr.add(1) = transform.position.y;
            *ptr.add(2) = transform.position.z;
            // rotation (3 × f32, 欧拉角弧度)
            *ptr.add(3) = transform.rotation.x;
            *ptr.add(4) = transform.rotation.y;
            *ptr.add(5) = transform.rotation.z;
            // fov (f32)
            *ptr.add(6) = transform.fov;
            // is_perspective (i32, 0/1)
            let i_ptr = ptr.add(7) as *mut i32;
            *i_ptr = if transform.is_perspective { 1 } else { 0 };
        }
    }
}

// 查询动画是否包含骨骼关键帧
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_HasBoneData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_bones() {
            1u8
        } else {
            0u8
        }
    } else {
        0u8
    }
}

// 查询动画是否包含表情关键帧
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_HasMorphData(
    _env: JNIEnv,
    _class: JClass,
    anim: jlong,
) -> jboolean {
    let animations = ANIMATIONS.read().unwrap();
    if let Some(animation) = animations.get(&anim) {
        if animation.has_morphs() {
            1u8
        } else {
            0u8
        }
    } else {
        0u8
    }
}

// 将 source 动画的骨骼和 Morph 数据合并到 target 动画中
// 实现方式：克隆 target → 合并 source → 替换回 HashMap
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_MergeAnimation(
    _env: JNIEnv,
    _class: JClass,
    target: jlong,
    source: jlong,
) {
    // 先读取并克隆两个动画，避免借用冲突
    let (target_clone, source_ref) = {
        let animations = ANIMATIONS.read().unwrap();
        let t = animations.get(&target);
        let s = animations.get(&source);
        (t, s)
    };

    if let (Some(target_arc), Some(source_arc)) = (target_clone, source_ref) {
        let mut merged = (*target_arc).clone();
        merged.merge(&source_arc);
        // 写回 HashMap，替换原 target
        let mut animations = ANIMATIONS.write().unwrap();
        animations.insert(target, Arc::new(merged));
    }
}

// 设置模型全局变换（用于人物移动时传递位置给物理系统）
// 传入 4x4 矩阵的 16 个 float 值（列主序）
