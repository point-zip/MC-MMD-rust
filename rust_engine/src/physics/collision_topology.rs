//! 基于 PMX 关节图的动态刚体自碰撞过滤。
//!
//! 过滤只作用于同碰撞组的动态刚体子图。动态刚体与跟骨刚体、不同组刚体
//! 之间的碰撞始终保留，避免衣物失去身体碰撞后穿模。

use std::collections::VecDeque;

use glam::{Mat4, Vec3};

/// 碰撞稳定模式。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollisionStabilityMode {
    /// 不增加拓扑过滤；直接相邻刚体仍由 Bullet 约束的禁碰参数处理。
    Strict,
    /// 忽略同组动态子图中距离不超过 2 的刚体对。
    Stable,
    /// 忽略同组动态子图同一连通分量内的全部刚体对。
    Relaxed,
}

impl CollisionStabilityMode {
    /// 将外部整数映射为模式；未知值回退到默认 Stable。
    pub fn from_i32(value: i32) -> Self {
        match value {
            0 => Self::Strict,
            1 => Self::Stable,
            2 => Self::Relaxed,
            _ => Self::Stable,
        }
    }

    /// 返回稳定的诊断名称，避免依赖 Debug 格式作为日志协议。
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Strict => "Strict",
            Self::Stable => "Stable",
            Self::Relaxed => "Relaxed",
        }
    }
}

/// 拓扑过滤所需的最小刚体描述。
#[derive(Debug, Clone, Copy)]
pub struct CollisionBody {
    pub group: u8,
    pub collision_mask: u16,
    pub is_dynamic: bool,
    pub is_active: bool,
    /// 初始姿态下旋转碰撞体的保守世界 AABB；缺失时仅使用拓扑规则。
    pub initial_aabb: Option<CollisionAabb>,
}

/// 初始姿态的世界轴对齐包围盒，仅用于构建期稳定过滤和诊断。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CollisionAabb {
    pub center: Vec3,
    pub half_extents: Vec3,
}

impl CollisionAabb {
    /// 将局部半尺寸按刚体旋转投影到世界轴，得到保守 AABB。
    pub fn from_transform(transform: Mat4, local_half_extents: Vec3) -> Option<Self> {
        if !transform.is_finite()
            || !local_half_extents.is_finite()
            || local_half_extents.cmple(Vec3::ZERO).any()
        {
            return None;
        }
        let x = transform.x_axis.truncate().abs();
        let y = transform.y_axis.truncate().abs();
        let z = transform.z_axis.truncate().abs();
        let half_extents =
            x * local_half_extents.x + y * local_half_extents.y + z * local_half_extents.z;
        Some(Self {
            center: transform.w_axis.truncate(),
            half_extents,
        })
    }

    fn overlaps(self, other: Self) -> bool {
        let separation = (self.center - other.center).abs();
        separation
            .cmplt(self.half_extents + other.half_extents)
            .all()
    }
}

/// 构建阶段生成的精确过滤计划及诊断统计。
#[derive(Debug, Default, PartialEq, Eq)]
pub struct CollisionFilterPlan {
    pub pairs: Vec<(usize, usize)>,
    pub preserved_dynamic_kinematic_pairs: usize,
    pub largest_dynamic_component: usize,
    pub initial_overlap_dynamic_dynamic_pairs: usize,
    pub initial_overlap_dynamic_kinematic_pairs: usize,
    pub filtered_initial_overlap_pairs: usize,
}

/// 根据同组动态刚体子图生成需要忽略的刚体对。
///
/// 无效索引、自连接和重复边会被忽略。仅统计 PMX 掩码原本允许碰撞的对，
/// 返回值规范为 `(较小索引, 较大索引)`。该函数只在模型构建阶段执行。
pub fn build_filter_plan(
    bodies: &[CollisionBody],
    joints: &[(i32, i32)],
    mode: CollisionStabilityMode,
) -> CollisionFilterPlan {
    let mut plan = CollisionFilterPlan::default();
    if bodies.len() < 2 {
        return plan;
    }

    for a in 0..bodies.len() {
        for b in (a + 1)..bodies.len() {
            if !bodies[a].is_active
                || !bodies[b].is_active
                || !collision_allowed(bodies[a], bodies[b])
            {
                continue;
            }
            let initially_overlapping = aabb_overlap(bodies[a], bodies[b]);
            if bodies[a].is_dynamic != bodies[b].is_dynamic {
                plan.preserved_dynamic_kinematic_pairs += 1;
                if initially_overlapping {
                    plan.initial_overlap_dynamic_kinematic_pairs += 1;
                }
            } else if bodies[a].is_dynamic && bodies[b].is_dynamic && initially_overlapping {
                plan.initial_overlap_dynamic_dynamic_pairs += 1;
            }
        }
    }

    let mut adjacency = vec![Vec::<usize>::new(); bodies.len()];
    for &(a, b) in joints {
        let (Ok(a), Ok(b)) = (usize::try_from(a), usize::try_from(b)) else {
            continue;
        };
        if a >= bodies.len()
            || b >= bodies.len()
            || a == b
            || !bodies[a].is_active
            || !bodies[b].is_active
            || !bodies[a].is_dynamic
            || !bodies[b].is_dynamic
            || bodies[a].group != bodies[b].group
        {
            continue;
        }
        if !adjacency[a].contains(&b) {
            adjacency[a].push(b);
            adjacency[b].push(a);
        }
    }

    let mut distances = vec![usize::MAX; bodies.len()];
    let mut queue = VecDeque::new();
    for start in 0..bodies.len() {
        if !bodies[start].is_active || !bodies[start].is_dynamic || adjacency[start].is_empty() {
            continue;
        }
        distances.fill(usize::MAX);
        queue.clear();
        distances[start] = 0;
        queue.push_back(start);

        while let Some(current) = queue.pop_front() {
            for &next in &adjacency[current] {
                if distances[next] == usize::MAX {
                    distances[next] = distances[current] + 1;
                    queue.push_back(next);
                }
            }
        }

        let component_size = distances
            .iter()
            .filter(|&&distance| distance != usize::MAX)
            .count();
        plan.largest_dynamic_component = plan.largest_dynamic_component.max(component_size);
        for other in (start + 1)..bodies.len() {
            let distance = distances[other];
            let should_ignore = match mode {
                CollisionStabilityMode::Strict => false,
                // Stable 只处理局部关节邻域；保守 AABB 可能把正常贴近误判为穿插。
                CollisionStabilityMode::Stable => distance <= 2,
                CollisionStabilityMode::Relaxed => distance != usize::MAX,
            };
            if should_ignore && collision_allowed(bodies[start], bodies[other]) {
                plan.pairs.push((start, other));
                if dynamic_pair_initially_overlaps(bodies[start], bodies[other]) {
                    plan.filtered_initial_overlap_pairs += 1;
                }
            }
        }
    }

    plan
}

fn dynamic_pair_initially_overlaps(a: CollisionBody, b: CollisionBody) -> bool {
    a.group == b.group && a.is_dynamic && b.is_dynamic && aabb_overlap(a, b)
}

fn aabb_overlap(a: CollisionBody, b: CollisionBody) -> bool {
    matches!((a.initial_aabb, b.initial_aabb), (Some(a), Some(b)) if a.overlaps(b))
}

/// Bullet 只有在双方组位均被对方掩码允许时才会创建接触。
fn collision_allowed(a: CollisionBody, b: CollisionBody) -> bool {
    let a_group = 1u16 << a.group.min(15);
    let b_group = 1u16 << b.group.min(15);
    a.collision_mask & b_group != 0 && b.collision_mask & a_group != 0
}

#[cfg(test)]
mod tests {
    use super::{build_filter_plan, CollisionAabb, CollisionBody, CollisionStabilityMode};
    use glam::{Mat4, Vec3};

    const DYNAMIC_GROUP_7: CollisionBody = CollisionBody {
        group: 7,
        collision_mask: 0xFFFF,
        is_dynamic: true,
        is_active: true,
        initial_aabb: None,
    };

    #[test]
    fn stable_and_relaxed_filter_same_group_dynamic_chain() {
        let bodies = [DYNAMIC_GROUP_7; 4];
        let joints = [(0, 1), (1, 2), (2, 3)];
        assert!(
            build_filter_plan(&bodies, &joints, CollisionStabilityMode::Strict)
                .pairs
                .is_empty()
        );
        assert_eq!(
            build_filter_plan(&bodies, &joints, CollisionStabilityMode::Stable).pairs,
            vec![(0, 1), (0, 2), (1, 2), (1, 3), (2, 3)]
        );
        let relaxed = build_filter_plan(&bodies, &joints, CollisionStabilityMode::Relaxed);
        assert_eq!(relaxed.pairs.len(), 6);
        assert_eq!(relaxed.largest_dynamic_component, 4);
    }

    #[test]
    fn dynamic_to_kinematic_collision_is_always_preserved() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_dynamic: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.preserved_dynamic_kinematic_pairs, 2);
    }

    #[test]
    fn kinematic_bridge_does_not_join_dynamic_components() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_dynamic: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(!plan.pairs.contains(&(0, 2)));
        assert_eq!(plan.largest_dynamic_component, 0);
    }

    #[test]
    fn cross_group_dynamic_bodies_are_not_filtered() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                group: 8,
                ..DYNAMIC_GROUP_7
            },
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn cross_group_body_cannot_bridge_same_group_bodies() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                group: 8,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
    }

    #[test]
    fn inactive_body_cannot_be_endpoint_or_bridge() {
        let bodies = [
            DYNAMIC_GROUP_7,
            CollisionBody {
                is_active: false,
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1), (1, 2)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.largest_dynamic_component, 0);
    }

    #[test]
    fn duplicate_and_invalid_edges_do_not_duplicate_pairs() {
        let bodies = [DYNAMIC_GROUP_7; 2];
        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 0), (0, 1), (-1, 0), (0, 9), (1, 1)],
            CollisionStabilityMode::Stable,
        );
        assert_eq!(plan.pairs, vec![(0, 1)]);
    }

    #[test]
    fn pairs_already_disabled_by_pmx_mask_are_not_counted() {
        let bodies = [
            CollisionBody {
                collision_mask: !(1 << 7),
                ..DYNAMIC_GROUP_7
            },
            DYNAMIC_GROUP_7,
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Relaxed);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.largest_dynamic_component, 2);
    }

    #[test]
    fn integer_mapping_uses_stable_as_fallback() {
        assert_eq!(
            CollisionStabilityMode::from_i32(0),
            CollisionStabilityMode::Strict
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(1),
            CollisionStabilityMode::Stable
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(2),
            CollisionStabilityMode::Relaxed
        );
        assert_eq!(
            CollisionStabilityMode::from_i32(99),
            CollisionStabilityMode::Stable
        );
    }

    #[test]
    fn stable_reports_but_preserves_overlap_beyond_graph_distance() {
        let mut bodies = [DYNAMIC_GROUP_7; 4];
        bodies[0].initial_aabb =
            CollisionAabb::from_transform(Mat4::from_translation(Vec3::ZERO), Vec3::splat(1.0));
        bodies[3].initial_aabb = CollisionAabb::from_transform(
            Mat4::from_translation(Vec3::new(1.5, 0.0, 0.0)),
            Vec3::splat(1.0),
        );
        let plan = build_filter_plan(
            &bodies,
            &[(0, 1), (1, 2), (2, 3)],
            CollisionStabilityMode::Stable,
        );
        assert!(!plan.pairs.contains(&(0, 3)));
        assert_eq!(plan.initial_overlap_dynamic_dynamic_pairs, 1);
        assert_eq!(plan.filtered_initial_overlap_pairs, 0);
    }

    #[test]
    fn dynamic_kinematic_overlap_is_reported_but_not_filtered() {
        let bounds = CollisionAabb::from_transform(Mat4::IDENTITY, Vec3::ONE);
        let bodies = [
            CollisionBody {
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
            CollisionBody {
                is_dynamic: false,
                initial_aabb: bounds,
                ..DYNAMIC_GROUP_7
            },
        ];
        let plan = build_filter_plan(&bodies, &[(0, 1)], CollisionStabilityMode::Stable);
        assert!(plan.pairs.is_empty());
        assert_eq!(plan.initial_overlap_dynamic_kinematic_pairs, 1);
    }
}
