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
import javax.lang.model.util.Types;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.Diagnostic.Kind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
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

    List<VariableElement> allFields = ElementFilter.fieldsIn(element.getEnclosedElements());
    for (VariableElement field : allFields) {
      if (!ignoreField(field)) {
        fields.add(field);
      }
    }
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

  private boolean isMappedSuper(Element mappedSuper) {
    return mappedSuper.getAnnotation(MappedSuperclass.class) != null;
  }

  private boolean isEntityOrEmbedded(Element mappedSuper) {
    return mappedSuper.getAnnotation(Entity.class) != null
        || mappedSuper.getAnnotation(Embeddable.class) != null;
  }

  /**
   * Return true if it is a DbJson field.
   */
  public static boolean dbJsonField(Element field) {
    return (field.getAnnotation(DbJson.class) != null
        || field.getAnnotation(DbJsonB.class) != null);
  }

  /**
   * Return true if it is a DbArray field.
   */
  public static boolean dbArrayField(Element field) {
    return (field.getAnnotation(DbArray.class) != null);
  }

  public PropertyType getPropertyType(VariableElement field) {

    TypeMirror typeMirror = field.asType();

    TypeMirror currentType = typeMirror;
    while (currentType != null) {
    	PropertyType type = propertyTypeMap.getType(currentType.toString());
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
      DeclaredType declaredType = (DeclaredType)typeMirror;
      String fullType = declaredType.getTypeArguments().get(0).toString();
      return new PropertyTypeArray(fullType, Split.shortName(fullType));
    }

    Element fieldType = typeUtils.asElement(typeMirror);

    if (fieldType != null) {
      if (fieldType.getKind() == ElementKind.ENUM) {
        String fullType = typeMirror.toString();
        return new PropertyTypeEnum(fullType, Split.shortName(fullType));
      }

      if (isEntityOrEmbedded(fieldType)) {
        //  public QAssocContact<QCustomer> contacts;
        return createPropertyTypeAssoc(typeMirror.toString());
      }

      if (typeMirror.getKind() == TypeKind.DECLARED) {
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() == 1) {
          TypeMirror argType = typeArguments.get(0);
          Element argElement = typeUtils.asElement(argType);
          if (isEntityOrEmbedded(argElement)) {
            return createPropertyTypeAssoc(argElement.asType().toString());
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
      writeManifest(StandardLocation.CLASS_OUTPUT);
    } catch (IOException e) {
      logError(null, "Error writing manifest " + e);
    }
  }

  private void writeManifest(JavaFileManager.Location location) throws IOException {

	  try {
		  // When eclipse does a partial build, not all classes are processed by the processor
		  // which leads to an incomplete typequery.mf file. (some packages missing)
		  // Then, the enhancer will not enhance all packages.
		  FileObject descFile = filer.getResource(location, "", META_INF + "/" + EBEAN_TYPEQUERY_MF);
		  Reader reader = descFile.openReader(true);
		  BufferedReader br = new BufferedReader(reader);
		  try {
			  String line = br.readLine();
			  line = line.replace("packages:", "").trim();
			  String[] pkgs = line.split(",");
			  for (String pkg : pkgs) {
				  packages.add(pkg.trim());
			  }
		  } finally {
			  br.close();
		  }
		  messager.printMessage(Kind.NOTE, "... re-read the manifest.");
	  } catch (IOException e) {
		  // file does not exist, ignore and build new one
	  }
	
    FileObject resource = filer.createResource(location, "", META_INF + "/" + EBEAN_TYPEQUERY_MF);

    try (Writer writer = resource.openWriter()) {
      writeManifest(writer, packages);
    }
  }

  //Visible for testing
  static void writeManifest(final Writer writer, Set<String> packages) throws IOException {
    writer.append("packages: ");
    int count = 0;
    for (String aPackage : packages) {
      if (count++ > 0) {
        writer.append(",\n ");
      }
      writer.append(aPackage);
    }

    writer.append(NEWLINE).append(NEWLINE);
    writer.flush();
  }
}
