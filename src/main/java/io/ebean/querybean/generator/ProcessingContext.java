package io.ebean.querybean.generator;

import io.ebean.annotation.DbArray;
import io.ebean.annotation.DbJson;
import io.ebean.annotation.DbJsonB;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.MappedSuperclass;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.ebean.querybean.generator.Constants.GENERATED;

/**
 * Context for the source generation.
 */
class ProcessingContext {

  private final Types typeUtils;

  private final Filer filer;

  private final Messager messager;

  private final Elements elementUtils;

  private final boolean generatedAvailable;

  private final PropertyTypeMap propertyTypeMap = new PropertyTypeMap();

  ProcessingContext(ProcessingEnvironment processingEnv) {
    this.typeUtils = processingEnv.getTypeUtils();
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
    this.elementUtils = processingEnv.getElementUtils();
    this.generatedAvailable = isTypeAvailable(GENERATED);
  }

  private boolean isTypeAvailable(String canonicalName) {
    return null != elementUtils.getTypeElement(canonicalName);
  }

  /**
   * Gather all the fields (properties) for the given bean element.
   */
  List<VariableElement> allFields(Element element) {

    List<VariableElement> list = new ArrayList<>();
    gatherProperties(list, element);
    return list;
  }

  /**
   * Recursively gather all the fields (properties) for the given bean element.
   */
  private void gatherProperties(List<VariableElement> fields, Element element) {

    TypeElement typeElement = (TypeElement) element;
    TypeMirror superclass = typeElement.getSuperclass();
    Element mappedSuper = typeUtils.asElement(superclass);
    if (isMappedSuperOrInheritance(mappedSuper)) {
      gatherProperties(fields, mappedSuper);
    }

    List<VariableElement> allFields = ElementFilter.fieldsIn(element.getEnclosedElements());
    for (VariableElement field : allFields) {
      if (!ignoreField(field)) {
        fields.add(field);
      }
    }
  }

  /**
   * Find the annotation searching the inheritance hierarchy.
   */
  <A extends Annotation> A findAnnotation(TypeElement element, Class<A> anno) {

    final A annotation = element.getAnnotation(anno);
    if (annotation != null) {
      return annotation;
    }
    final TypeMirror typeMirror = element.getSuperclass();
    if (typeMirror.getKind() == TypeKind.NONE) {
      return null;
    }
    final TypeElement element1 = (TypeElement)typeUtils.asElement(typeMirror);
    return findAnnotation(element1, anno);
  }

  /**
   * Not interested in static, transient or Ebean internal fields.
   */
  private boolean ignoreField(VariableElement field) {
    return isStaticOrTransient(field) || ignoreEbeanInternalFields(field);
  }

  private boolean ignoreEbeanInternalFields(VariableElement field) {
    String fieldName = field.getSimpleName().toString();
    return fieldName.startsWith("_ebean") || fieldName.startsWith("_EBEAN");
  }

  private boolean isStaticOrTransient(VariableElement field) {
    Set<Modifier> modifiers = field.getModifiers();
    return (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT));
  }

  private boolean isMappedSuperOrInheritance(Element mappedSuper) {
    return mappedSuper.getAnnotation(MappedSuperclass.class) != null
      || mappedSuper.getAnnotation(Inheritance.class) != null;
  }

  private boolean isEntityOrEmbedded(Element mappedSuper) {
    return mappedSuper != null
      && (mappedSuper.getAnnotation(Entity.class) != null
      || mappedSuper.getAnnotation(Embeddable.class) != null);
  }

  /**
   * Return true if it is a DbJson field.
   */
  private static boolean dbJsonField(Element field) {
    return (field.getAnnotation(DbJson.class) != null
      || field.getAnnotation(DbJsonB.class) != null);
  }

  /**
   * Return true if it is a DbArray field.
   */
  private static boolean dbArrayField(Element field) {
    return (field.getAnnotation(DbArray.class) != null);
  }

  /**
   * Escape the type (e.g. java.lang.String) from the TypeMirror toString().
   */
  private static String typeDef(TypeMirror typeMirror) {
    if (typeMirror.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) typeMirror;
      return declaredType.asElement().toString();
    } else {
      return typeMirror.toString();
    }
  }

  PropertyType getPropertyType(VariableElement field) {

    TypeMirror typeMirror = field.asType();

    TypeMirror currentType = typeMirror;
    while (currentType != null) {
      PropertyType type = propertyTypeMap.getType(typeDef(currentType));
      if (type != null) {
        // simple scalar type
        return type;
      }
      // go up in class hierarchy
      TypeElement fieldType = (TypeElement) typeUtils.asElement(currentType);
      currentType = (fieldType == null) ? null : fieldType.getSuperclass();
    }

    if (dbJsonField(field)) {
      return propertyTypeMap.getDbJsonType();
    }

    if (dbArrayField(field)) {
      // get generic parameter type
      DeclaredType declaredType = (DeclaredType) typeMirror;
      String fullType = typeDef(declaredType.getTypeArguments().get(0));
      return new PropertyTypeArray(fullType, Split.shortName(fullType));
    }

    Element fieldType = typeUtils.asElement(typeMirror);

    if (fieldType != null) {
      // workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=544288
      fieldType = elementUtils.getTypeElement(fieldType.toString());

      if (fieldType.getKind() == ElementKind.ENUM) {
        String fullType = typeDef(typeMirror);
        return new PropertyTypeEnum(fullType, Split.shortName(fullType));
      }

      if (isEntityOrEmbedded(fieldType)) {
        //  public QAssocContact<QCustomer> contacts;
        return createPropertyTypeAssoc(typeDef(typeMirror));
      }

      if (typeMirror.getKind() == TypeKind.DECLARED) {
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() == 1) {
          TypeMirror argType = typeArguments.get(0);
          Element argElement = typeUtils.asElement(argType);
          if (isEntityOrEmbedded(argElement)) {
            return createPropertyTypeAssoc(typeDef(argElement.asType()));
          }
        }
      }
    }

    return null;
  }

  /**
   * Create the QAssoc PropertyType.
   */
  private PropertyType createPropertyTypeAssoc(String fullName) {

    String[] split = Split.split(fullName);
    String propertyName = "QAssoc" + split[1];
    String packageName = packageAppend(split[0], "query.assoc");
    return new PropertyTypeAssoc(propertyName, packageName);
  }

  /**
   * Prepend the package to the suffix taking null into account.
   */
  private String packageAppend(String origPackage, String suffix) {
    if (origPackage == null) {
      return suffix;
    } else {
      return origPackage + "." + suffix;
    }
  }

  /**
   * Create a file writer for the given class name.
   */
  JavaFileObject createWriter(String factoryClassName, Element originatingElement) throws IOException {
    return filer.createSourceFile(factoryClassName, originatingElement);
  }

  /**
   * Log an error message.
   */
  void logError(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
  }

  /**
   * Log a info message.
   */
  void logNote(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
  }

  /**
   * Return true if javax.annotation.Generated is available in the claspath.
   */
  boolean isGeneratedAvailable() {
    return generatedAvailable;
  }
}
