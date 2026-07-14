//! 负责实现 1.21.5 渲染管线所需的安全 JNI 入口。

use std::slice;

use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;

use crate::texture::load_texture;

use super::render_data::{
    copy_bone_palette_matrices, copy_draw_commands, copy_frame_vertices, copy_indices, copy_matrix,
    copy_mesh_description, copy_texture_pixels,
};
use super::{
    catch_bridge, AnimationHandle, BridgeError, BridgeResult, ModelHandle, TextureHandle,
    ABI_VERSION, NATIVE_RUNTIME,
};

const TEXTURE_DESCRIPTION_SIZE: usize = 16;

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_getAbiVersion(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    ABI_VERSION
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_describeMesh(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_mesh_description(&model, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyIndices(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_indices(&model, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyFrameVertices(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_frame_vertices(&model, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyDrawCommands(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_draw_commands(&model, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyBonePaletteMatrices(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_bone_palette_matrices(&model, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyHandMatrix(
    env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    hand: jint,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        let matrix = match hand {
            0 => model.get_right_hand_matrix(),
            1 => model.get_left_hand_matrix(),
            _ => {
                return Err(BridgeError::InvalidData(format!(
                    "无效手部矩阵标识: {hand}"
                )))
            }
        };
        with_direct_buffer(&env, &buffer, |destination| {
            copy_matrix(matrix, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_getTexturePath(
    mut env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
    texture_index: jint,
) -> jstring {
    let result = catch_bridge(|| {
        let index = usize::try_from(texture_index)
            .map_err(|_| BridgeError::InvalidData(format!("无效纹理索引: {texture_index}")))?;
        let model = NATIVE_RUNTIME.model(ModelHandle::from_raw(raw_model)?)?;
        let model = model.lock()?;
        Ok(model.texture_paths.get(index).cloned())
    });
    match result {
        Ok(Some(path)) => match env.new_string(path) {
            Ok(value) => value.into_raw(),
            Err(error) => {
                throw_bridge_error(&mut env, &BridgeError::InvalidData(error.to_string()));
                std::ptr::null_mut()
            }
        },
        Ok(None) => std::ptr::null_mut(),
        Err(error) => {
            throw_bridge_error(&mut env, &error);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_decodeTexture(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jlong {
    let result = catch_bridge(|| {
        let path: String = env
            .get_string(&path)
            .map_err(|error| BridgeError::InvalidData(error.to_string()))?
            .into();
        let texture =
            load_texture(path).map_err(|error| BridgeError::InvalidData(error.to_string()))?;
        NATIVE_RUNTIME
            .register_texture(texture)
            .map(TextureHandle::raw)
    });
    match result {
        Ok(handle) => handle,
        Err(error) => {
            throw_bridge_error(&mut env, &error);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_describeTexture(
    env: JNIEnv,
    _class: JClass,
    raw_texture: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let texture = NATIVE_RUNTIME.texture(TextureHandle::from_raw(raw_texture)?)?;
        with_direct_buffer(&env, &buffer, |destination| {
            if destination.len() < TEXTURE_DESCRIPTION_SIZE {
                return Err(BridgeError::BufferTooSmall {
                    required: TEXTURE_DESCRIPTION_SIZE,
                    actual: destination.len(),
                });
            }
            let byte_count =
                u64::try_from(texture.data.len()).map_err(|_| BridgeError::SizeOverflow)?;
            destination[..TEXTURE_DESCRIPTION_SIZE].fill(0);
            destination[0..4].copy_from_slice(&texture.width.to_le_bytes());
            destination[4..8].copy_from_slice(&texture.height.to_le_bytes());
            destination[8..16].copy_from_slice(&byte_count.to_le_bytes());
            Ok(TEXTURE_DESCRIPTION_SIZE)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_copyTexturePixels(
    env: JNIEnv,
    _class: JClass,
    raw_texture: jlong,
    buffer: JByteBuffer,
) -> jint {
    render_buffer_call(|| {
        let texture = NATIVE_RUNTIME.texture(TextureHandle::from_raw(raw_texture)?)?;
        with_direct_buffer(&env, &buffer, |destination| {
            copy_texture_pixels(&texture, destination)
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_textureHasAlpha(
    _env: JNIEnv,
    _class: JClass,
    raw_texture: jlong,
) -> jint {
    status_call(|| {
        let texture = NATIVE_RUNTIME.texture(TextureHandle::from_raw(raw_texture)?)?;
        Ok(i32::from(texture.has_alpha))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_releaseModel(
    _env: JNIEnv,
    _class: JClass,
    raw_model: jlong,
) -> jint {
    status_call(|| {
        let handle = ModelHandle::from_raw(raw_model)?;
        if !NATIVE_RUNTIME.remove_model(handle)? {
            return Err(BridgeError::InvalidHandle(raw_model));
        }
        Ok(0)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_releaseAnimation(
    _env: JNIEnv,
    _class: JClass,
    raw_animation: jlong,
) -> jint {
    status_call(|| {
        let handle = AnimationHandle::from_raw(raw_animation)?;
        if !NATIVE_RUNTIME.remove_animation(handle)? {
            return Err(BridgeError::InvalidHandle(raw_animation));
        }
        Ok(0)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_shiroha_mmdskin_bridge_NativeBindings_releaseTexture(
    _env: JNIEnv,
    _class: JClass,
    raw_texture: jlong,
) -> jint {
    status_call(|| {
        let handle = TextureHandle::from_raw(raw_texture)?;
        if !NATIVE_RUNTIME.remove_texture(handle)? {
            return Err(BridgeError::InvalidHandle(raw_texture));
        }
        Ok(0)
    })
}

fn render_buffer_call(operation: impl FnOnce() -> BridgeResult<usize>) -> jint {
    status_call(|| {
        let written = operation()?;
        jint::try_from(written).map_err(|_| BridgeError::SizeOverflow)
    })
}

fn status_call(operation: impl FnOnce() -> BridgeResult<jint>) -> jint {
    match catch_bridge(operation) {
        Ok(value) => value,
        Err(error) => {
            log::error!("native bridge 调用失败: {error}");
            error.status()
        }
    }
}

fn with_direct_buffer<T>(
    env: &JNIEnv,
    buffer: &JByteBuffer,
    operation: impl FnOnce(&mut [u8]) -> BridgeResult<T>,
) -> BridgeResult<T> {
    let address = env
        .get_direct_buffer_address(buffer)
        .map_err(|_| BridgeError::NotDirectBuffer)?;
    let capacity = env
        .get_direct_buffer_capacity(buffer)
        .map_err(|_| BridgeError::NotDirectBuffer)?;
    if address.is_null() {
        return Err(BridgeError::NotDirectBuffer);
    }
    // SAFETY: JVM 保证 direct ByteBuffer 地址在本次 JNI 调用期间有效，容量由 JNI 返回。
    let destination = unsafe { slice::from_raw_parts_mut(address, capacity) };
    operation(destination)
}

fn throw_bridge_error(env: &mut JNIEnv, error: &BridgeError) {
    let class = match error {
        BridgeError::InvalidHandle(_) | BridgeError::InvalidData(_) => {
            "java/lang/IllegalArgumentException"
        }
        _ => "java/lang/IllegalStateException",
    };
    if let Err(throw_error) = env.throw_new(class, error.to_string()) {
        log::error!("无法向 Java 抛出 native bridge 异常: {throw_error}");
    }
}
