//! TaCZ 第三人称上臂相对旋转增量。
//!
//! Java 侧从 TaCZ 的当前姿态减去该枪械腰射基线；这里仅叠加到 MMD/VMD 上臂，
//! 不猜测 TaCZ 未提供的第三人称腕端或护木坐标。

use glam::Quat;

use crate::skeleton::BoneManager;

const EPSILON: f32 = 1.0e-6;
const LEFT_UPPER_ARM_NAMES: &[&str] = &[
    "左腕",
    "leftUpperArm",
    "LeftUpperArm",
    "left_arm",
    "LeftArm",
];
const RIGHT_UPPER_ARM_NAMES: &[&str] = &[
    "右腕",
    "rightUpperArm",
    "RightUpperArm",
    "right_arm",
    "RightArm",
];

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

#[derive(Debug, Default)]
pub struct TaczThirdPersonArmCache {
    left_upper_arm: Option<usize>,
    right_upper_arm: Option<usize>,
    resolved: bool,
}

impl TaczThirdPersonArmCache {
    pub fn apply(&mut self, bones: &mut BoneManager, rotations: TaczThirdPersonArmRotations) -> u8 {
        self.resolve(bones);
        let mut applied_mask = 0;
        if let (Some(index), Some(rotation)) = (self.left_upper_arm, rotations.left) {
            bones.add_bone_rotation(index, rotation);
            bones.update_single_bone_global(index);
            applied_mask |= 0b01;
        }
        if let (Some(index), Some(rotation)) = (self.right_upper_arm, rotations.right) {
            bones.add_bone_rotation(index, rotation);
            bones.update_single_bone_global(index);
            applied_mask |= 0b10;
        }
        applied_mask
    }

    fn resolve(&mut self, bones: &BoneManager) {
        if self.resolved {
            return;
        }
        self.left_upper_arm = find_first(bones, LEFT_UPPER_ARM_NAMES);
        self.right_upper_arm = find_first(bones, RIGHT_UPPER_ARM_NAMES);
        self.resolved = true;
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

fn find_first(bones: &BoneManager, names: &[&str]) -> Option<usize> {
    names.iter().find_map(|name| bones.find_bone_by_name(name))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::skeleton::BoneLink;

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

    #[test]
    fn identity_delta_preserves_the_existing_vmd_rotation() {
        let mut bones = BoneManager::new();
        bones.add_bone(BoneLink::new("左腕".to_string()));
        bones.build_hierarchy();
        let vmd_rotation = Quat::from_rotation_z(0.35);
        bones.set_bone_rotation(0, vmd_rotation);
        bones.update_single_bone_global(0);

        let applied = TaczThirdPersonArmCache::default().apply(
            &mut bones,
            TaczThirdPersonArmRotations {
                left: Some(Quat::IDENTITY),
                right: None,
            },
        );

        assert_eq!(applied, 0b01);
        assert!(bones
            .get_bone(0)
            .expect("left upper arm")
            .animation_rotate
            .abs_diff_eq(vmd_rotation, 1.0e-6));
    }

    #[test]
    fn missing_left_arm_does_not_block_the_right_side() {
        let mut bones = BoneManager::new();
        bones.add_bone(BoneLink::new("右腕".to_string()));
        bones.build_hierarchy();
        let delta = Quat::from_rotation_x(0.2);

        let applied = TaczThirdPersonArmCache::default().apply(
            &mut bones,
            TaczThirdPersonArmRotations {
                left: Some(Quat::from_rotation_y(0.4)),
                right: Some(delta),
            },
        );

        assert_eq!(applied, 0b10);
        assert!(bones
            .get_bone(0)
            .expect("right upper arm")
            .animation_rotate
            .abs_diff_eq(delta, 1.0e-6));
    }
}
