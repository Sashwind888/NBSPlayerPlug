package sashwind.mc.plugin.nbsplayer.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import sashwind.mc.plugin.nbsplayer.Nbsplayer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia 与传统 Bukkit/Paper 兼容的通用调度器
 */
public final class FoliaScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        IS_FOLIA = folia;
    }

    private FoliaScheduler() {}

    /**
     * 判断当前服务端是否为 Folia 环境
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    private static Plugin getPlugin() {
        return Nbsplayer.getInstance();
    }

    private static class TaskWrapper implements WrappedTask {
        private volatile ScheduledTask scheduledTask;
        private volatile BukkitTask bukkitTask;
        private volatile boolean cancelled = false;

        public void setScheduledTask(ScheduledTask scheduledTask) {
            this.scheduledTask = scheduledTask;
            if (this.cancelled && scheduledTask != null) {
                scheduledTask.cancel();
            }
        }

        public void setBukkitTask(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
            if (this.cancelled && bukkitTask != null) {
                bukkitTask.cancel();
            }
        }

        @Override
        public void cancel() {
            this.cancelled = true;
            if (scheduledTask != null) {
                scheduledTask.cancel();
            }
            if (bukkitTask != null) {
                bukkitTask.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            if (cancelled) return true;
            if (scheduledTask != null) return scheduledTask.isCancelled();
            if (bukkitTask != null) return bukkitTask.isCancelled();
            return false;
        }
    }

    /**
     * 全局延迟调度（Ticks）
     */
    public static WrappedTask runGlobalLater(Runnable runnable, long delayTicks) {
        return runGlobalLater(task -> runnable.run(), delayTicks);
    }

    public static WrappedTask runGlobalLater(Consumer<WrappedTask> consumer, long delayTicks) {
        Objects.requireNonNull(consumer, "consumer");
        TaskWrapper wrapper = new TaskWrapper();
        long delay = Math.max(1L, delayTicks);

        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().runDelayed(getPlugin(), scheduledTask -> {
                if (!wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                }
            }, delay);
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                if (!wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                }
            }, delay);
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 全局定时调度（Ticks）
     */
    public static WrappedTask runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return runGlobalTimer(task -> runnable.run(), initialDelayTicks, periodTicks);
    }

    public static WrappedTask runGlobalTimer(Consumer<WrappedTask> consumer, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(consumer, "consumer");
        TaskWrapper wrapper = new TaskWrapper();
        long initialDelay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);

        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().runAtFixedRate(getPlugin(), scheduledTask -> {
                if (wrapper.isCancelled()) {
                    scheduledTask.cancel();
                    return;
                }
                consumer.accept(wrapper);
            }, initialDelay, period);
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
                if (wrapper.isCancelled()) {
                    return;
                }
                consumer.accept(wrapper);
            }, initialDelay, period);
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 全局立即调度
     */
    public static WrappedTask runGlobal(Runnable runnable) {
        return runGlobal(task -> runnable.run());
    }

    public static WrappedTask runGlobal(Consumer<WrappedTask> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        TaskWrapper wrapper = new TaskWrapper();

        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().run(getPlugin(), scheduledTask -> {
                if (!wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                }
            });
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (!wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                }
            });
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 在实体所属 Region 调度执行单次任务
     */
    public static WrappedTask runAtEntity(Entity entity, Runnable runnable) {
        return runAtEntity(entity, task -> runnable.run(), null);
    }

    public static WrappedTask runAtEntity(Entity entity, Consumer<WrappedTask> consumer, Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(consumer, "consumer");
        TaskWrapper wrapper = new TaskWrapper();

        if (IS_FOLIA) {
            ScheduledTask st = entity.getScheduler().run(getPlugin(), scheduledTask -> {
                if (!wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                }
            }, retired);
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (entity.isValid() && !wrapper.isCancelled()) {
                    consumer.accept(wrapper);
                } else if (!entity.isValid() && retired != null) {
                    retired.run();
                }
            });
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 在实体所属 Region 调度定时任务
     */
    public static WrappedTask runAtEntityTimer(Entity entity, Runnable runnable, Runnable retired, long initialDelayTicks, long periodTicks) {
        return runAtEntityTimer(entity, task -> runnable.run(), retired, initialDelayTicks, periodTicks);
    }

    public static WrappedTask runAtEntityTimer(Entity entity, Consumer<WrappedTask> consumer, Runnable retired, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(consumer, "consumer");
        TaskWrapper wrapper = new TaskWrapper();
        long initialDelay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);

        if (IS_FOLIA) {
            ScheduledTask st = entity.getScheduler().runAtFixedRate(getPlugin(), scheduledTask -> {
                if (wrapper.isCancelled()) {
                    scheduledTask.cancel();
                    return;
                }
                consumer.accept(wrapper);
            }, () -> {
                wrapper.cancel();
                if (retired != null) {
                    retired.run();
                }
            }, initialDelay, period);
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskTimer(getPlugin(), () -> {
                if (wrapper.isCancelled()) {
                    return;
                }
                if (!entity.isValid()) {
                    wrapper.cancel();
                    if (retired != null) {
                        retired.run();
                    }
                    return;
                }
                consumer.accept(wrapper);
            }, initialDelay, period);
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 异步调度单次任务
     */
    public static WrappedTask runAsync(Runnable runnable) {
        TaskWrapper wrapper = new TaskWrapper();
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler().runNow(getPlugin(), task -> {
                if (!wrapper.isCancelled()) {
                    runnable.run();
                }
            });
            wrapper.setScheduledTask(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
                if (!wrapper.isCancelled()) {
                    runnable.run();
                }
            });
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }

    /**
     * 异步定时调度（毫秒）
     */
    public static WrappedTask runAsyncTimer(Runnable runnable, long initialDelayMs, long periodMs) {
        TaskWrapper wrapper = new TaskWrapper();
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler().runAtFixedRate(getPlugin(), task -> {
                if (wrapper.isCancelled()) {
                    task.cancel();
                    return;
                }
                runnable.run();
            }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
            wrapper.setScheduledTask(st);
        } else {
            long initialTicks = Math.max(1L, initialDelayMs / 50L);
            long periodTicks = Math.max(1L, periodMs / 50L);
            BukkitTask bt = Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), () -> {
                if (wrapper.isCancelled()) {
                    return;
                }
                runnable.run();
            }, initialTicks, periodTicks);
            wrapper.setBukkitTask(bt);
        }
        return wrapper;
    }
}
