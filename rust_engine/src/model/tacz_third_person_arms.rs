//! TaCZ 第三人称左右上臂绝对局部姿态数据包。
//!
//! 实际腕目标重建与双骨 IK 位于 `tacz_arm_targets`，本模块只负责 JNI 数据校验。

use glam::Quat;

const EPSILON: f32 = 1.0e-6;

#[derive(Clone, Copy, Debug)]
pub struct TaczThirdPersonArmRotations {
    pub left: Option<Quat>,
    pub right: Option<Quat>,
}

impl TaczThirdPersonArmRotations {
    /// JNI 布局为左、右各 xyzw 四项；只校验有效位指定的一侧。
    pub fn from_xyzw_slice(values: &[f32], valid_mask: u8) -> Option<Self> {
        if values.len() != 8 || valid_mask == 0 || valid_mask & !0b11 != 0 {
            return None;
        }
        let left = (valid_mask & 0b01 != 0)
            .then(|| normalized_quat(&values[..4]))
            .flatten();
        let right = (valid_mask & 0b10 != 0)
            .then(|| normalized_quat(&values[4..]))
            .flatten();
        if (valid_mask & 0b01 != 0 && left.is_none()) || (valid_mask & 0b10 != 0 && right.is_none())
        {
            return None;
        }
        Some(Self { left, right })
    }

    pub fn valid_mask(self) -> u8 {
        u8::from(self.left.is_some()) | (u8::from(self.right.is_some()) << 1)
    }
}

fn normalized_quat(values: &[f32]) -> Option<Quat> {
    let [x, y, z, w]: [f32; 4] = values.try_into().ok()?;
    if ![x, y, z, w].iter().all(|value| value.is_finite()) {
        return None;
    }
    let rotation = Quat::from_xyzw(x, y, z, w);
    (rotation.length_squared() > EPSILON).then(|| rotation.normalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn packet_accepts_one_valid_side_without_reading_the_other() {
        let packet = TaczThirdPersonArmRotations::from_xyzw_slice(
            &[0.0, 0.0, 0.0, 1.0, f32::NAN, 0.0, 0.0, 0.0],
            0b01,
        )
        .expect("left-only packet");
        assert_eq!(packet.valid_mask(), 0b01);
        assert!(packet.right.is_none());
    }

    #[test]
    fn packet_rejects_non_finite_or_zero_valid_quaternion() {
        assert!(TaczThirdPersonArmRotations::from_xyzw_slice(
            &[f32::NAN, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0],
            0b01,
        )
        .is_none());
        assert!(TaczThirdPersonArmRotations::from_xyzw_slice(&[0.0; 8], 0b10).is_none());
    }
}
