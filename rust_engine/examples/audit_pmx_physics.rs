//! 输出指定 PMX 模型中与裙摆碰撞有关的原始物理数据。

use std::env;
use std::process::ExitCode;

use mmd_engine::model::load_pmx;

const TARGET_NAMES: &[&str] = &[
    // TohsakaRin 奔跑时反复出现接触和限位异常的刚体。
    "左袖1",
    "左袖2",
    "左袖3",
    "左袖4",
    "右袖1",
    "右袖2",
    "右袖3",
    "右袖4",
    "M-M-M-下半身",
    "頭",
    "左髮飾3",
    "左髮飾4",
    "右髮飾3",
    "右髮飾4",
    // TohsakaRin 裙摆主链及日志中持续分离的横向关节端点。
    "裙_4_0",
    "裙_5_0",
    "裙_5_1",
    "裙_5_2",
    "裙_6_0",
    "裙_7_16",
    "裙_8_1",
    "左衣帶_8_1",
    "右衣帶_8_1",
    "左馬尾1-9",
    "右馬尾1-9",
    "Sp_Hi_Tail0_B_00_body_blocker",
    "left_thigh_skirt_collider",
    "right_thigh_skirt_collider",
    "left_shin_skirt_collider",
    "right_shin_skirt_collider",
    "Sp_Hi_MSkirt0_F_00_skirt_physics",
    "Sp_Hi_MSkirt0_FL_00_skirt_physics",
    "Sp_Hi_MSkirt0_FR_00_skirt_physics",
];

fn is_target(name: &str) -> bool {
    TARGET_NAMES.iter().any(|target| name == *target)
}

fn main() -> ExitCode {
    let Some(path) = env::args().nth(1) else {
        eprintln!("用法: cargo run --example audit_pmx_physics -- <模型.pmx>");
        return ExitCode::FAILURE;
    };

    let model = match load_pmx(&path) {
        Ok(model) => model,
        Err(error) => {
            eprintln!("读取 PMX 失败: {error}");
            return ExitCode::FAILURE;
        }
    };

    println!("模型: {}", model.name);
    println!("刚体总数: {}", model.rigid_bodies.len());
    println!("关节总数: {}", model.joints.len());

    println!("\n旋转绝对值超过 2π 的刚体:");
    for (index, body) in model.rigid_bodies.iter().enumerate() {
        if body
            .rotation
            .iter()
            .any(|value| value.abs() > std::f32::consts::TAU)
        {
            println!(
                "  #{index} {} shape={:?} rotation={:?}",
                body.local_name, body.shape, body.rotation
            );
        }
    }

    let mut selected_indices = Vec::new();
    for (index, body) in model.rigid_bodies.iter().enumerate() {
        if is_target(&body.local_name) {
            selected_indices.push(index);
            println!("\n刚体 #{index}: {}", body.local_name);
            println!("  bone_index={}", body.bone_index);
            println!(
                "  group={} excluded=0x{:04X}",
                body.group, body.un_collision_group_flag
            );
            println!("  shape={:?} size={:?}", body.shape, body.size);
            println!(
                "  position={:?} rotation={:?}",
                body.position, body.rotation
            );
            println!(
                "  mass={} move_damping={} rotation_damping={} restitution={} friction={} mode={:?}",
                body.mass,
                body.move_attenuation,
                body.rotation_attenuation,
                body.repulsion,
                body.friction,
                body.mode
            );
        }
    }

    println!("\n关联关节:");
    for (index, joint) in model.joints.iter().enumerate() {
        let body_a = joint.rigid_body_a_index as usize;
        let body_b = joint.rigid_body_b_index as usize;
        if selected_indices.contains(&body_a) || selected_indices.contains(&body_b) {
            println!("\n关节 #{index}: {}", joint.local_name);
            println!(
                "  type={:?} rigid_body_a={} ({}) rigid_body_b={} ({})",
                joint.type_,
                joint.rigid_body_a_index,
                model
                    .rigid_bodies
                    .get(body_a)
                    .map_or("<invalid>", |body| body.local_name.as_str()),
                joint.rigid_body_b_index,
                model
                    .rigid_bodies
                    .get(body_b)
                    .map_or("<invalid>", |body| body.local_name.as_str())
            );
            println!(
                "  position={:?} rotation={:?}",
                joint.position, joint.rotation
            );
            println!(
                "  position_min={:?} position_max={:?}",
                joint.position_min, joint.position_max
            );
            println!(
                "  rotation_min={:?} rotation_max={:?}",
                joint.rotation_min, joint.rotation_max
            );
            println!(
                "  position_spring={:?} rotation_spring={:?}",
                joint.position_spring, joint.rotation_spring
            );
        }
    }

    ExitCode::SUCCESS
}
