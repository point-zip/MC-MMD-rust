//! 负责集中承载 Java 与 Rust 之间的受检 native 边界。

mod animation_handle;
mod error;
mod model_handle;
mod native_bindings;
mod render_data;
mod runtime;
mod runtime_bindings;
mod texture_handle;

pub(crate) use animation_handle::AnimationHandle;
pub(crate) use error::{catch_bridge, BridgeError, BridgeResult};
pub(crate) use model_handle::ModelHandle;
pub(crate) use render_data::ABI_VERSION;
pub(crate) use runtime::{ANIMATIONS, FBX_CACHE, MODELS, NATIVE_RUNTIME};
pub(crate) use texture_handle::TextureHandle;

use crate::animation::VmdAnimation;
use crate::model::MmdModel;

pub fn register_model(model: MmdModel) -> i64 {
    NATIVE_RUNTIME
        .register_model(model)
        .map(ModelHandle::raw)
        .unwrap_or(0)
}

pub fn register_animation(animation: VmdAnimation) -> i64 {
    NATIVE_RUNTIME
        .register_animation(animation)
        .map(AnimationHandle::raw)
        .unwrap_or(0)
}
