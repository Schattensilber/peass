package de.dagere.peass.ci;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.config.TestSelectionConfig;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.KiekerConfig;
import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependency.persistence.InitialVersion;
import de.dagere.peass.dependency.persistence.VersionStaticSelection;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;

public class TestContinuousDependencyReader {

   @Test
   public void testNoChangeHappened() throws JsonGenerationException, JsonMappingException, IOException {
      
      
      ResultsFolders resultsFolders = new ResultsFolders(new File("target/current_results"), "current");
      
      StaticTestSelection value = new StaticTestSelection();
      value.setInitialversion(new InitialVersion());
      value.getVersions().put("A", new VersionStaticSelection());
      Constants.OBJECTMAPPER.writeValue(resultsFolders.getStaticTestSelectionFile(), value);
      
      ContinuousDependencyReader reader = new ContinuousDependencyReader(new TestSelectionConfig(1, false), new ExecutionConfig(), new KiekerConfig(),
            new PeassFolders(new File("target/current")), resultsFolders, new EnvironmentVariables());

      reader.getDependencies(null, "git:dummyUrl");
   }
}
