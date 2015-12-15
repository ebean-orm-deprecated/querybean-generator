package org.avaje.ebean.querybean.generator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 */
public class Processor extends AbstractProcessor {

  private ProcessingContext processingContext;

  boolean count;

  public Processor() {

  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.processingContext = new ProcessingContext(processingEnv);
    System.out.println("Initialised Processor ");
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {

    Set<String> annotations = new LinkedHashSet<>();
    annotations.add(Entity.class.getCanonicalName());
    annotations.add(MappedSuperclass.class.getCanonicalName());
    annotations.add(Embeddable.class.getCanonicalName());
    return annotations;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_7;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {


    processingContext.logNote("process ....");

    //loadMappedSuperclasses(roundEnv);

    Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(Entity.class);
    processingContext.logNote("Found "+elementsAnnotatedWith);

    for (Element element : elementsAnnotatedWith) {
      try {
        processingContext.logNote("process bean ... "+element.getSimpleName());
        TypeElement type = (TypeElement)element;
        SimpleQueryBeanWriter beanWriter = new SimpleQueryBeanWriter(type, processingContext);
        beanWriter.writeRootBean();
        beanWriter.writeAssocBean();

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    processingContext.writeManifest();

    return true;
  }

//  private void loadMappedSuperclasses(RoundEnvironment roundEnv) {
//    Set<? extends Element> mapped = roundEnv.getElementsAnnotatedWith(MappedSuperclass.class);
//    for (Element mappedSuper : mapped) {
//      processingContext.addMappedSuper(mappedSuper);
//    }
//  }

}
