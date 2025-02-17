package de.dagere.peass.dependency.kiekerTemp;


import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import kieker.analysis.plugin.reader.depcompression.AbstractDecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.Bzip2DecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.DeflateDecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.GZipDecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.NoneDecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.XZDecompressionFilter;
import kieker.analysis.plugin.reader.depcompression.ZipDecompressionFilter;
import kieker.analysis.plugin.reader.util.FSReaderUtil;
import kieker.analysis.source.file.AbstractEventDeserializer;
import kieker.analysis.source.file.AbstractMapDeserializer;
import kieker.analysis.source.file.MapFileFilter;
import kieker.common.record.IMonitoringRecord;
import kieker.common.registry.reader.ReaderRegistry;
import kieker.common.util.filesystem.FSUtil;
import teetime.stage.basic.AbstractTransformation;

/**
 * Copy of Kiekers stage; will be removed as soon as Kieker publishes a bugfix version
 */
public class DirectoryReaderStage extends AbstractTransformation<File, IMonitoringRecord> {

   private final FilenameFilter mapFilter = new MapFileFilter();
   private final Integer dataBufferSize;
   private final boolean verbose;

   public DirectoryReaderStage(final boolean verbose, final int dataBufferSize) {
      this.verbose = verbose;
      this.dataBufferSize = dataBufferSize;
   }

   @Override
   protected void execute(final File directory) {
      final ReaderRegistry<String> registry = new ReaderRegistry<>();
      /** read all map files. */
      final File[] mapFiles = directory.listFiles(this.mapFilter);
      if (mapFiles == null) {
         this.logger.error("{} is not a proper directory.", directory.getAbsolutePath());
      } else {
         for (final File mapFile : mapFiles) {
            final String mapFileName = mapFile.getName();
            try (InputStream inputStream = Files.newInputStream(mapFile.toPath(), StandardOpenOption.READ)) {
               this.readMapFile(inputStream, mapFileName, registry);
            } catch (final IOException e) {
               this.logger.error("Cannot find map file {}.", mapFileName);
            }
         }

         /** read log files. */
         try {
            try (Stream<Path> stream = Files.list(directory.toPath())){
               stream.sorted().forEach(logFilePath -> {
                  final File logFile = logFilePath.toFile();
                  final String logFileName = logFile.getName();
                  try (InputStream inputStream = Files.newInputStream(logFile.toPath(), StandardOpenOption.READ)) {
                     this.readLogFile(inputStream, logFileName, registry);
                  } catch (final IOException e) {
                     this.logger.error("Cannot find log file {}.", logFileName);
                  }
               });
            }
         } catch (final IOException e1) {
            this.logger.error("Cannot process directory {}", directory.getAbsolutePath());
         }

      }
   }

   private AbstractDecompressionFilter findDecompressionFilterByExtension(final String filename) {
      final String extension = FSReaderUtil.getExtension(filename);
      if (FSUtil.GZIP_FILE_EXTENSION.equals(extension)) {
         return new GZipDecompressionFilter();
      } else if (FSUtil.DEFLATE_FILE_EXTENSION.equals(extension)) {
         return new DeflateDecompressionFilter();
      } else if (FSUtil.XZ_FILE_EXTENSION.equals(extension)) {
         return new XZDecompressionFilter();
      } else if (FSUtil.ZIP_FILE_EXTENSION.equals(extension)) {
         return new ZipDecompressionFilter();
      } else if (FSUtil.BZIP2_FILE_EXTENSION.equals(extension)) {
         return new Bzip2DecompressionFilter();
      } else {
         return new NoneDecompressionFilter();
      }
   }

   /**
    * Read a map file stream and initialize the registry.
    *
    * @param inputStream
    *            the input stream
    * @param logFileName
    *            the name of the log file used for user feedback
    * @param registry
    *            string registry
    */
   private void readMapFile(final InputStream inputStream, final String mapFileName, final ReaderRegistry<String> registry) {
      final AbstractDecompressionFilter decompressionFilter = this.findDecompressionFilterByExtension(mapFileName);
      this.logger.debug("Reading map file {}", mapFileName);

      /** detecting correct map file deserializer. */
      final Class<? extends AbstractMapDeserializer> deserializerClass;
      if (decompressionFilter instanceof NoneDecompressionFilter) {
         deserializerClass = FSReaderUtil.findMapDeserializer(mapFileName);
      } else {
         final String baseName = mapFileName.substring(0, mapFileName.lastIndexOf('.') - 1);
         deserializerClass = FSReaderUtil.findMapDeserializer(baseName);
      }

      try (InputStream chainedInputStream = decompressionFilter.chainInputStream(inputStream)) {
         try {
            final AbstractMapDeserializer deserializer = deserializerClass.getConstructor().newInstance();
            deserializer.processDataStream(decompressionFilter.chainInputStream(inputStream), registry, mapFileName);
         } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
               | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            this.logger.error("Cannot instantiate filter {} for decompression.", deserializerClass.getName());
         }
      } catch (final IOException ex) {
         this.logger.error("Reading map file {} failed.", mapFileName);
      }
   }

   /**
    * Read a log file stream and produce Kieker events.
    *
    * @param inputStream
    *            the input stream
    * @param logFileName
    *            the name of the log file used for user feedback
    * @param registry
    *            string registry
    */
   private void readLogFile(final InputStream inputStream, final String logFileName, final ReaderRegistry<String> registry) {
      final AbstractDecompressionFilter decompressionFilter = this.findDecompressionFilterByExtension(logFileName);
      if (this.verbose) {
         this.logger.info("Reading log file {}", logFileName);
      } else {
         this.logger.debug("Reading log file {}", logFileName);
      }

      /** detecting correct log file deserializer. */
      final Class<? extends AbstractEventDeserializer> deserializerClass;
      if (decompressionFilter instanceof NoneDecompressionFilter) {
         deserializerClass = FSReaderUtil.findEventDeserializer(logFileName);
      } else {
         final String baseName = logFileName.substring(0, logFileName.lastIndexOf('.'));
         deserializerClass = FSReaderUtil.findEventDeserializer(baseName);
      }

      if (deserializerClass != null) {
         try (InputStream chainedInputStream = decompressionFilter.chainInputStream(inputStream)) {
            try {
               final AbstractEventDeserializer deserializer = deserializerClass.getConstructor(Integer.class, ReaderRegistry.class)
                     .newInstance(this.dataBufferSize, registry);

               deserializer.processDataStream(chainedInputStream, this.outputPort);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                  | InvocationTargetException | NoSuchMethodException | SecurityException e) {
               this.logger.error("Cannot instantiate filter {} for decompression.", deserializerClass.getName());
            }
         } catch (final IOException e) {
            this.logger.error("Reading log file {} failed.", logFileName);
         }
      } else {
         this.logger.debug("Skipping file {}, as the extension indicates that it is not a log file.", logFileName);
      }
   }

}
