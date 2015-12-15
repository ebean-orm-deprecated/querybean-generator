package org.avaje.ebean.querybean.generator;


import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.persistence.Entity;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A simple implementation that generates and writes query beans.
 */
public class SimpleQueryBeanWriter {

  //protected static final Logger logger = LoggerFactory.getLogger(SimpleQueryBeanWriter.class);

  public static final String NEWLINE = "\n";

  private final Set<String> importTypes = new TreeSet<>();

  private final List<PropertyMeta> properties = new ArrayList<>();

  private final TypeElement element;

  private final ProcessingContext processingContext;

  private final String beanFullName;
  private boolean writingAssocBean;

  private String destPackage;
  protected String origDestPackage;

  protected String shortName;
  protected String origShortName;

  protected Writer writer;


  public SimpleQueryBeanWriter(TypeElement element, ProcessingContext processingContext) {
    this.element = element;
    this.processingContext = processingContext;

    this.beanFullName = element.getQualifiedName().toString();
    this.destPackage = derivePackage(beanFullName)+".query";
    this.shortName = deriveShortName(beanFullName);

    processingContext.addPackage(destPackage);

    System.out.println("beanFullName [" + beanFullName + "] destPackage[" + destPackage + "] shortName:"+shortName);
  }

  protected void gatherPropertyDetails() {

    importTypes.add(beanFullName);//asDotNotation(element.getQualifiedName().toString()));
    importTypes.add("org.avaje.ebean.typequery.TQRootBean");
    importTypes.add("org.avaje.ebean.typequery.TypeQueryBean");
    importTypes.add("com.avaje.ebean.EbeanServer");

    addClassProperties();
  }

  /**
   * Recursively add properties from the inheritance hierarchy.
   * <p>
   * Includes properties from mapped super classes and usual inheritance.
   * </p>
   */
  protected void addClassProperties() {

    List<VariableElement> fields = processingContext.allFields(element);

    for (VariableElement field : fields) {
      String name = field.getSimpleName().toString();
      //ElementKind kind = field.getKind();
      TypeMirror typeMirror = field.asType();

      PropertyType type = processingContext.getPropertyType(field, destPackage);
      if (type == null) {
        System.out.println("No support for field [" + name + "] desc[" + typeMirror + "] signature [" + "" + "]");
      } else {
        type.addImports(importTypes);
        System.out.println("field name: "+name);
        properties.add(new PropertyMeta(name, type));
      }
    }
  }

  /**
   * Write the type query bean (root bean).
   */
  public void writeRootBean() throws IOException {

    gatherPropertyDetails();

    if (isEntity()) {
      writer = createFileWriter();

      writePackage();
      writeImports();
      writeClass();
      writeAlias();
      writeFields();
      writeConstructors();
      writeClassEnd();

      writer.flush();
      writer.close();
    }
  }

  private boolean isEntity() {
    return element.getAnnotation(Entity.class) != null;
  }

  /**
   * Write the type query assoc bean.
   */
  public void writeAssocBean() throws IOException {

    writingAssocBean = true;
    origDestPackage = destPackage;
    destPackage = destPackage+".assoc";
    origShortName = shortName;
    shortName = "Assoc"+shortName;

    prepareAssocBeanImports();

    writer = createFileWriter();

    writePackage();
    writeImports();
    writeClass();
    writeFields();
    writeConstructors();
    writeClassEnd();

    writer.flush();
    writer.close();
  }

  /**
   * Prepare the imports for writing assoc bean.
   */
  protected void prepareAssocBeanImports() {

    importTypes.remove("org.avaje.ebean.typequery.TQRootBean");
    importTypes.remove("com.avaje.ebean.EbeanServer");
    importTypes.add("org.avaje.ebean.typequery.TQAssocBean");
    if (isEntity()) {
      importTypes.add("org.avaje.ebean.typequery.TQProperty");
      importTypes.add(origDestPackage + ".Q" + origShortName);
    }

//    if (!config.isAopStyle()) {
//      importTypes.add("org.avaje.ebean.typequery.TQPath");
//    }

    // remove imports for the same package
    Iterator<String> importsIterator = importTypes.iterator();
    while (importsIterator.hasNext()){
      String importType = importsIterator.next();
      // there are no subpackages so just use startsWith(destPackage)
      if (importType.startsWith(destPackage)) {
        importsIterator.remove();
      }
    }
  }

  /**
   * Write constructors.
   */
  protected void writeConstructors() throws IOException {

    if (writingAssocBean) {
      writeAssocBeanFetch();
      writeAssocBeanConstructor();
    } else {
      writeRootBeanConstructor();
    }
  }

  /**
   * Write the constructors for 'root' type query bean.
   */
  protected void writeRootBeanConstructor() throws IOException {

    writer.append(NEWLINE);
    writer.append("  /**").append(NEWLINE);
    writer.append("   * Construct with a given EbeanServer.").append(NEWLINE);
    writer.append("   */").append(NEWLINE);
    writer.append("  public Q").append(shortName).append("(EbeanServer server) {").append(NEWLINE);
    writer.append("    super(").append(shortName).append(".class, server);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);
    writer.append(NEWLINE);

    writer.append("  /**").append(NEWLINE);
    writer.append("   * Construct using the default EbeanServer.").append(NEWLINE);
    writer.append("   */").append(NEWLINE);
    writer.append("  public Q").append(shortName).append("() {").append(NEWLINE);
    writer.append("    super(").append(shortName).append(".class);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);

    writer.append(NEWLINE);
    writer.append("  /**").append(NEWLINE);
    writer.append("   * Construct for Alias.").append(NEWLINE);
    writer.append("   */").append(NEWLINE);
    writer.append("  private Q").append(shortName).append("(boolean dummy) {").append(NEWLINE);
    writer.append("    super(dummy);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);
  }

  protected void writeAssocBeanFetch() throws IOException {

    if (isEntity()) {
      writer.append("  /**").append(NEWLINE);
      writer.append("   * Eagerly fetch this association loading the specified properties.").append(NEWLINE);
      writer.append("   */").append(NEWLINE);
      writer.append("  @SafeVarargs").append(NEWLINE);
      writer.append("  public final R fetch(TQProperty<Q").append(origShortName).append(">... properties) {").append(NEWLINE);
      writer.append("    return fetchProperties(properties);").append(NEWLINE);
      writer.append("  }").append(NEWLINE);
      writer.append(NEWLINE);
    }
  }

  /**
   * Write constructor for 'assoc' type query bean.
   */
  protected void writeAssocBeanConstructor() throws IOException {

    // minimal constructor
    writer.append("  public Q").append(shortName).append("(String name, R root) {").append(NEWLINE);
    writer.append("    super(name, root);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);
  }

  /**
   * Return true if this has at least one 'assoc' property.
   */
  protected boolean hasAssocProperties() {
    for (PropertyMeta property : properties) {
      if (property.isAssociation()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Write all the fields.
   */
  protected void writeFields() throws IOException {

    for (PropertyMeta property : properties) {
      property.writeFieldDefn(writer, shortName, writingAssocBean);
      writer.append(NEWLINE);
    }
    writer.append(NEWLINE);
  }

  /**
   * Write the class definition.
   */
  protected void writeClass() throws IOException {

    if (writingAssocBean) {
      writer.append("/**").append(NEWLINE);
      writer.append(" * Association query bean for ").append(shortName).append(".").append(NEWLINE);
      writer.append(" * ").append(NEWLINE);
      writer.append(" * THIS IS A GENERATED OBJECT, DO NOT MODIFY THIS CLASS.").append(NEWLINE);
      writer.append(" */").append(NEWLINE);
      //public class QAssocContact<R>
      writer.append("@TypeQueryBean").append(NEWLINE);
      writer.append("public class ").append("Q").append(shortName);
      writer.append("<R> extends TQAssocBean<").append(origShortName).append(",R> {").append(NEWLINE);

    } else {
      writer.append("/**").append(NEWLINE);
      writer.append(" * Query bean for ").append(shortName).append(".").append(NEWLINE);
      writer.append(" * ").append(NEWLINE);
      writer.append(" * THIS IS A GENERATED OBJECT, DO NOT MODIFY THIS CLASS.").append(NEWLINE);
      writer.append(" */").append(NEWLINE);
      //  public class QContact extends TQRootBean<Contact,QContact> {
      writer.append("@TypeQueryBean").append(NEWLINE);
      writer.append("public class ").append("Q").append(shortName)
          .append(" extends TQRootBean<").append(shortName).append(",Q").append(shortName).append("> {").append(NEWLINE);
    }

    writer.append(NEWLINE);
  }

  protected void writeAlias() throws IOException {
    if (!writingAssocBean) {
      writer.append("  private static final Q").append(shortName).append(" _alias = new Q");
      writer.append(shortName).append("(true);").append(NEWLINE);
      writer.append(NEWLINE);

      writer.append("  /**").append(NEWLINE);
      writer.append("   * Return the shared 'Alias' instance used to provide properties to ").append(NEWLINE);
      writer.append("   * <code>select()</code> and <code>fetch()</code> ").append(NEWLINE);
      writer.append("   */").append(NEWLINE);
      writer.append("  public static Q").append(shortName).append(" alias() {").append(NEWLINE);
      writer.append("    return _alias;").append(NEWLINE);
      writer.append("  }").append(NEWLINE);
      writer.append(NEWLINE);
    }
  }

  protected void writeClassEnd() throws IOException {
    writer.append("}").append(NEWLINE);
  }

  /**
   * Write all the imports.
   */
  protected void writeImports() throws IOException {

    for (String importType : importTypes) {
      writer.append("import ").append(importType).append(";").append(NEWLINE);
    }
    writer.append(NEWLINE);
  }

  protected void writePackage() throws IOException {
    writer.append("package ").append(destPackage).append(";").append(NEWLINE).append(NEWLINE);
  }


  protected Writer createFileWriter() throws IOException {

//    String fileName = "Q"+shortName+".java";

    JavaFileObject jfo = processingContext.createWriter(destPackage + "." + "Q" + shortName);
    return jfo.openWriter();

//    String destDirectory = config.getDestDirectory();
//    File destDir = new File(destDirectory);
//
//    String packageAsDir = asSlashNotation(destPackage);
//
//    File packageDir = new File(destDir, packageAsDir);
//    if (!packageDir.exists() && !packageDir.mkdirs()) {
//      logger.logError("Failed to create directory [{}] for generated code", packageDir.getAbsoluteFile());
//    }
//
//    File dest = new File(packageDir, fileName);
//
//    logger.info("writing {}", dest.getAbsolutePath());
//
//    return new FileWriter(dest);
  }

  protected String asDotNotation(String path) {
    return path.replace('/', '.');
  }

  protected String asSlashNotation(String path) {
    return path.replace('.', '/');
  }

  protected String derivePackage(String name) {
    int pos = name.lastIndexOf('.');
    if (pos == -1) {
      return "";
    }
    return name.substring(0, pos);
  }
  protected String deriveShortName(String name) {
    int pos = name.lastIndexOf('.');
    if (pos == -1) {
      return name;
    }
    return name.substring(pos+1);
  }
}
