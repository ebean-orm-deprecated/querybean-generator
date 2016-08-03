package org.avaje.ebean.querybean.generator;


import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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
class SimpleQueryBeanWriter {

  static final String NEWLINE = "\n";

  private final Set<String> importTypes = new TreeSet<>();

  private final List<PropertyMeta> properties = new ArrayList<>();

  private final TypeElement element;

  private final ProcessingContext processingContext;

  private final String beanFullName;
  private boolean writingAssocBean;

  private String destPackage;
  private String origDestPackage;

  private String shortName;
  private String origShortName;

  private Writer writer;


  SimpleQueryBeanWriter(TypeElement element, ProcessingContext processingContext) {
    this.element = element;
    this.processingContext = processingContext;

    this.beanFullName = element.getQualifiedName().toString();
    this.destPackage = derivePackage(beanFullName)+".query";
    this.shortName = deriveShortName(beanFullName);

    processingContext.addPackage(destPackage);
  }

  private void gatherPropertyDetails() {

    importTypes.add(beanFullName);
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
  private void addClassProperties() {

    List<VariableElement> fields = processingContext.allFields(element);

    for (VariableElement field : fields) {
      PropertyType type = processingContext.getPropertyType(field);
      if (type != null) {
        type.addImports(importTypes);
        properties.add(new PropertyMeta(field.getSimpleName().toString(), type));
      }
    }
  }

  /**
   * Write the type query bean (root bean).
   */
  void writeRootBean() throws IOException {

    gatherPropertyDetails();

    if (isEntity()) {
      writer = createFileWriter();

      writePackage();
      writeImports();
      writeClass();
      writeAlias();
      writeFields();
      writeConstructors();
      writeStaticAliasClass();
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
  void writeAssocBean() throws IOException {

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
  private void prepareAssocBeanImports() {

    importTypes.remove("org.avaje.ebean.typequery.TQRootBean");
    importTypes.remove("com.avaje.ebean.EbeanServer");
    importTypes.add("org.avaje.ebean.typequery.TQAssocBean");
    if (isEntity()) {
      importTypes.add("org.avaje.ebean.typequery.TQProperty");
      importTypes.add(origDestPackage + ".Q" + origShortName);
    }

    // remove imports for the same package
    Iterator<String> importsIterator = importTypes.iterator();
    String checkImportStart = destPackage + ".QAssoc";
    while (importsIterator.hasNext()){
      String importType = importsIterator.next();
      if (importType.startsWith(checkImportStart)) {
        importsIterator.remove();
      }
    }
  }

  /**
   * Write constructors.
   */
  private void writeConstructors() throws IOException {

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
  private void writeRootBeanConstructor() throws IOException {

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

  private void writeAssocBeanFetch() throws IOException {

    if (isEntity()) {
      writeAssocBeanFetch("", "Eagerly fetch this association loading the specified properties.");
      writeAssocBeanFetch("Query", "Eagerly fetch this association using a 'query join' loading the specified properties.");
      writeAssocBeanFetch("Lazy", "Use lazy loading for this association loading the specified properties.");
    }
  }

  private void writeAssocBeanFetch(String fetchType, String comment) throws IOException {

    writer.append("  /**").append(NEWLINE);
    writer.append("   * ").append(comment).append(NEWLINE);
    writer.append("   */").append(NEWLINE);
    writer.append("  @SafeVarargs").append(NEWLINE);
    writer.append("  public final R fetch").append(fetchType).append("(TQProperty<Q").append(origShortName).append(">... properties) {").append(NEWLINE);
    writer.append("    return fetch").append(fetchType).append("Properties(properties);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);
    writer.append(NEWLINE);
  }

  /**
   * Write constructor for 'assoc' type query bean.
   */
  private void writeAssocBeanConstructor() throws IOException {

    // minimal constructor
    writer.append("  public Q").append(shortName).append("(String name, R root) {").append(NEWLINE);
    writer.append("    super(name, root);").append(NEWLINE);
    writer.append("  }").append(NEWLINE);
  }

  /**
   * Write all the fields.
   */
  private void writeFields() throws IOException {

    for (PropertyMeta property : properties) {
      property.writeFieldDefn(writer, shortName, writingAssocBean);
      writer.append(NEWLINE);
    }
    writer.append(NEWLINE);
  }

  /**
   * Write the class definition.
   */
  private void writeClass() throws IOException {

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

  private void writeAlias() throws IOException {
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

  private void writeStaticAliasClass() throws IOException {

    writer.append(NEWLINE);
    writer.append("  /**").append(NEWLINE);
    writer.append("   * Provides static properties to use in <em> select() and fetch() </em>").append(NEWLINE);
    writer.append("   * clauses of a query. Typically referenced via static imports. ").append(NEWLINE);
    writer.append("   */").append(NEWLINE);
    writer.append("  public static class Alias {").append(NEWLINE);
    for (PropertyMeta property : properties) {
      property.writeFieldAliasDefn(writer, shortName);
      writer.append(NEWLINE);
    }
    writer.append("  }").append(NEWLINE);
  }

  private void writeClassEnd() throws IOException {
    writer.append("}").append(NEWLINE);
  }

  /**
   * Write all the imports.
   */
  private void writeImports() throws IOException {

    for (String importType : importTypes) {
      writer.append("import ").append(importType).append(";").append(NEWLINE);
    }
    writer.append(NEWLINE);
  }

  private void writePackage() throws IOException {
    writer.append("package ").append(destPackage).append(";").append(NEWLINE).append(NEWLINE);
  }


  private Writer createFileWriter() throws IOException {

    JavaFileObject jfo = processingContext.createWriter(destPackage + "." + "Q" + shortName);
    return jfo.openWriter();
  }

  private String derivePackage(String name) {
    int pos = name.lastIndexOf('.');
    if (pos == -1) {
      return "";
    }
    return name.substring(0, pos);
  }

  private String deriveShortName(String name) {
    int pos = name.lastIndexOf('.');
    if (pos == -1) {
      return name;
    }
    return name.substring(pos+1);
  }
}
