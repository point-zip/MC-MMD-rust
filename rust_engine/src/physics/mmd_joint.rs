//! MMD 关节（约束）封装
//!
//! 移植自 babylon-mmd 的关节构建逻辑。
//! 使用 Bullet3 btGeneric6DofSpringConstraint。

use glam::{Mat4, Quat, Vec3};

use mmd::pmx::joint::Joint as PmxJoint;

use super::bullet_ffi::{BulletConstraint, BulletRigidBody, BT_CONSTRAINT_STOP_ERP};
use super::joint_parameters::JointParameters;

/// 沿用既有 MMD Bullet 实现的限位纠偏比例，避免混入未经验证的参数实验。
const JOINT_STOP_ERP: f32 = 0.475;

/// MMD 关节数据
pub struct MmdJointData {
    /// 关节名称
    pub name: String,
    /// 刚体 A 索引
    pub rigid_body_a_index: i32,
    /// 刚体 B 索引
    pub rigid_body_b_index: i32,
    /// 关节在刚体 A 局部空间中的 frame，供运行时诊断锚点误差。
    pub frame_a: Mat4,
    /// 关节在刚体 B 局部空间中的 frame，供运行时诊断锚点误差。
    pub frame_b: Mat4,
    /// Bullet3 约束
    pub constraint: Option<BulletConstraint>,
}

impl MmdJointData {
    /// 从 PMX 关节数据创建 Bullet3 6DOF 弹簧约束
    ///
    /// 移植自 babylon-mmd buildPhysics() 关节部分。
    /// 欧拉角使用 XYZ intrinsic（等价 saba btMatrix3x3::setEulerZYX）。
    pub fn from_pmx(
        pmx_joint: &PmxJoint,
        rb_a: &BulletRigidBody,
        rb_b: &BulletRigidBody,
        rb_a_initial_transform: Mat4,
        rb_b_initial_transform: Mat4,
    ) -> Self {
        let position = Vec3::new(
            pmx_joint.position[0],
            pmx_joint.position[1],
            pmx_joint.position[2],
        );

        // 关节沿用 Bullet setEulerZYX 对应的 X-Y-Z intrinsic 组合。
        let rotation = joint_rotation(pmx_joint.rotation);

        let joint_transform = Mat4::from_rotation_translation(rotation, position);
        let parameters = JointParameters::from_pmx(
            pmx_joint.position_min,
            pmx_joint.position_max,
            pmx_joint.rotation_min,
            pmx_joint.rotation_max,
            pmx_joint.position_spring,
            pmx_joint.rotation_spring,
        );

        // frameA = rbA_initialTransform.inverse() * jointTransform
        // frameB = rbB_initialTransform.inverse() * jointTransform
        let frame_a = rb_a_initial_transform.inverse() * joint_transform;
        let frame_b = rb_b_initial_transform.inverse() * joint_transform;

        // 创建 Bullet3 6DOF 弹簧约束
        let constraint = BulletConstraint::new_6dof_spring(rb_a, rb_b, frame_a, frame_b, true);

        // 配置约束参数（仅在创建成功时）
        if let Some(ref c) = constraint {
            for axis in 0..6 {
                c.set_param(BT_CONSTRAINT_STOP_ERP, JOINT_STOP_ERP, axis);
            }

            c.set_linear_lower_limit(
                parameters.linear[0].lower,
                parameters.linear[1].lower,
                parameters.linear[2].lower,
            );
            c.set_linear_upper_limit(
                parameters.linear[0].upper,
                parameters.linear[1].upper,
                parameters.linear[2].upper,
            );
            c.set_angular_lower_limit(
                parameters.angular[0].lower,
                parameters.angular[1].lower,
                parameters.angular[2].lower,
            );
            c.set_angular_upper_limit(
                parameters.angular[0].upper,
                parameters.angular[1].upper,
                parameters.angular[2].upper,
            );

            for (axis, axis_parameters) in parameters
                .linear
                .iter()
                .chain(parameters.angular.iter())
                .enumerate()
            {
                if axis_parameters.spring_enabled {
                    c.set_stiffness(axis as i32, axis_parameters.stiffness);
                }
                c.enable_spring(axis as i32, axis_parameters.spring_enabled);
            }
        }

        Self {
            name: pmx_joint.local_name.clone(),
            rigid_body_a_index: pmx_joint.rigid_body_a_index,
            rigid_body_b_index: pmx_joint.rigid_body_b_index,
            frame_a,
            frame_b,
            constraint,
        }
    }
}

/// 将 PMX 关节欧拉角转换为 Bullet setEulerZYX 等价旋转。
fn joint_rotation(rotation: [f32; 3]) -> Quat {
    // 6DOF frame 必须保留 Bullet setEulerZYX 的 Z-Y-X 矩阵组合；
    // 它定义了平移/旋转限位轴，不能直接复用刚体的 Y-X-Z 欧拉约定。
    Quat::from_rotation_z(rotation[2])
        * Quat::from_rotation_y(rotation[1])
        * Quat::from_rotation_x(rotation[0])
}

/// 计算两个刚体上的关节锚点在 Bullet 世界空间中的距离。
pub(crate) fn joint_anchor_position_error(
    body_a_transform: Mat4,
    frame_a: Mat4,
    body_b_transform: Mat4,
    frame_b: Mat4,
) -> (f32, Vec3, Vec3) {
    let anchor_a = (body_a_transform * frame_a).w_axis.truncate();
    let anchor_b = (body_b_transform * frame_b).w_axis.truncate();
    (anchor_a.distance(anchor_b), anchor_a, anchor_b)
}

#[cfg(test)]
mod tests {
    use super::{joint_anchor_position_error, joint_rotation, MmdJointData, JOINT_STOP_ERP};
    use crate::physics::bullet_ffi::{BulletRigidBody, BulletShape, RigidBodyInfo};
    use glam::{Mat4, Quat, Vec3};
    use mmd::pmx::joint::{Joint, JointType};

    fn test_body(shape: &BulletShape) -> BulletRigidBody {
        BulletRigidBody::new(
            &RigidBodyInfo {
                mass: 1.0,
                linear_damping: 0.0,
                angular_damping: 0.0,
                friction: 0.5,
                restitution: 0.0,
                additional_damping: false,
                is_kinematic: false,
                disable_deactivation: true,
                no_contact_response: false,
                initial_transform: Mat4::IDENTITY,
            },
            shape,
        )
        .expect("应能创建关节测试刚体")
    }

    #[test]
    fn joint_rotation_matches_bullet_zyx_matrix_composition() {
        let [x, y, z] = [0.31, -0.47, 0.83];
        let expected =
            Quat::from_rotation_z(z) * Quat::from_rotation_y(y) * Quat::from_rotation_x(x);
        assert!(joint_rotation([x, y, z]).abs_diff_eq(expected, 1e-6));
    }

    #[test]
    fn stabilization_parameters_stay_in_bullet_ranges() {
        assert!((0.0..=1.0).contains(&JOINT_STOP_ERP));
    }

    #[test]
    fn pmx_joint_disables_zero_springs_in_bullet() {
        let shape = BulletShape::sphere(0.25).expect("应能创建关节测试形状");
        let body_a = test_body(&shape);
        let body_b = test_body(&shape);
        let joint = Joint {
            local_name: "测试关节".to_owned(),
            universal_name: "test_joint".to_owned(),
            type_: JointType::Spring6DOF,
            rigid_body_a_index: 0,
            rigid_body_b_index: 1,
            position: [0.0; 3],
            rotation: [0.0; 3],
            position_min: [0.0; 3],
            position_max: [0.0; 3],
            rotation_min: [-0.2; 3],
            rotation_max: [0.2; 3],
            position_spring: [0.0, 3.0, 0.0],
            rotation_spring: [0.0, 0.0, 5.0],
        };

        let data = MmdJointData::from_pmx(&joint, &body_a, &body_b, Mat4::IDENTITY, Mat4::IDENTITY);
        let diagnostic = data
            .constraint
            .as_ref()
            .and_then(|constraint| constraint.diagnostic())
            .expect("应能回读 PMX 关节配置");

        assert_eq!(
            diagnostic.spring_enabled,
            [false, true, false, false, false, true]
        );
        assert_eq!(diagnostic.stiffness, [0.0, 3.0, 0.0, 0.0, 0.0, 5.0]);
    }

    #[test]
    fn joint_anchor_error_tracks_world_space_separation() {
        let body_a = Mat4::from_translation(Vec3::new(1.0, 2.0, 3.0));
        let frame_a = Mat4::from_translation(Vec3::X);
        let body_b = Mat4::from_translation(Vec3::new(2.0, 2.0, 3.0));
        let (same_error, _, _) =
            joint_anchor_position_error(body_a, frame_a, body_b, Mat4::IDENTITY);
        assert!(same_error.abs() < 1e-6);

        let shifted_body_b = Mat4::from_translation(Vec3::new(2.5, 2.0, 3.0));
        let (shifted_error, anchor_a, anchor_b) =
            joint_anchor_position_error(body_a, frame_a, shifted_body_b, Mat4::IDENTITY);
        assert!((shifted_error - 0.5).abs() < 1e-6);
        assert_eq!(anchor_b - anchor_a, Vec3::new(0.5, 0.0, 0.0));
    }
}
