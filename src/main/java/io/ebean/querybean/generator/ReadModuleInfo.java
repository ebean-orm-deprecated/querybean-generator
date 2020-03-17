package io.ebean.querybean.generator;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper to read the current/existing prefixed entity classes.
 * <p>
 * These are added back on partial compile.
 */
class ReadModuleInfo {

  private final ProcessingContext ctx;

  public ReadModuleInfo(ProcessingContext ctx) {
    this.ctx = ctx;
  }

  List<String> read(Element element) {

    final List<? extends AnnotationMirror> mirrors = element.getAnnotationMirrors();

    for (AnnotationMirror mirror : mirrors) {
      final String name = mirror.getAnnotationType().asElement().toString();
      if (Constants.MODULEINFO.equals(name)) {
        return readEntities(mirror);
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<String> readEntities(AnnotationMirror mirror) {

    final Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = mirror.getElementValues();
    final Set<? extends Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> entries = elementValues.entrySet();

    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : entries) {
      if ("entities".equals(entry.getKey().getSimpleName().toString())) {
        final Object entitiesValue = entry.getValue().getValue();
        if (entitiesValue != null) {
          try {
            List<String> vals = new ArrayList<>();
            List<AnnotationValue> coll = (List<AnnotationValue>) entitiesValue;
            for (AnnotationValue annotationValue : coll) {
              vals.add((String) annotationValue.getValue());
            }
            return vals;
          } catch (Exception e) {
            ctx.logError(null, "Error reading ModuleInfo annotation, err " + e);
            return null;
          }
        }

      }
    }
    return null;
  }
}
