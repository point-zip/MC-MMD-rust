// 负责定义 Frame Queue 与具体 GPU 提交实现之间的 Seam。
package com.shiroha.mmdskin.client.draw;

import java.util.List;

@FunctionalInterface
public interface MmdDrawDevice {
    void draw(List<MmdDrawRequest> orderedRequests);
}

