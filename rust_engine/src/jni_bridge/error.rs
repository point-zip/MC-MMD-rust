//! 负责统一 native bridge 的校验错误与 JNI 状态映射。

use std::any::Any;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::PoisonError;

use thiserror::Error;

pub const STATUS_INVALID_HANDLE: i32 = -1;
pub const STATUS_NOT_DIRECT_BUFFER: i32 = -2;
pub const STATUS_BUFFER_TOO_SMALL: i32 = -3;
pub const STATUS_INVALID_DATA: i32 = -4;
pub const STATUS_OVERFLOW: i32 = -5;
pub const STATUS_LOCK_POISONED: i32 = -6;
pub const STATUS_PANIC: i32 = -127;

pub type BridgeResult<T> = Result<T, BridgeError>;

#[derive(Debug, Error, PartialEq, Eq)]
pub enum BridgeError {
    #[error("无效 native 句柄: {0}")]
    InvalidHandle(i64),
    #[error("目标不是 direct ByteBuffer")]
    NotDirectBuffer,
    #[error("缓冲区容量不足，需要 {required} 字节，实际 {actual} 字节")]
    BufferTooSmall { required: usize, actual: usize },
    #[error("无效渲染数据: {0}")]
    InvalidData(String),
    #[error("缓冲区大小计算溢出")]
    SizeOverflow,
    #[error("native runtime 锁已中毒")]
    LockPoisoned,
    #[error("native 调用发生 panic: {0}")]
    Panic(String),
}

impl BridgeError {
    pub const fn status(&self) -> i32 {
        match self {
            Self::InvalidHandle(_) => STATUS_INVALID_HANDLE,
            Self::NotDirectBuffer => STATUS_NOT_DIRECT_BUFFER,
            Self::BufferTooSmall { .. } => STATUS_BUFFER_TOO_SMALL,
            Self::InvalidData(_) => STATUS_INVALID_DATA,
            Self::SizeOverflow => STATUS_OVERFLOW,
            Self::LockPoisoned => STATUS_LOCK_POISONED,
            Self::Panic(_) => STATUS_PANIC,
        }
    }
}

impl<T> From<PoisonError<T>> for BridgeError {
    fn from(_: PoisonError<T>) -> Self {
        Self::LockPoisoned
    }
}

pub fn catch_bridge<T>(operation: impl FnOnce() -> BridgeResult<T>) -> BridgeResult<T> {
    catch_unwind(AssertUnwindSafe(operation))
        .map_err(|payload| BridgeError::Panic(panic_message(payload)))?
}

fn panic_message(payload: Box<dyn Any + Send>) -> String {
    if let Some(message) = payload.downcast_ref::<&str>() {
        (*message).to_owned()
    } else if let Some(message) = payload.downcast_ref::<String>() {
        message.clone()
    } else {
        "未知 panic".to_owned()
    }
}

#[cfg(test)]
mod tests {
    use super::{catch_bridge, BridgeError, STATUS_PANIC};

    #[test]
    fn catch_bridge_should_map_panic_to_stable_status() {
        let error = catch_bridge::<()>(|| panic!("boom")).expect_err("panic should be mapped");

        assert!(matches!(&error, BridgeError::Panic(message) if message == "boom"));
        assert_eq!(error.status(), STATUS_PANIC);
    }
}
