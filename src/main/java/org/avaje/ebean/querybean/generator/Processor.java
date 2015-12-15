package org.avaje.ebean.querybean.generator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Process compiled entity beans and generates 'query beans' for them.
 */
public class Processor extends AbstractProcessor {

  private ProcessingContext processingContext;

  public Processor() {
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.processingContext = new ProcessingContext(processingEnv);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {

    Set<String> annotations = new LinkedHashSet<>();
    annotations.add(Entity.class.getCanonicalName());
    annotations.add(Embeddable.class.getCanonicalName());
    return annotations;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_7;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    int entityCount = 0;
    int embeddableCount = 0;

    for (Element element : roundEnv.getElementsAnnotatedWith(Entity.class)) {
      generateQueryBeans(element);
      entityCount++;
    }

    for (Element element : roundEnv.getElementsAnnotatedWith(Embeddable.class)) {
      generateQueryBeans(element);
      embeddableCount++;
    }

    processingContext.writeManifest();

    if (entityCount > 0 || embeddableCount > 0) {
      processingContext.logNote("Generated query beans for [" + entityCount + "] entities [" + embeddableCount + "] embeddable");
    }

    return true;
  }

  private void generateQueryBeans(Element element) {
    try {
      SimpleQueryBeanWriter beanWriter = new SimpleQueryBeanWriter((TypeElement)element, processingContext);
      beanWriter.writeRootBean();
      beanWriter.writeAssocBean();

    } catch (IOException e) {
      processingContext.logError(element, "Error generating query beans: " + e);
    }
  }

}
