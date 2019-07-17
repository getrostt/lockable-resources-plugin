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
    LockableResourcesManager.get().createResource("resource1");

    WorkflowJob p1 = j.jenkins.createProject(WorkflowJob.class, "p");
    String script = "pipeline {\n" +
      "   agent any\n" +
      "   stages {\n" +
      "     stage('test'){\n" +
      "       options {\n" +
      "         lock('resource1')\n" +
      "         timeout(time: 1, unit: 'SECONDS')\n" +
      "       }\n" +
      "       steps {\n" +
      "         echo 'locked!'\n" +
      "         semaphore 'wait-inside'\n" +
      "       }\n" +
      "     }\n" +
      "   }\n" +
      "}";
    p1.setDefinition(
      new CpsFlowDefinition(script));
    WorkflowRun b1 = p1.scheduleBuild2(0).waitForStart();
    j.waitForMessage("locked!", b1);
    SemaphoreStep.waitForStart("wait-inside/1", b1);
    j.waitForCompletion(b1);
    j.assertBuildStatus(Result.ABORTED, b1);

    Assert.assertEquals(1, LockableResourcesManager.get().getFreeResourceAmount("resource1"));
  }
}
