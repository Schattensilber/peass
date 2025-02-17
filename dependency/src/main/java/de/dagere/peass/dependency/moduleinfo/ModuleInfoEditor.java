package de.dagere.peass.dependency.moduleinfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;

import de.dagere.peass.dependency.changesreading.JavaParserProvider;

/**
 * If a module-info exists, kieker needs to be added as required, since its used directly in the source code.
 * 
 * @author DaGeRe
 *
 */
public enum ModuleInfoEditor {
   ;

   public static void addKiekerRequires(final File moduleInfoFile) throws IOException {
      CompilationUnit unit = JavaParserProvider.parse(moduleInfoFile);
      ModuleDeclaration module = unit.getModule().get();

      addRequires(module, "kieker");
      addRequires(module, "kopeme.core"); // only needed until Kieker contains the DurationRecord

      Files.write(moduleInfoFile.toPath(), unit.toString().getBytes(StandardCharsets.UTF_8));
   }

   private static void addRequires(final ModuleDeclaration module, final String name) {
      ModuleRequiresDirective requirement = new ModuleRequiresDirective();
      requirement.setName(name);
      module.getDirectives().add(requirement);
   }
}
