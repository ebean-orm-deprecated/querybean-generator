package org.avaje.ebean.querybean.generator;

import com.avaje.ebean.annotation.DbJson;
import com.avaje.ebean.annotation.DbJsonB;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Context for the source generation.
 */
public class ProcessingContext {

  private static final String EBEAN_TYPEQUERY_MF = "ebean-typequery.mf";

  private static final String META_INF = "META-INF";

  private static final String NEWLINE = "\n";

  private final Types typeUtils;
  private final Filer filer;
  private final Messager messager;

  private final PropertyTypeMap propertyTypeMap = new PropertyTypeMap();

  /**
   * The set of packages that query beans are generated into.
   */
  private final Set<String> packages = new LinkedHashSet<>();

  private boolean writeOnce;

  public ProcessingContext(ProcessingEnvironment processingEnv) {
    this.typeUtils = processingEnv.getTypeUtils();
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
  }

  /**
   * Gather all the fields (properties) for the given bean element.
   */
  public List<VariableElement> allFields(Element element) {

    List<VariableElement> list = new ArrayList<>();
    gatherProperties(list, element);
    return list;
  }

  /**
   * Recursively gather all the fields (properties) for the given bean element.
   */
  protected void gatherProperties(List<VariableElement> fields, Element element) {

    TypeElement typeElement = (TypeElement) element;
    TypeMirror superclass = typeElement.getSuperclass();
    Element mappedSuper = typeUtils.asElement(superclass);
    if (isMappedSuper(mappedSuper)) {
      gatherProperties(fields, mappedSuper);
    }

    fields.addAll(ElementFilter.fieldsIn(element.getEnclosedElements()));
  }

  private boolean isMappedSuper(Element mappedSuper) {
    return mappedSuper.getAnnotation(MappedSuperclass.class) != null;
  }

  private boolean isEntity(Element mappedSuper) {
    return mappedSuper.getAnnotation(Entity.class) != null;
  }

  /**
   * Return true if it is a DbJson field.
   */
  public static boolean dbJsonField(Element field) {

    return (field.getAnnotation(DbJson.class) != null
        || field.getAnnotation(DbJsonB.class) != null);
  }

  public PropertyType getPropertyType(VariableElement field, String destPackage) {

    String fieldName = field.getSimpleName().toString();
    TypeMirror typeMirror = field.asType();

    PropertyType type = propertyTypeMap.getType(typeMirror.toString());
    if (type != null) {
      // simple scalar type
      return type;
    }

    if (dbJsonField(field)) {
      return propertyTypeMap.getDbJsonType();
    }

    Element fieldType = typeUtils.asElement(typeMirror);

    if (fieldType != null) {
      if (fieldType.getKind() == ElementKind.ENUM) {
        String fieldTypeClassName = typeMirror.toString();
        return new PropertyTypeEnum(fieldTypeClassName, deriveShortName(fieldTypeClassName));
      }

      if (isEntity(fieldType)) {
        //  public QAssocContact<QCustomer> contacts;
        String propertyName = "QAssoc" + deriveShortName(typeMirror.toString());
        return new PropertyTypeAssoc(propertyName, destPackage + ".assoc");
      }

      if (typeMirror.getKind() == TypeKind.DECLARED) {
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() == 1) {
          TypeMirror argType = typeArguments.get(0);
          Element argElement = typeUtils.asElement(argType);
          if (isEntity(argElement)) {
            String propertyName = "QAssoc" + deriveShortName(argElement.asType().toString());
            return new PropertyTypeAssoc(propertyName, destPackage + ".assoc");
          }
        }
      }
    }
    logNote("... no PropertyType for fieldName:" + fieldName + " type:" + typeMirror);
    return null;
  }

  protected String deriveShortName(String className) {
    int startPos = className.lastIndexOf('.');
    if (startPos == -1) {
      return className;
    }
    return className.substring(startPos + 1);
  }

  /**
   * Create a file writer for the given class name.
   */
  public JavaFileObject createWriter(String factoryClassName) throws IOException {
    return filer.createSourceFile(factoryClassName);
  }

  /**
   * Log an error message.
   */
  public void logError(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
  }

  /**
   * Log a info message.
   */
  public void logNote(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
  }

  /**
   * Add a package that a query bean is generated into.
   */
  public void addPackage(String destPackage) {
    packages.add(destPackage);
  }

  public void writeManifest() {

    if (writeOnce) {
      return;
    }
    logNote("... writing manifest " + EBEAN_TYPEQUERY_MF);
    writeOnce = true;
    try {
      JavaFileManager.Location location = StandardLocation.CLASS_OUTPUT;

      FileObject resource = filer.createResource(location, "", META_INF + "/" + EBEAN_TYPEQUERY_MF);

      Writer writer = resource.openWriter();
      writer.append("packages: ");
      int count = 0;
      for (String aPackage : packages) {
        if (count++ > 0) {
          writer.append(",");
        }
        writer.append(aPackage);
      }

      writer.append(NEWLINE).append(NEWLINE);
      writer.flush();
      writer.close();
    } catch (IOException e) {
      logError(null, "Error writing manifest " + e);
    }
  }
}
