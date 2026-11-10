package com.example.firststep.game.composition.identity;

import static org.junit.Assert.*;

import com.example.firststep.game.auth.Failure;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class CredentialClearCompletionTest {
  private static final class Timer implements CredentialClearCompletion.Scheduler {
    Runnable task;
    boolean removed;

    public Runnable schedule(Runnable task, long delayMillis) {
      this.task = task;
      return () -> removed = true;
    }

    void fire() {
      task.run();
    }
  }

  @Test
  public void noCallbackTimesOutAndIgnoresLateCallbacks() {
    Timer timer = new Timer();
    List<Failure> results = new ArrayList<>();
    int[] cancellations = {0};
    CredentialClearCompletion clear =
        new CredentialClearCompletion(results::add, () -> cancellations[0]++);
    clear.startDeadline(timer, 10);
    timer.fire();
    clear.success();
    clear.error();
    assertEquals(1, results.size());
    assertEquals(Failure.UNAVAILABLE, results.get(0));
    assertEquals(1, cancellations[0]);
    assertTrue(timer.removed);
  }

  @Test
  public void successAndErrorRemoveDeadlineAndCompleteOnce() {
    for (boolean success : new boolean[] {true, false}) {
      Timer timer = new Timer();
      List<Failure> results = new ArrayList<>();
      CredentialClearCompletion clear = new CredentialClearCompletion(results::add, () -> fail());
      clear.startDeadline(timer, 10);
      if (success) clear.success();
      else clear.error();
      timer.fire();
      assertEquals(1, results.size());
      assertEquals(success ? null : Failure.UNAVAILABLE, results.get(0));
      assertTrue(timer.removed);
    }
  }

  @Test
  public void cancellationLatchesBeforeSynchronousProviderCallback() {
    Timer timer = new Timer();
    List<Failure> results = new ArrayList<>();
    CredentialClearCompletion[] clear = new CredentialClearCompletion[1];
    clear[0] = new CredentialClearCompletion(results::add, () -> clear[0].success());
    clear[0].startDeadline(timer, 10);
    clear[0].cancel();
    timer.fire();
    assertEquals(1, results.size());
    assertEquals(Failure.CANCELED, results.get(0));
    assertTrue(timer.removed);
  }

  @Test
  public void timeoutLatchesBeforeSynchronousProviderCallback() {
    Timer timer = new Timer();
    List<Failure> results = new ArrayList<>();
    CredentialClearCompletion[] clear = new CredentialClearCompletion[1];
    clear[0] = new CredentialClearCompletion(results::add, () -> clear[0].success());
    clear[0].startDeadline(timer, 10);
    timer.fire();
    assertEquals(1, results.size());
    assertEquals(Failure.UNAVAILABLE, results.get(0));
  }

  @Test
  public void workerSuccessAndErrorDeliverOnlyOnOwnedThread() throws InterruptedException {
    Thread owner = Thread.currentThread();
    for (boolean success : new boolean[] {true, false}) {
      Timer timer = new Timer();
      List<Failure> results = new ArrayList<>();
      List<Runnable> queue = new ArrayList<>();
      CredentialClearCompletion clear =
          new CredentialClearCompletion(
              failure -> {
                assertSame(owner, Thread.currentThread());
                results.add(failure);
              },
              () -> fail());
      clear.startDeadline(timer, 10);
      Thread provider =
          new Thread(
              () -> {
                if (success) clear.providerSuccess(queue::add);
                else clear.providerError(queue::add);
              });
      provider.start();
      provider.join();
      assertTrue(results.isEmpty());
      assertEquals(1, queue.size());
      queue.remove(0).run();
      timer.fire();
      assertEquals(1, results.size());
      assertEquals(success ? null : Failure.UNAVAILABLE, results.get(0));
      assertTrue(timer.removed);
    }
  }
}
