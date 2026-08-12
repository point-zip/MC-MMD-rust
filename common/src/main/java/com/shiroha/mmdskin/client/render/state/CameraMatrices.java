// 捕获 26.2 相机渲染状态中的视图与投影矩阵，供 MMD 自建 RenderPass 使用。
package com.shiroha.mmdskin.client.render.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/**
 * 26.2 中实体渲染不再有全局 ModelViewMat/ProjMat，需要从
 * {@code GameRenderer#gameRenderState().levelRenderState.cameraRenderState()}
 * 自行构造。约定与官方 "Rendering in the World" 例程一致：
 * 顶点使用相机相对坐标（世界坐标 - camera），因此 ModelViewMat 只含视图旋转
 * （viewRotationMatrix 不含平移），顶点坐标自身已经携带 -pos。
 */
public record CameraMatrices(Matrix4f modelView, Matrix4f projection) {

    public static CameraMatrices capture() {
        CameraRenderState camera = Minecraft.getInstance().gameRenderer
                .gameRenderState().levelRenderState.cameraRenderState;
        Matrix4f projection = camera.projectionMatrix;
        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix);
        return new CameraMatrices(modelView, projection);
    }
}
