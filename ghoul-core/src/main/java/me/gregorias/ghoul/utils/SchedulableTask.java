package me.gregorias.ghoul.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO
/**
 * Wrapper task which execution should be scheduled and that may be called repeatedly.
 *
 * Setting up this task with non-zero delay causes the task to be called every delay time until
 * explicitly stopped. Zero delay causes this task to be one-shot.
 *
 * Calling schedule methods causes the task to be cancelled and rescheduled.
 */
public class SchedulableTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(SchedulableTask.class);

  private final Runnable mBaseTask;
  private final Runnable mWrapperTask;
  private final int mDelay;
  private final TimeUnit mDelayUnit;
  private final ScheduledExecutorService mExecutor;

  public SchedulableTask(Runnable baseTask,
                         int delay,
                         TimeUnit delayUnit,
                         ScheduledExecutorService executor) {
    mBaseTask = baseTask;
    mWrapperTask = new WrapperTask();
    mDelay = delay;
    mDelayUnit = delayUnit;
    mExecutor = executor;
  }

  public void scheduleNow() {
    scheduleWithInitialDelay(0, TimeUnit.MILLISECONDS);
  }

  public void scheduleWithInitialDelay(int initialDelay, TimeUnit delayUnit) {
    mExecutor.schedule(mWrapperTask, initialDelay, delayUnit);
  }

  public void stop() {

  }

  private class WrapperTask implements Runnable {

    @Override
    public void run() {
      try {
        mBaseTask.run();
      } catch (RuntimeException e) {
        LOGGER.error("run(): Caught RuntimeException in base task.", e);
      }

      if (mDelay > 0) {
        mExecutor.schedule(this, mDelay, mDelayUnit);
      }
    }
  }
}
