//! 负责定义跨 JNI 边界使用的动画强类型句柄。

use super::{BridgeError, BridgeResult};

/// 不暴露原生地址的动画标识。
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub struct AnimationHandle(u64);

impl AnimationHandle {
    pub(crate) fn new(value: u64) -> BridgeResult<Self> {
        if value == 0 || value > i64::MAX as u64 {
            return Err(BridgeError::InvalidHandle(value as i64));
        }
        Ok(Self(value))
    }

    pub(crate) fn from_raw(value: i64) -> BridgeResult<Self> {
        let value = u64::try_from(value).map_err(|_| BridgeError::InvalidHandle(value))?;
        Self::new(value)
    }

    pub(crate) const fn raw(self) -> i64 {
        self.0 as i64
    }
}
