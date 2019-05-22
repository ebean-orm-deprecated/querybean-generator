package io.ebean.querybean.generator;


import io.ebean.annotation.DbName;

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

import static io.ebean.querybean.generator.Constants.AT_GENERATED;
import static io.ebean.querybean.generator.Constants.AT_TYPEQUERYBEAN;
import static io.ebean.querybean.generator.Constants.DATABASE;
import static io.ebean.querybean.generator.Constants.DB;
import static io.ebean.querybean.generator.Constants.GENERATED;
import static io.ebean.querybean.generator.Constants.TQASSOCBEAN;
import static io.ebean.querybean.generator.Constants.TQPROPERTY;
import static io.ebean.querybean.generator.Constants.TQROOTBEAN;
import static io.ebean.querybean.generator.Constants.TYPEQUERYBEAN;

/**
 * A simple implementation that generates and writes query beans.
 */
class SimpleQueryBeanWriter {

  static final String NEWLINE = "\n";

  private final Set<String> importTypes = new TreeSet<>();

  private final List<PropertyMeta> properties = new ArrayList<>();

  private final TypeElement element;

  private final ProcessingContext processingContext;

  private final String dbName;
  private final String beanFullName;
  private boolean writingAssocBean;

  private String destPackage;
  private String origDestPackage;

  private String shortName;
  private String origShortName;

  private Append writer;


  SimpleQueryBeanWriter(TypeElement element, ProcessingContext processingContext) {
    this.element = element;
    this.processingContext = processingContext;

    final DbName name = processingContext.findAnnotation(element, DbName.class);
    this.dbName = (name == null) ? null : name.value();
    this.beanFullName = element.getQualifiedName().toString();
    this.destPackage = derivePackage(beanFullName) + ".query";
    this.shortName = deriveShortName(beanFullName);
  }

  private void gatherPropertyDetails() {

    importTypes.add(beanFullName);
    if (processingContext.isGeneratedAvailable()) {
      importTypes.add(GENERATED);
    }
    importTypes.add(TQROOTBEAN);
    importTypes.add(TYPEQUERYBEAN);
    importTypes.add(DATABASE);
    if (dbName != null) {
      importTypes.add(DB);
    }

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
      writer = new Append(createFileWriter());

      writePackage();
      writeImports();
      writeClass();
      writeAlias();
      writeFields();
      writeConstructors();
      writeStaticAliasClass();
      writeClassEnd();

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
    destPackage = destPackage + ".assoc";
    origShortName = shortName;
    shortName = "Assoc" + shortName;

    prepareAssocBeanImports();

    writer = new Append(createFileWriter());

    writePackage();
    writeImports();
    writeClass();
    writeFields();
    writeConstructors();
    writeClassEnd();

    writer.close();
  }

  /**
   * Prepare the imports for writing assoc bean.
   */
  private void prepareAssocBeanImports() {

    importTypes.remove(DB);
    importTypes.remove(TQROOTBEAN);
    importTypes.remove(DATABASE);
    importTypes.add(TQASSOCBEAN);
    if (isEntity()) {
      importTypes.add(TQPROPERTY);
      importTypes.add(origDestPackage + ".Q" + origShortName);
    }

    // remove imports for the same package
    Iterator<String> importsIterator = importTypes.iterator();
    String checkImportStart = destPackage + ".QAssoc";
    while (importsIterator.hasNext()) {
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

    writer.eol();
    writer.append("  /**").eol();
    writer.append("   * Construct with a given Database.").eol();
    writer.append("   */").eol();
    writer.append("  public Q%s(Database server) {", shortName).eol();
    writer.append("    super(%s.class, server);", shortName).eol();
    writer.append("  }").eol();
    writer.eol();

    String name = (dbName == null) ? "default" : dbName;
    writer.append("  /**").eol();
    writer.append("   * Construct using the %s Database.", name).eol();
    writer.append("   */").eol();
    writer.append("  public Q%s() {", shortName).eol();
    if (dbName == null) {
      writer.append("    super(%s.class);", shortName).eol();
    } else {
      writer.append("    super(%s.class, DB.byName(\"%s\"));", shortName, dbName).eol();
    }
    writer.append("  }").eol();

    writer.eol();
    writer.append("  /**").eol();
    writer.append("   * Construct for Alias.").eol();
    writer.append("   */").eol();
    writer.append("  private Q%s(boolean dummy) {", shortName).eol();
    writer.append("    super(dummy);").eol();
    writer.append("  }").eol();
  }

  private void writeAssocBeanFetch() {

    if (isEntity()) {
      writeAssocBeanFetch("", "Eagerly fetch this association loading the specified properties.");
      writeAssocBeanFetch("Query", "Eagerly fetch this association using a 'query join' loading the specified properties.");
      writeAssocBeanFetch("Lazy", "Use lazy loading for this association loading the specified properties.");
    }
  }

  private void writeAssocBeanFetch(String fetchType, String comment) {

    writer.append("  /**").eol();
    writer.append("   * ").append(comment).eol();
    writer.append("   */").eol();
    writer.append("  @SafeVarargs").eol();
    writer.append("  public final R fetch%s(TQProperty<Q%s>... properties) {", fetchType, origShortName).eol();
    writer.append("    return fetch%sProperties(properties);", fetchType).eol();
    writer.append("  }").eol();
    writer.eol();
  }

  /**
   * Write constructor for 'assoc' type query bean.
   */
  private void writeAssocBeanConstructor() {
    writer.append("  public Q%s(String name, R root) {", shortName).eol();
    writer.append("    super(name, root);").eol();
    writer.append("  }").eol().eol();

    writer.append("  public Q%s(String name, R root, String prefix) {", shortName).eol();
    writer.append("    super(name, root, prefix);").eol();
    writer.append("  }").eol();
  }

  /**
   * Write all the fields.
   */
  private void writeFields() throws IOException {

    for (PropertyMeta property : properties) {
      property.writeFieldDefn(writer, shortName, writingAssocBean);
      writer.eol();
    }
    writer.eol();
  }

  /**
   * Write the class definition.
   */
  private void writeClass() {

    if (writingAssocBean) {
      writer.append("/**").eol();
      writer.append(" * Association query bean for %s.", shortName).eol();
      writer.append(" * ").eol();
      writer.append(" * THIS IS A GENERATED OBJECT, DO NOT MODIFY THIS CLASS.").eol();
      writer.append(" */").eol();
      if (processingContext.isGeneratedAvailable()) {
        writer.append(AT_GENERATED).eol();
      }
      writer.append(AT_TYPEQUERYBEAN).eol();
      writer.append("public class Q%s<R> extends TQAssocBean<%s,R> {", shortName, origShortName).eol();

    } else {
      writer.append("/**").eol();
      writer.append(" * Query bean for %s.", shortName).eol();
      writer.append(" * ").eol();
      writer.append(" * THIS IS A GENERATED OBJECT, DO NOT MODIFY THIS CLASS.").eol();
      writer.append(" */").eol();
      if (processingContext.isGeneratedAvailable()) {
        writer.append(AT_GENERATED).eol();
      }
      writer.append(AT_TYPEQUERYBEAN).eol();
      writer.append("public class Q%s extends TQRootBean<%1$s,Q%1$s> {", shortName).eol();
    }

    writer.eol();
  }

  private void writeAlias() throws IOException {
    if (!writingAssocBean) {
      writer.append("  private static final Q%s _alias = new Q%1$s(true);", shortName).eol().eol();

      writer.append("  /**").eol();
      writer.append("   * Return the shared 'Alias' instance used to provide properties to ").eol();
      writer.append("   * <code>select()</code> and <code>fetch()</code> ").eol();
      writer.append("   */").eol();
      writer.append("  public static Q%s alias() {", shortName).eol();
      writer.append("    return _alias;").eol();
      writer.append("  }").eol();
      writer.eol();
    }
  }

  private void writeStaticAliasClass() throws IOException {

    writer.eol();
    writer.append("  /**").eol();
    writer.append("   * Provides static properties to use in <em> select() and fetch() </em>").eol();
    writer.append("   * clauses of a query. Typically referenced via static imports. ").eol();
    writer.append("   */").eol();
    writer.append("  public static class Alias {").eol();
    for (PropertyMeta property : properties) {
      property.writeFieldAliasDefn(writer, shortName);
      writer.eol();
    }
    writer.append("  }").eol();
  }

  private void writeClassEnd() {
    writer.append("}").eol();
  }

  /**
   * Write all the imports.
   */
  private void writeImports() {

    for (String importType : importTypes) {
      writer.append("import %s;", importType).eol();
    }
    writer.eol();
  }

  private void writePackage() {
    writer.append("package %s;", destPackage).eol().eol();
  }


  private Writer createFileWriter() throws IOException {
    JavaFileObject jfo = processingContext.createWriter(destPackage + "." + "Q" + shortName, element);
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
    return name.substring(pos + 1);
  }
}
