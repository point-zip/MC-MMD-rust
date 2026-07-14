// 负责动画层播放、过渡、权重、骨骼掩码与定位入口。


// 停止指定层的动画
// layer: 动画层ID（0-3）

// 暂停指定层的动画
// layer: 动画层ID（0-3）

// 恢复指定层的动画
// layer: 动画层ID（0-3）

// 设置动画层权重
// layer: 动画层ID（0-3）
// weight: 权重值（0.0 - 1.0）

// 设置动画层播放速度
// layer: 动画层ID（0-3）
// speed: 速度倍率（0.0+，1.0为正常速度）

// 跳转到指定帧
// layer: 动画层ID（0-3）
// frame: 目标帧号
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SeekLayer(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    frame: jfloat,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.seek_layer(layer as usize, frame);
    }
}

// 设置动画层淡入淡出时间
// layer: 动画层ID（0-3）
// fadeIn: 淡入时间（秒）
// fadeOut: 淡出时间（秒）

// 带过渡地切换指定层的动画（姿态缓存过渡）
//
// 从当前骨骼姿态平滑过渡到新动画，避免动作切换时的突兀感。
//
// # 参数
// - model: 模型句柄
// - layer: 动画层ID（0-3）
// - animation: 动画句柄（0表示清除动画）
// - transition_time: 过渡时间（秒），推荐 0.2 ~ 0.5 秒
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_TransitionLayerTo(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    animation: jlong,
    transition_time: jfloat,
) {
    let anim = if animation != 0 {
        let animations = ANIMATIONS.read().unwrap();
        animations.get(&animation)
    } else {
        None
    };
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.transition_layer_to(layer as usize, anim, transition_time);
    }
}

// 获取动画层最大帧数
// layer: 动画层ID（0-3）

// 检测层动画是否播放完毕（非循环动画到达末帧）
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_IsLayerAnimationFinished(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
) -> jboolean {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let model = model_arc.lock().unwrap();
        if model.is_layer_finished(layer as usize) {
            1
        } else {
            0
        }
    } else {
        1 // 无模型视为已完成
    }
}

// 设置层骨骼遮罩（按根骨骼名，仅影响该骨骼及其子孙）
// bone_name 为 null 时清除遮罩
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetLayerBoneMask(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    bone_name: JString,
) -> jboolean {
    let name_opt: Option<String> = if bone_name.is_null() {
        None
    } else {
        match env.get_string(&bone_name) {
            Ok(s) => Some(s.into()),
            Err(_) => return 0,
        }
    };

    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let ok = model.set_layer_bone_mask_by_name(layer as usize, name_opt.as_deref());
        if ok {
            1
        } else {
            0
        }
    } else {
        0
    }
}

// 设置层骨骼排除集（按根骨骼名，该骨骼及其子孙不受动画影响）
// bone_name 为 null 时清除排除
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetLayerBoneExclude(
    mut env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    bone_name: JString,
) -> jboolean {
    let name_opt: Option<String> = if bone_name.is_null() {
        None
    } else {
        match env.get_string(&bone_name) {
            Ok(s) => Some(s.into()),
            Err(_) => return 0,
        }
    };

    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        let ok = model.set_layer_bone_exclude_by_name(layer as usize, name_opt.as_deref());
        if ok {
            1
        } else {
            0
        }
    } else {
        0
    }
}

// 设置动画层是否循环播放
// loop_play: true=循环，false=播放到尾帧后停留
#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_SetLayerLoop(
    _env: JNIEnv,
    _class: JClass,
    model: jlong,
    layer: jlong,
    loop_play: jboolean,
) {
    let models = MODELS.read().unwrap();
    if let Some(model_arc) = models.get(&model) {
        let mut model = model_arc.lock().unwrap();
        model.set_layer_loop(layer as usize, loop_play != 0);
    }
}

// ============================================================================
// 物理系统相关函数
// ============================================================================

// 初始化模型物理系统
