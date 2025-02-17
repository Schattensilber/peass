package de.dagere.peass.dependency.analysis;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.peass.dependency.kiekerTemp.LogsReaderCompositeStage;
import kieker.analysis.stage.DynamicEventDispatcher;
import kieker.analysis.stage.IEventMatcher;
import kieker.analysis.stage.ImplementsEventMatcher;
import kieker.analysis.trace.execution.ExecutionRecordTransformationStage;
import kieker.analysis.trace.reconstruction.TraceReconstructionStage;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.model.repository.SystemModelRepository;
import teetime.framework.Configuration;

public class KiekerReaderConfiguration extends Configuration {
   
   private static final Logger LOG = LogManager.getLogger(KiekerReaderConfiguration.class);
   
   protected SystemModelRepository systemModelRepositoryNew = new SystemModelRepository();
   
   public KiekerReaderConfiguration() {
      super();
   }

   public CalledMethodStage exampleReader(final File kiekerTraceFolder, final String prefix, final ModuleClassMapping mapping) {
      TraceReconstructionStage traceReconstructionStage = prepareTillExecutionTrace(kiekerTraceFolder);

      CalledMethodStage myStage = new CalledMethodStage(systemModelRepositoryNew, prefix, mapping);
      this.connectPorts(traceReconstructionStage.getExecutionTraceOutputPort(), myStage.getInputPort());

      LOG.debug("Reading from {}", kiekerTraceFolder);
      return myStage;
   }

   protected TraceReconstructionStage prepareTillExecutionTrace(final File kiekerTraceFolder) {
      final ExecutionRecordTransformationStage executionRecordTransformationStage = prepareTillExecutions(kiekerTraceFolder);
      
      TraceReconstructionStage traceReconstructionStage = new TraceReconstructionStage(systemModelRepositoryNew, TimeUnit.MILLISECONDS, false, Long.MAX_VALUE);
      this.connectPorts(executionRecordTransformationStage.getOutputPort(), traceReconstructionStage.getInputPort());
      return traceReconstructionStage;
   }
   
   protected ExecutionRecordTransformationStage prepareTillExecutions(final File kiekerTraceFolder) {
      List<File> inputDirs = new LinkedList<File>();
      inputDirs.add(kiekerTraceFolder);
      LogsReaderCompositeStage logReaderStage = new LogsReaderCompositeStage(inputDirs, true, 4096);

      final ExecutionRecordTransformationStage executionRecordTransformationStage = new ExecutionRecordTransformationStage(systemModelRepositoryNew);
      

      final DynamicEventDispatcher dispatcher = new DynamicEventDispatcher(null, false, true, false);
      final IEventMatcher<? extends OperationExecutionRecord> operationExecutionRecordMatcher = new ImplementsEventMatcher<>(OperationExecutionRecord.class, null);
      dispatcher.registerOutput(operationExecutionRecordMatcher);

      this.connectPorts(logReaderStage.getOutputPort(), dispatcher.getInputPort());
      this.connectPorts(operationExecutionRecordMatcher.getOutputPort(), executionRecordTransformationStage.getInputPort());
      return executionRecordTransformationStage;
   }
}