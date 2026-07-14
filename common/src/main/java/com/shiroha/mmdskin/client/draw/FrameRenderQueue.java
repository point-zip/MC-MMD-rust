// 负责收集、排序并在批处理边界统一提交 MMD 绘制请求。
package com.shiroha.mmdskin.client.draw;

import com.shiroha.mmdskin.client.metrics.FrameQueueMetricsPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FrameRenderQueue implements FrameQueueMetricsPort {
    private static final Comparator<QueuedRequest> DRAW_ORDER = Comparator
            .comparing((QueuedRequest queued) -> queued.request.transparent())
            .thenComparing((QueuedRequest queued) -> queued.request.transparent()
                    ? -queued.request.snapshot().cameraDistanceSquared()
                    : 0.0D)
            .thenComparingLong(QueuedRequest::sequence);

    private final List<QueuedRequest> requests = new ArrayList<>();
    private long nextSequence;

    public void enqueue(MmdDrawRequest request) {
        enqueue(request, null);
    }

    public void enqueue(MmdDrawRequest request, AutoCloseable frameLease) {
        requests.add(new QueuedRequest(
                Objects.requireNonNull(request, "request"), nextSequence++, frameLease));
    }

    @Override
    public int size() {
        return requests.size();
    }

    public void clear() {
        requests.forEach(QueuedRequest::closeLease);
        requests.clear();
        nextSequence = 0L;
    }

    public void flush(MmdDrawDevice device) {
        Objects.requireNonNull(device, "device");
        if (requests.isEmpty()) {
            return;
        }

        List<QueuedRequest> queued = new ArrayList<>(requests);
        List<MmdDrawRequest> ordered = queued.stream()
                .sorted(DRAW_ORDER)
                .map(QueuedRequest::request)
                .toList();
        requests.clear();
        nextSequence = 0L;
        try {
            device.draw(ordered);
        } finally {
            queued.forEach(QueuedRequest::closeLease);
        }
    }

    private record QueuedRequest(MmdDrawRequest request, long sequence, AutoCloseable frameLease) {
        private void closeLease() {
            if (frameLease == null) {
                return;
            }
            try {
                frameLease.close();
            } catch (Exception exception) {
                throw new IllegalStateException("failed to release frame model lease", exception);
            }
        }
    }
}
