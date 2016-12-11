package io.ebean.querybean.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Property type definition.
 */
public class PropertyType {

  public static final String NEWLINE = SimpleQueryBeanWriter.NEWLINE;

  /**
   * The property type className or primitive short name.
   */
  protected final String propertyType;

  /**
   * Construct with a className of primitive name for the type.
   */
  public PropertyType(String propertyType) {
    this.propertyType = propertyType;
  }

  /**
   * Return true if this is an association type.
   */
  public boolean isAssociation() {
    // overridden by PropertyTypeAssoc
    return false;
  }

  /**
   * Return the type definition for this property.
   *
   * @param shortName The short name of the property type
   * @param assoc     flag set to true if the property is on an association bean
   */
  public String getTypeDefn(String shortName, boolean assoc) {
    if (assoc) {
      //    PLong<R>
      return propertyType + "<R>";

    } else {
      //    PLong<QCustomer>
      return propertyType + "<Q" + shortName + ">";
    }
  }

  /**
   * Add any required imports for this property to the allImports set.
   */
  public void addImports(Set<String> allImports) {

    allImports.add("io.ebean.typequery." + propertyType);
  }

  /**
   * Write the constructor source code.
   *
   * @param writer The writer java source code is written to
   * @param name   the property name
   * @param assoc  if true the property is on an a associated bean (not at root level)
   */
  public void writeConstructor(Writer writer, String name, boolean assoc) throws IOException {

    //PLong<>("id", this);
    //PLong<>("id", root, path);

    writer.append(propertyType).append("<>(\"").append(name).append("\"");
    if (assoc) {
      writer.append(", root, path);").append(NEWLINE);

    } else {
      writer.append(", this);").append(NEWLINE);
    }
  }
}
