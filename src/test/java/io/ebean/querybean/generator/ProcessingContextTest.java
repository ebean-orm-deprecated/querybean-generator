package io.ebean.querybean.generator;

import static java.util.Arrays.asList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Manifest;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ProcessingContextTest {

  @Test
  public void generate_and_read_lots_of_packages() throws IOException {
    Set<String> packages = new LinkedHashSet<>();
    for (int i = 0; i < 1000; i++) {
      packages.add("com.example.i" + i);
    }
    manifestTester(packages);
  }

  @Test
  public void generate_and_read_single_package() throws IOException {
    Set<String> packages = Collections.singleton("com.example");
    manifestTester(packages);
  }

  public void manifestTester(final Set<String> packages) throws IOException {
    final StringWriter stringWriter = new StringWriter();
    ProcessingContext.writeManifest(stringWriter, packages);
    final Manifest manifest = new Manifest(new ByteArrayInputStream(stringWriter.toString().getBytes(StandardCharsets.UTF_8)));
    final String[] manifestAttribute = manifest.getMainAttributes()
                                               .getValue("packages")
                                               .split(" *(,|;| ) *");
    Assert.assertEquals(packages, asList(manifestAttribute));
  }

}
