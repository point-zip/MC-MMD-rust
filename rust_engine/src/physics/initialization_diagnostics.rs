//! 物理初始化数据诊断。

use glam::{Mat4, Quat, Vec3};

use super::mmd_joint::{joint_anchor_position_error, MmdJointData};
use super::mmd_rigid_body::MmdRigidBodyData;

/// 输出 PMX 输入、静态 offset、运行姿态目标和 Bullet 回读的完整初始化链。
pub(super) fn log_initialized_bodies(rigid_bodies: &[MmdRigidBodyData], bone_transforms: &[Mat4]) {
    for (index, data) in rigid_bodies.iter().enumerate() {
        let Some(body) = data.bullet_body.as_ref() else {
            continue;
        };

        let bone_index = data.bone_index;
        let runtime_bone = usize::try_from(bone_index)
            .ok()
            .and_then(|bone_index| bone_transforms.get(bone_index))
            .copied();
        let expected = runtime_bone
            .map(super::inv_z)
            .map(|bone| data.compute_body_matrix(bone))
            .unwrap_or(data.initial_transform);
        let readback = body.get_transform();
        let matrix_error = max_matrix_error(expected, readback);
        let (_, expected_rotation, expected_position) = expected.to_scale_rotation_translation();
        let (_, readback_rotation, readback_position) = readback.to_scale_rotation_translation();
        let (_, offset_rotation, offset_position) =
            data.body_offset_matrix.to_scale_rotation_translation();
        let runtime_bone_position = runtime_bone
            .map(|matrix| matrix.w_axis.truncate())
            .unwrap_or(Vec3::ZERO);

        log::info!(
            "[Bullet3][PHYSICS_INIT_BODY] index={} name='{}' bone={} mode={:?} shape={} size={} raw_pos={} raw_rot={} decoded_rot={} runtime_bone_pos={} offset_pos={} offset_rot={} expected_pos={} expected_rot={} readback_pos={} readback_rot={} matrix_error={:.7}",
            index,
            data.name,
            bone_index,
            data.physics_mode,
            data.shape_name,
            format_array(data.shape_size),
            format_array(data.raw_position),
            format_array(data.raw_rotation),
            format_array(data.decoded_rotation),
            format_vec3(runtime_bone_position),
            format_vec3(offset_position),
            format_quat(offset_rotation),
            format_vec3(expected_position),
            format_quat(expected_rotation),
            format_vec3(readback_position),
            format_quat(readback_rotation),
            matrix_error,
        );
    }
}

/// 在首个求解步之前输出误差最大的约束锚点。
pub(super) fn log_joint_anchor_baseline(
    rigid_bodies: &[MmdRigidBodyData],
    joints: &[MmdJointData],
) {
    let mut peak: Option<(&MmdJointData, f32, Vec3, Vec3)> = None;
    for joint in joints {
        let (Ok(a), Ok(b)) = (
            usize::try_from(joint.rigid_body_a_index),
            usize::try_from(joint.rigid_body_b_index),
        ) else {
            continue;
        };
        let (Some(body_a), Some(body_b)) = (
            rigid_bodies
                .get(a)
                .and_then(|body| body.bullet_body.as_ref()),
            rigid_bodies
                .get(b)
                .and_then(|body| body.bullet_body.as_ref()),
        ) else {
            continue;
        };
        let (error, anchor_a, anchor_b) = joint_anchor_position_error(
            body_a.get_transform(),
            joint.frame_a,
            body_b.get_transform(),
            joint.frame_b,
        );
        if error.is_finite() && peak.is_none_or(|(_, current, _, _)| error > current) {
            peak = Some((joint, error, anchor_a, anchor_b));
        }
    }

    if let Some((joint, error, anchor_a, anchor_b)) = peak {
        let body_a = rigid_bodies
            .get(joint.rigid_body_a_index as usize)
            .map_or("?", |body| body.name.as_str());
        let body_b = rigid_bodies
            .get(joint.rigid_body_b_index as usize)
            .map_or("?", |body| body.name.as_str());
        log::info!(
            "[Bullet3][INIT_ANCHOR_PRE_STEP] joint='{}' error={:.5} anchor_a={} anchor_b={}",
            joint.name,
            error,
            format_vec3(anchor_a),
            format_vec3(anchor_b),
        );
        log::info!(
            "[Bullet3][INIT_ANCHOR_PRE_STEP_BODIES] bodies='{}'/'{}'",
            body_a,
            body_b,
        );
    }
}

fn max_matrix_error(expected: Mat4, actual: Mat4) -> f32 {
    expected
        .to_cols_array()
        .into_iter()
        .zip(actual.to_cols_array())
        .map(|(expected, actual)| (expected - actual).abs())
        .fold(0.0, f32::max)
}

fn format_array(value: [f32; 3]) -> String {
    format!("({:.6},{:.6},{:.6})", value[0], value[1], value[2])
}

fn format_vec3(value: Vec3) -> String {
    format!("({:.6},{:.6},{:.6})", value.x, value.y, value.z)
}

fn format_quat(value: Quat) -> String {
    format!(
        "({:.6},{:.6},{:.6},{:.6})",
        value.x, value.y, value.z, value.w
    )
}

#[cfg(test)]
mod tests {
    use super::max_matrix_error;
    use glam::{Mat4, Quat, Vec3};

    #[test]
    fn matrix_error_detects_rotation_and_translation_changes() {
        let expected =
            Mat4::from_rotation_translation(Quat::from_rotation_y(0.4), Vec3::new(1.0, 2.0, 3.0));
        assert_eq!(max_matrix_error(expected, expected), 0.0);
        assert!(max_matrix_error(expected, Mat4::IDENTITY) > 0.1);
    }
}
