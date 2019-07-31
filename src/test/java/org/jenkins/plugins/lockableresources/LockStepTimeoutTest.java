package org.jenkins.plugins.lockableresources;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.Optional;

/**
 * Check that locks are released after a timeout
 * @author getrostt
 */
public class LockStepTimeoutTest extends LockStepTestBase {
  @Rule
  public JenkinsRule j = new JenkinsRule();

  @Issue("JENKINS-50260")
  @Test
  public void timeoutBuildClearsLock() throws Exception {
    LockableResourcesManager.get().createResourceWithLabel("resource1", "my-label");

    WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "p");
    String script = "pipeline {\n" +
      "   agent any\n" +
      "   stages {\n" +
      "     stage('test'){\n" +
      "       failFast true\n" +
      "       options {\n" +
      "         timeout(time: 5, unit: 'SECONDS', activity: true)\n" +
      "       }\n" +
      "       parallel {\n" +
      "         stage('p1') {\n" +
      "           options {\n" +
      "             lock('my-label')\n" +
      "           }\n" +
      "           steps {\n" +
      "             echo 'locked!'\n" +
      "             sleep 10\n" +
      "           }\n" +
      "         }\n" +
      "         stage('p2') {\n" +
      "           steps {\n" +
      "             sleep 10\n" +
      "           }\n" +
      "         }\n" +
      "       }\n" +
      "     }\n" +
      "   }\n" +
      "}";
    p1.setDefinition(
      new CpsFlowDefinition(script));
    WorkflowRun b1 = p1.scheduleBuild2(0).waitForStart();

    //SemaphoreStep.waitForStart("wait-b/1", b1);
    //SemaphoreStep.success("wait-b/1", null);

    j.waitForMessage("locked!", b1);
    //SemaphoreStep.waitForStart("wait-a/1", b1);
    j.waitForCompletion(b1);
    j.assertBuildStatus(Result.ABORTED, b1);

    Optional<LockableResource> resource1 = LockableResourcesManager.get().getResources().stream()
      .filter(r -> r.getName().equals("resource1"))
      .findFirst();
    Assert.assertTrue(resource1.isPresent());
    Assert.assertFalse(resource1.get().isLocked());

    Assert.assertEquals(1, LockableResourcesManager.get().getFreeResourceAmount("my-label"));
  }

  @Issue("JENKINS-50260")
  @Test
  public void timeoutBuildClearsLockScripted() throws Exception {
    LockableResourcesManager.get().createResourceWithLabel("resource1", "my-label");

    WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "p");
    String script = "timeout(time: 5, unit: 'SECONDS') { lock(label: 'my-label') { semaphore 'wait-a' } }";
    p1.setDefinition(
      new CpsFlowDefinition(script));
    WorkflowRun b1 = p1.scheduleBuild2(0).waitForStart();

    j.waitForMessage("Lock acquired on [Label: my-label]", b1);
    SemaphoreStep.waitForStart("wait-a/1", b1);
    j.waitForCompletion(b1);
    j.assertBuildStatus(Result.ABORTED, b1);

    Optional<LockableResource> resource1 = LockableResourcesManager.get().getResources().stream()
      .filter(r -> r.getName().equals("resource1"))
      .findFirst();
    Assert.assertTrue(resource1.isPresent());
    Assert.assertFalse(resource1.get().isLocked());

    Assert.assertEquals(1, LockableResourcesManager.get().getFreeResourceAmount("my-label"));
  }
}
